package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

abstract class RoadPiece {
    protected int _direction;
    protected int _position;

    protected double _offsetStart;
    protected double _offsetEnd;
    protected double _widthStart;
    protected double _widthEnd;
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

    protected void renderAsphalt(Graphics2D g) {
//        g.setColor(_selected ? Utils.DEFAULT_SELECTED_ASPHALT_COLOR: Utils.DEFAULT_ASPHALT_COLOR);
//
//        g.fillPolygon(getAsphaltOutline());
//
//        g.setColor(new Color(0, 0, 0, 0));
//        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    protected void renderRoadLine(Graphics2D g, double offsetStart, double offsetEnd, Utils.DividerType type, Color color) {
        double pixelsPerMeter = 100.0 / _mv.getDist100Pixel();
        double stripeWidth = 1.4/8;

        if (type == Utils.DividerType.DASHED) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 3, pixelsPerMeter * 9));
        } else if (type == Utils.DividerType.QUICK_DASHED) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 1, pixelsPerMeter * 3));
        } else if (type == Utils.DividerType.SOLID) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 3, 0));
        } else if (type == Utils.DividerType.DOUBLE_SOLID) {
            renderRoadLine(g, offsetStart + stripeWidth, offsetEnd + stripeWidth, Utils.DividerType.SOLID, color);
            renderRoadLine(g, offsetStart - stripeWidth, offsetEnd - stripeWidth, Utils.DividerType.SOLID, color);
            return;
        } else if (type == Utils.DividerType.DASHED_FOR_RIGHT) {
            renderRoadLine(g, offsetStart + stripeWidth, offsetEnd + stripeWidth, Utils.DividerType.SOLID, color);
            renderRoadLine(g, offsetStart - stripeWidth, offsetEnd - stripeWidth, Utils.DividerType.DASHED, color);
            return;
        } else if (type == Utils.DividerType.DASHED_FOR_LEFT) {
            renderRoadLine(g, offsetStart - stripeWidth, offsetEnd - stripeWidth, Utils.DividerType.SOLID, color);
            renderRoadLine(g, offsetStart + stripeWidth, offsetEnd + stripeWidth, Utils.DividerType.DASHED, color);
            return;
        } else if (type == Utils.DividerType.CENTRE_DIVIDER_WIDE) {
            renderRoadLine(g, offsetStart + ((getWidth(true)-Utils.RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((getWidth(false)-Utils.RENDERING_WIDTH_DIVIDER) / 2), Utils.DividerType.DOUBLE_SOLID, color);
            renderRoadLine(g, offsetStart - ((getWidth(true)-Utils.RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((getWidth(false)-Utils.RENDERING_WIDTH_DIVIDER) / 2), Utils.DividerType.DOUBLE_SOLID, color);
            return;
        } else if (type == Utils.DividerType.FORWARD_DIVIDER_WIDE) {
            renderRoadLine(g, offsetStart + ((getWidth(true)-Utils.RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((getWidth(false)-Utils.RENDERING_WIDTH_DIVIDER) / 2), Utils.DividerType.SOLID, color);
            renderRoadLine(g, offsetStart - ((getWidth(true)-Utils.RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((getWidth(false)-Utils.RENDERING_WIDTH_DIVIDER) / 2), Utils.DividerType.SOLID, color);
            return;
        } else if (type == Utils.DividerType.BACKWARD_DIVIDER_WIDE) {
            renderRoadLine(g, offsetStart + ((getWidth(true)-Utils.RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((getWidth(false)-Utils.RENDERING_WIDTH_DIVIDER) / 2), Utils.DividerType.SOLID, color);
            renderRoadLine(g, offsetStart - ((getWidth(true)-Utils.RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((getWidth(false)-Utils.RENDERING_WIDTH_DIVIDER) / 2), Utils.DividerType.SOLID, color);
            return;
        } else if (type == Utils.DividerType.CENTRE_LANE) {
            renderRoadLine(g, offsetStart + ((getWidth(true)-Utils.RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((getWidth(false)-Utils.RENDERING_WIDTH_DIVIDER) / 2), Utils.DividerType.DASHED_FOR_RIGHT, color);
            renderRoadLine(g, offsetStart - ((getWidth(true)-Utils.RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((getWidth(false)-Utils.RENDERING_WIDTH_DIVIDER) / 2), Utils.DividerType.DASHED_FOR_LEFT, color);
            return;
        }
        Way alignment = Utils.getParallel(_parent.getAlignment(), offsetStart, offsetEnd, false, _parent.otherStartAngle, _parent.otherEndAngle);
        int[] xPoints = new int[alignment.getNodesCount()];
        int[] yPoints = new int[alignment.getNodesCount()];
        for (int i = 0; i < alignment.getNodesCount(); i++) {
            xPoints[i] = (int) (_mv.getPoint(alignment.getNode(i).getCoor()).getX() + 0.5);
            yPoints[i] = (int) (_mv.getPoint(alignment.getNode(i).getCoor()).getY() + 0.5);
        }

        g.setColor(color);
        g.drawPolyline(xPoints, yPoints, xPoints.length);

        // THESE TWO LINES ARE FOR REMOVING THE WHITE BOX AROUND THE SCREEN... DON'T DELETE THESE
        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    protected double parseLaneWidth(String value) {
        if (value.endsWith(" lane")) {
            return Utils.WIDTH_LANES*Double.parseDouble(value.substring(0, value.length()-5));
        } else if (value.endsWith(" lanes")) {
            return Utils.WIDTH_LANES*Double.parseDouble(value.substring(0, value.length()-6));
        } else if (value.endsWith(" m")) {
            return Double.parseDouble(value.substring(0, value.length()-2));
        } else if (value.endsWith(" km")) {
            return 1000 * Double.parseDouble(value.substring(0, value.length()-3));
        } else if (value.endsWith(" mi")) {
            return 1609.344 * Double.parseDouble(value.substring(0, value.length()-3));
        } else if (value.endsWith("\'")) {
            return 1 / 3.28084 * Double.parseDouble(value.substring(0, value.length()-1));
        } else if (value.endsWith("\"") && !value.contains("'")) {
            return 1 / 3.28084 / 12 * Double.parseDouble(value.substring(0, value.length()-1));
        } else if (value.endsWith("\"") && value.contains("'")) {
            String[] split = value.split("'");
            double feet = Double.parseDouble(split[0]);
            double inches = Double.parseDouble(split[1].substring(0, value.length()-1));
            return 1 / 3.28084 * (feet + inches/12);
        }
        return Double.parseDouble(value);
    }

    private Stroke getCustomStroke(double width, double metersDash, double metersGap) {
        if (metersGap <= 0.01 && metersGap >= -0.01) {
            return new BasicStroke((float) width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1);
        } else {
            return new BasicStroke((float) width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1,
                    new float[] {(float) metersDash, (float) metersGap}, 0.0F);
        }
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
