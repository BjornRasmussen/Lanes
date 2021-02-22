package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;

import java.awt.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

/*
 * IntersectionRenderer - renders and edits Intersections.
 *
 * -> Uses bezier curves and nearby RoadRenderers to render a 2D area around the intersection.
 * -> Allows for connectivity relations to be viewed and edited using drag and drop pieces.
 */

public class NodeIntersectionRenderer extends IntersectionRenderer {

    // <editor-fold defaultstate="collapsed" desc="Variables"

    private Node _node;

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Constructors">

    public NodeIntersectionRenderer(Node n, MapView mv, LaneMappingMode m) {
        super(mv, m);
        _node = n;
        _trimWays = false; // Only multi intersections do this.
        createIntersectionLayout();
    }

    // </editor-fold>

    @Override
    public List<WayVector> waysClockwiseOrder() {
        return Utils.getWaysFromNode(_node, _m);
    }

    @Override
    public List<List<IntersectionGraphSegment>> getPerimeter() {
        List<List<IntersectionGraphSegment>> out = new ArrayList<>();
        for (int i = 0; i < _wayVectors.size(); i++) out.add(new ArrayList<>());
        return out;
    }

    @Override
    public LatLon getPos() {
        return _node.getCoor();
    }

    public Way getOutline() {
        return _outline;
    }

    public Way getLowResOutline() { return _lowResOutline; }

    public Node getNode() {
        return _node;
    }
}