package org.openstreetmap.josm.plugins.lanes;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
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
 * -> RoadRenderer is a class that represents 1 OSM way, and handles all rendering of that way.
 *
 * This class stores a list of RoadRenderers, and calls on each of them each time paint() is called.
 */

public class LaneMappingMode extends MapMode implements MouseListener, MouseMotionListener,
        MapViewPaintable, UndoRedoHandler.CommandQueuePreciseListener {

    private List<RoadRenderer> roads = null;
    private MapView _mv;
    private List<ChangeListener> _closePopups = new ArrayList<>(); // For closing pop-ups when Lane editing mode is left.
    private List<ActionListener> _updatePopups = new ArrayList<>(); // For updating the lane diagram on the popups.
    public Map<Long, RoadRenderer> wayIdToRSR = new HashMap<>();

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
        if (mv.getScale() > 16) return; // Don't render when the map is too zoomed out

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


        // Render each road
        for (RoadRenderer r : roads) {
            if (wayShouldBeRendered(bounds, r.getWay())) {
                r.render(g);
            }
        }

        // Render intersections
//        for (IntersectionRenderer i : intersections) {
//            if (intersectionShouldBeRendered(bounds, i)) i.render(g);
//        }
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
        if (roads == null/* || intersections == null*/) {
            roads = getAllRoadRenderers(new ArrayList<>(MainApplication.getLayerManager().getEditDataSet().getWays()), _mv);
//            intersections = getAllIntersections(_mv);
        }
    }

    /**
     * Generates a list of RoadRenderers for the input ways.
     * @param ways The list of ways to make RoadRenderers out of.
     * @param mv The MapView each RoadRenderer should use.
     * @return The list of created RoadRenderers.
     */
    private List<RoadRenderer> getAllRoadRenderers(List<Way> ways, MapView mv) {
        wayIdToRSR = new HashMap<>();
        List<RoadRenderer> output = new ArrayList<>();
        for (Way w : ways) {
            RoadRenderer rr = RoadRenderer.buildRoadRenderer(w, mv, this);
            if (rr == null) continue;
            wayIdToRSR.put(w.getUniqueId(), rr);
            output.add(rr);
        }

        for (RoadRenderer rr : output) {
            rr.updateAlignment(); // updates alignment based on nearby ways.
        }

        return output;
    }

    /**
     * Generates a list of InterSectionRenderers based on the list of
     * @param mv The MapView each IntersectionRenderer should use.
     * @return The created list of IntersectionRenderers.
     */
    private List<IntersectionRenderer> getAllIntersections(MapView mv) {
        List<IntersectionRenderer> intersections = new ArrayList<>();
        Set<Long> nodeIds = new HashSet<>();
        if (roads == null) throw new RuntimeException("RoadRenderers not initiated before calling getAllIntersections().");
        for (RoadRenderer r : roads) {
            for (Node n : r.getWay().getNodes()) {
                if (!nodeIds.contains(n.getUniqueId()) && Utils.nodeShouldBeIntersection(n, this)) {
                    intersections.add(new IntersectionRenderer(n, mv, this));
                    nodeIds.add(n.getUniqueId());
                }
            }
        }
        return intersections;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for Handling Dataset Changes">

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
     */
    private void updateDataset() {
        roads = null;
//        intersections = null;
        ensureRoadSegmentsNotNull();
        for (ActionListener a : _updatePopups) a.actionPerformed(null);
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