package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;

import java.util.ArrayList;
import java.util.List;

public class IntersectionRenderer {

    // <editor-fold defaultstate="collapsed" desc="Variables">

    private Node _node;
    private List<Way> _ways;
    private Way _alignment;
    private MapView _mv;

    private Way _outline;

    protected boolean _isValid = true;

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Constructors">
    public IntersectionRenderer(List<Way> ways, MapView mv) {
        _ways = ways;
        _mv = mv;
        try {
            createIntersectionLayout();
        } catch (Exception e) { _isValid = false; }
    }

    public IntersectionRenderer(Node n, MapView mv) {
        _node = n;
        _mv = mv;
        try {
            createIntersectionLayout();
        } catch (Exception e) { _isValid = false; }
    }

    // </editor-fold>

    private void createIntersectionLayout() {

    }
}
