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

public class Divider extends RoadPiece {
    Utils.DividerType _type = null;

    public Divider(int direction, int position, MapView mv, MarkedRoadRenderer parent) {
        super(direction, position, mv, parent);
    }

    @Override
    public double getWidth(boolean start) {
        // Returns width of rendered component in meters, not width of actual lane divider.
        double output = 0;
        String laneSymbol = "\\|"; // This is what is used between divider widths to mark a lane.
        try {
            String value = "0";
            if (_direction == 0) {
                if (_way.hasTag("width:centre_divider:start") && start) {
                    value = _way.getInterestingTags().get("width:centre_divider:start");
                } else if (_way.hasTag("width:centre_divider:end") && !start) {
                    value = _way.getInterestingTags().get("width:centre_divider:end");
                } else if (_way.hasTag("width:centre_divider")) {
                    value = _way.getInterestingTags().get("width:centre_divider");
                } else {
                    value = "0";
                }
                output = Utils.parseWidth(value);
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
                output = Utils.parseWidth(value);
            }
            if (_direction == -1) {
                if (_way.hasTag("width:dividers:backward:start") && start) {
                    value = _way.getInterestingTags().get("width:dividers:backward:start").split(laneSymbol)[_position];
                } else if (_way.hasTag("width:dividers:backward:end") && !start) {
                    value = _way.getInterestingTags().get("width:dividers:backward:end").split(laneSymbol)[_position];
                } else if (_way.hasTag("width:dividers:backward")) {
                    value = _way.getInterestingTags().get("width:dividers:backward").split(laneSymbol)[_position];
                }
                output = Utils.parseWidth(value);
            }
        } catch (Exception ignored) {}
        return output + Utils.RENDERING_WIDTH_DIVIDER;
    }

    @Override
    public void render(Graphics2D g) {
        if (_direction == 0 && (getWidth(true) > Utils.RENDERING_WIDTH_DIVIDER + 0.5 ||
                    getWidth(false) > Utils.RENDERING_WIDTH_DIVIDER + 0.5)) {
            Utils.renderRoadLine(g, _mv, _parent, getWidth(true), getWidth(false), _offsetStart, _offsetEnd, Utils.DividerType.CENTRE_DIVIDER_WIDE, Color.YELLOW);
        }
        else if (_direction == 1 && (getWidth(true) > Utils.RENDERING_WIDTH_DIVIDER + 0.5 ||
                    getWidth(false) > Utils.RENDERING_WIDTH_DIVIDER + 0.5)) {
            Utils.renderRoadLine(g, _mv, _parent, getWidth(true), getWidth(false), _offsetStart, _offsetEnd, Utils.DividerType.FORWARD_DIVIDER_WIDE, Color.WHITE);
        }
        else if (_direction == -1 && (getWidth(true) > Utils.RENDERING_WIDTH_DIVIDER + 0.5 ||
                    getWidth(false) > Utils.RENDERING_WIDTH_DIVIDER + 0.5)) {
            Utils.renderRoadLine(g, _mv, _parent, getWidth(true), getWidth(false), _offsetStart, _offsetEnd, Utils.DividerType.BACKWARD_DIVIDER_WIDE, Color.WHITE);
        }
        else {
            if (_type == null) getDividerType();
            Utils.DividerType type = _type;
            if (_direction == -1) {
                if (_type == Utils.DividerType.DASHED_FOR_RIGHT) type = Utils.DividerType.DASHED_FOR_LEFT;
                if (_type == Utils.DividerType.DASHED_FOR_LEFT) type = Utils.DividerType.DASHED_FOR_RIGHT;
            }
            Utils.renderRoadLine(g, _mv, _parent, getWidth(true), getWidth(false), _offsetStart, _offsetEnd, type, _direction == 0 ? Color.YELLOW : Color.WHITE);
        }
    }

    @Override
    public boolean defaultChangeToThis() {
        return _direction != 0;
    }

    // <editor-fold defaultstate="collapsed" desc="Methods For Displaying Pop-Up"

    @Override
    protected JPanel getPopupJPanel() {
        JPanel output = new JPanel();
        output.setLayout(new GridLayout(0, _direction == 0 ? 4 /* fixme */ : 4));
        if (_direction == 0) {
                output.add(getIconButton(Utils.DividerType.SOLID, "SolidCentre"));
                output.add(getIconButton(Utils.DividerType.DASHED_FOR_RIGHT, "SolidLeftCentre"));
                output.add(getIconButton(Utils.DividerType.DASHED_FOR_LEFT, "SolidRightCentre"));
                output.add(getIconButton(Utils.DividerType.DASHED, "DashedCentre"));
            } else {
                output.add(getIconButton(Utils.DividerType.SOLID, "Solid"));
                output.add(getIconButton(Utils.DividerType.DASHED_FOR_RIGHT, "SolidLeft"));
                output.add(getIconButton(Utils.DividerType.DASHED_FOR_LEFT, "SolidRight"));
                output.add(getIconButton(Utils.DividerType.DASHED, "Dashed"));
            }
        return output;
    }

    private JPanel getIconButton(Utils.DividerType type, String path) {
        JPanel buttonWrapper = new JPanel();
        buttonWrapper.setBorder(new StrokeBorder(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND),
                new Color(0, 0, 0, 0)));
        Image buttonIcon = ImageProvider.get("dialogs", path + ".png").getImage();
        JButton button = new JButton(new ImageIcon(buttonIcon));
        button.setMinimumSize(new Dimension(50, 50));
        buttonWrapper.add(button);
        button.setBorder(new EmptyBorder(10, 10, 10, 10));
        button.setContentAreaFilled(false);
        button.addMouseListener(getButtonMouseListener(type));
        return buttonWrapper;
    }

    private MouseListener getButtonMouseListener(Utils.DividerType type) {
        return new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Since the roadPieces get replaced each time a tag gets changed, find the correct NEW
                // divider in the same place as this divider. This will allow each pop-up to be used more than once.
                List<RoadPiece> roadPieces = _parent.getRoadPieces(false);
                for (RoadPiece rp : roadPieces) {
                    if(rp instanceof Divider && rp._direction == _direction && rp._position == _position) {
                        ((Divider) rp).setDividerType(type);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        };
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for change:lanes Tags"

    private void getDividerType() {
        // Define cd and rh to make the logic statements more readable:
        boolean cd = _direction == 0;
        boolean rh = Utils.isRightHand(_way);

        // Get change values of the right side and left side lanes:
        boolean changeFromLeft = extractChange((Lane) _left, !cd || !rh);
        boolean changeFromRight = extractChange((Lane) _right, cd && !rh);

        // If one of the lanes is opening/closing, take note.  This is so the renderer can render some dashed lines as quick dashes.
        boolean widthChanges = Math.abs(_left.getWidth(true) - _left.getWidth(false)) > 0.5 ||
                Math.abs(_right.getWidth(true) - _right.getWidth(false)) > 0.5;

        // Set the divider type based on the known information:
        Utils.DividerType typeIfDashed = (cd || !widthChanges) ? Utils.DividerType.DASHED : Utils.DividerType.QUICK_DASHED;

        Utils.DividerType typeIfNotDashed = (changeFromLeft) ? Utils.DividerType.DASHED_FOR_LEFT :
                                            (changeFromRight) ? Utils.DividerType.DASHED_FOR_RIGHT :
                                            cd ? Utils.DividerType.DOUBLE_SOLID : Utils.DividerType.SOLID;

        _type = (changeFromLeft && changeFromRight) ? typeIfDashed : typeIfNotDashed;
    }

    private void setDividerType(Utils.DividerType type) {
        // Define cd and rh to make the logic statements more readable:
        boolean cd = _direction == 0;
        boolean rh = Utils.isRightHand(_way);

        // Convert DividerType to change from left/right:
        boolean changeFromLeft = type == Utils.DividerType.DASHED ||
                type == Utils.DividerType.DASHED_FOR_LEFT || type == Utils.DividerType.QUICK_DASHED;
        boolean changeFromRight = type == Utils.DividerType.SOLID || type == Utils.DividerType.SOLID_DIVIDER_WIDE ||
                type == Utils.DividerType.DOUBLE_SOLID || type==Utils.DividerType.CENTRE_DIVIDER_WIDE ||
                type == Utils.DividerType.DASHED_FOR_LEFT;

        // Extract existing change values from nearby lanes:
        boolean leftChangeLeft   = extractChange((Lane) _left,  false);
        boolean leftChangeRight  = extractChange((Lane) _left,  true);
        boolean rightChangeLeft  = extractChange((Lane) _right, false);
        boolean rightChangeRight = extractChange((Lane) _right, true);
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
        ((Lane) _left).setChange(changeLeft);
        ((Lane) _right).setChange(changeRight);
        _parent.updateChangeTags();
    }

    private boolean extractChange(Lane lane, boolean toRight) {
        String change = lane.getChange();

        if (change.equals("yes")) return true;
        if (change.equals("no")) return false;
        if (change.equals("not_right")) return !toRight;
        if (change.equals("not_left")) return toRight;

        throw new RuntimeException("getChange() method returned invalid value \"" + change + "\", unreachable code reached.");
    }

    // </editor-fold>
}

