package org.openstreetmap.josm.plugins.lanes;

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

    protected List<Way> _outlines = null;

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

    abstract void render(Graphics2D g);

    public boolean defaultChangeToThis() {
        return true;
    }

    protected void setOffset(double offsetStart, double offsetEnd) {
        _offsetStart = offsetStart;
        _offsetEnd = offsetEnd;
    }

    protected List<Polygon> getAsphaltOutlines() {
        List<Polygon> output = new ArrayList<>();
        for (int i = 0; i < _parent.startPoints.size(); i++) {
            double swt = (Math.max(_parent.startPoints.get(i), 0)/_parent.getAlignment().getLength());
            double ewt = (Math.min(_parent.endPoints.get(i), _parent.getAlignment().getLength())/_parent.getAlignment().getLength());

            double widthStart = swt*getWidth(false) + (1-swt)*getWidth(true);
            double widthEnd = ewt*getWidth(false) + (1-ewt)*getWidth(true);

            double startOffset = swt*_offsetEnd + (1-swt)*_offsetStart;
            double endOffset = ewt*_offsetEnd + (1-ewt)*_offsetStart;

            Way subpart = Utils.getSubPart(_parent.getAlignment(), _parent.startPoints.get(i), _parent.endPoints.get(i));
            Way left = Utils.getParallel(subpart, startOffset + (widthStart / 2.0), endOffset + (widthEnd / 2.0),
                    false, _parent.otherStartAngle, _parent.otherEndAngle);
            Way right = Utils.getParallel(subpart, startOffset - (widthStart / 2.0), endOffset - (widthEnd / 2.0),
                    false, _parent.otherStartAngle, _parent.otherEndAngle);

            int[] xPoints = new int[left.getNodesCount() + right.getNodesCount() + 1];
            int[] yPoints = new int[xPoints.length];

            for (int j = 0; j < left.getNodesCount(); j++) {
                xPoints[j] = (int) (_mv.getPoint(left.getNode(j).getCoor()).getX() + 0.5);
                yPoints[j] = (int) (_mv.getPoint(left.getNode(j).getCoor()).getY() + 0.5);
            }

            for (int j = 0; j < right.getNodesCount(); j++) {
                xPoints[j + left.getNodesCount()] = (int) (_mv.getPoint(right.getNode(right.getNodesCount() - j - 1).getCoor()).getX() + 0.5);
                yPoints[j + left.getNodesCount()] = (int) (_mv.getPoint(right.getNode(right.getNodesCount() - j - 1).getCoor()).getY() + 0.5);
            }

            xPoints[left.getNodesCount() * 2] = (int) (_mv.getPoint(left.getNode(0).getCoor()).getX() + 0.5);
            yPoints[left.getNodesCount() * 2] = (int) (_mv.getPoint(left.getNode(0).getCoor()).getY() + 0.5);
            output.add(new Polygon(xPoints, yPoints, xPoints.length));
        }
        return output;
    }


    // <editor-fold desc="Mouse Listeners">

    public void mouseClicked(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) return;

        Utils.displayPopup(getPopupContent(), e, _mv, _way);
    }

    protected JPanel getPopupContent() {
        return null;
    }

    private String pieceID() {
        return _way.getId() + " " + _direction + " " + _position + (this instanceof Lane ? "l" : this instanceof Divider ? "d" : "o");
    }

    // </editor-fold>
}
