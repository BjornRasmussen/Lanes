package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiIntersectionRenderer extends IntersectionRenderer {

    private List<WayVector> _wayVectors;
    private List<IntersectionGraphSegment> _internalGraph;
    private List<NodeIntersectionRenderer> _space;
    private List<Long> _nodeIds;
    private LatLon _pos;
    private RightOfWay _rightOfWay;

    public MultiIntersectionRenderer(List<NodeIntersectionRenderer> nodeOnlyIntersections, List<IntersectionRenderer> addToThis) {
        super(nodeOnlyIntersections.get(0)._mv, nodeOnlyIntersections.get(0)._m);
        // Fixme remove
        _ordering = new ArrayList<>();
        _vertextOrdering = new ArrayList<>();

        if (nodeOnlyIntersections.size() == 1) {
            _rightOfWay = nodeOnlyIntersections.get(0).getRightOfWay();
        }

        _internalGraph = new ArrayList<>();
        _wayVectors = new ArrayList<>();
        _space = new ArrayList<>();
        _nodeIds = new ArrayList<>();
        for (NodeIntersectionRenderer n : nodeOnlyIntersections) _nodeIds.add(n.getNode().getUniqueId());
        _space.addAll(nodeOnlyIntersections);
        // Get _wayVectors and _internalGraph:
        explore(nodeOnlyIntersections.get(0), new HashSet<>(), nodeOnlyIntersections, _internalGraph, _wayVectors, 0);
        if (nodeOnlyIntersections.size() != 0) new MultiIntersectionRenderer(nodeOnlyIntersections, addToThis);
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

    private void explore(NodeIntersectionRenderer currentNode, Set<Long> exploredNodes, List<NodeIntersectionRenderer> space,
                         List<IntersectionGraphSegment> exploredWays, List<WayVector> wayVectors, double bearing) {
        // Explores all wayVectors leading out of a single-node.
        // Recursive, used to find internal road network.
        exploredNodes.add(currentNode.getNode().getUniqueId());
        _vertextOrdering.add(currentNode.getNode()); // TODO remove
        space.remove(currentNode);
        List<WayVector> ways = Utils.getWaysFromNode(currentNode.getNode(), _m, bearing);
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
                    if (Utils.numRoadsFromNode(way.getNode(i), _m) != 2 || distSoFar > 100) {
                        routeSoFar.add(new WayVector(pos, i, way));
                        keepgoing = false;
                        break;
                    }

                    if ((dir == -1 && i == 0) || (dir == 1 && i == way.getNodesCount()-1)) {
                        // Reached end, hop on to other road and continue.
                        routeSoFar.add(new WayVector(pos, i, way));

                        List<WayVector> allWayVectorsFromHere = Utils.getWaysFromNode(way.getNode(i), _m);
                        wayVector = allWayVectorsFromHere.get(0).getParent().getUniqueId() == way.getUniqueId() ? allWayVectorsFromHere.get(1) : allWayVectorsFromHere.get(0);
                        break;
                    }
                }
            }

            // If the intersection at the end is part of this intersection, explore out from it.
            WayVector endVector = routeSoFar.get(routeSoFar.size()-1);
            Node endNode = endVector.getParent().getNode(endVector.getTo());
            NodeIntersectionRenderer endNodeRen = null;
            boolean isPart = false;
            boolean nodeExplored = exploredNodes.contains(endNode.getUniqueId());
            for (NodeIntersectionRenderer n : _space) {
                if (endNode.getUniqueId() == n.getNode().getUniqueId()) {
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
                _ordering.add(w.getParent().getNode(w.getTo()));
            }
            if (isPart) {
                _toBeTrimmed.addAll(routeSoFar);
            }
        }
    }

    @Override
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
                List<WayVector> vectors = Utils.getWaysFromNode(n, _m, bearing);
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

    @Override
    public List<WayVector> waysClockwiseOrder() {
        return _wayVectors;
    }

    @Override
    public LatLon getPos() {
        return _pos;
    }

    @Override
    RightOfWay getRightOfWay() {
        return _rightOfWay;
    }

    public List<NodeIntersectionRenderer> getNodeOnlyIntersections() {
        return _space;
    }

    public List<Long> getNodeIntersections() { return _nodeIds; }
}
