package org.openstreetmap.josm.plugins.lanes;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Shortcut;

import javax.swing.*;
import javax.swing.event.ChangeListener;

/*
 * Lane-Mapping Map Mode - allows for the lanes of highways to be viewed and edited directly on the map.
 *
 * -> The LanesPlugin class is only run when JOSM boots up.
 * -> This class is for entering the lane mapping mode and handling all of the rendered roads.
 * -> RoadRenderer is a class that represents 1 OSM way, and handles all rendering of that way.
 *
 * This class stores a list of RoadRenderers, and calls on each of them each time paint() is called.
 */

public class LaneMappingMode extends MapMode implements MouseListener, MouseMotionListener,
        MapViewPaintable, UndoRedoHandler.CommandQueuePreciseListener {

    private List<RoadRenderer> roads = null;
    private List<IntersectionRenderer> intersections = null;
    private MapView _mv;
    private List<Way> _ways = new ArrayList<>();
    private List<ChangeListener> _closePopups = new ArrayList<>(); // For closing pop-ups when Lane editing mode is left.
    private List<ActionListener> _updatePopups = new ArrayList<>(); // For updating the lane diagram on the popups.
    public Map<Long, RoadRenderer> wayIdToRSR = new HashMap<>();
    public Map<Long, IntersectionRenderer> nodeIdToISR = new HashMap<>();
    private int mapChangeTolerance = 0; // Other objects can increase this by X to make it ignore the next X times the dataset changes.

    public LaneMappingMode() {
        super(tr("Lane Editing"), "laneconnectivity.png", tr("Activate lane editing mode"),
                Shortcut.registerShortcut("mapmode:lanemapping", tr("Mode: {0}",
                tr("Lane Editing Mode")), KeyEvent.VK_2, Shortcut.SHIFT),
                Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    /**
     * Paints each child RoadRenderer and IntersectionRenderer
     * @param g The graphics to paint on.
     * @param mv The object that can translate GeoPoints to screen coordinates.
     * @param bbox The Bounding box
     */
    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        if (mv == null || mv.getScale() > 16) return; // Don't render when the map is too zoomed out

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double cushion = 200;
        _mv = mv;

        // Get map data for rendering:
        ensureRoadSegmentsNotNull();

        // Get bounds where rendering should happen
        ProjectionBounds bounds = mv.getProjectionBounds();
        bounds = new ProjectionBounds(bounds.minEast - cushion,
                bounds.minNorth - cushion,
                bounds.maxEast + cushion,
                bounds.maxNorth + cushion);

        // Render intersections
        for (IntersectionRenderer i : intersections) {
            if (intersectionShouldBeRendered(bounds, i)) {
                try {
                    i.render(g);
                } catch (Exception ignored) {}
            }
        }

        // Render each road
        for (RoadRenderer r : roads) {
            if (wayShouldBeRendered(bounds, r.getWay())) {
                try {
                    r.render(g);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void enterMode() {
        super.enterMode();
        roads = null;

        if (getLayerManager().getEditDataSet() == null) return;

        MapFrame map = MainApplication.getMap();
        map.mapView.addMouseListener(this);
        map.mapView.addTemporaryLayer(this);
        UndoRedoHandler.getInstance().addCommandQueuePreciseListener(this);
        updateStatusLine();
    }

    @Override
    public void exitMode() {
        super.exitMode();

        MapFrame map = MainApplication.getMap();
        map.mapView.removeMouseListener(this);
        map.mapView.removeTemporaryLayer(this);
        try { UndoRedoHandler.getInstance().removeCommandQueuePreciseListener(this); } catch (Exception ignored) {}
        for (ChangeListener c : _closePopups) c.stateChanged(null);
        _closePopups = new ArrayList<>();
        _updatePopups = new ArrayList<>();
    }

    /**
     * Popups can add listeners here so they close when the user leaves the mode.
     * @param c The listener that gets called when the user leaves LaneMappingMode.
     */
    public void addQuitListener(ChangeListener c) {
        _closePopups.add(c);
    }

    /**
     * Popups can add listeners here so they update just after the dataset is updated.
     * @param a The listener, called whenever the list of RoadRenderers is updated.
     */
    public void addUpdateListener(ActionListener a) {
        _updatePopups.add(a);
    }

    /**
     * Popups can remove listeners here so they no longer update just after the dataset is updated.
     * @param a The listener to be removed.
     */
    public void removeUpdateListener(ActionListener a) {
        _updatePopups.remove(a);
    }

    // <editor-fold defaultstate="collapsed" desc="Boring Methods">

    @Override
    public boolean layerIsSupported(Layer l) {
        return l instanceof OsmDataLayer;
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditLayer() != null);
    }

    /**
     * Determines whether way is on the map and should be rendered
     * (will sometimes return false incorrectly for ways with long segments)
     * @param bounds The bounds of the MapView (or something slightly larger).
     * @param w The way to be rendered.
     * @return True if way has node inside of the bounds, false otherwise.
     */
    private boolean wayShouldBeRendered(ProjectionBounds bounds, Way w) {
        for (int i = 0; i < w.getNodesCount(); i++) {
            if (bounds.contains(w.getNode(i).getEastNorth())) {
                return true;
            }
        }
        return false;
    }

    private boolean intersectionShouldBeRendered(ProjectionBounds bounds, IntersectionRenderer i) {
        double minEast = bounds.minEast - 100;
        double maxEast = bounds.maxEast + 100;
        double minNorth = bounds.minNorth - 100;
        double maxNorth = bounds.maxNorth + 100;

        ProjectionBounds bigger = new ProjectionBounds(minEast, minNorth, maxEast, maxNorth);
        return bigger.contains(new Node(i.getPos()).getEastNorth());
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for Building RoadSegmentRenderers">

    /**
     * Builds the RoadRenderers / IntersectionRenderers if they are currently null.
     */
    private void ensureRoadSegmentsNotNull() {
        if (roads == null || intersections == null) {
            roads = getAllRoadRenderers(new ArrayList<>(MainApplication.getLayerManager().getEditDataSet().getWays()), _mv);
            intersections = getAllIntersections(_mv);
        }
    }

    /**
     * Generates a list of RoadRenderers for the input ways.
     * @param ways The list of ways to make RoadRenderers out of.
     * @param mv The MapView each RoadRenderer should use.
     * @return The list of created RoadRenderers.
     */
    private List<RoadRenderer> getAllRoadRenderers(List<Way> ways, MapView mv) {
        _ways = ways;
        wayIdToRSR = new Hashtable<>();
        List<RoadRenderer> output = new Vector<>();

        // Generate each RoadRenderer.
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        for (Way w : ways) {
            Runnable r = () -> {
                try {
                    RoadRenderer rr = RoadRenderer.buildRoadRenderer(w, mv, this);
                    if (rr == null) return;
                    wayIdToRSR.put(w.getUniqueId(), rr);
                    output.add(rr);
                } catch (Exception ignored) {}
            };
            executor.execute(r);
        }
        executor.shutdown();
        try { executor.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        // Give each RoadRenderer a chance to look at roads at endpoints and adjust endpoint angles.
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        for (RoadRenderer rr : output) {
            Runnable r = () -> {
                try {
                    rr.updateAlignment(); // updates alignment based on nearby ways.
                } catch (Exception ignored) {}
            };
            executor.execute(r);
        }

        executor.shutdown();
        try { executor.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}


        return output;
    }

    /**
     * Generates a list of InterSectionRenderers based on the list of
     * @param mv The MapView each IntersectionRenderer should use.
     * @return The created list of IntersectionRenderers.
     */
    private List<IntersectionRenderer> getAllIntersections(MapView mv) {

        // Get all node-only intersections.
        nodeIdToISR = new Hashtable<>();
        Set<Long> handled = new HashSet();
        List<NodeIntersectionRenderer> intersections = new Vector<>();
        if (roads == null) throw new RuntimeException("RoadRenderers not initialized before calling getAllIntersections().");


        // Get node-only intersections.
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        for (RoadRenderer r : roads) {
            for (Node n : r.getWay().getNodes()) {
                if (!handled.contains(n.getUniqueId())) {
                    handled.add(n.getUniqueId());
                    executor.execute(() -> {
                        Utils.WayConnectionType type = Utils.calculateNodeIntersectionType(n, this);
                        if (type != null && type != Utils.WayConnectionType.INCOMPLETE && type != Utils.WayConnectionType.CONTINUATION) System.out.println("Node " + n.getId() + " is " + type);
                        if (type == Utils.WayConnectionType.INTERSECTION) {
//                        try {
                            NodeIntersectionRenderer newest = new NodeIntersectionRenderer(n, mv, this);
                            intersections.add(newest);
                            nodeIdToISR.put(n.getUniqueId(), newest);
//                        } catch (Exception ignored) {}
                        }
                    });
                }
            }
        }

        executor.shutdown();
        try { executor.awaitTermination(60, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        intersections.sort(Comparator.comparingDouble(o -> o.getPos().lat()));


        // Merge overlapping node-only intersections.
        int[] groups = new int[intersections.size()];
        int max = 0;
        int[] thisGroup = new int[10]; // Max num of existing separate intersections this node intersection can be merged with. Max real world is usually like 3.
        for (int i = 1; i < intersections.size(); i++) {
            int num = 0;
            // Go back along i until an intersect is found:
            // Max diff in lat is 0.001.
            for (int j = i-1; j >= 0; j--) {
                if (Math.abs(intersections.get(j).getPos().lat()-intersections.get(i).getPos().lat()) > 0.0005) break; // Don't try to connect two intersections that are more than ~150 ft apart.
                if (sameIntersection(intersections.get(j), intersections.get(i))) {
                    thisGroup[num] = groups[j];
                    num++;
                }
            }
            if (num == 0) {
                max++;
                groups[i] = max;
            } else {
                groups[i] = thisGroup[0];

                // Now, check to see if two groups need to be merged.
                if (num > 1) {
                    for (int j = 0; j < i; j++) {
                        for (int k = 1; k < num; k++) {
                            if (groups[j] == thisGroup[k]) {
                                groups[j] = thisGroup[0];
                                break;
                            }
                        }
                    }
                }
            }
        }

        List<NodeIntersectionRenderer>[] multiNodeCollections = new List[max+1];
        for (int i = 0; i < intersections.size(); i++) {
            if (multiNodeCollections[groups[i]] == null) multiNodeCollections[groups[i]] = new ArrayList<>();
            multiNodeCollections[groups[i]].add(intersections.get(i));
        }

        List<IntersectionRenderer> out = new Vector<>();
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        for (List<NodeIntersectionRenderer> group : multiNodeCollections) {
            if (group != null) {
                executor.execute(() -> {
                    try {
                        new MultiIntersectionRenderer(group, out);
                    } catch (Exception ignored) {}
                });
            }
        }

        executor.shutdown();
        try { executor.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        nodeIdToISR = new HashMap<>();

        for (IntersectionRenderer m : out) for (long l : ((MultiIntersectionRenderer) m).getNodeIntersections()) nodeIdToISR.put(l, m);

        return out;
    }

    private boolean sameIntersection(NodeIntersectionRenderer a, NodeIntersectionRenderer b) {
        if (a.getPos().greatCircleDistance(b.getPos()) > 100) return false;
        if (a.getPos().greatCircleDistance(b.getPos()) < 15) return true;
        if (Utils.intersect(a._lowResOutline, b._lowResOutline, new double[2], false, 0, false, false) == null) return false;
        return true;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for Handling Dataset Changes">

    public void forceUpdateIgnore(int num) {
        mapChangeTolerance += num;
    }

    @Override
    public void commandAdded(UndoRedoHandler.CommandAddedEvent e) {
        updateDataset();
    }

    @Override
    public void cleaned(UndoRedoHandler.CommandQueueCleanedEvent e) {
        updateDataset();
    }

    @Override
    public void commandUndone(UndoRedoHandler.CommandUndoneEvent e) {
        updateDataset();
    }

    @Override
    public void commandRedone(UndoRedoHandler.CommandRedoneEvent e) {
        updateDataset();
    }

    /**
     * Forces the dataset to be updated next time paint() is called.
     * If the forceUpdateIgnore count is more than 0, this will take
     * one away from the count instead of updating the dataset.
     */
    private void updateDataset() {
        // Don't update dataset when the lower level objects have requested to do it themselves
        mapChangeTolerance--;
        if (mapChangeTolerance >= 0) return;
        mapChangeTolerance = 0;

        roads = null;
        intersections = null;
        ensureRoadSegmentsNotNull();
        Object[] updatePopups = _updatePopups.toArray();
        for (Object a : updatePopups) ((ActionListener) a).actionPerformed(null);
    }

    /**
     * Updates this way and the nearby intersections.
     * Only to be used on ways who's alignments or tags were changed.
     * If the way got deleted, this will not work.
     * @param uniqueID The unique id of the Way
     */
    public void updateOneRoad(long uniqueID) {
        Way w = null;
        for (Way way : _ways) if (way.getUniqueId() == uniqueID) w = way;
        if (w == null || !wayIdToRSR.containsKey(uniqueID)) { // Way deleted/never existed, use slow method instead.
            mapChangeTolerance = 0;
            updateDataset();
            return;
        }

        // Replace the single roadRenderer:
        RoadRenderer replacementRR = RoadRenderer.buildRoadRenderer(w, _mv, this);
        if (replacementRR != null) {
            roads.remove(wayIdToRSR.get(uniqueID));
            wayIdToRSR.put(uniqueID, replacementRR);
            roads.add(replacementRR);
            replacementRR.updateAlignment();
        }

        // Find all nearby intersections and prepare to update them.
        List<IntersectionRenderer> intersectionsToUpdate = new ArrayList<>();
        List<RoadRenderer> roadsToUpdate = new ArrayList<>();
        for (Node n : w.getNodes()) {
            if (nodeIdToISR.containsKey(n.getUniqueId())) {
                for (Way w2 : n.getParentWays()) {
                    if (wayIdToRSR.containsKey(w2.getUniqueId())) {
                        roadsToUpdate.add(wayIdToRSR.get(w2.getUniqueId()));
                        for (Node n2 : w2.getNodes()) {
                            if (nodeIdToISR.containsKey(n2.getUniqueId())) {
                                intersectionsToUpdate.add(nodeIdToISR.get(n2.getUniqueId()));
                            }
                        }
                    }
                }
            }
        }

        // For all roads that were part of one of those intersections, reset all hidden sections.
        for (RoadRenderer r : roadsToUpdate) r.resetRenderingGaps();

        // For all intersections near on the those roads, update their alignments.
        for (IntersectionRenderer i : intersectionsToUpdate) i.updateAlignment();
    }

    // </editor-fold>

    // <editor-fold desc="Methods for Handling Mouse Events">

    @Override
    public void mousePressed(MouseEvent e) {
        ensureRoadSegmentsNotNull();
        RoadRenderer r = getShortestSegmentMouseEvent(e);
        if (r != null) {
            r.mousePressed(e);
        } else {
            MainApplication.getLayerManager().getActiveData().clearSelection();
        }
        _mv.repaint();
    }

    // <editor-fold defaultstate="collapsed" desc="Unused mouse listener methods">
    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseMoved(MouseEvent e) {}
    // </editor-fold>

    /**
     * Finds the shortest RoadRenderer overlapping with the coordinates of the MouseEvent.
     * @param e The event used to determine which RoadRenderers could have been clicked.
     * @return The shortest RoadRenderer clicked by the MouseEvent.
     */
    private RoadRenderer getShortestSegmentMouseEvent(MouseEvent e) {
        ensureRoadSegmentsNotNull();

        RoadRenderer min = null;
        for (RoadRenderer r : roads) {
            try {
                if (Utils.mouseEventIsInside(e, r.getAsphaltOutlinePixels(), _mv) && (min == null || r.getWay().getLength() < min.getWay().getLength())) {
                    min = r;
                }
            } catch (Exception ignored) {} // When the alignment is invalid, aka it goes from 0 to 21 lanes wide in 10 meters, this catches the exception.
        }
        return min;
    }

    // </editor-fold>


}