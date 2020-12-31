package org.openstreetmap.josm.plugins.lanes;

/*
 * Lane-Mapping Map Mode - allows for the lanes of highways to be viewed and edited directly on the map.
 *
 * -> The LanesPlugin class is only run when JOSM boots up.
 * -> This class is for entering the lane mapping mode and handling all of the rendered roads.
 * -> RoadRenderer is a class that represents 1 OSM way, and handles all rendering of that way.
 *
 * This class stores a list of RoadRenderers, and calls on each of them each time paint() is called.
 */

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.*;
import java.util.List;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.projection.proj.Proj;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Shortcut;

import javax.swing.event.ChangeListener;

public class LaneMappingMode extends MapMode implements MouseListener, MouseMotionListener,
        MapViewPaintable, UndoRedoHandler.CommandQueuePreciseListener {

    private List<RoadRenderer> roads = null;
    private MapView _mv;
    private List<ChangeListener> _listeners = new ArrayList<>();
    private long selected = 0L;

    public Map<Long, RoadRenderer> wayIdToRSR = new HashMap<>();

    public LaneMappingMode(MapFrame mapFrame) {
        super(tr("Lane Editing"), "laneconnectivity.png", tr("Activate lane editing mode"),
                Shortcut.registerShortcut("mapmode:lanemapping", tr("Mode: {0}",
                tr("Lane Editing Mode")), KeyEvent.VK_2, Shortcut.SHIFT),
                Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        if (mv.getScale() > 8) return; // Don't render when the map is too zoomed out

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
        for (ChangeListener c : _listeners) c.stateChanged(null);
        _listeners = new ArrayList<>();
    }

    // Popups can add listeners here so they close when the user leaves the mode.
    public void addQuitListener(ChangeListener c) {
        _listeners.add(c);
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

    private boolean wayShouldBeRendered(ProjectionBounds bounds, Way w) {
        for (int i = 0; i < w.getNodesCount(); i++) {
            if (bounds.contains(w.getNode(i).getEastNorth())) {
                return true;
            }
        }
        return false; // TODO also return true if the way intersects bounds at all.
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

    private void ensureRoadSegmentsNotNull() {
        if (roads == null/* || intersections == null*/) {
            roads = getAllRoadSegments(new ArrayList<>(MainApplication.getLayerManager().getEditDataSet().getWays()), _mv);
//            intersections = getAllIntersections(_mv);
        }
    }

    private List<RoadRenderer> getAllRoadSegments(List<Way> ways, MapView mv) {
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

    private List<IntersectionRenderer> getAllIntersections(MapView mv) {
        List<IntersectionRenderer> intersections = new ArrayList<>();
        Set<Long> nodeIds = new HashSet<>();
        for (RoadRenderer r : roads) {
            for (Node n : r.getWay().getNodes()) {
                if (!nodeIds.contains(n.getUniqueId()) && Utils.nodeShouldBeIntersection(n, this)) {
                    intersections.add(new IntersectionRenderer(n, _mv, this));
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

    private void updateDataset() {
        roads = null;
//        intersections = null;
    }

    // </editor-fold>

    // <editor-fold desc="Methods for Handling Mouse Events">

    public void setSelected(long uniqueWayId) {
        selected = uniqueWayId;
        if (selected == 0L) {
            MainApplication.getLayerManager().getActiveData().clearSelection();
        } else {
            MainApplication.getLayerManager().getActiveData().setSelected(wayIdToRSR.get(uniqueWayId).getWay());
        }
    }

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