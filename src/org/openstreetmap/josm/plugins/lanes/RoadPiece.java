package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

abstract class RoadPiece {
    protected int _direction;
    protected int _position;

    protected double _offsetStart;
    protected double _offsetEnd;
    protected boolean _selected;

    protected MapView _mv;
    protected Way _way;
    protected MarkedRoadRenderer _parent;

    protected RoadPiece _left = null;
    protected RoadPiece _right = null;


    protected RoadPiece(int direction, int position, MapView mv, MarkedRoadRenderer parent) {
        _direction = direction;
        _position = position;
        _mv = mv;
        _way = parent.getWay();
        _parent = parent;
        _selected = false;
    }

    public void setLeftPiece(RoadPiece left) {
        _left = left;
    }

    public void setRightPiece(RoadPiece right) {
        _right = right;
    }

    public void setSelected(boolean selected) {
        _selected = selected;
    }

    abstract double getWidth(boolean start);

    abstract double getWidthTagged(boolean start);

    abstract String widthTag(boolean start);

    abstract void render(Graphics2D g);

    abstract void renderPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter);

    public boolean defaultChangeToThis() {
        return true;
    }

    protected void setOffset(double offsetStart, double offsetEnd) {
        _offsetStart = offsetStart;
        _offsetEnd = offsetEnd;
    }

    protected List<Polygon> getAsphaltOutlines() {
        List<Polygon> output = new ArrayList<>();
        for (int i = 0; i < _parent.segmentStartPoints.size(); i++) {
            double swt = (Math.max(_parent.segmentStartPoints.get(i), 0)/_parent.getAlignment().getLength());
            double ewt = (Math.min(_parent.segmentEndPoints.get(i), _parent.getAlignment().getLength())/_parent.getAlignment().getLength());

            double widthStart = swt*getWidth(false) + (1-swt)*getWidth(true);
            double widthEnd = ewt*getWidth(false) + (1-ewt)*getWidth(true);

            double startOffset = swt*_offsetEnd + (1-swt)*_offsetStart;
            double endOffset = ewt*_offsetEnd + (1-ewt)*_offsetStart;

            Way subpart = Utils.getSubPart(_parent.getAlignment(), _parent.segmentStartPoints.get(i), _parent.segmentEndPoints.get(i));
            Way left = Utils.getParallel(subpart, startOffset + (widthStart / 2.0), endOffset + (widthEnd / 2.0),
                    false, _parent.otherStartAngle, _parent.otherEndAngle);
            Way right = Utils.getParallel(subpart, startOffset - (widthStart / 2.0), endOffset - (widthEnd / 2.0),
                    false, _parent.otherStartAngle, _parent.otherEndAngle);
            List<Node> outline = new ArrayList<>();

            for (int j = 0; j < left.getNodesCount(); j++) outline.add(left.getNode(j));

            for (int j = 0; j < right.getNodesCount(); j++) outline.add(right.getNode(right.getNodesCount() - j - 1));

            outline.add(left.getNode(0));
            Way outlineWay = new Way();
            outlineWay.setNodes(outline);
            output.add(Utils.wayToPolygon(outlineWay, _mv));
        }
        return output;
    }

    // <editor-fold desc="Mouse Listeners">

    public void mouseClicked(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) return;

        Utils.displayPopup(getPopupContent(), e, _mv, _way, _parent._parent);
    }

    protected JPanel getPopupContent() {
        return null;
    }

    private String pieceID() {
        return _way.getId() + " " + _direction + " " + _position + (this instanceof Lane ? "l" : this instanceof Divider ? "d" : "o");
    }

    // </editor-fold>
}
