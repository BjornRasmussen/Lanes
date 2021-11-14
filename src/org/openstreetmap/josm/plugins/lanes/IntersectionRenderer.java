package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class IntersectionRenderer {
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
    protected List<Way> _roadMarkingsSimplified;

    private List<IntersectionGraphSegment> _internalGraph; // Internal graph structure, used for routing between nodes.
    private List<Long> _nodeIds; // List of uniqueIds of nodes in intersections within the renderer.
    private LatLon _pos;


    public IntersectionRenderer(List<Long> nodeIds, List<IntersectionRenderer> addToThis, MapView mv, LaneMappingMode m) {
        _mv = mv;
        _m = m;
        _toBeTrimmed = new ArrayList<>();

        _internalGraph = new ArrayList<>();
        _wayVectors = new ArrayList<>();
        _nodeIds = new ArrayList<>();
        _nodeIds.addAll(nodeIds);

        List<Long> nodeIdsCopy = new ArrayList<>();
        nodeIdsCopy.addAll(nodeIds);

        // Get _wayVectors and _internalGraph:
        explore((Node) MainApplication.getLayerManager().getEditDataSet().getPrimitiveById(nodeIds.get(0), OsmPrimitiveType.NODE),
                new HashSet<>(), nodeIdsCopy, _internalGraph, _wayVectors, 0);

        // If there are still remaining nodes that weren't part of this intersection, create a new one with them.
        if (nodeIdsCopy.size() != 0) new IntersectionRenderer(nodeIdsCopy, addToThis, mv, m);

        _trimWays = true;
        createIntersectionLayout();

        if (!_isValid) return;

        // Get average of wayVector froms.
        double totalLat = 0;
        double totalLon = 0;
        int num = 0;
        for (WayVector w : _wayVectors) {
            try {
                LatLon thisLatLon = w.getParent().getNode(w.getFrom()).getCoor();
                totalLat += thisLatLon.lat();
                totalLon += thisLatLon.lon();
                num++;
            } catch (Exception ignored) {}
        }
        _pos = new LatLon(totalLat/num, totalLon/num);
        addToThis.add(this);
    }



    protected double meters100PixLastRender = -1; // for knowing if the zoom has been changed since last render.

    protected boolean _isValid = true;

    protected void createIntersectionLayout() { // Minor data change, reset intersection layout, assume same road graph
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

        _roadMarkingsSimplified = null; // Must be reset to null every time data changes.


        // Get useful info about this intersection.
        if (_wayVectors.size() < 3) {
            // Shouldn't be an intersection, is isolated island.
            _isValid = false;
            return;
        }
        int motorways = 0;
        for (WayVector w : _wayVectors) if (w.getParent().hasTag("highway", "motorway") || w.getParent().hasTag("highway", "motorway_link") ||
                w.getParent().hasTag("motorroad", "yes") || w.getParent().hasTag("expressway", "yes")) motorways++;
        boolean motorway = motorways >= 3 || _wayVectors.get(0).getParent().getNode(_wayVectors.get(0).getFrom()).hasTag("highway", "motorway_junction");

        // For each corner, get the intersect data.
        for (int i = 0; i < _wayVectors.size(); i++) {
            // Get way at i right road edge (right going out from intersection, left going in)
            WayVector ith = _wayVectors.get(i);
            RoadRenderer ithrr = _m.wayIdToRSR.get(ith.getParent().getUniqueId());
            Way rightSubPart = UtilsSpatial.getSubPart(ith.isForward() ? ithrr.getEdge(-1, true) : ithrr.getEdge(-1, false),
                    ith.isForward() ? Math.min(ith.getFrom(), ith.getTo()) : 0,
                    ith.isForward() ? ithrr.getWay().getNodesCount()-1 : Math.max(ith.getFrom(), ith.getTo()));
            Way rightEdge = (ith.isForward() ? rightSubPart : UtilsSpatial.reverseNodes(rightSubPart));
            _edges.add(rightEdge);

            // Get way at i+1 left road edge (left going out from intersection, right going in)
            WayVector ipoth = _wayVectors.get((i == _wayVectors.size() - 1) ? 0 : i + 1);
            RoadRenderer ipothrr = _m.wayIdToRSR.get(ipoth.getParent().getUniqueId());
            Way leftSubPart = UtilsSpatial.getSubPart(ipoth.isForward() ? ipothrr.getEdge(-1, false) : ipothrr.getEdge(-1, true),
                    ipoth.isForward() ? Math.min(ipoth.getFrom(), ipoth.getTo()) : 0,
                    ipoth.isForward() ? ipothrr.getWay().getNodesCount()-1 : Math.max(ipoth.getFrom(), ipoth.getTo()));
            Way leftEdge = (ipoth.isForward() ? leftSubPart : UtilsSpatial.reverseNodes(leftSubPart));
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
            LatLon intersectRight = UtilsSpatial.intersect(rightEdge, rightIntersectWay, distancesRight, true, 0, motorway, false);
            LatLon intersectLeft = UtilsSpatial.intersect(leftEdge, leftIntersectWay, distancesLeft, true, 0, motorway, false);
            double ext = 8;
            double extensionUsedRight = 0;
            double extensionUsedLeft = 0;

            if (intersectRight == null) {
                try {
                    intersectRight = UtilsSpatial.intersect(UtilsSpatial.extendWay(rightEdge, true, ext),
                            UtilsSpatial.extendWay(rightIntersectWay, true, ext), distancesRight, true, ext, motorway, false);
                    if (intersectRight != null) extensionUsedRight = ext;
                } catch (Exception e) { /* Usually means either rightEdge or rightIntersectWay had no nodes or one node, couldn't be extended. */ }
            }

            if (intersectLeft == null) {
                try {
                    intersectLeft = UtilsSpatial.intersect(UtilsSpatial.extendWay(leftEdge, true, ext),
                            UtilsSpatial.extendWay(leftIntersectWay, true, ext), distancesLeft, true, ext, motorway, false);
                    if (intersectLeft != null) extensionUsedLeft = ext;
                } catch (Exception ignored) { /* Same as above, either leftEdge or leftIntersectWay didn't have any nodes, or had only one node. */ }
            }
            _intersects.add(intersectRight);
            _oneSideDistances.add(Math.max(distancesRight[0]-extensionUsedRight, 0));
            _oneSideDistances.add(Math.max(distancesLeft[0]-extensionUsedLeft, 0));

            // Generate curve backbones (which will be trimmed later)
            double rightBearing = rightEdge.getNode(0).getCoor().bearing(rightEdge.getNode(1).getCoor());
            double leftBearing = leftEdge.getNode(0).getCoor().bearing(leftEdge.getNode(1).getCoor());

            boolean skipPeri = UtilsSpatial.intersect(UtilsSpatial.extendWay(rightEdge, true, ext), UtilsSpatial.extendWay(leftEdge, true, ext),
                    new double[2], true, 0, false, true) != null;
            if (noPeri || skipPeri) {
                _rightBackbones.add(UtilsSpatial.reverseNodes(rightEdge));
                _leftBackbones.add(leftEdge);
            } else {
                _rightBackbones.add(UtilsSpatial.glue(UtilsSpatial.reverseNodes(rightEdge), rightIntersectWay, ext));
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
            Way leftSideSetBack = UtilsSpatial.getSubPart(UtilsSpatial.extendWay(_edges.get(i != 0 ? 2*i-1 : _edges.size()-1), false, 5000), maxDist, maxDist+radius);
            Way rightSideSetBack = UtilsSpatial.getSubPart(UtilsSpatial.extendWay(_edges.get(2*i), false, 5000), maxDist, maxDist+radius);

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
            LatLon l = UtilsSpatial.intersect(UtilsSpatial.extendWay(newAlignment, false, 100), crossSection,
                    distances, false, 0, false, false);
            if (l == null) { // If the intersect failed, then the alignment isn't between the edges (aka placement=left_of:10).
                crossSection = UtilsSpatial.extendWay(UtilsSpatial.extendWay(crossSection, true, 30), false, 30);
                l = UtilsSpatial.intersect(newAlignment, crossSection, distances, false, 0,false, false);
            }
            if (l == null) {
                double distIntoAlignment = (rightSideSetBack.getLength()+leftSideSetBack.getLength())/2;
                double distIntersection = UtilsSpatial.getSubPart(alignment, 0, _wayVectors.get(i).getFrom()).getLength();
                distances[0] = distIntersection + (_wayVectors.get(i).isForward() ? 1 : -1)*distIntoAlignment;
                if (distances[0] < 0) distances[0] = 0;
                if (distances[0] > alignment.getLength()) distances[0] = alignment.getLength();
            }
            // Replace final nodes of the setBacks with properly parallel nodes and add gap to RoadRenderer
            // Get a "better" crossSection, which is always perpendicular to the way.
            double percent = distances[0] / alignment.getLength();
            LatLon left = UtilsSpatial.getParallelPoint(newAlignment, distances[0],
                    percent * rr.sideWidth(false, true) + (1 - percent) * rr.sideWidth(true, true));
            LatLon right = UtilsSpatial.getParallelPoint(newAlignment, distances[0],
                    -percent * rr.sideWidth(false, false) - (1 - percent) * rr.sideWidth(true, false));
            if (!_wayVectors.get(i).isForward()) { LatLon temp = left; left = right; right = temp; }
            Way betterCrossSection = new Way();
            List<Node> betterCSNodes = new ArrayList<>();

            betterCSNodes.add(new Node(left));
            betterCSNodes.add(new Node(right));

            betterCrossSection.setNodes(betterCSNodes);

            // Trim backbones to where they intersect the improved cross section.
            Way rightBackbone = _rightBackbones.get(i);

            _rightBearings.add((right.bearing(left) - Math.PI / 2) % (2 * Math.PI));
            _rightPoints.add(right);

            if (rightBackbone != null && rightBackbone.getNodesCount() >= 2) {
                double[] distancesRight = new double[2];
                double ext = 40;
                LatLon rightIntersect = UtilsSpatial.intersect(UtilsSpatial.extendWay(UtilsSpatial.extendWay(betterCrossSection, false, 0.5), true, 0.5),
                        UtilsSpatial.extendWay(rightBackbone, true, ext), distancesRight, false, 0, false, false);
                if (rightIntersect == null) {
                    rightIntersect = UtilsSpatial.intersect(UtilsSpatial.extendWay(UtilsSpatial.extendWay(betterCrossSection, false, 5), true, 5),
                            UtilsSpatial.extendWay(rightBackbone, true, ext), distancesRight, false, 0, false, false);
                }
                if (rightIntersect != null) {
                    _rightBackbones.set(i, UtilsSpatial.getSubPart(rightBackbone, distancesRight[1] - ext, rightBackbone.getLength()));
                    _rightPoints.set(_rightPoints.size()-1, rightIntersect);
                    _rightBearings.set(_rightBearings.size()-1, UtilsSpatial.bearingAt(rightBackbone, distancesRight[1]-ext));
                } else {
                    _rightBackbones.set(i, null); // No solution, can't create alignment. No sub part will look good.
                }
            }

            // Trim backbones to where they intersect the improved cross section.
            Way leftBackbone = _leftBackbones.get(i == 0 ? _leftBackbones.size()-1 : i-1);
            _leftPoints.add(left);
            _leftBearings.add((right.bearing(left)-Math.PI/2)%(2*Math.PI));
            if (leftBackbone != null && leftBackbone.getNodesCount() >= 2) {
                double[] distancesLeft = new double[2];
                LatLon leftIntersect = UtilsSpatial.intersect(UtilsSpatial.extendWay(UtilsSpatial.extendWay(betterCrossSection, true, 0.5), false, 0.5),
                        UtilsSpatial.extendWay(leftBackbone, false, 40), distancesLeft, false, 0, false, false);
                if (leftIntersect == null) {
                    leftIntersect = UtilsSpatial.intersect(UtilsSpatial.extendWay(UtilsSpatial.extendWay(betterCrossSection, true, 5), false, 5),
                            UtilsSpatial.extendWay(leftBackbone, false, 30), distancesLeft, false, 0, false, false);
                }
                if (leftIntersect != null) {
                    _leftBackbones.set(i == 0 ? _leftBackbones.size() - 1 : i - 1, UtilsSpatial.getSubPart(leftBackbone, 0, distancesLeft[1]));
                    _leftPoints.set(_leftPoints.size()-1, leftIntersect);
                    _leftBearings.set(_leftBearings.size()-1, (UtilsSpatial.bearingAt(leftBackbone, distancesLeft[1]) + Math.PI)%(2*Math.PI));
                } else {
                    _leftBackbones.set(i == 0 ? _leftBackbones.size() - 1 : i - 1, null); // No solution, can't create alignment.  No sub part will look good.
                }
            }

            // Stop the RoadRenderer from rendering at the intersection.
            double distCenter = _wayVectors.get(i).getFrom() == 0 ? 0.0 : UtilsSpatial.getSubPart(rr.getAlignment(), 0, _wayVectors.get(i).getFrom()).getLength();
            if (_trimWays) rr.addRenderingGap(distCenter, distances[0]);
//            new Thread(() -> JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
//                    "added gap, from " + distCenter + " to " + distances[0] + " on len " + rr.getAlignment().getLength())).start();
            _setBacks.add(leftSideSetBack);
            _setBacks.add(rightSideSetBack);
        }

        // Revisit every corner and add its bezier curve to the outline.
        List<Node> outlineNodes = new ArrayList<>();
        List<Node> lowResOutlineNodes = new ArrayList<>();
        for (int i = 0; i < _wayVectors.size(); i++) {

            // Get curve backbone
            double extension = motorway ? 80 : 25;
            Way backbone = _rightBackbones.get(i) == null || _leftBackbones.get(i) == null ? null
                    : UtilsSpatial.glue(_rightBackbones.get(i), _leftBackbones.get(i), extension);
            double md = Math.PI / 30;
            try {
                if (UtilsSpatial.anglesAreWithinAngle(_rightBearings.get(i),
                        _leftBearings.get(i == _leftBearings.size() - 1 ? 0 : i + 1), md) &&
                        UtilsSpatial.anglesAreWithinAngle(_rightBearings.get(i),
                                _rightPoints.get(i).bearing(_leftPoints.get(i == _leftBearings.size() - 1 ? 0 : i + 1)), md)) {
                    List<Node> straightLineBackbone = new ArrayList<>();
                    straightLineBackbone.add(new Node(_rightPoints.get(i)));
                    straightLineBackbone.add(new Node(_leftPoints.get(i == _leftPoints.size() - 1 ? 0 : i + 1)));
                    backbone.setNodes(straightLineBackbone);
                }
            } catch (Exception ignored) {}

            int ipo = i == _leftPoints.size() - 1 ? 0 : i + 1;
            if (_rightPoints.get(i) != null && _leftPoints.get(ipo) != null) {
                double dist = _rightPoints.get(i).greatCircleDistance(_leftPoints.get(ipo));
                if (dist < 6 || backbone == null || _wayVectors.size() == 3) {
                    // Make simple 4 node backbone instead.
                    double angDiff = _rightBearings.get(i) - _leftBearings.get(ipo);
                    double distOutBackboneNodes = dist * (0.6 + 0.4 * Math.cos(angDiff));

                    List<Node> newBackBone = new ArrayList<>();
                    newBackBone.add(new Node(_rightPoints.get(i)));
                    newBackBone.add(new Node(UtilsSpatial.getLatLonRelative(_rightPoints.get(i), _rightBearings.get(i), distOutBackboneNodes)));
                    newBackBone.add(new Node(UtilsSpatial.getLatLonRelative(_leftPoints.get(ipo), _leftBearings.get(ipo), distOutBackboneNodes)));
                    newBackBone.add(new Node(_leftPoints.get(ipo)));
                    backbone = new Way();
                    backbone.setNodes(newBackBone);
                }
            }

            _backbones.add(backbone);

            // Get bezier curve:
            List<LatLon> bezierNodes = new ArrayList<>();
            if (_backbones.get(i) != null) {
                for (Node n : _backbones.get(i).getNodes()) if (n != null && n.getCoor() != null) bezierNodes.add(n.getCoor());
                if (bezierNodes.size() == 0 || bezierNodes.size() > 20) continue;

                // Generate curve
                int nodes = 60;
                int nodesLowRes = 2;
                List<Node> curve = new ArrayList<>();
                List<Node> curveLowRes = new ArrayList<>();
                for (int j = 0; j <= nodes; j++) curve.add(new Node(UtilsSpatial.bezier(j*1.0/nodes, bezierNodes)));
                for (int j = 0; j <= nodesLowRes; j++) curveLowRes.add(new Node(UtilsSpatial.bezier(j*1.0/nodesLowRes, bezierNodes)));

                outlineNodes.addAll(curve);
                lowResOutlineNodes.addAll(curveLowRes);


                // Get parallel line to curve, used for drawing the painted white line at the road edge.
                Way roadMarking = new Way();
                roadMarking.setNodes(curve);
                _roadMarkings.add(UtilsSpatial.getParallel(roadMarking, -UtilsRender.RENDERING_WIDTH_DIVIDER/2, -UtilsRender.RENDERING_WIDTH_DIVIDER/2,
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
        try {
            // Fill in asphalt.
            UtilsRender.drawOnMap(g, _mv, _outline, UtilsRender.DEFAULT_ASPHALT_COLOR,
                    null, 1, true, false, false);

            // Draw road lines:
            double ratio = meters100PixLastRender/_mv.getDist100Pixel();
            boolean simplify = false;
            boolean mustUpdate = false;

            if (_roadMarkingsSimplified == null) {
                _roadMarkingsSimplified = new ArrayList<>();
                for (int i = 0; i < _roadMarkings.size(); i++) _roadMarkingsSimplified.add(new Way());
                mustUpdate = true;
            }

            if (ratio > 1.5 || ratio < 0.75 || mustUpdate) {
                simplify = true;
                for (int i = 0; i < _roadMarkingsSimplified.size(); i++) {
                    _roadMarkingsSimplified.get(i).setNodes(_roadMarkings.get(i).getNodes());
                }
                meters100PixLastRender = _mv.getDist100Pixel();
            }
            for (Way w : _roadMarkingsSimplified) {
                UtilsRender.drawOnMap(g, _mv, w, UtilsRender.DEFAULT_UNTAGGED_ROADEDGE_COLOR,
                        null, 0.125F, false, true, simplify);
            }
//
//            for (long nodeid : getNodeIntersections()) {
//                Way lowresoutline = _m.nodeIntersections.get(nodeid);
//                UtilsRender.drawOnMap(g, _mv, lowresoutline, Color.RED, null, 1.0F, false, true, false);
//            }
        } catch (Exception ignored) {}
    }

    public void updateAlignment() {
        // One of the child way's tags was just changed.  Update shape:
        try {
            createIntersectionLayout();
        } catch (Exception ignored) {}
    }

    public List<Node> perimeterToNodes(List<IntersectionGraphSegment> igsList) {
        Way outputWay = new Way();
        for (int j = 0; j < igsList.size(); j++) { // This runs for each graphSegment (it runs just one time 99.9% of the time)
            IntersectionGraphSegment igs = igsList.get(j);
            for (int k = 0; k < igs.wayVectors().size(); k++) { // This runs for each wayVector in the graphSegment (runs just one time 99% of the time)
                WayVector wv = igs.wayVectors().get(k);
                RoadRenderer parallelRR = _m.wayIdToRSR.get(wv.getParent().getUniqueId());
                Way parallel = wv.isForward() ? parallelRR.getEdge(-1, false) : parallelRR.getEdge(-1, true);
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
                outputWay = outputWay.getNodesCount() == 0 ? parallelSubPartWay : UtilsSpatial.glue(outputWay, parallelSubPartWay, 30);
            }
        }
        return outputWay.getNodes();
    }




    private void explore(Node currentNode, Set<Long> exploredNodes, List<Long> space,
                         List<IntersectionGraphSegment> exploredWays, List<WayVector> wayVectors, double bearing) {
        // Explores all wayVectors leading out of a single-node.
        // Recursive, used to find internal road network.
        exploredNodes.add(currentNode.getUniqueId());
        space.remove(currentNode.getUniqueId());
        List<WayVector> ways = UtilsSpatial.getWaysFromNode(currentNode, _m, bearing);
        for (WayVector w : ways) {
            // If this connection has already been explored, quit now.
            boolean explored = false;
            for (IntersectionGraphSegment i : exploredWays) {
                if (i.containsWaySegment(w)) {
                    explored = true;
                    break;
                }
            }
            if (explored) continue;


            // Explore this segment:
            WayVector wayVector = w;
            List<WayVector> routeSoFar = new ArrayList<>();
            boolean keepgoing = true;
            double distSoFar = 0;

            while(keepgoing) { // Runs for each way between two nodes of intersection.  Should run just one time in most cases.

                Way way = wayVector.getParent();
                int dir = wayVector.isForward() ? 1 : -1;
                int pos = wayVector.getFrom();

                for (int i = pos+dir; dir > 0 ? i < way.getNodesCount() : i >= 0; i += dir) {
                    distSoFar += way.getNode(i-dir).getCoor().greatCircleDistance(way.getNode(i).getCoor());
                    if (UtilsSpatial.numRoadsFromNode(way.getNode(i), _m) != 2 || distSoFar > 100) {
                        routeSoFar.add(new WayVector(pos, i, way));
                        keepgoing = false;
                        break;
                    }

                    if ((dir == -1 && i == 0) || (dir == 1 && i == way.getNodesCount()-1)) {
                        // Reached end, hop on to other road and continue.
                        routeSoFar.add(new WayVector(pos, i, way));

                        List<WayVector> allWayVectorsFromHere = UtilsSpatial.getWaysFromNode(way.getNode(i), _m);
                        wayVector = allWayVectorsFromHere.get(0).getParent().getUniqueId() == way.getUniqueId() ? allWayVectorsFromHere.get(1) : allWayVectorsFromHere.get(0);
                        break;
                    }
                }
            }

            // If the intersection at the end is part of this intersection, explore out from it.
            WayVector endVector = routeSoFar.get(routeSoFar.size()-1);
            Node endNode = endVector.getParent().getNode(endVector.getTo());
            Node endNodeRen = null;
            boolean isPart = false;
            boolean nodeExplored = exploredNodes.contains(endNode.getUniqueId());
            for (Long nodeId : _nodeIds) {
                Node n = (Node) MainApplication.getLayerManager().getEditDataSet().getPrimitiveById(nodeId, OsmPrimitiveType.NODE);
                if (endNode.getUniqueId() == n.getUniqueId()) {
                    isPart = true;
                    endNodeRen = n;
                    break;
                }
            }

            // Mark as explored:
            if (nodeExplored) exploredWays.add(new IntersectionGraphSegment(routeSoFar));
            if (isPart && !nodeExplored && !routeSoFar.get(0).getParent().hasTag("in_a_junction", "no")) {
                double thisBearing = endVector.getParent().getNode(endVector.getTo()).getCoor().bearing(
                        endVector.getParent().getNode(endVector.getTo() + (endVector.isForward() ? -1 : 1)).getCoor());
                explore(endNodeRen, exploredNodes, space, exploredWays, wayVectors, thisBearing);
            } else if (!nodeExplored) {
                wayVectors.add(w);
            }
            if (isPart) {
                _toBeTrimmed.addAll(routeSoFar);
            }
        }
    }


    public List<List<IntersectionGraphSegment>> getPerimeter() {
        List<List<IntersectionGraphSegment>> out = new ArrayList<>();
        for (int i = 0; i < _wayVectors.size(); i++) {
            // Start marching around the edge:
            Node n = _wayVectors.get(i).getParent().getNode(_wayVectors.get(i).getFrom());
            WayVector w = _wayVectors.get(i);
            List<IntersectionGraphSegment> segments = new ArrayList<>();
            for(int dontuse = 0; dontuse < 10; dontuse++) { // This loop should run two times 99% of the time, 3 times 0.99% of the time, 4 times 0.01% of the time.  Used to get to next wayVector.
                // Rotate around this node:
                double bearing = w.getParent().getNode(w.getFrom()).getCoor().bearing(w.getParent().getNode(w.getFrom() + (w.isForward() ? 1 : -1)).getCoor());
                List<WayVector> vectors = UtilsSpatial.getWaysFromNode(n, _m, bearing);
                WayVector x = vectors.get(0);
                WayVector next = _wayVectors.get(i == _wayVectors.size()-1 ? 0 : i+1);
                if (next.contains(x)) break;

                // Go to next node:
                // Get graph segment that contains x:
                IntersectionGraphSegment g = null;
                for (IntersectionGraphSegment s : _internalGraph) {
                    if (s.containsWaySegment(x)) {
                        g = s;
                        break;
                    }
                }
                if (g == null) {
                    segments = new ArrayList<>();
                    break;
                }
                g = g.nodeIsStart(n) ? g : g.reverse();
                segments.add(g);

                // Get correct endpoint:
                n = g.getEndpointNot(n);
                w = g.getEndTwoNodes();
            }
            out.add(segments);
        }
        return out;
    }

    public List<WayVector> waysClockwiseOrder() {
        return _wayVectors;
    }

    public LatLon getPos() {
        return _pos;
    }

    public List<Long> getNodeIntersections() { return _nodeIds; }

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
