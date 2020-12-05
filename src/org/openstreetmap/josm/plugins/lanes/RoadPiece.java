package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;

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

    abstract void render(Graphics2D g);

    public boolean defaultChangeToThis() {
        return true;
    }


    protected void setOffset(double offsetStart, double offsetEnd) {
        _offsetStart = offsetStart;
        _offsetEnd = offsetEnd;
    }

//    protected Polygon getAsphaltOutline() {
//        double widthStart = getWidth(true);
//        double widthEnd = getWidth(false);
//
//        Way left = Utils.getParallel(_parent.getAlignment(), _offsetStart + (widthStart / 2.0), _offsetEnd + (widthEnd / 2.0), false, _parent.startAngle, _parent.endAngle);
//        Way right = Utils.getParallel(_parent.getAlignment(), _offsetStart - (widthStart / 2.0), _offsetEnd - (widthEnd / 2.0), false, _parent.startAngle, _parent.endAngle);
//
//        int[] xPoints = new int[left.getNodesCount() + right.getNodesCount() + 1];
//        int[] yPoints = new int[xPoints.length];
//
//        for (int i = 0; i < left.getNodesCount(); i++) {
//            xPoints[i] = (int) (_mv.getPoint(left.getNode(i).getCoor()).getX() + 0.5);
//            yPoints[i] = (int) (_mv.getPoint(left.getNode(i).getCoor()).getY() + 0.5);
//        }
//
//        for (int i = 0; i < right.getNodesCount(); i++) {
//            xPoints[i+left.getNodesCount()] = (int) (_mv.getPoint(right.getNode(right.getNodesCount()-i-1).getCoor()).getX() + 0.5);
//            yPoints[i+left.getNodesCount()] = (int) (_mv.getPoint(right.getNode(right.getNodesCount()-i-1).getCoor()).getY() + 0.5);
//        }
//
//        xPoints[left.getNodesCount()*2] = (int) (_mv.getPoint(left.getNode(0).getCoor()).getX() + 0.5);
//        yPoints[left.getNodesCount()*2] = (int) (_mv.getPoint(left.getNode(0).getCoor()).getY() + 0.5);
//        return new Polygon(xPoints, yPoints, xPoints.length);
//    }


    // <editor-fold desc="Mouse Listeners">

    public void mouseClicked(MouseEvent e) {
        // FIXME these comments are so the Pop-Up is never shown, since it's not yet ready.
        //  This plugin will be released as a visualization tool first.

//        if (!SwingUtilities.isLeftMouseButton(e)) return;
//        JPanel content = getPopupJPanel();
//
//        _selected = true;
//        MainApplication.getLayerManager().getActiveData().setSelected(_way);
//
//        if (content == null) return;
//
//        JPanel mainPanel = new JPanel();
//        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
//        mainPanel.add(content);
//
//        JWindow w = new JWindow(MainApplication.getMainFrame());
//        w.add(mainPanel);
//        w.pack();
//
//        Point aboveMouse = new Point(e.getXOnScreen() - (mainPanel.getWidth()/2), e.getYOnScreen() - mainPanel.getHeight() - 10);
//        Point belowMouse = new Point(e.getXOnScreen() - (mainPanel.getWidth()/2), e.getYOnScreen() + 10);
//        w.setLocation(e.getY() - 30 > mainPanel.getHeight() ? aboveMouse : belowMouse);
//
//        w.setVisible(true);
//        _mv.repaint();
//
//        // <editor-fold defaultstate=collapsed desc="Things that close the Window">
//
//        // * Map moved / zoom changed
//        _mv.addRepaintListener(new MapView.RepaintListener() {
//            double scale = _mv.getScale();
//            EastNorth center = _mv.getCenter();
//
//            @Override
//            public void repaint(long tm, int x, int y, int width, int height) {
//                // This runs when something has changed.  Check if scale or map position have changed.
//                if (Math.abs(_mv.getScale() - scale) > 0.001 || Math.abs(_mv.getCenter().getX() - center.getX()) > 0.001 ||
//                        Math.abs(_mv.getCenter().getY() - center.getY()) > 0.001) {
//                    scale = _mv.getScale();
//                    center = _mv.getCenter();
//                    unClick(w);
//                }
//            }
//        });
//
//        // * Mouse pressed down somewhere on the map outside of the window (just map clicks - editing the tags of selected way won't close it)
//        _mv.addMouseListener(new MouseListener() {
//            @Override
//            public void mousePressed(MouseEvent e) { unClick(w); }
//
//            @Override
//            public void mouseClicked(MouseEvent e) {}
//
//            @Override
//            public void mouseReleased(MouseEvent e) {}
//
//            @Override
//            public void mouseEntered(MouseEvent e) {}
//
//            @Override
//            public void mouseExited(MouseEvent e) {}
//        });
//
//        // * Way corresponding to Window no longer exists.
//        UndoRedoHandler.getInstance().addCommandQueuePreciseListener(new UndoRedoHandler.CommandQueuePreciseListener() {
//            @Override
//            public void commandAdded(UndoRedoHandler.CommandAddedEvent e) { verifyExistence(); }
//
//            @Override
//            public void cleaned(UndoRedoHandler.CommandQueueCleanedEvent e) { verifyExistence(); }
//
//            @Override
//            public void commandUndone(UndoRedoHandler.CommandUndoneEvent e) { verifyExistence(); }
//
//            @Override
//            public void commandRedone(UndoRedoHandler.CommandRedoneEvent e) { verifyExistence(); }
//
//            private void verifyExistence() {
//                if (_way.isDeleted()) unClick(w);
//            }
//        });
//
//        // </editor-fold>
    }

    protected JPanel getPopupJPanel() {
        return null;
    }

    private void unClick(Window w) {
        w.setVisible(false);
        _mv.repaint();
    }

    private String pieceID() {
        return _way.getId() + " " + _direction + " " + _position + (this instanceof Lane ? "l" : this instanceof Divider ? "d" : "o");
    }

    // </editor-fold>
}
