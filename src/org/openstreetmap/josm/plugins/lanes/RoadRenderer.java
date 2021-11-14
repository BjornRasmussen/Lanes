package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class RoadRenderer {

    // <editor-fold defaultstate="collapsed" desc="Variables">

    protected final Way _way;
    protected final MapView _mv;
    protected final LaneMappingMode _parent;

    // Asphalt outline as rendered on map.
    protected List<Way> _asphalt;

    // Store "angle" of roads at endpoints.  If there isn't an obvious continuation road, these remain NaN.
    public double otherStartAngle = Double.NaN;
    public double otherEndAngle = Double.NaN;

    // Store gaps added by intersections.
    List<Double> segmentStartPoints; // Measured in meters from start.
    List<Double> segmentEndPoints; // Anything greater than or equal to way length means end.

    // </editor-fold>

    protected RoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        _way = w;
        _mv = mv;
        _parent = parent;

        // Set start/end segments.
        resetRenderingGaps();
    }

    // Static constructor used by LaneMappingMode to create RoadRenderers
    public static RoadRenderer buildRoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        // Ensure the way is some type of road renderer.
        if (!wayHasRoadTags(w) && !wayHasLaneTags(w)) return null;
        if (w.getNodesCount() == 0 || !w.isVisible()) return null;
        if (!w.isDrawable()) return null;

        // Figure out type and return.
        if (w.hasTag("lane_markings", "no") || w.hasTag("lanes", "1.5")) {
            return new RoadRendererUnmarked(w, mv, parent);

        } else if (wayHasLaneTags(w)) {
            RoadRendererMarked mrr = new RoadRendererMarked(w, mv, parent);
            return mrr._isValid ? mrr : new RoadRendererUntagged(w, mv, parent, false);

        } else {
            return new RoadRendererUntagged(w, mv, parent, true);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Rendering">

    abstract void render(Graphics2D g);

    abstract void renderPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter);

    protected void renderAsphalt(Graphics2D g, Color color) {
        g.setColor(color);
        for (Polygon p : getAsphaltOutlinePixels()) g.fillPolygon(p);

        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    protected void renderAsphaltPopup(Graphics2D g, Color color, Point center, double bearing, double distOut, double pixelsPerMeter) {
        g.setColor(color);
        Point start = UtilsRender.goInDirection(center, bearing+Math.PI, distOut);
        Point startLeft = UtilsRender.goInDirection(start, bearing-Math.PI/2, pixelsPerMeter*(getWidth(true) + UtilsRender.RENDERING_WIDTH_DIVIDER)/2 + 1);
        Point startRight = UtilsRender.goInDirection(start, bearing+Math.PI/2, pixelsPerMeter*(getWidth(true) + UtilsRender.RENDERING_WIDTH_DIVIDER)/2 + 1);
        Point end = UtilsRender.goInDirection(center, bearing, distOut);
        Point endLeft = UtilsRender.goInDirection(end, bearing-Math.PI/2, pixelsPerMeter*(getWidth(false) + UtilsRender.RENDERING_WIDTH_DIVIDER)/2 + 1);
        Point endRight = UtilsRender.goInDirection(end, bearing+Math.PI/2, pixelsPerMeter*(getWidth(false) + UtilsRender.RENDERING_WIDTH_DIVIDER)/2 + 1);

        g.fillPolygon(new int[] {startLeft.x, startRight.x, endRight.x, endLeft.x, startLeft.x},
                new int[] {startLeft.y, startRight.y, endRight.y, endLeft.y, startLeft.y}, 5);

        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    protected void renderRoadEdgesPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter, boolean tagged) {
        Color left = UtilsGeneral.isRightHand(getWay()) && UtilsGeneral.isOneway(getWay()) && tagged ?
                UtilsRender.DEFAULT_CENTRE_DIVIDER_COLOR : UtilsRender.DEFAULT_DIVIDER_COLOR;
        Color right = !UtilsGeneral.isRightHand(getWay()) && UtilsGeneral.isOneway(getWay()) && tagged ?
                UtilsRender.DEFAULT_CENTRE_DIVIDER_COLOR : UtilsRender.DEFAULT_DIVIDER_COLOR;

        Point start = UtilsRender.goInDirection(center, bearing+Math.PI, distOut);
        Point startLeft = UtilsRender.goInDirection(start, bearing-Math.PI/2, pixelsPerMeter*(getWidth(true))/2 + 1);
        Point startRight = UtilsRender.goInDirection(start, bearing+Math.PI/2, pixelsPerMeter*(getWidth(true))/2 + 1);
        Point end = UtilsRender.goInDirection(center, bearing, distOut);
        Point endLeft = UtilsRender.goInDirection(end, bearing-Math.PI/2, pixelsPerMeter*(getWidth(false))/2 + 1);
        Point endRight = UtilsRender.goInDirection(end, bearing+Math.PI/2, pixelsPerMeter*(getWidth(false))/2 + 1);

        UtilsRender.renderRoadLinePopup(g, startLeft, endLeft, bearing, 0, 0, pixelsPerMeter, DividerType.SOLID, left);
        UtilsRender.renderRoadLinePopup(g, startRight, endRight, bearing, 0, 0, pixelsPerMeter, DividerType.SOLID, right);
    }

    public List<Polygon> getAsphaltOutlinePixels() {
        if (_asphalt == null) _asphalt = getAsphaltOutlineCoords();

        List<Polygon> output = new ArrayList<>();
        for (Way asphalt : _asphalt) output.add(UtilsRender.wayToPolygon(asphalt, _mv));
        return output;
    }

    public List<Way> getAsphaltOutlineCoords() {
        List<Way> output = new ArrayList<>();
        for (int i = 0; i < getAlignments().size(); i++) {
            Way left = getEdge(i, false);
            Way right = getEdge(i, true);

            List<Node> points = new ArrayList<>();

            for (int j = 0; j < left.getNodesCount(); j++) points.add(left.getNode(j));
            for (int j = 0; j < right.getNodesCount(); j++) points.add(right.getNode(right.getNodesCount()-j-1));
            points.add(left.getNode(0));

            Way thisSegment = new Way();
            thisSegment.setNodes(points);
            output.add(thisSegment);
        }
        return output;
    }

    // </editor-fold>

    // <editor-fold defaultstate=collapsed desc="Alignments, Shape, Width, Angles">

    // For getting the OSM way that the RoadRenderer is modeled after.
    public Way getWay() { return _way; }

    // For getting a different version of _way that's parallel to the lanes.
    // Only different from _way when the way has different placement at start/end.
    // This version isn't guaranteed to have any tags.
    abstract Way getAlignment();

    // For getting alignment split up by road segment (alignment minus the gaps).
    public List<Way> getAlignments() {
        // Returns sub parts of alignment.
        List<Way> output = new ArrayList<>();
        for (int i = 0; i < segmentStartPoints.size(); i++) {
            double start = Math.max(segmentStartPoints.get(i), 0);
            double end = Math.min(segmentEndPoints.get(i), getAlignment().getLength());
            if (end-start < 0.01) continue;
            Way alignmentPart = UtilsSpatial.getSubPart(getAlignment(), start, end);

            if (alignmentPart != null && alignmentPart.getLength() > 0.01) {
                output.add(alignmentPart);
            }
        }
        return output;
    }


    // Width in meters, includes extra Utils.RENDERING_WIDTH_DIVIDER amount. (for a 7 m wide road, this returns 7.6)
    abstract double getWidth(boolean start);

    // Just width/2 unless there are placement tags, in which case this method is overriden.
    public double sideWidth(boolean start, boolean left) {
        return getWidth(start)/2;
    }

    // Get edge methods.  Return edge of rendering, aka like 0.3 meters more than actual edge.
    abstract Way getEdge(int segment /* -1 for every segment, else specific iD for segment */, boolean right);


    // IntersectionRenderers add gaps where they rendered so the RoadRenderer knows not to render there.
    public synchronized void addRenderingGap(int from, int to) {
        addRenderingGap(UtilsSpatial.nodeIdToDist(getAlignment(), from), UtilsSpatial.nodeIdToDist(getAlignment(), to));
    }

    public synchronized void addRenderingGap(double from, double to) {
        double startGap = Math.max(Math.min(from, to), 0);
        double endGap = Math.min(Math.max(from, to), getAlignment().getLength());
        for (int i = 0; i < segmentStartPoints.size(); i++) {
            double startSegment = segmentStartPoints.get(i);
            double endSegment = segmentEndPoints.get(i);
            if (endGap < endSegment && startGap > startSegment && endGap > startSegment && startGap < endSegment) { // completely inside
                // Add new end at min, new start at max
                segmentEndPoints.add(i, startGap);
                segmentStartPoints.add(i+1, endGap);
                return; // To avoid problems.
            } else if (endGap < endSegment && startGap <= startSegment && endGap > startSegment) { // inside start half
                segmentStartPoints.set(i, endGap);
                return;
            } else if (endGap >= endSegment && startGap > startSegment && startGap < endSegment) { // inside end half
                segmentEndPoints.set(i, startGap);
            } else if (endGap >= endSegment && startGap <= startSegment) { // Outside, containing this range.
                // Remove this range.
                segmentEndPoints.remove(i);
                segmentStartPoints.remove(i);
                i--;
            }
        }
        _asphalt = null;

        // Find any segments that are less than 0.1 meters long, and clean them up.
        for (int i = segmentStartPoints.size()-1; i > 0; i--) {
            if (segmentEndPoints.get(i) - segmentStartPoints.get(i) < 0.1) {
                segmentStartPoints.remove(i);
                segmentEndPoints.remove(i);
            }
        }    }

    public void resetRenderingGaps() {
        segmentStartPoints = new ArrayList<>();
        segmentStartPoints.add(0.0);
        segmentEndPoints = new ArrayList<>();
        segmentEndPoints.add(_way.getLength() + 100);
        _asphalt = null;
    }


    public void updateEndAngles() {
        getOtherAngle(true);
        getOtherAngle(false);
    }

    public double getOtherAngle(boolean start) {
        if (start && Double.isNaN(otherStartAngle)) {
            otherStartAngle = calculateOtherAngle(true);
        }

        if (!start && Double.isNaN(otherEndAngle)) {
            otherEndAngle = calculateOtherAngle(false);
        }

        return start ? otherStartAngle : otherEndAngle;
    }

    private double calculateOtherAngle(boolean start) {
        Node pivot = _way.getNode(start ? 0 : _way.getNodesCount()-1);

        int numValidWays = 0;
        boolean somethingIsNotValid = false;
        Way otherWay = null;
        boolean otherWayStartsHere = false;

        // Ensure the node only shows up once in this way:
        int connectionsThisWay = 0;
        for (Node n : _way.getNodes()) {
            if (n.getUniqueId() == pivot.getUniqueId()) {
                connectionsThisWay++;
            }
        }
        if (connectionsThisWay != 1) somethingIsNotValid = true;

        // Ensure that there is only one other way, and that the node shows up only once in that way.
        for (Way w : pivot.getParentWays()) {
            if (w.getUniqueId() == _way.getUniqueId() || !_parent.wayIdToRSR.containsKey(w.getUniqueId())) continue;
            otherWay = _parent.wayIdToRSR.get(w.getUniqueId()).getAlignment();
            numValidWays++;
            // Check to ensure that pivot is only part of w at one of the endpoints.
            int numConnections = 0;
            for (int i = 0; i < w.getNodesCount(); i++) {
                if (w.getNode(i).getUniqueId() != pivot.getUniqueId()) continue;
                numConnections++;
                if (i!=0 && i!=w.getNodesCount()-1) {
                    somethingIsNotValid = true;
                }
                otherWayStartsHere = i==0;
            }
            if (numConnections > 1) {
                somethingIsNotValid = true;
            }
        }
        if (numValidWays != 1 || otherWay == null) {
            somethingIsNotValid = true;
        }

        if (!somethingIsNotValid) {
            try {
                Node secondToLast = otherWayStartsHere ? otherWay.getNode(1) : otherWay.getNode(otherWay.getNodesCount() - 2);
                Node last = otherWayStartsHere ? otherWay.getNode(0) : otherWay.getNode(otherWay.getNodesCount() - 1);
                return last.getCoor().bearing(secondToLast.getCoor());
            } catch (NullPointerException ignored) {}
        }

        return (getThisAngle(start) + Math.PI) % (2*Math.PI);
    }

    public double getThisAngle(boolean start) {
        try {
            Node first = start ? getAlignment().getNode(0) : getAlignment().getNode(getAlignment().getNodesCount() - 1);
            Node second = start ? getAlignment().getNode(1) : getAlignment().getNode(getAlignment().getNodesCount() - 2);
            return first.getCoor().bearing(second.getCoor());
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate=collapsed desc="Mouse Pressed / Popup">

    public void mousePressed(MouseEvent e) {
        // Ensure it was a left click:
        if (!SwingUtilities.isLeftMouseButton(e)) return;

        // Ensure event is inside of rendered asphalt:
        boolean inside = false;
        for (Polygon p : getAsphaltOutlinePixels()) {
            if (p.contains(e.getPoint())) {
                inside = true;
                break;
            }
        }
        if (!inside) return;

        // Set selected
        MainApplication.getLayerManager().getActiveData().setSelected(_parent.wayIdToRSR.get(_way.getUniqueId()).getWay());

        // Make Pop-up
        UtilsClicksAndPopups.displayPopup(new LaneLayoutPopup(this), e, _mv, getWay(), _parent);
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Static Methods for Determining if ways should be RoadRenderers">

    private static boolean wayHasLaneTags(Way way) {
        return !way.hasAreaTags() && UtilsGeneral.wayHasOneOfKeys(way, "lanes", "lanes:forward", "lanes:backward",
                "turn:lanes", "turn:lanes:forward", "turn:lanes:backward", "change:lanes", "change:lanes:forward", "change:lanes:backward",
                "bicycle:lanes", "bicycle:lanes:forward", "bicycle:lanes:backward", "width:lanes", "width:lanes:forward", "width:lanes:backward",
                "access:lanes", "access:lanes:forward", "access:lanes:backward", "psv:lanes", "psv:lanes:forward", "psv:lanes:backward",
                "surface:lanes", "surface:lanes:forward", "surface:lanes:backward", "bus:lanes", "bus:lanes:forward", "bus:lanes:backward",
                "lane_markings", "in_a_junction");
    }

    private static boolean wayHasRoadTags(Way way) {
        return (!way.hasAreaTags() && UtilsGeneral.wayHasTags(way, "highway", "motorway", "motorway_link",
                "trunk", "trunk_link", "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
                "residential", "unclassified", "bus_guideway", "living_street", "busway"));
    }

    // </editor-fold>

}
