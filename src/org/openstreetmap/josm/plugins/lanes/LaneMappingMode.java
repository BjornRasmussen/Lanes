package org.openstreetmap.josm.plugins.lanes;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.*;
import java.awt.event.*;

import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.*;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.osm.event.*;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import org.openstreetmap.josm.tools.Shortcut;

import javax.swing.event.ChangeListener;

/*
 * Lane-Mapping Map Mode - allows for the lanes of highways to be viewed and edited directly on the map.
 *
 * -> The LanesPlugin class is only run when JOSM boots up.
 * -> This class is for entering the lane mapping mode and handling all of the rendered roads.
 *
 * This class stores a list of RoadRenderers, and calls on each of them each time paint() is called.
 */

public class LaneMappingMode extends MapMode implements MapViewPaintable {
    // List of roads and intersections currently active in plugin.
    private List<RoadRenderer> _roads = null;
    private List<IntersectionRenderer> _intersections = null;

    // Faster way of converting ids to Render objects.
    public Map<Long, RoadRenderer> wayIdToRSR = new HashMap<>();
    public Map<Long, IntersectionRenderer> nodeIdToISR = new HashMap<>();

    // MapView, used for finding the connection between pixels on the screen and coordinates on earth.
    public MapView _mv;

    // Listener lists
    private List<ChangeListener> _modeExited; // For closing pop-ups when Lane editing mode is left.
    private List<ActionListener> _dataChanged; // For updating the lane diagram on the popups.

    // Listeners (stored here so they can be removed on exit)
    private MouseListener _ml;
    private UndoRedoHandler.CommandQueuePreciseListener _cqpl;
    private DataSetListener _dsl;

    // When the dataset changes, the entire structure of RoadRenderers and IntersectionRenderers gets updated.
    // This usually takes about a second, which is slow when the user is just changing the number of lanes or something.
    // This override allows popups to prevent X updates from happening, instead allowing them to directly change just one detail,
    // which is way faster.
    private int mapChangeTolerance = 0;


    public LaneMappingMode() {
        super(tr("Lane Editing"), "laneconnectivity.png", tr("Activate lane editing mode"),
                Shortcut.registerShortcut("mapmode:lanemapping", tr("Mode: {0}",
                tr("Lane Editing Mode")), KeyEvent.VK_2, Shortcut.SHIFT),
                Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    @Override
    public void enterMode() {
        super.enterMode();

        // Force regeneration of all roads and intersections.
        _roads = null;

        // Adding this as a temporary layer is what makes drawing on top of everything else possible.
        MainApplication.getMap().mapView.addTemporaryLayer(this);

        // Create listener objects
        // <editor-fold defaultstate="collapsed" desc="Anonymous Listeners">
        _dsl = new DataSetListener() {
            @Override
            public void primitivesAdded(PrimitivesAddedEvent event) {

            }

            @Override
            public void primitivesRemoved(PrimitivesRemovedEvent event) {

            }

            @Override
            public void tagsChanged(TagsChangedEvent event) {

            }

            @Override
            public void nodeMoved(NodeMovedEvent event) {

            }

            @Override
            public void wayNodesChanged(WayNodesChangedEvent event) {

            }

            @Override
            public void relationMembersChanged(RelationMembersChangedEvent event) {

            }

            @Override
            public void otherDatasetChange(AbstractDatasetChangedEvent event) {

            }

            @Override
            public void dataChanged(DataChangedEvent event) {
                updateDataset();
            }
        };
        _ml = new MouseListener() {
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

            // <editor-fold defaultstate="collapsed" desc="Unused Listener Methods">
            @Override
            public void mouseClicked(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
            // </editor-fold>
        };
        _cqpl = new UndoRedoHandler.CommandQueuePreciseListener() {
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
        };
        // </editor-fold>

        // Add them to notifiers.
        MainApplication.getMap().mapView.addMouseListener(_ml);
        UndoRedoHandler.getInstance().addCommandQueuePreciseListener(_cqpl);
        MainApplication.getLayerManager().getEditDataSet().addDataSetListener(_dsl);

        // Reset _modeEntered and _modeExited listener lists
        _modeExited = new ArrayList<>();
        _dataChanged = new ArrayList<>();

        // I don't think this is needed, but I might add some status line stuff later, so who knows.
        updateStatusLine();
    }

    @Override
    public void exitMode() {
        super.exitMode();
        MainApplication.getMap().mapView.removeTemporaryLayer(this);

        // Remove listener objects from notifiers.
        MainApplication.getMap().mapView.removeMouseListener(_ml);
        try { MainApplication.getLayerManager().getEditDataSet().removeDataSetListener(_dsl); } catch (Exception ignored) {}
        try { UndoRedoHandler.getInstance().removeCommandQueuePreciseListener(_cqpl); } catch (Exception ignored) {}

        // Notify listeners about mode exit.
        for (ChangeListener c : _modeExited) c.stateChanged(null);
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        // Don't render when the map is too zoomed out or when mv is null.
        if (mv == null || mv.getScale() > 16) return;

        _mv = mv;

        // Get map data for rendering:
        ensureRoadSegmentsNotNull();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double cushion = _roads.size() > 100 ? 300 : 10000; // Distance in meters around edge of screen where renderer looks for roads to render.

        // Get bounds where rendering should happen
        ProjectionBounds bounds = mv.getProjectionBounds();
        bounds = new ProjectionBounds(bounds.minEast - cushion,
                bounds.minNorth - cushion,
                bounds.maxEast + cushion,
                bounds.maxNorth + cushion);

        // Render intersections first to ensure gaps get added to roads.
        for (IntersectionRenderer i : _intersections) {
            if (intersectionShouldBeRendered(bounds, i)) {
                try {
                    i.render(g);
                } catch (Exception ignored) {}
            }
        }

        // Render each road
        for (RoadRenderer r : _roads) {
            if (wayShouldBeRendered(bounds, r.getWay())) {
//                try {
                    r.render(g);
//                } catch (Exception ignored) {}
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Methods for adding listeners">

    /**
     * Popups can add listeners here so they close when the user leaves the mode.
     * @param c The listener that gets called when the user leaves LaneMappingMode.
     */
    public void addQuitListener(ChangeListener c) {
        _modeExited.add(c);
    }

    /**
     * Popups can add listeners here so they update just after the dataset is updated.
     * @param a The listener, called whenever the list of RoadRenderers is updated.
     */
    public void addUpdateListener(ActionListener a) {
        _dataChanged.add(a);
    }

    /**
     * Popups can remove listeners here so they no longer update just after the dataset is updated.
     * @param a The listener to be removed.
     */
    public void removeUpdateListener(ActionListener a) {
        _dataChanged.remove(a);
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for Building RoadSegmentRenderers">

    private void ensureRoadSegmentsNotNull() {
        if (_roads == null || _intersections == null) {
            _roads = getAllRoadRenderers(new ArrayList<>(MainApplication.getLayerManager().getEditDataSet().getWays()), _mv);
            _intersections = getAllIntersections(_mv);
        }
    }

    /**
     * Generates a list of RoadRenderers for the input ways.
     * @param ways The list of ways to make RoadRenderers out of.
     * @param mv The MapView each RoadRenderer should use.
     * @return The list of created RoadRenderers.
     */
    private List<RoadRenderer> getAllRoadRenderers(List<Way> ways, MapView mv) {
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
     * Generates a list of InterSectionRenderers based on the list of RoadRenderers already created.
     * @param mv The MapView each IntersectionRenderer should use.
     * @return The created list of IntersectionRenderers.
     */
    private List<IntersectionRenderer> getAllIntersections(MapView mv) {

        // Get all node-only intersections.
        nodeIdToISR = new Hashtable<>();
        Set<Long> handled = new HashSet();
        List<NodeIntersectionRenderer> intersections = new Vector<>();
        if (_roads == null) throw new RuntimeException("RoadRenderers not initialized before calling getAllIntersections().");


        // Get node-only intersections.
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        for (RoadRenderer r : _roads) {
            for (Node n : r.getWay().getNodes()) {
                if (!handled.contains(n.getUniqueId())) {
                    handled.add(n.getUniqueId());
                    executor.execute(() -> {
                        if (!Utils.nodeShouldBeIntersection(n, this)) return;
//                        try {
                            NodeIntersectionRenderer newest = new NodeIntersectionRenderer(n, mv, this);
                            intersections.add(newest);
                            nodeIdToISR.put(n.getUniqueId(), newest);
//                        } catch (Exception ignored) {}
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
        int[] thisGroup = new int[100]; // Max num of existing separate intersections this node intersection can be merged with. Max real world is usually like 3.
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

    // <editor-fold defaultstate="collapsed" desc="Methods for Handling Dataset Changes">

    public void forceUpdateIgnore(int num) {
        mapChangeTolerance += num;
    }

    /**
     * Forces the dataset to be updated next time paint() is called.
     * If the forceUpdateIgnore count is more than 0, this will take
     * one away from the count instead of updating the dataset.
     */
    private void updateDataset() {
        // Prevent crash just after data is uploaded to server.
        if (MainApplication.getLayerManager().getEditDataSet() == null) return;

        // Don't update dataset when the lower level objects have requested to do it themselves
        mapChangeTolerance--;
        if (mapChangeTolerance >= 0) return;
        mapChangeTolerance = 0;

        // Set roads and intersections to null to force update, then ensure road segments aren't null.
        _roads = null;
        _intersections = null;
        ensureRoadSegmentsNotNull();

        // Notify all change listeners that the dataset has been changed.
        Object[] listeners = _dataChanged.toArray();
        for (Object a : listeners) ((ActionListener) a).actionPerformed(null);
    }

    /**
     * Updates this way and the nearby intersections.
     * Only to be used on ways who's alignments or tags were changed.
     * If the way got deleted, this will not work.
     * @param uniqueID The unique id of the Way
     */
    public void updateOneRoad(long uniqueID) {
        Way w = wayIdToRSR.get(uniqueID).getWay();
//        for (Way way : _ways) if (way.getUniqueId() == uniqueID) w = way; TODO delete this if no problems arise from using method above instead.
        if (w == null || !wayIdToRSR.containsKey(uniqueID)) { // Way deleted/never existed, use slow method instead.
            mapChangeTolerance = 0;
            updateDataset();
            return;
        }

        // Replace the single roadRenderer:
        RoadRenderer replacementRR = RoadRenderer.buildRoadRenderer(w, _mv, this);
        if (replacementRR != null) {
            _roads.remove(wayIdToRSR.get(uniqueID));
            wayIdToRSR.put(uniqueID, replacementRR);
            _roads.add(replacementRR);
            replacementRR.updateAlignment();
        }

        // Find all nearby intersections and roads.
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

    /**
     * Finds the shortest RoadRenderer overlapping with the coordinates of the MouseEvent.
     * @param e The event used to determine which RoadRenderers could have been clicked.
     * @return The shortest RoadRenderer clicked by the MouseEvent.
     */
    private RoadRenderer getShortestSegmentMouseEvent(MouseEvent e) {
        ensureRoadSegmentsNotNull();

        RoadRenderer min = null;
        for (RoadRenderer r : _roads) {
            try {
                if (Utils.mouseEventIsInside(e, r.getAsphaltOutlinePixels(), _mv) && (min == null || r.getWay().getLength() < min.getWay().getLength())) {
                    min = r;
                }
            } catch (Exception ignored) {} // When the alignment is invalid, aka it goes from 0 to 21 lanes wide in 10 meters, this catches the exception.
        }
        return min;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Boring Methods">

    @Override
    public boolean layerIsSupported(Layer l) {
        return l instanceof OsmDataLayer;
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditLayer() != null);
    }

    // </editor-fold>
}