package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class IntersectionRenderer {

    // <editor-fold defaultstate="collapsed" desc="Variables">

    private Node _node;
    private List<Way> _ways;
    private Way _alignment;
    private MapView _mv;
    private LaneMappingMode _m;

    private Way _outline;
    List<LatLon> _intersects;
    List<Way> _edges = new ArrayList<>();

    protected boolean _isValid = true;

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Constructors">

    public IntersectionRenderer(Node n, MapView mv, LaneMappingMode m) {
        _node = n;
        _mv = mv;
        _m = m;
        try {
            createIntersectionLayout();
        } catch (Exception e) { _isValid = false; }
    }

    // </editor-fold>

    private void createIntersectionLayout() {
        // Start at way going straight up (inclusive) then go clockwise around analyzing each way.
        List<WayVector> wayVectors = waysClockwiseOrder();

        _intersects = new ArrayList<>();

        // To test out the way intersection feature, find all intersects around each intersection
        for (int i = 0; i < wayVectors.size(); i++) {
            // Get way at i right road edge (going out from intersection, left going in))
            WayVector ith = wayVectors.get(i);
            RoadRenderer ithrr = _m.wayIdToRSR.get(ith._parent.getUniqueId());
            Way rightEdge = (ith.isForward() ? Utils.getSubPart(ithrr.getRightEdge(), Math.min(ith._from, ith._to), Math.max(ith._from, ith._to))
                    : Utils.reverseNodes(Utils.getSubPart(ithrr.getLeftEdge(), Math.min(ith._from, ith._to), Math.max(ith._from, ith._to))));

            WayVector ipoth = wayVectors.get((i == wayVectors.size()-1) ? 0 : i+1);
            RoadRenderer ipothrr = _m.wayIdToRSR.get(ipoth._parent.getUniqueId());
            Way leftEdge = (ith.isForward() ? Utils.getSubPart(ithrr.getLeftEdge(), Math.min(ith._from, ith._to), Math.max(ith._from, ith._to))
                    : Utils.reverseNodes(Utils.getSubPart(ithrr.getRightEdge(), Math.min(ith._from, ith._to), Math.max(ith._from, ith._to))));

            _edges.add(rightEdge);
            _edges.add(leftEdge);

            LatLon intersect = Utils.intersect(rightEdge, leftEdge);

            if (intersect != null) { _intersects.add(intersect); }
        }
    }

    private List<WayVector> waysClockwiseOrder() {
        List<WayVector> output = new ArrayList<>();

        // For each way, ensure it's part of the intersection and then add WayVectors from it to output.
        for (Way w : _node.getParentWays()) {
            if (!_m.wayIdToRSR.containsKey(w.getUniqueId())) continue;
            if (w.getNodesCount() < 2) continue;

            for (int i = 0; i < w.getNodesCount(); i++) {
                // For each node, if it's the pivot, add a WayVector to output for both directions.
                if (w.getNode(i).getUniqueId() != _node.getUniqueId()) continue;
                if (i != 0) output.add(new WayVector(i, i-1, w));
                if (i != w.getNodesCount()-1) output.add(new WayVector(i, i+1, w));
            }
        }

        // Bubble sort by bearing.
        for (int i = 0; i < output.size()-1; i++) {
            for (int j = 0; j < output.size()-1-i; j++) {
                if (output.get(j).bearing() > output.get(j+1).bearing()) {
                    WayVector temp = output.get(j);
                    output.set(j, output.get(j+1));
                    output.set(j+1, temp);
                }
            }
        }

        return output;
    }

    public void render(Graphics2D g) {
        g.setStroke(GuiHelper.getCustomizedStroke("14"));
        g.setColor(Color.RED);
        for (LatLon l : _intersects) {
            Point p = _mv.getPoint(l);
            g.drawLine(p.x, p.y, p.x, p.y);
//            g.setColor(Utils.DEFAULT_ASPHALT_COLOR);
//            g.fillPolygon(Utils.wayToPolygon(_outline, _mv));
        }
        for (Way w : _edges) {
            int[] xPoints = new int[w.getNodesCount()];
            int[] yPoints = new int[w.getNodesCount()];

            for (int j = 0; j < w.getNodesCount(); j++) {
                xPoints[j] = (int) (_mv.getPoint(w.getNode(j).getCoor()).getX() + 0.5);
                yPoints[j] = (int) (_mv.getPoint(w.getNode(j).getCoor()).getY() + 0.5);
            }

            g.drawPolyline(xPoints, yPoints, Math.min(xPoints.length, 4));
        }
        // THESE TWO LINES ARE FOR REMOVING THE WHITE BOX AROUND THE SCREEN... DON'T DELETE THESE
        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    public LatLon getPos() {
        return _node.getCoor();
    }

    private class WayVector {
        private int _from, _to;
        private Way _parent;

        public WayVector(int from, int to, Way parent) { _from = from; _to = to; _parent = parent; }

        public int getFrom() { return _from; }
        public int getTo() { return _to; }
        public Way getParent() { return _parent; }
        public double bearing() { return _parent.getNode(_from).getCoor().bearing(_parent.getNode(_to).getCoor()); }
        public boolean isForward() { return _to > _from; }
    }
}
