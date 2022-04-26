package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.tools.ImageProvider;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.StrokeBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;

/*
 * RoadPieceDivider - stores information about a divider between two lanes in a RoadRendererMarked.
 *
 * -> Renders and edits dividers.
 * -> Handles most of the work surrounding change:lanes tagging.
 */

public class RoadPieceDivider extends RoadPiece {
    DividerType _type = null;

    public RoadPieceDivider(int direction, int position, MapView mv, RoadRendererMarked parent) {
        super(direction, position, mv, parent);
    }

    @Override
    public double getWidth(boolean start) {
        // Returns width of rendered component, not actual divider.
        return getWidthTagged(start) + UtilsRender.RENDERING_WIDTH_DIVIDER;
    }

    @Override
    public double getWidthTagged(boolean start) {
        // Returns width of divider, not rendered component.
        double output = UtilsGeneral.parseWidth(widthTag(start));
        if (Double.isNaN(output)) output = 0;
        return output;
    }

    @Override
    String widthTag(boolean start) {
        String laneSymbol = "\\|"; // This is what is used between divider widths to mark a lane.
        String value = null;
        try {
            if (_direction == 0) {
                if (_way.hasTag("width:centre_divider:start") && start) {
                    value = _way.getInterestingTags().get("width:centre_divider:start");
                } else if (_way.hasTag("width:centre_divider:end") && !start) {
                    value = _way.getInterestingTags().get("width:centre_divider:end");
                } else if (_way.hasTag("width:centre_divider")) {
                    value = _way.getInterestingTags().get("width:centre_divider");
                }
            }
            if (_direction == 1) {
                if (_way.hasTag("width:dividers:forward:start") && start) {
                    value = _way.getInterestingTags().get("width:dividers:forward:start").split(laneSymbol)[_position];
                } else if (_way.hasTag("width:dividers:forward:end") && !start) {
                    value = _way.getInterestingTags().get("width:dividers:forward:end").split(laneSymbol)[_position];
                } else if (_way.hasTag("width:dividers:forward")) {
                    value = _way.getInterestingTags().get("width:dividers:forward").split(laneSymbol)[_position];
                } else if (_way.hasTag("width:dividers:start") && _way.isOneway() == 1 && start) {
                    value = _way.getInterestingTags().get("width:dividers:start").split(laneSymbol)[_position];
                } else if (_way.hasTag("width:dividers:end") && _way.isOneway() == 1 && !start) {
                    value = _way.getInterestingTags().get("width:dividers:end").split(laneSymbol)[_position];
                } else if (_way.hasTag("width:dividers") && _way.isOneway() == 1) {
                    value = _way.getInterestingTags().get("width:dividers").split(laneSymbol)[_position];
                }
            }
            if (_direction == -1) {
                if (_way.hasTag("width:dividers:backward:start") && start) {
                    value = _way.getInterestingTags().get("width:dividers:backward:start").split(laneSymbol)[_position];
                } else if (_way.hasTag("width:dividers:backward:end") && !start) {
                    value = _way.getInterestingTags().get("width:dividers:backward:end").split(laneSymbol)[_position];
                } else if (_way.hasTag("width:dividers:backward")) {
                    value = _way.getInterestingTags().get("width:dividers:backward").split(laneSymbol)[_position];
                }
            }
        } catch (Exception ignored) {}
        return value;
    }

    @Override
    public void render(Graphics2D g) {
        if (_mv.getScale() > 4) return; // Don't render when the map is too zoomed out

        if (_direction == 0 && (getWidth(true) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5 ||
                    getWidth(false) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5)) {
            UtilsRender.renderRoadLine(g, _mv, _parent, getWidth(true), getWidth(false), _offsetStart, _offsetEnd, DividerType.CENTRE_DIVIDER_WIDE, Color.YELLOW, false);
        }
        else if (_direction == 1 && (getWidth(true) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5 ||
                    getWidth(false) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5)) {
            UtilsRender.renderRoadLine(g, _mv, _parent, getWidth(true), getWidth(false), _offsetStart, _offsetEnd, DividerType.FORWARD_DIVIDER_WIDE, Color.WHITE, false);
        }
        else if (_direction == -1 && (getWidth(true) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5 ||
                    getWidth(false) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5)) {
            UtilsRender.renderRoadLine(g, _mv, _parent, getWidth(true), getWidth(false), _offsetStart, _offsetEnd, DividerType.BACKWARD_DIVIDER_WIDE, Color.WHITE, false);
        }
        else {
            if (_type == null) getDividerType();
            DividerType type = _type;
            if (_direction == -1) {
                if (_type == DividerType.DASHED_FOR_RIGHT) type = DividerType.DASHED_FOR_LEFT;
                if (_type == DividerType.DASHED_FOR_LEFT) type = DividerType.DASHED_FOR_RIGHT;
            }
            UtilsRender.renderRoadLine(g, _mv, _parent, getWidth(true), getWidth(false), _offsetStart, _offsetEnd, type, _direction == 0 ? Color.YELLOW : Color.WHITE, false);
        }
    }

    @Override
    void renderPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter) {
        double offsetStart = _offsetStart-(_parent._offsetToLeftStart - _parent.getWidth(true)/2);
        double offsetEnd = _offsetEnd-(_parent._offsetToLeftEnd - _parent.getWidth(false)/2);

        Point lineStart = UtilsRender.goInDirection(UtilsRender.goInDirection(center, bearing+Math.PI, distOut), bearing-Math.PI/2, pixelsPerMeter*offsetStart);
        Point lineEnd = UtilsRender.goInDirection(UtilsRender.goInDirection(center, bearing, distOut), bearing-Math.PI/2, pixelsPerMeter*offsetEnd);

        if (_direction == 0 && (getWidth(true) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5 ||
                getWidth(false) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5)) {
            UtilsRender.renderRoadLinePopup(g, lineStart, lineEnd, bearing, getWidth(true), getWidth(false), pixelsPerMeter, DividerType.CENTRE_DIVIDER_WIDE, Color.YELLOW);
        }
        else if (_direction == 1 && (getWidth(true) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5 ||
                getWidth(false) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5)) {
            UtilsRender.renderRoadLinePopup(g, lineStart, lineEnd, bearing, getWidth(true), getWidth(false), pixelsPerMeter, DividerType.FORWARD_DIVIDER_WIDE, Color.WHITE);
        }
        else if (_direction == -1 && (getWidth(true) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5 ||
                getWidth(false) > UtilsRender.RENDERING_WIDTH_DIVIDER + 0.5)) {
            UtilsRender.renderRoadLinePopup(g, lineStart, lineEnd, bearing, getWidth(true), getWidth(false), pixelsPerMeter, DividerType.BACKWARD_DIVIDER_WIDE, Color.WHITE);
        }
        else {
            if (_type == null) getDividerType();
            DividerType type = _type;
            if (_direction == -1) {
                if (_type == DividerType.DASHED_FOR_RIGHT) type = DividerType.DASHED_FOR_LEFT;
                if (_type == DividerType.DASHED_FOR_LEFT) type = DividerType.DASHED_FOR_RIGHT;
            }
            UtilsRender.renderRoadLinePopup(g, lineStart, lineEnd, bearing, getWidth(true), getWidth(false), pixelsPerMeter, type, _direction == 0 ? Color.YELLOW : Color.WHITE);
        }

    }

    @Override
    public boolean defaultChangeToThis() {
        return _direction != 0;
    }

    // <editor-fold defaultstate="collapsed" desc="Methods For Displaying Pop-Up"

    @Override
    protected JPanel getPopupContent() {
        JPanel output = new JPanel();
        output.setLayout(new GridLayout(0, _direction == 0 ? 4 /* fixme make 6 */ : 4));
        if (_direction == 0) {
            output.add(getIconButton(DividerType.SOLID, "SolidCentre"));
            output.add(getIconButton(DividerType.DASHED_FOR_RIGHT, "SolidLeftCentre"));
            output.add(getIconButton(DividerType.DASHED_FOR_LEFT, "SolidRightCentre"));
            output.add(getIconButton(DividerType.DASHED, "DashedCentre"));
        } else {
            output.add(getIconButton(DividerType.SOLID, "Solid"));
            output.add(getIconButton(DividerType.DASHED_FOR_RIGHT, "SolidLeft"));
            output.add(getIconButton(DividerType.DASHED_FOR_LEFT, "SolidRight"));
            output.add(getIconButton(DividerType.DASHED, "Dashed"));
        }
        return output;
    }

    private JPanel getIconButton(DividerType type, String path) {
        JPanel buttonWrapper = new JPanel();
        buttonWrapper.setBorder(new StrokeBorder(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND),
                new Color(0, 0, 0, 0)));
        Image buttonIcon = ImageProvider.get("dialogs", path).getImage();
        JButton button = new JButton(new ImageIcon(buttonIcon));
        button.setMinimumSize(new Dimension(50, 50));
        buttonWrapper.add(button);
        button.setBorder(new EmptyBorder(10, 10, 10, 10));
        button.setContentAreaFilled(false);
        button.addMouseListener(getButtonMouseListener(type));
        return buttonWrapper;
    }

    private MouseListener getButtonMouseListener(DividerType type) {
        return new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Since the roadPieces get replaced each time a tag gets changed, find the correct NEW
                // divider in the same place as this divider. This will allow each pop-up to be used more than once.
                RoadRenderer parent = _parent._parent.wayIdToRSR.get(_parent.getWay().getUniqueId());
                if (!(parent instanceof RoadRendererMarked)) return;
                List<RoadPiece> roadPieces = ((RoadRendererMarked) parent).getRoadPieces(false);
                for (RoadPiece rp : roadPieces) {
                    if (rp instanceof RoadPieceDivider && rp._direction == _direction && rp._position == _position) {
                        ((RoadPieceDivider) rp).setDividerType(type);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {}

            @Override
            public void mouseReleased(MouseEvent e) {}

            @Override
            public void mouseEntered(MouseEvent e) {}

            @Override
            public void mouseExited(MouseEvent e) {}
        };
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for change:lanes Tags"

    private void getDividerType() {
        // Define cd and rh to make the logic statements more readable:
        boolean cd = _direction == 0;
        boolean rh = UtilsGeneral.isRightHand(_way);

        // Get change values of the right side and left side lanes:
        boolean changeFromLeft = extractChange((RoadPieceLane) _left, !cd || !rh);
        boolean changeFromRight = extractChange((RoadPieceLane) _right, cd && !rh);

        // If one of the lanes is opening/closing, take note.  This is so the renderer can render some dashed lines as quick dashes.
        boolean widthChanges = _left.getWidth(true) == 0 || _left.getWidth(false) == 0 ||
                _right.getWidth(true) == 0 || _right.getWidth(false) == 0;

        // Set the divider type based on the known information:
        DividerType typeIfDashed = (cd || !widthChanges) ? DividerType.DASHED : DividerType.QUICK_DASHED;

        DividerType typeIfNotDashed = (changeFromLeft) ? DividerType.DASHED_FOR_LEFT :
                                            (changeFromRight) ? DividerType.DASHED_FOR_RIGHT :
                                            cd ? DividerType.DOUBLE_SOLID : DividerType.SOLID;

        _type = (changeFromLeft && changeFromRight) ? typeIfDashed : typeIfNotDashed;
    }

    private void setDividerType(DividerType type) {
        // Define cd and rh to make the logic statements more readable:
        boolean cd = _direction == 0;
        boolean rh = UtilsGeneral.isRightHand(_way);

        // Convert DividerType to change from left/right:
        boolean changeFromLeft = type == DividerType.DASHED ||
                type == DividerType.DASHED_FOR_LEFT || type == DividerType.QUICK_DASHED;
        boolean changeFromRight = type == DividerType.SOLID || type == DividerType.SOLID_DIVIDER_WIDE ||
                type == DividerType.DOUBLE_SOLID || type==DividerType.CENTRE_DIVIDER_WIDE ||
                type == DividerType.DASHED_FOR_LEFT;

        // Extract existing change values from nearby lanes:
        boolean leftChangeLeft   = extractChange((RoadPieceLane) _left,  false);
        boolean leftChangeRight  = extractChange((RoadPieceLane) _left,  true);
        boolean rightChangeLeft  = extractChange((RoadPieceLane) _right, false);
        boolean rightChangeRight = extractChange((RoadPieceLane) _right, true);
        JOptionPane.showMessageDialog(_mv, _position + ":" + leftChangeLeft + ", " + leftChangeRight + ", " + rightChangeLeft + ", " + rightChangeRight);

        // Change those extracted values, paying close attention to the center-divider and right-hand / left-hand state.
        if (!cd || !rh) { leftChangeRight = changeFromLeft; } else { leftChangeLeft = changeFromLeft; }
        if (cd && !rh) { rightChangeRight = changeFromRight; } else { rightChangeLeft = changeFromRight; }
        JOptionPane.showMessageDialog(_mv, _position + ":" + leftChangeLeft + ", " + leftChangeRight + ", " + rightChangeLeft + ", " + rightChangeRight);

        // Convert those values to change values for the left / right sides:
        String changeLeft = (leftChangeLeft && leftChangeRight) ? "yes" :
                (leftChangeLeft) ? "not_right" : (leftChangeRight) ? "not_left" : "no";
        String changeRight = (rightChangeLeft && rightChangeRight) ? "yes" :
                (rightChangeLeft) ? "not_right" : (rightChangeRight) ? "not_left" : "no";

        // Change the tags of the ways to the right and left of this divider:
        ((RoadPieceLane) _left).setChange(changeLeft);
        ((RoadPieceLane) _right).setChange(changeRight);
        _parent.updateChangeTags();
    }

    private boolean extractChange(RoadPieceLane lane, boolean toRight) {
        String change = lane.getChange();

        if (change.equals("yes")) return true;
        if (change.equals("no")) return false;
        if (change.equals("not_right")) return !toRight;
        if (change.equals("not_left")) return toRight;

        throw new RuntimeException("getChange() method returned invalid value \"" + change + "\", unreachable code reached.");
    }

    // </editor-fold>
}

