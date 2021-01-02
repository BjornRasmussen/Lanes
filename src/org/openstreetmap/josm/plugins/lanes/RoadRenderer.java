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

    List<Double> startPoints; // Measured in meters from start.
    List<Double> endPoints; // Anything greater than or equal to way length means end.

    protected final Way _way;
    protected final MapView _mv;
    protected final LaneMappingMode _parent;

    protected List<Way> _asphalt;

    public double otherStartAngle = Double.NaN;
    public double otherEndAngle = Double.NaN;


    // </editor-fold>

    protected RoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        _way = w;
        _mv = mv;
        _parent = parent;

        // Set start/end segments.
        startPoints = new ArrayList<>();
        startPoints.add(0.0);
        endPoints = new ArrayList<>();
        endPoints.add(w.getLength() + 100);
    }

    // Renders to road to g, always called by LaneMappingMode.
    abstract void render(Graphics2D g);
    abstract void renderPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter);

    // For getting the OSM way that the RoadRenderer is modeled after.
    public Way getWay() { return _way; }

    // For getting a different version of _way that's parallel to the lanes.
    // Only different from _way when the way has different placement at start/end.
    abstract Way getAlignment();

    // Width in meters, doesn't care about placement.
    abstract double getWidth(boolean start);

    // For getting alignment split up by road segment.
    public List<Way> getAlignments() {
        // Returns sub parts of alignment.
        List<Way> output = new ArrayList<>();
        for (int i = 0; i < startPoints.size(); i++) {
            double start = Math.max(startPoints.get(i), 0);
            double end = Math.min(endPoints.get(i), getAlignment().getLength());

            Way alignmentPart = Utils.getSubPart(getAlignment(), start, end);

            if (alignmentPart != null) {
                output.add(alignmentPart);
            }
        }
        return output;
    }

    // Get edge methods.  Return edge of rendering, aka like 0.3 meters more than actual edge.
    public Way getLeftEdge() { return getLeftEdge(null, 0); }
    public Way getRightEdge() { return getRightEdge(null, 0); }
    abstract Way getLeftEdge(Way waySegment, int segment);
    abstract Way getRightEdge(Way waySegment, int segment);

    // Static constructor used by LaneMappingMode to create
    //  RoadRenderers without having to worry about which kind is created.
    public static RoadRenderer buildRoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        if (!wayHasRoadTags(w) && !wayHasLaneTags(w)) return null;
        if (w.getNodesCount() == 0 || !w.isVisible()) return null;

        if (w.hasTag("lane_markings", "no")) {
            return new UnmarkedRoadRenderer(w, mv, parent);
        } else if (wayHasLaneTags(w)) {
            MarkedRoadRenderer mrr = new MarkedRoadRenderer(w, mv, parent);
            if (mrr._isValid) {
                return mrr;
            } else {
                return new UntaggedRoadRenderer(w, mv, parent, false);
            }
        } else {
            return new UntaggedRoadRenderer(w, mv, parent, true);
        }
    }

    // <editor-fold defaultstate=collapsed desc="Methods for Angles and Alignments">

    public void updateAlignment() {
        otherStartAngle = getOtherAngle(true);
        otherEndAngle = getOtherAngle(false);
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

    // <editor-fold defaultstate="collapsed" desc="Methods for Rendering">

    protected void renderAsphalt(Graphics2D g, Color color) {
        g.setColor(color);

        for (Polygon p : getAsphaltOutlinePixels()) g.fillPolygon(p);

        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    protected void renderAsphaltPopup(Graphics2D g, Color color, Point center, double bearing, double distOut, double pixelsPerMeter) {
        g.setColor(color);
        Point start = Utils.goInDirection(center, bearing+Math.PI, distOut);
        Point startLeft = Utils.goInDirection(start, bearing-Math.PI/2, pixelsPerMeter*(getWidth(true) + Utils.RENDERING_WIDTH_DIVIDER)/2 + 1);
        Point startRight = Utils.goInDirection(start, bearing+Math.PI/2, pixelsPerMeter*(getWidth(true) + Utils.RENDERING_WIDTH_DIVIDER)/2 + 1);
        Point end = Utils.goInDirection(center, bearing, distOut);
        Point endLeft = Utils.goInDirection(end, bearing-Math.PI/2, pixelsPerMeter*(getWidth(false) + Utils.RENDERING_WIDTH_DIVIDER)/2 + 1);
        Point endRight = Utils.goInDirection(end, bearing+Math.PI/2, pixelsPerMeter*(getWidth(false) + Utils.RENDERING_WIDTH_DIVIDER)/2 + 1);

        g.fillPolygon(new int[] {startLeft.x, startRight.x, endRight.x, endLeft.x, startLeft.x},
                      new int[] {startLeft.y, startRight.y, endRight.y, endLeft.y, startLeft.y}, 5);

        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    protected void renderRoadEdgesPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter, boolean tagged) {
        Color left = Utils.isRightHand(getWay()) && Utils.isOneway(getWay()) && tagged ? Utils.DEFAULT_CENTRE_DIVIDER_COLOR : Utils.DEFAULT_DIVIDER_COLOR;
        Color right = !Utils.isRightHand(getWay()) && Utils.isOneway(getWay()) && tagged ? Utils.DEFAULT_CENTRE_DIVIDER_COLOR : Utils.DEFAULT_DIVIDER_COLOR;

        Point start = Utils.goInDirection(center, bearing+Math.PI, distOut);
        Point startLeft = Utils.goInDirection(start, bearing-Math.PI/2, pixelsPerMeter*(getWidth(true))/2 + 1);
        Point startRight = Utils.goInDirection(start, bearing+Math.PI/2, pixelsPerMeter*(getWidth(true))/2 + 1);
        Point end = Utils.goInDirection(center, bearing, distOut);
        Point endLeft = Utils.goInDirection(end, bearing-Math.PI/2, pixelsPerMeter*(getWidth(false))/2 + 1);
        Point endRight = Utils.goInDirection(end, bearing+Math.PI/2, pixelsPerMeter*(getWidth(false))/2 + 1);

        Utils.renderRoadLinePopup(g, startLeft, endLeft, bearing, 0, 0, pixelsPerMeter, Utils.DividerType.SOLID, left);
        Utils.renderRoadLinePopup(g, startRight, endRight, bearing, 0, 0, pixelsPerMeter, Utils.DividerType.SOLID, right);
    }

    public List<Polygon> getAsphaltOutlinePixels() {
        if (_asphalt == null) _asphalt = getAsphaltOutlineCoords();

        List<Polygon> output = new ArrayList<>();
        for (Way asphalt : _asphalt) output.add(Utils.wayToPolygon(asphalt, _mv));
        return output;
    }

    public List<Way> getAsphaltOutlineCoords() {
        List<Way> output = new ArrayList<>();
        for (int i = 0; i < startPoints.size(); i++) {
            double start = Math.max(startPoints.get(i), 0);
            double end = Math.min(endPoints.get(i), getAlignment().getLength());

            Way alignmentPart = Utils.getSubPart(getAlignment(), start, end);
            if (alignmentPart == null) {
                // Most likely invalid bounds
                return new ArrayList<>();
            }

            Way left = getLeftEdge(alignmentPart, i);
            Way right = getRightEdge(alignmentPart, i);

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

    // <editor-fold defaultstate=collapsed desc="Methods for Mouse Handling">

    public void mousePressed(MouseEvent e) {
        // Ensure it's a left click:
        if (!SwingUtilities.isLeftMouseButton(e)) return;

        // Check to make sure event is inside of rendered asphalt:
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

        // Call child method
        makePopup(e);
    }

    public void makePopup(MouseEvent e) {
        Utils.displayPopup(new LaneLayoutPopup(this), e, _mv, getWay(), _parent);
    }

    // </editor-fold>

    private static boolean wayHasLaneTags(Way way) {
        return (!way.hasAreaTags() && way.isDrawable() &&
                (way.hasTag("lanes") || way.hasTag("lanes:forward") || way.hasTag("lanes:backward") ||
                way.hasTag("turn:lanes") || way.hasTag("turn:lanes:forward") || way.hasTag("turnlanes:backward") ||
                way.hasTag("change:lanes") || way.hasTag("change:lanes:forward") || way.hasTag("change:lanes:backward") ||
                way.hasTag("bicycle:lanes") || way.hasTag("bicycle:lanes:forward") || way.hasTag("bicycle:lanes:backward") ||
                way.hasTag("width:lanes") || way.hasTag("width:lanes:forward") || way.hasTag("width:lanes:backward") ||
                way.hasTag("access:lanes") || way.hasTag("access:lanes:forward") || way.hasTag("access:lanes:backward") ||
                way.hasTag("psv:lanes") || way.hasTag("psv:lanes:forward") || way.hasTag("psv:lanes:backward") ||
                way.hasTag("surface:lanes") || way.hasTag("surface:lanes:forward") || way.hasTag("surface:lanes:backward") ||
                way.hasTag("bus:lanes") || way.hasTag("bus:lanes:forward") || way.hasTag("bus:lanes:backward")) ||
                way.hasTag("lane_markings", "no"));
    }

    private static boolean wayHasRoadTags(Way way) {
        return (!way.hasAreaTags() && way.isDrawable() &&
                (way.hasTag("highway", "motorway") || way.hasTag("highway", "motorway_link") ||
                        way.hasTag("highway", "trunk") || way.hasTag("highway", "trunk_link") ||
                        way.hasTag("highway", "primary") || way.hasTag("highway", "primary_link") ||
                        way.hasTag("highway", "secondary") || way.hasTag("highway", "secondary_link") ||
                        way.hasTag("highway", "tertiary") || way.hasTag("highway", "tertiary_link") ||
                        way.hasTag("highway", "residential") || way.hasTag("highway", "unclassified") ||
                        way.hasTag("highway", "bus_guideway") || way.hasTag("highway", "living_street")));
    }
}
