package org.openstreetmap.josm.plugins.lanes;

/*
 * Lane-Mapping Map Mode - allows for the lanes of highways to be viewed and edited directly on the map.
 *
 * -> The LanesPlugin class is only run when JOSM boots up.
 * -> This class is for entering the lane mapping mode and handling all of the rendered roads.
 * -> RoadSegmentRenderer is a class that represents 1 OSM way, and handles all rendering of that way.
 *
 * This class stores a list of roadSegmentRenderers, and calls on each of them each time paint() is called.
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
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Shortcut;

import javax.swing.*;

public class LaneMappingMode extends MapMode implements MouseListener, MouseMotionListener,
        MapViewPaintable, UndoRedoHandler.CommandQueuePreciseListener {

//    private List<TagWithValues> tagsForRendering = null;
    private List<RoadSegmentRenderer> roadSegments = null;
    private MapView _mv;

    public Map<Long, RoadSegmentRenderer> wayIdToRSR = new HashMap<>();

    public LaneMappingMode(MapFrame mapFrame) {
        super(tr("Lane Editing"), "laneconnectivity.png", tr("Activate lane editing mode"),
                Shortcut.registerShortcut("mapmode:lanemapping", tr("Mode: {0}",
                        tr("Lane Editing Mode")), KeyEvent.VK_2, Shortcut.SHIFT),
                Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        double cushion = 200;
        _mv = mv;

        // Get map data for rendering:
        if (roadSegments == null) roadSegments = getAllRoadSegments(getWays(), mv);

        // Get bounds where rendering should happen
        ProjectionBounds bounds = mv.getProjectionBounds();
        bounds = new ProjectionBounds(bounds.minEast - cushion,
                bounds.minNorth - cushion,
                bounds.maxEast + cushion,
                bounds.maxNorth + cushion);


        // Render each road
        for (RoadSegmentRenderer r : roadSegments) {
            if (wayShouldBeRendered(bounds, r.getWay())) r.render(g);
        }

        // Render intersections
        // TODO
    }

    @Override
    public void enterMode() {
        super.enterMode();
//        tagsForRendering = autofillTagsForRendering();
        roadSegments = null;

        if (getLayerManager().getEditDataSet() == null) return;

//        getLayerManager().getActiveLayer().setVisible(false); TODO uncomment to hide other data when editing lanes.

        MapFrame map = MainApplication.getMap();
        map.mapView.addMouseListener(this);
//        map.mapView.addMouseMotionListener(this);
        map.mapView.addTemporaryLayer(this);
        UndoRedoHandler.getInstance().addCommandQueuePreciseListener(this);
        updateStatusLine();
    }

    @Override
    public void exitMode() {
        super.exitMode();

//        if (getLayerManager().getActiveLayer() != null) getLayerManager().getActiveLayer().setVisible(true); TODO uncomment in future

        MapFrame map = MainApplication.getMap();
        map.mapView.removeMouseListener(this);
//        map.mapView.removeMouseMotionListener(this);
        map.mapView.removeTemporaryLayer(this);
        UndoRedoHandler.getInstance().removeCommandQueuePreciseListener(this);
//        map.keyDetector.removeKeyListener(this);
//        map.keyDetector.removeModifierExListener(this);
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
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for Building RoadSegmentRenderers">
//    private List<TagWithValues> autofillTagsForRendering() {
//        List<TagWithValues> output = new LinkedList<>();
//        output.add(new TagWithValues("lanes"));
//        output.add(new TagWithValues("turn:lanes"));
//        output.add(new TagWithValues("access:lanes"));
//        output.add(new TagWithValues("surface:lanes"));
//        output.add(new TagWithValues("bicycle:lanes"));
//        output.add(new TagWithValues("bus:lanes"));
//        output.add(new TagWithValues("psv:lanes"));
//        output.add(new TagWithValues("width:lanes"));
//        output.add(new TagWithValues("highway", new String[] {
//                "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
//                "secondary", "secondary_link", "tertiary", "tertiary_link", "residential",
//                "unclassified", "bus_guideway"
//        }));
//        return output;
//    }

    private List<RoadSegmentRenderer> getAllRoadSegments(List<Way> ways, MapView mv) {
        wayIdToRSR = new HashMap<>();
        List<RoadSegmentRenderer> output = new ArrayList<>();
        for (Way w : ways) {
            RoadSegmentRenderer rsr = new RoadSegmentRenderer(w, mv, this);
            wayIdToRSR.put(w.getId(), rsr);
            output.add(rsr);
        }
        return output;
    }


    private List<Way> getWays() {
        List<Way> output = new ArrayList<>();
        Collection<Way> allWaysForRendering = MainApplication.getLayerManager().getEditDataSet().getWays();

        for (Iterator<Way> it = allWaysForRendering.iterator(); it.hasNext(); ) {
            Way possibleRoad = it.next();
            if (isRoadForLaneEditor(possibleRoad)) {
                output.add(possibleRoad);
            }
        }

        return output;
    }

    private boolean isRoadForLaneEditor(Way way) {
        return (!way.hasAreaTags() && way.isDrawable() &&
                (way.hasTag("lanes") || way.hasTag("lanes:forward") || way.hasTag("lanes:backward")));
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
        roadSegments = null;
    }

    // </editor-fold>

    // <editor-fold desc="Methods for Handling Mouse Events">
    @Override
    public void mouseClicked(MouseEvent e) {
//        if (roadSegments == null) roadSegments = getAllRoadSegments(getWays(), _mv);
//        RoadSegmentRenderer r = getShortestSegmentMouseEvent(e);
//        if (r != null) {
//            r.mouseClicked(e);
//        } else {
//            RoadSegmentRenderer.selected = "";
//            MainApplication.getLayerManager().getActiveData().clearSelection();
//        }
//        _mv.repaint();
    }

    // <editor-fold defaultstate="collapsed" desc="Unused mouse listener methods">
    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseMoved(MouseEvent e) {}
    // </editor-fold>

    private RoadSegmentRenderer getShortestSegmentMouseEvent(MouseEvent e) {
        RoadSegmentRenderer min = null;
        if (roadSegments == null) roadSegments = getAllRoadSegments(getWays(), _mv);
        for (RoadSegmentRenderer r : roadSegments) {
            if (r.mouseEventIsInside(e) && (min == null || r.getWay().getLength() < min.getWay().getLength())) min = r;
        }
        return min;
    }

    // </editor-fold>

    private class TagWithValues {
        // For storing which tags allow a way to get loaded into the custom renderer.
        private String _key;
        private List<String> _values;

        public TagWithValues(String key, List<String> values) {
            _key = key;
            _values = values;
        }

        public TagWithValues(String key, String[] values) {
            _key = key;
            _values = Arrays.asList(values);
        }

        public TagWithValues(String key) {
            _key = key;
            _values = null;
        }

        public String getKey() { return _key; }
        public List<String> getValues() { return _values; }
    }
}
