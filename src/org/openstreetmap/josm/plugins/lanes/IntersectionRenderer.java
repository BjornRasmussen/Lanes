package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class IntersectionRenderer {
    protected MapView _mv;
    protected LaneMappingMode _m;

    protected Way _outline;
    protected Way _lowResOutline; // For overlap between intersections only.
    protected List<LatLon> _intersects;
    protected List<Way> _edges;
    protected List<Double> _oneSideDistances;
    protected List<Way> _setBacks;
    protected List<List<IntersectionGraphSegment>> _perimeter;
    protected List<WayVector> _wayVectors;
    protected List<Way> _backbones;
    protected List<Way> _rightBackbones;
    protected List<Way> _leftBackbones;
    protected List<Way> _crossSections;

    protected List<WayVector> _toBeTrimmed;
    protected boolean _trimWays;
    protected List<LatLon> _rightPoints;
    protected List<LatLon> _leftPoints;
    protected List<Double> _rightBearings;
    protected List<Double> _leftBearings;
    protected List<Way> _roadMarkings;
    protected List<Node> _bruh;

    protected IntersectionRenderer(MapView mv, LaneMappingMode m) {
        _mv = mv;
        _m = m;
        _toBeTrimmed = new ArrayList<>();
    }

    abstract List<WayVector> waysClockwiseOrder();

    protected void createIntersectionLayout() {
        _wayVectors = waysClockwiseOrder();
        _perimeter = getPerimeter();
        _intersects = new ArrayList<>();
        _edges = new ArrayList<>();
        _oneSideDistances = new ArrayList<>();
        _setBacks = new ArrayList<>();
        _roadMarkings = new ArrayList<>();
        _backbones = new ArrayList<>();
        _leftBackbones = new ArrayList<>();
        _rightBackbones = new ArrayList<>();
        _crossSections = new ArrayList<>();

        _rightPoints = new ArrayList<>();
        _leftPoints = new ArrayList<>();
        _rightBearings = new ArrayList<>();
        _leftBearings = new ArrayList<>();
        _bruh = new ArrayList<>();
        // Get useful info about this intersection.
        int motorways = 0;
        for (WayVector w : _wayVectors) if (w.getParent().hasTag("highway", "motorway") || w.getParent().hasTag("highway", "motorway_link") ||
                w.getParent().hasTag("motorroad", "yes") || w.getParent().hasTag("expressway", "yes")) motorways++;
        boolean motorway = motorways >= 3 || _wayVectors.get(0).getParent().getNode(_wayVectors.get(0).getFrom()).hasTag("highway", "motorway_junction");

        // For each corner, get the intersect data.
        for (int i = 0; i < _wayVectors.size(); i++) {
            // Get way at i right road edge (right going out from intersection, left going in)
            WayVector ith = _wayVectors.get(i);
            RoadRenderer ithrr = _m.wayIdToRSR.get(ith.getParent().getUniqueId());
            Way rightSubPart = Utils.getSubPart(ith.isForward() ? ithrr.getRightEdge() : ithrr.getLeftEdge(),
                    ith.isForward() ? Math.min(ith.getFrom(), ith.getTo()) : 0,
                    ith.isForward() ? ithrr.getWay().getNodesCount()-1 : Math.max(ith.getFrom(), ith.getTo()));
            Way rightEdge = (ith.isForward() ? rightSubPart : Utils.reverseNodes(rightSubPart));
            _edges.add(rightEdge);

            // Get way at i+1 left road edge (left going out from intersection, right going in)g
            WayVector ipoth = _wayVectors.get((i == _wayVectors.size() - 1) ? 0 : i + 1);
            RoadRenderer ipothrr = _m.wayIdToRSR.get(ipoth.getParent().getUniqueId());
            Way leftSubPart = Utils.getSubPart(ipoth.isForward() ? ipothrr.getLeftEdge() : ipothrr.getRightEdge(),
                    ipoth.isForward() ? Math.min(ipoth.getFrom(), ipoth.getTo()) : 0,
                    ipoth.isForward() ? ipothrr.getWay().getNodesCount()-1 : Math.max(ipoth.getFrom(), ipoth.getTo()));
            Way leftEdge = (ipoth.isForward() ? leftSubPart : Utils.reverseNodes(leftSubPart));
            _edges.add(leftEdge);

            // Get ways from perimeter for each to intersect.  Usually, there aren't any and they just intersect each other.
            Way rightIntersectWay;
            Way leftIntersectWay;
            boolean noPeri = _perimeter.get(i).size() == 0;
            if (noPeri) {
                rightIntersectWay = leftEdge;
                leftIntersectWay = rightEdge;
            } else {
                Way intersect = new Way();
                intersect.setNodes(perimeterToNodes(_perimeter.get(i)));
                rightIntersectWay = intersect;
                leftIntersectWay = intersect;
            }

            // Get the intersect of the lines, as well as info about how far into each way that intersect is.
            double[] distancesRight = new double[2];
            double[] distancesLeft = new double[2];
            LatLon intersectRight = Utils.intersect(rightEdge, rightIntersectWay, distancesRight, true, 0, motorway, false);
            LatLon intersectLeft = Utils.intersect(leftEdge, leftIntersectWay, distancesLeft, true, 0, motorway, false);
            double extension = 5;
            double extensionUsedRight = 0;
            double extensionUsedLeft = 0;

            if (intersectRight == null) {
                try {
                    intersectRight = Utils.intersect(Utils.extendWay(rightEdge, true, extension),
                            Utils.extendWay(rightIntersectWay, true, extension), distancesRight, true, extension, motorway, false);
                    if (intersectRight != null) extensionUsedRight = extension;
                } catch (Exception e) { /* Usually means either rightEdge or rightIntersectWay had no nodes or one node, couldn't be extended. */ }
            }

            if (intersectLeft == null) {
                try {
                    intersectLeft = Utils.intersect(Utils.extendWay(leftEdge, true, extension),
                            Utils.extendWay(leftIntersectWay, true, extension), distancesLeft, true, extension, motorway, false);
                    if (intersectLeft != null) extensionUsedLeft = extension;
                } catch (Exception ignored) { /* Same as above, either leftEdge or leftIntersectWay didn't have any nodes, or had only one node. */ }
            }
            _intersects.add(intersectRight);
            _oneSideDistances.add(Math.max(distancesRight[0]-extensionUsedRight, 0));
            _oneSideDistances.add(Math.max(distancesLeft[0]-extensionUsedLeft, 0));

            // Generate curve backbones (which will be trimmed later)
            double ext = 5;
            double rightBearing = rightEdge.getNode(0).getCoor().bearing(rightEdge.getNode(1).getCoor());
            double leftBearing = leftEdge.getNode(0).getCoor().bearing(leftEdge.getNode(1).getCoor());

            boolean skipPeri = Utils.intersect(Utils.extendWay(rightEdge, true, ext), Utils.extendWay(leftEdge, true, ext),
                    new double[2], true, 0, false, true) != null;
            if (noPeri || skipPeri) {
                _rightBackbones.add(Utils.reverseNodes(rightEdge));
                _leftBackbones.add(leftEdge);
            } else {
                _rightBackbones.add(glue(Utils.reverseNodes(rightEdge), rightIntersectWay, extension));
                _leftBackbones.add(leftEdge);
            }
        }

        // Merge corner data from the left and right side of every road going out.
        for (int i = 0; i < _wayVectors.size(); i++) {
            // Get the max distance of the two sides.
            double maxDist = Math.max(_oneSideDistances.get(2*i), _oneSideDistances.get(i != 0 ? 2*i-1 : _oneSideDistances.size()-1));

            // Get ways from intersect (including intersect node) out along edges for a few meters.
            double radius = 6;
            if (_wayVectors.get(i).getParent().hasTag("in_a_junction", "yes") && !_trimWays) { radius = 30; }
            Way leftSideSetBack = Utils.getSubPart(Utils.extendWay(_edges.get(i != 0 ? 2*i-1 : _edges.size()-1), false, 5000), maxDist, maxDist+radius);
            Way rightSideSetBack = Utils.getSubPart(Utils.extendWay(_edges.get(2*i), false, 5000), maxDist, maxDist+radius);

            // Get way between endpoints of the two intersects.
            Way crossSection = new Way();
            List<Node> crossSectionNodes = new ArrayList<>();
            crossSectionNodes.add(rightSideSetBack.getNode(rightSideSetBack.getNodesCount()-1));
            crossSectionNodes.add(leftSideSetBack.getNode(leftSideSetBack.getNodesCount()-1));
            crossSection.setNodes(crossSectionNodes);

            // Find intersect between the cross section and the alignment to find out how far into the alignment the cross sections go.
            double[] distances = new double[2];
            RoadRenderer rr = _m.wayIdToRSR.get(_wayVectors.get(i).getParent().getUniqueId());
            Way alignment = rr.getAlignment();
            List<Node> alignmentNodesNoNull = new ArrayList<>();
            for (Node n : alignment.getNodes()) if (n != null && n.getCoor() != null) alignmentNodesNoNull.add(n);
            Way newAlignment = new Way();
            newAlignment.setNodes(alignmentNodesNoNull);
            LatLon l = Utils.intersect(Utils.extendWay(newAlignment, false, 100), crossSection,
                    distances, false, 0, false, false);
            if (l == null) { // If the intersect failed, then the alignment isn't between the edges (aka placement=left_of:10).
                crossSection = Utils.extendWay(Utils.extendWay(crossSection, true, 30), false, 30);
                l = Utils.intersect(newAlignment, crossSection, distances, false, 0,false, false);
            }

            // Replace final nodes of the setBacks with properly parallel nodes and add gap to RoadRenderer
            if (l != null) {
                // Get a "better" crossSection, which is always perpendicular to the way.
                double percent = distances[0] / rr.getAlignment().getLength();
                LatLon left = Utils.getParallelPoint(newAlignment, distances[0],
                        percent * rr.sideWidth(false, true) + (1 - percent) * rr.sideWidth(true, true));
                LatLon right = Utils.getParallelPoint(newAlignment, distances[0],
                        -percent * rr.sideWidth(false, false) - (1 - percent) * rr.sideWidth(true, false));
                Way betterCrossSection = new Way();
                List<Node> betterCSNodes = new ArrayList<>();

                betterCSNodes.add(new Node(left));
                betterCSNodes.add(new Node(right));

                betterCrossSection.setNodes(betterCSNodes);

                // Trim backbones to where they intersect the improved cross section.
                Way rightBackbone = _rightBackbones.get(i);

                _rightBearings.add((right.bearing(left) - Math.PI / 2) % (2 * Math.PI));
                _rightPoints.add(right);

//                try {
                    if (rightBackbone != null && rightBackbone.getNodesCount() >= 2) {
                        double[] distancesRight = new double[2];
                        double ext = 40;
                        LatLon rightIntersect = Utils.intersect(Utils.extendWay(Utils.extendWay(betterCrossSection, false, 0.5), true, 0.5),
                                Utils.extendWay(rightBackbone, true, ext), distancesRight, false, 0, false, false);
                        if (rightIntersect == null) {
                            rightIntersect = Utils.intersect(Utils.extendWay(Utils.extendWay(betterCrossSection, false, 5), true, 5),
                                    Utils.extendWay(rightBackbone, true, ext), distancesRight, false, 0, false, false);
                        }
                        if (rightIntersect != null) {
                            _rightBackbones.set(i, Utils.getSubPart(rightBackbone, distancesRight[1] - ext, rightBackbone.getLength()));
                        }
                        _rightPoints.set(_rightPoints.size()-1, rightIntersect);
                        _rightBearings.set(_rightBearings.size()-1, Utils.bearingAt(rightBackbone, distancesRight[1]-30));
                    }
//                } catch (Exception ignored) {}

                // Trim backbones to where they intersect the improved cross section.
                Way leftBackbone = _leftBackbones.get(i == 0 ? _leftBackbones.size()-1 : i-1);
                _leftPoints.add(left);
                _leftBearings.add((right.bearing(left)-Math.PI/2)%(2*Math.PI));
//                try {
                    if (leftBackbone != null && leftBackbone.getNodesCount() >= 2) {
                        double[] distancesLeft = new double[2];
                        LatLon leftIntersect = Utils.intersect(Utils.extendWay(Utils.extendWay(betterCrossSection, true, 0.5), false, 0.5),
                                Utils.extendWay(leftBackbone, false, 40), distancesLeft, false, 0, false, false);
                        if (leftIntersect == null) {
                            leftIntersect = Utils.intersect(Utils.extendWay(Utils.extendWay(betterCrossSection, true, 5), false, 5),
                                    Utils.extendWay(leftBackbone, false, 30), distancesLeft, false, 0, false, false);
                        }
                        if (leftIntersect != null) {
                            _leftBackbones.set(i == 0 ? _leftBackbones.size() - 1 : i - 1, Utils.getSubPart(leftBackbone, 0, distancesLeft[1]));
                        }
                        _leftPoints.set(_leftPoints.size()-1, leftIntersect);
                        _leftBearings.set(_leftBearings.size()-1, Utils.bearingAt(leftBackbone, distancesLeft[1]));
                    }
//                } catch (Exception ignored) {}

                // Stop the RoadRenderer from rendering at the intersection.
                double distCenter = _wayVectors.get(i).getFrom() == 0 ? 0.0 : Utils.getSubPart(rr.getAlignment(), 0, _wayVectors.get(i).getFrom()).getLength();
                if (_trimWays) rr.addRenderingGap(distCenter, distances[0]);
            } else {
                _rightPoints.add(null);
                _leftPoints.add(null);
                _rightBearings.add(_wayVectors.get(i).bearing());
                _leftBearings.add(_wayVectors.get(i).bearing());
            }
            _setBacks.add(leftSideSetBack);
            _setBacks.add(rightSideSetBack);
        }

        // Revisit every corner and add its bezier curve to the outline.
        List<Node> outlineNodes = new ArrayList<>();
        List<Node> lowResOutlineNodes = new ArrayList<>();
        for (int i = 0; i < _wayVectors.size(); i++) {

            // Get curve backbone
            double extension = motorway ? 80 : 12;
            if (_rightBackbones.get(i).getNodesCount() < 2) throw new RuntimeException("Fail, a len = " + _rightBackbones.get(i).getNodesCount());
            Way backbone = _rightBackbones.get(i) == null ? _leftBackbones.get(i) : _leftBackbones.get(i) == null ?
                    _rightBackbones.get(i) : glue(_rightBackbones.get(i), _leftBackbones.get(i), extension);
            double md = Math.PI/30;
            try {
                if (Utils.anglesAreWithinAngle(_rightBearings.get(i),
                        _leftBearings.get(i == _leftBearings.size() - 1 ? 0 : i + 1), md) &&
                        Utils.anglesAreWithinAngle(_rightBearings.get(i),
                                _rightPoints.get(i).bearing(_leftPoints.get(i == _leftBearings.size() - 1 ? 0 : i + 1)), md)) {
                    List<Node> straightLineBackbone = new ArrayList<>();
                    straightLineBackbone.add(new Node(_rightPoints.get(i)));
                    straightLineBackbone.add(new Node(_leftPoints.get(i == _leftPoints.size() - 1 ? 0 : i + 1)));
                    backbone.setNodes(straightLineBackbone);
                }
            } catch (Exception ignored) {}
            _backbones.add(backbone);

            // Get bezier curve:
            List<LatLon> bezierNodes = new ArrayList<>();
            if (_backbones.get(i) != null) {
                for (Node n : _backbones.get(i).getNodes()) if (n != null && n.getCoor() != null) bezierNodes.add(n.getCoor());
                if (bezierNodes.size() == 0 || bezierNodes.size() > 20) continue;

                // Generate curve
                int nodes = 100;
                int nodesLowRes = 2;
                List<Node> curve = new ArrayList<>();
                List<Node> curveLowRes = new ArrayList<>();
                for (int j = 0; j <= nodes; j++) curve.add(new Node(Utils.bezier(j*1.0/nodes, bezierNodes)));
                for (int j = 0; j <= nodesLowRes; j++) curveLowRes.add(new Node(Utils.bezier(j*1.0/nodesLowRes, bezierNodes)));

                outlineNodes.addAll(curve);
                lowResOutlineNodes.addAll(curveLowRes);


                // Get parallel line to curve, used for drawing the painted white line at the road edge.
                Way roadMarking = new Way();
                roadMarking.setNodes(curve);
                _roadMarkings.add(Utils.getParallel(roadMarking, -Utils.RENDERING_WIDTH_DIVIDER/2, -Utils.RENDERING_WIDTH_DIVIDER/2,
                        false, Double.NaN, Double.NaN));

            } else {
                _roadMarkings.add(null);
            }

        }
        if (outlineNodes.size() != 0) outlineNodes.add(outlineNodes.get(0));
        if (lowResOutlineNodes.size() != 0) lowResOutlineNodes.add(lowResOutlineNodes.get(0));

        // Set outline.
        _outline = new Way();
        _outline.setNodes(outlineNodes);
        _lowResOutline = new Way();
        _lowResOutline.setNodes(lowResOutlineNodes);

        // Trim roads assigned to be trimmed by child class:
        for (WayVector w : _toBeTrimmed) {
            _m.wayIdToRSR.get(w.getParent().getUniqueId()).addRenderingGap(w.getFrom(), w.getTo());
        }
    }

    public void render(Graphics2D g) {
        // Fill in asphalt.
        int[] xPoints = new int[_outline.getNodesCount()];
        int[] yPoints = new int[_outline.getNodesCount()];
        for (int i = 0; i < _outline.getNodesCount(); i++) {
            xPoints[i] = (int) (_mv.getPoint(_outline.getNode(i).getCoor()).getX() + 0.5);
            yPoints[i] = (int) (_mv.getPoint(_outline.getNode(i).getCoor()).getY() + 0.5);
        }
        g.setColor(Utils.DEFAULT_ASPHALT_COLOR);
        g.fillPolygon(xPoints, yPoints, xPoints.length);

        // Draw road lines:
        double pixelsPerMeter = 100 / _mv.getDist100Pixel();
        for (Way w : _roadMarkings) {
            if (w == null) continue;
            // To reduce jitter, ensure no more than one vertex per 10 pixels or so. TODO use better simplification
            int everyNth = Math.max((int) (w.getNodesCount() / (Math.max(w.getLength()*pixelsPerMeter/7, 10)) + 1.5), 1);
            xPoints = new int[w.getNodesCount()];
            yPoints = new int[w.getNodesCount()];
            int num = 0;
            int topLefts = 0;
            for (int i = 0; i < w.getNodesCount(); i++) {
                if (i%everyNth!=0 && i != w.getNodesCount()-1) continue;
                xPoints[num] = (int) (_mv.getPoint(w.getNode(i).getCoor()).getX() + 0.5);
                yPoints[num] = (int) (_mv.getPoint(w.getNode(i).getCoor()).getY() + 0.5);
                if (xPoints[num] == 0 && yPoints[num] == 0) topLefts++;
                num++;
            }
            g.setColor(Utils.DEFAULT_UNTAGGED_ROADEDGE_COLOR);
            g.setStroke(GuiHelper.getCustomizedStroke((12.5 / _mv.getDist100Pixel() + 1) + ""));
            if (topLefts < 2) g.drawPolyline(xPoints, yPoints, num); // Render road line unless it would shoot to the top left point of the screen.
        }

        g.setStroke(new BasicStroke(10));
        g.setColor(Color.GREEN);

//        // DRAW PERIMETER

//        g.setStroke(new BasicStroke(10));
//        for (int i = 0; i < _backbones.size(); i++) {
//            if (_backbones.get(i) == null) continue;
//            g.setColor(i == 0 ? Color.ORANGE : i == 1 ? Color.BLUE : i == 2 ? Color.GREEN : i == 3 ? Color.YELLOW : i == 4 ? Color.CYAN : Color.PINK);
//            int[] xPoints2 = new int[_backbones.get(i).getNodesCount()];
//            int[] yPoints2 = new int[xPoints2.length];
//            for (int j = 0; j < _backbones.get(i).getNodesCount(); j++) {
//                xPoints2[j] = (int) (_mv.getPoint(_backbones.get(i).getNode(j).getCoor()).getX() + 0.5);
//                yPoints2[j] = (int) (_mv.getPoint(_backbones.get(i).getNode(j).getCoor()).getY() + 0.5);
//            }
//            g.drawPolyline(xPoints2, yPoints2, xPoints2.length);
//        }

//        g.setStroke(new BasicStroke(10));
//        g.setColor(Color.RED);
//        for (int i = 0; i < _bruh.size(); i++) {
//            int x = (int) (_mv.getPoint(_bruh.get(i).getCoor()).getX() + 0.5);
//            int y = (int) (_mv.getPoint(_bruh.get(i).getCoor()).getY() + 0.5);
//            g.drawLine(x, y, x, y);
//        }

        // THESE TWO LINES ARE FOR REMOVING THE WHITE BOX AROUND THE SCREEN... DON'T DELETE THESE
        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    public void updateAlignment() {
        // One of the child way's tags was just changed.  Update shape:
        createIntersectionLayout();
    }

    public List<Node> perimeterToNodes(List<IntersectionGraphSegment> igsList) {
        Way outputWay = new Way();
        for (int j = 0; j < igsList.size(); j++) { // This runs for each graphSegment (it runs just one time 99.9% of the time)
            IntersectionGraphSegment igs = igsList.get(j);
            for (int k = 0; k < igs.wayVectors().size(); k++) { // This runs for each wayVector in the graphSegment (runs just one time 99% of the time)
                WayVector wv = igs.wayVectors().get(k);
                RoadRenderer parallelRR = _m.wayIdToRSR.get(wv.getParent().getUniqueId());
                Way parallel = wv.isForward() ? parallelRR.getLeftEdge() : parallelRR.getRightEdge();
                List<Node> parallelSubPart = new ArrayList<>();
//                for (int l = wv.getFrom(); wv.isForward() ? (l <= wv.getTo()) : (l >= wv.getTo()); l += (wv.isForward()?1:-1)) { // Runs for each node in the wayVector(1-2 times 95% of the time)
//                    if (l == wv.getFrom() && j != 0) continue;
//                    output.add(parallel.getNode(l));
//                }
                for (int l = wv.getFrom(); wv.isForward() ? (l <= wv.getTo()) : (l >= wv.getTo()); l += (wv.isForward()?1:-1)) { // Runs for each node in the wayVector(1-2 times 95% of the time)
                    parallelSubPart.add(parallel.getNode(l));
                }
                Way parallelSubPartWay = new Way();
                parallelSubPartWay.setNodes(parallelSubPart);
                outputWay = outputWay.getNodesCount() == 0 ? parallelSubPartWay : glue(outputWay, parallelSubPartWay, 30);
            }
        }
        return outputWay.getNodes();
    }

    abstract List<List<IntersectionGraphSegment>> getPerimeter();

    abstract LatLon getPos();

    public Way glue(Way a, Way b, double extension) {
        // Glue first half of a to second half of b.  Split into halves at intersect.
        double[] distances = new double[2];
        if (b == null || b.getNodesCount() < 2) throw new RuntimeException("bruh for b");//return (a == null || a.getNodesCount() < 2) ? null : a;
        if (a == null || a.getNodesCount() < 2) throw new RuntimeException("bruh for a");// return b;
        LatLon intersect = Utils.intersect(Utils.reverseNodes(a), b, distances, false, 0, false, false);
        double extensionUsed = 0;
        if (intersect == null) {
            try {
                intersect = Utils.intersect(Utils.extendWay(Utils.reverseNodes(a), true, extension),
                        Utils.extendWay(b, true, extension), distances, false, 0, false, false);
                extensionUsed = extension;
            } catch (Exception e) {
                return null;
            }
        }
//        if (intersect == null) {
//            _bruh.add(Utils.extendWay(Utils.reverseNodes(a), true, extension));
//            _bruh.add(Utils.extendWay(b, true, extension));
//            throw new RuntimeException("bro");
//        }
        List<Node> nodes = new ArrayList<>();
        Way output = new Way();

        Way firstHalfA = Utils.getSubPart(a, 0.0, a.getLength() + extensionUsed - distances[0]);
        Way secondHalfB = Utils.getSubPart(b, distances[1]-extensionUsed, b.getLength());

        if (intersect == null || firstHalfA == null || secondHalfB == null) {
            nodes.addAll(a.getNodes());
            nodes.addAll(b.getNodes());
        } else {
            nodes.addAll(firstHalfA.getNodes());
            if (firstHalfA.getNodesCount() != 0 && secondHalfB.getNodesCount() != 0) nodes.remove(nodes.size()-1);
            nodes.addAll(secondHalfB.getNodes());
        }

        output.setNodes(nodes);
        return output;
    }

}


class WayVector { // Stores a part a way.
    private final int _from, _to;
    private final Way _parent;

    public WayVector(int from, int to, Way parent) { _from = putIntoRange(from, parent); _to = putIntoRange(to, parent); _parent = parent; }

    private static int putIntoRange(int i, Way p) { return Math.min(p.getNodesCount()-1, Math.max(i, 0)); }

    public int getFrom() { return _from; }
    public int getTo() { return _to; }
    public Way getParent() { return _parent; }
    public double bearing() { return _parent.getNode(_from).getCoor().bearing(_parent.getNode(_to).getCoor()); }
    public boolean isForward() { return _to > _from; }
    public boolean contains(WayVector other) {
        if (getParent().getUniqueId() != other.getParent().getUniqueId()) return false;

        int start = Math.max(Math.min(getFrom(), getTo()), 0);
        int end = Math.min(getParent().getNodesCount(), Math.max(getFrom(), getTo()));
        int otherStart = Math.max(Math.min(other.getFrom(), other.getTo()), 0);
        int otherEnd = Math.min(other.getParent().getNodesCount(), Math.max(other.getFrom(), other.getTo()));

        return otherStart >= start && otherStart <= end && otherEnd >= start && otherEnd <= end;
    }
    public WayVector reverse() {
        return new WayVector(_to, _from, _parent);
    }
    public boolean equals(WayVector other) {
        return getFrom() == other.getFrom() && getTo() == other.getTo() && getParent().getUniqueId() == other.getParent().getUniqueId();
    }
}


class IntersectionGraphSegment {
    // Stores a connection between two "intersection nodes".  Can traverse multiple ways.
    private List<WayVector> _connection;

    public IntersectionGraphSegment(List<WayVector> connection) {
        _connection = connection;
    }

    public boolean containsWaySegment(WayVector w) {
        for (WayVector segment : _connection) if (segment.contains(w)) return true;
        return false;
    }

    public Node getEndpointNot(Node other) {
        Node start = _connection.get(0).getParent().getNode(_connection.get(0).getFrom());
        Node end = _connection.get(_connection.size()-1).getParent().getNode(_connection.get(_connection.size()-1).getTo());
        return start.getUniqueId() == other.getUniqueId() ? end : start;
    }

    public boolean nodeIsStart(Node n) {
        return _connection.get(0).getParent().getNode(_connection.get(0).getFrom()).getUniqueId() == n.getUniqueId();
    }

    public IntersectionGraphSegment reverse() {
        List<WayVector> output = new ArrayList<>();
        for (int i = _connection.size()-1; i >= 0; i--) {
            output.add(_connection.get(i).reverse());
        }
        return new IntersectionGraphSegment(output);
    }

    public WayVector getStartTwoNodes() {
        WayVector s = _connection.get(0);
        return new WayVector(s.getFrom(), s.getFrom()+(s.isForward() ? 1 : -1), s.getParent());
    }

    public WayVector getEndTwoNodes() {
        return reverse().getStartTwoNodes();
    }

    public List<WayVector> wayVectors() { return _connection; }
}
