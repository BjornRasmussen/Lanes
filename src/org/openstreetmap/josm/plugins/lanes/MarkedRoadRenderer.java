package org.openstreetmap.josm.plugins.lanes;

import jdk.jfr.Percentage;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class MarkedRoadRenderer extends RoadRenderer {

    // <editor-fold defaultstate="collapsed" desc="Variables">

    private Way _outline;
    protected Way _alignment;

    public static String selected = "";

    public double _offsetToLeftStart; // Distance between left side of road and the centre of the way.
    public double _offsetToLeftEnd;

    public final List<RoadPiece> _forwardLanes = new ArrayList<>();
    public final List<RoadPiece> _forwardDividers = new ArrayList<>();
    public final List<RoadPiece> _backwardLanes = new ArrayList<>();
    public final List<RoadPiece> _backwardDividers = new ArrayList<>();
    private RoadPiece _bothWaysLane;
    private RoadPiece _leftRoadEdge;
    private RoadPiece _rightRoadEdge;

    protected boolean _isValid = true; // Display red error line if tags are wrong.

    // </editor-fold>

    protected MarkedRoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        super(w, mv, parent);

        try { createRoadLayout(); } catch (Exception e) { _isValid = false; _alignment = w; }
    }

    @Override
    public Way getAlignment() { return _alignment; }

    // <editor-fold defaultstate="collapsed" desc="Methods for rendering">

    public void render(Graphics2D g) {
        if (!_isValid) {
            // Get the centre line of the road to be rendered.
            int[] pointXs = new int[_way.getNodesCount()];
            int[] pointYs = new int[_way.getNodesCount()];
            for (int i = 0; i < _way.getNodesCount(); i++) {
                pointXs[i] = _mv.getPoint(_way.getNode(i).getCoor()).x;
                pointYs[i] = _mv.getPoint(_way.getNode(i).getCoor()).y;
            }

            // Set the color and width to the "invalid" defaults.
            g.setColor(Utils.DEFAULT_INVALID_COLOR);
            g.setStroke(new BasicStroke((int) (Utils.
                    WIDTH_INVALID_METERS * 100.0 / _mv.getDist100Pixel()),
                    BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));

            // Draw the way
            g.drawPolyline(pointXs, pointYs, pointXs.length);

            // Get rid of that white rectangle that was appearing around the screen at high zoom levels:
            g.setColor(new Color(0, 0, 0, 0));
            g.setStroke(GuiHelper.getCustomizedStroke("0"));
            return;
        }
        try {
            renderAsphalt(g, Utils.DEFAULT_ASPHALT_COLOR);
            List<RoadPiece> roadPieces = getRoadPieces(true);
            for (RoadPiece roadPiece : roadPieces) {
                roadPiece.render(g);
            }
        } catch (Exception ignored) {} // Don't render roads that can't be rendered (due to crazy alignments or lanes that go from 0 to 1000 m wide in 10 m).
    }

    @Override
    void renderPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter) {
        renderAsphaltPopup(g, Utils.POPUP_ASPHALT_COLOR, center, bearing, distOut, pixelsPerMeter);

        for (RoadPiece p : getRoadPieces(true)) {
            p.renderPopup(g, center, bearing, distOut, pixelsPerMeter);
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for Parsing Lane Tags">

    private void createRoadLayout() {
        // Generate the road-layout / cross-section of _w.
        getLanesFromWay();

        // Get placement information.
        getPlacementInformation();

        // Apply placement information to each lane/divider.
        getOffsets();
    }


    private void getLanesFromWay() {
        Map<String, String> tags = _way.getInterestingTags();

        if (_way.isOneway() == -1) { // Supporting this would be suicidal.
            _isValid = false;
        }

        int numLanesForward  = getLanesInDirectionFromSuffix(1);
        int numLanesBackward = getLanesInDirectionFromSuffix(-1);
        int numLanesBothWays = getLanesInDirectionFromSuffix(0);

        if (tags.containsKey("lanes:forward") && numLanesForward == -1) {
            numLanesForward = Integer.parseInt(tags.get("lanes:forward"));
        }
        if (tags.containsKey("lanes") && Utils.isOneway(_way) && numLanesForward == -1) {
            numLanesForward = Integer.parseInt(tags.get("lanes"));
        }
        if (tags.containsKey("lanes:backward") && numLanesBackward == -1) {
            numLanesBackward = Integer.parseInt(tags.get("lanes:backward"));
        }
        if (tags.containsKey("lanes:both_ways") && numLanesBothWays == -1) {
            numLanesBothWays = Integer.parseInt(tags.get("lanes:both_ways"));
        }

        if (numLanesBothWays == -1) numLanesBothWays = 0; // Assume no centre lane.

        // Distribute remaining lanes to unspecified directions (lanes=5 & lanes:forward=3 -> assume lanes:backward=2)
        if (!(numLanesBackward == 0 && numLanesForward == 0 && numLanesBothWays != 0) && tags.containsKey("lanes") && !Utils.isOneway(_way)) {
            int lanes = Integer.parseInt(tags.get("lanes")) - numLanesBothWays;
            if (numLanesForward == -1 && numLanesBackward == -1) {
                numLanesForward = lanes-lanes/2;
                numLanesBackward = lanes/2;
            } else if (numLanesForward == -1) {
                numLanesForward = lanes - numLanesBackward;
            } else if (numLanesBackward == -1) {
                numLanesBackward = lanes - numLanesForward;
            }
        }

        if (numLanesBothWays == 1 && numLanesBackward == 0 && numLanesForward == 0) {
            numLanesForward = 1;
            numLanesBothWays = 0;
        }

        _leftRoadEdge = new RoadEdge(Utils.isRightHand(_way) ? -1 : 1, -1, _mv, this);
        _rightRoadEdge = new RoadEdge(Utils.isRightHand(_way) ? 1 : -1, -1, _mv, this);

        for (int i = 0; i < numLanesForward; i++) {
            _forwardLanes.add(new Lane(1, i, _mv, this));
            if (i != numLanesForward-1) {
                _forwardDividers.add(new Divider(1, i, _mv, this));
                _forwardLanes.get(i).setRightPiece(_forwardDividers.get(i));
                _forwardDividers.get(i).setLeftPiece(_forwardLanes.get(i));
            }
            if (i != 0) {
                _forwardLanes.get(i).setLeftPiece(_forwardDividers.get(i-1));
                _forwardDividers.get(i-1).setRightPiece(_forwardLanes.get(i));
            }
        }
        for (int i = 0; i < numLanesBackward; i++) {
            _backwardLanes.add(new Lane(-1, i, _mv, this));
            if (i != numLanesBackward-1) {
                _backwardDividers.add(new Divider(-1, i, _mv, this));
                _backwardLanes.get(i).setRightPiece(_backwardDividers.get(i));
                _backwardDividers.get(i).setLeftPiece(_backwardLanes.get(i));
            }
            if (i != 0) {
                _backwardLanes.get(i).setLeftPiece(_backwardDividers.get(i-1));
                _backwardDividers.get(i-1).setRightPiece(_backwardLanes.get(i));
            }
        }

        if (numLanesBackward != 0) {
            if (numLanesBothWays <= 0) {
                _bothWaysLane = new Divider(0, 0, _mv, this);
            } else {
                _bothWaysLane = new Lane(0, 0, _mv, this);
            }
        }
        if (_backwardLanes.size() != 0) {
            if (Utils.isRightHand(_way)) {
                _backwardLanes.get(0).setLeftPiece(_bothWaysLane);
                _bothWaysLane.setLeftPiece(_backwardLanes.get(0));
                _forwardLanes.get(0).setLeftPiece(_bothWaysLane);
                _bothWaysLane.setRightPiece(_forwardLanes.get(0));
            } else {
                _backwardLanes.get(_backwardLanes.size() - 1).setRightPiece(_bothWaysLane);
                _bothWaysLane.setRightPiece(_backwardLanes.get(_backwardLanes.size() - 1));
                _forwardLanes.get(_forwardLanes.size() - 1).setRightPiece(_bothWaysLane);
                _bothWaysLane.setLeftPiece(_forwardLanes.get(_forwardLanes.size() - 1));
            }
        }
    }

    private int getLanesInDirectionFromSuffix(int direction) {
        Map<String, String> tags = _way.getInterestingTags();
        int numLanes = -1;
        for (String key : tags.keySet()) {
            if (!key.contains("note") && (direction == 1 ? ((key.endsWith(":lanes") && Utils.isOneway(_way)) || key.endsWith(":lanes:forward")) :
                    direction == 0 ? key.endsWith(":lanes:both_ways") : key.endsWith(":lanes:backward"))) {

                // This runs if the tag being analyzed is a lane tag applying to this direction.
                int len = ("a" + tags.get(key) + "a").split("\\|").length;
                if (numLanes == -1) numLanes = len;
                if (numLanes != len) {
                    _isValid = false;
                }
            }
        }
        return numLanes;
    }

    public int getLaneCount(int direction) {
        if (direction > 0) return _forwardLanes.size();
        if (direction < 0) return _backwardLanes.size();
        return _bothWaysLane instanceof Lane ? 1 : 0;
    }

    private void getPlacementInformation() {
        _offsetToLeftStart = getPlacementAt(true, false);
        _offsetToLeftEnd = getPlacementAt(false, false);
        if (Double.isNaN(_offsetToLeftEnd)) {
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(), "End is NaN");
        }
        double placementDiff = getPlacementAt(false, true) - getPlacementAt(true, true);
        _alignment = Utils.getParallel(_way, 0, placementDiff, true, otherStartAngle, otherEndAngle);
        _offsetToLeftEnd -= placementDiff;
    }


    private double getPlacementAt(boolean start, boolean ignoreWidthTags) {
        return getPlacementDistance(start, getPlacementTag(start), ignoreWidthTags);
    }

    /** Calculates the offset from this road's way to the position specified by otherPlacement. */
    public double getPlacementOffsetFrom(String otherPlacement, int node) {
        boolean start = node == 0; // TODO support nodes in the middle of the way.
        return getPlacementAt(start, false) - getPlacementDistance(start, otherPlacement, false);
    }

    private double getPlacementDistance(boolean start, String placement, boolean ignoreWidthTags) {
        try {
            int direction = placement.charAt(placement.length() - 1) == 'f' ? 1 : placement.charAt(placement.length() - 1) == 'b' ? -1 : 0;
            placement = placement.substring(0, placement.length() - 1);
            int lane = Integer.parseInt(placement.split(":")[1]);

//        int directionOther = -2;
//        if (placementOther != null) {
//            directionOther = placementOther.charAt(placementOther.length()-1) == 'f' ? 1 :
//                    placementOther.charAt(placementOther.length()-1) == 'b' ? -1 : 0;
//            placementOther = placementOther.substring(0, placementOther.length()-1);
//        }

//        int laneOther = Integer.MIN_VALUE;
//        if (placementOther != null) Integer.parseInt(placementOther.split(":")[1]); // ?? isn't this a no-op?

            // Get offset
            List<RoadPiece> pieces = getRoadPieces(false);
            double offsetSoFar = 0;
            boolean valid = false;

            // For when placement is outside of the range of this road segment:
            RoadPiece minForward = null;
            RoadPiece maxForward = null;
            RoadPiece minBackward = null;
            RoadPiece maxBackward = null;

            double minForwardOffset = Double.NaN;
            double maxForwardOffset = Double.NaN;
            double minBackwardOffset = Double.NaN;
            double maxBackwardOffset = Double.NaN;

            for (int i = 1; i < pieces.size(); i++) {
                RoadPiece p = pieces.get(i);

                boolean correctLane = p._position+1 == lane && p._direction == direction && (p._direction == 0 || p instanceof Lane);
//                boolean correctLaneOther = false;
//                if (placementOther != null) correctLaneOther = p._position+1 == laneOther && p._direction == directionOther && (p._direction == 0 || p instanceof Lane);

                offsetSoFar += (ignoreWidthTags && !correctLane /*&& !correctLaneOther*/) ? (pieces.get(i-1) instanceof Lane ? (Utils.
                        WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER) :
                        Utils.RENDERING_WIDTH_DIVIDER)/2 : (pieces.get(i-1).getWidth(start) / 2);
                offsetSoFar += (ignoreWidthTags && !correctLane /*&& !correctLaneOther*/) ? (p instanceof Lane ? (Utils.
                        WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER) :
                        Utils.RENDERING_WIDTH_DIVIDER)/2 : (p.getWidth(start) / 2);

                if (p._direction == 1 && p instanceof Lane && (minForward == null || minForward._position > p._position)) {
                    minForward = p;
                    minForwardOffset = offsetSoFar;
                }
                if (p._direction == 1 && p instanceof Lane && (maxForward == null || maxForward._position < p._position)) {
                    maxForward = p;
                    maxForwardOffset = offsetSoFar;
                }
                if (p._direction == -1 && p instanceof Lane  && (minBackward == null || minBackward._position > p._position)) {
                    minBackward = p;
                    minBackwardOffset = offsetSoFar;
                }
                if (p._direction == -1 && p instanceof Lane  && (maxBackward == null || maxBackward._position < p._position)) {
                    maxBackward = p;
                    maxBackwardOffset = offsetSoFar;
                }

                if (p._position+1 == lane && p._direction == direction && (p._direction == 0 || p instanceof Lane)) {
                    if ((direction == 1 && placement.startsWith("left_of")) ||
                            (direction == -1 && placement.startsWith("right_of"))) {
                        offsetSoFar -= Utils.RENDERING_WIDTH_DIVIDER / 2 + (ignoreWidthTags ? (Utils.
                                WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER)/2 : (p.getWidth(start) / 2));
                    }
                    if ((direction == 1 && placement.startsWith("right_of")) ||
                            (direction == -1 && placement.startsWith("left_of"))) {
                        offsetSoFar += Utils.RENDERING_WIDTH_DIVIDER / 2 + (ignoreWidthTags ? (Utils.
                                WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER)/2 : (p.getWidth(start) / 2));
                    }
                    valid = true;
                    break;
                }
            }

            if (!valid && direction == 1) {
                if (minForward._position+1 > lane) {
                    int numLanesAway = minForward._position + 1 - lane;
                    offsetSoFar = minForwardOffset - numLanesAway * Utils.WIDTH_LANES;

                    if (placement.startsWith("left_of")) {
                        offsetSoFar -= Utils.RENDERING_WIDTH_DIVIDER / 2 + (ignoreWidthTags ? (Utils.
                                WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER)/2 : minForward.getWidth(start) / 2);
                    }
                    if (placement.startsWith("right_of")) {
                        offsetSoFar += Utils.RENDERING_WIDTH_DIVIDER / 2 + (ignoreWidthTags ? (Utils.
                                WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER)/2 : minForward.getWidth(start) / 2);
                    }
                }
                if (maxForward._position+1 < lane) {
                    int numLanesAway = maxForward._position + 1 - lane;
                    offsetSoFar = maxForwardOffset - numLanesAway * Utils.WIDTH_LANES;

                    if (placement.startsWith("left_of")) {
                        offsetSoFar -= Utils.RENDERING_WIDTH_DIVIDER / 2 + (ignoreWidthTags ? (Utils.
                                WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER)/2 : maxForward.getWidth(start) / 2);
                    }
                    if (placement.startsWith("right_of")) {
                        offsetSoFar += Utils.RENDERING_WIDTH_DIVIDER / 2 + (ignoreWidthTags ? (Utils.
                                WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER)/2 : maxForward.getWidth(start) / 2);
                    }
                }
                valid = true;
            }

            if (!valid && direction == -1) {
                if (minBackward._position+1 > lane) {
                    int numLanesAway = minBackward._position + 1 - lane;
                    offsetSoFar = minBackwardOffset + numLanesAway * Utils.WIDTH_LANES;

                    if (placement.startsWith("left_of")) {
                        offsetSoFar += Utils.RENDERING_WIDTH_DIVIDER / 2 + (ignoreWidthTags ? (Utils.
                                WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER)/2 : minBackward.getWidth(start) / 2);
                    }
                    if (placement.startsWith("right_of")) {
                        offsetSoFar -= Utils.RENDERING_WIDTH_DIVIDER / 2 + (ignoreWidthTags ? (Utils.
                                WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER)/2 : minBackward.getWidth(start) / 2);
                    }
                }
                if (maxBackward._position+1 < lane) {
                    int numLanesAway = maxBackward._position + 1 - lane;
                    offsetSoFar = maxBackwardOffset + numLanesAway * Utils.WIDTH_LANES;

                    if (placement.startsWith("left_of")) {
                        offsetSoFar -= Utils.RENDERING_WIDTH_DIVIDER / 2 + (ignoreWidthTags ?
                                (Utils.WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER)/2 : maxBackward.getWidth(start) / 2);
                    }
                    if (placement.startsWith("right_of")) {
                        offsetSoFar += Utils.RENDERING_WIDTH_DIVIDER / 2 + (ignoreWidthTags ?
                                (Utils.WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER)/2 : maxBackward.getWidth(start) / 2);
                    }
                }
                valid = true;
            }

            if (!valid) return getWidth(start) / 2.0;

            return offsetSoFar;
        } catch (Exception e) {
            return getWidth(start) / 2.0;
        }
    }

    public String getPlacementTag(boolean start) {
        String output = null;
        if (start && _way.hasTag("placement:start") && Utils.isOneway(_way)) {
            output = _way.get("placement:start") + "f";
        } else if (!start && _way.hasTag("placement:end") && Utils.isOneway(_way)) {
            output = _way.get("placement:end") + "f";
        } else if (start && _way.hasTag("placement:forward:start")) {
            output = _way.get("placement:forward:start") + "f";
        } else if (!start && _way.hasTag("placement:forward:end")) {
            output = _way.get("placement:forward:end") + "f";
        } else if (start && _way.hasTag("placement:backward:start")) {
            output = _way.get("placement:backward:start") + "b";
        } else if (!start && _way.hasTag("placement:backward:end")) {
            output = _way.get("placement:backward:end") + "b";
        } else if (start && _way.hasTag("placement:both_ways:start")) {
            output = _way.get("placement:both_ways:start") + "m";
        } else if (!start && _way.hasTag("placement:both_ways:end")) {
            output = _way.get("placement:both_ways:end") + "m";
        } else if (_way.hasTag("placement") && Utils.isOneway(_way)) {
            output = _way.get("placement") + "f";
        } else if (_way.hasTag("placement:forward")) {
            output = _way.get("placement:forward") + "f";
        } else if (_way.hasTag("placement:backward")) {
            output = _way.get("placement:backward") + "b";
        } else if (_way.hasTag("placement:both_ways")) {
            output = _way.get("placement:both_ways") + "m";
        }
        return output;
    }


    private void getOffsets() {
        List<RoadPiece> roadPieces = getRoadPieces(false);
        double offsetSoFarStart = _offsetToLeftStart;
        double offsetSoFarEnd = _offsetToLeftEnd;
        for (int i = 0; i < roadPieces.size(); i++) {
            roadPieces.get(i).setOffset(offsetSoFarStart, offsetSoFarEnd);
            offsetSoFarStart -= roadPieces.get(i).getWidth(true)/2 +
                    (i != roadPieces.size()-1 ? roadPieces.get(i+1).getWidth(true)/2 : 0);
            offsetSoFarEnd -= roadPieces.get(i).getWidth(false)/2 +
                    (i != roadPieces.size()-1 ? roadPieces.get(i+1).getWidth(false)/2 : 0);
        }
    }

    @Override
    public double getWidth(boolean start) {
        double output = -1 * Utils.RENDERING_WIDTH_DIVIDER; // To offset the shoulder width added to each side.
        for (RoadPiece piece : getRoadPieces(false)) {
            output += piece.getWidth(start);
        }
        return output;
    }

    @Override
    public double sideWidth(boolean start, boolean left) {
        double otls = (_offsetToLeftStart+Utils.RENDERING_WIDTH_DIVIDER/2); // otls = offset to left start
        double otle = (_offsetToLeftEnd+Utils.RENDERING_WIDTH_DIVIDER/2); // otle = offset to left end
        return start ? (left ? otls : getWidth(true)-Utils.RENDERING_WIDTH_DIVIDER-otls)
                : (left ? otle : getWidth(false)-Utils.RENDERING_WIDTH_DIVIDER-otle);
    }

    public List<RoadPiece> getRoadPieces(boolean renderingOrder) {
        List<RoadPiece> output = new ArrayList<>();

        if (renderingOrder) {
            output.addAll(getLanesAndDividers(_forwardLanes, _forwardDividers, 1));
            output.addAll(getLanesAndDividers(_backwardLanes, _backwardDividers, -1));
            if (_backwardLanes.size() != 0 || (_bothWaysLane instanceof Lane)) {
                output.add(_bothWaysLane);
            }
            output.add(_leftRoadEdge);
            output.add(_rightRoadEdge);
        } else {
            output.add(_leftRoadEdge);
            if (Utils.isRightHand(_way)) {
                output.addAll(getLanesAndDividers(_backwardLanes, _backwardDividers, -1));
                if (_backwardLanes.size() != 0 || _bothWaysLane instanceof Lane) output.add(_bothWaysLane);
                output.addAll(getLanesAndDividers(_forwardLanes, _forwardDividers, 1));
            } else {
                output.addAll(getLanesAndDividers(_forwardLanes, _forwardDividers, 1));
                if (_backwardLanes.size() != 0 || _bothWaysLane instanceof Lane) output.add(_bothWaysLane);
                output.addAll(getLanesAndDividers(_backwardLanes, _backwardDividers, -1));
            }
            output.add(_rightRoadEdge);
        }
        return output;
    }

    private List<RoadPiece> getLanesAndDividers(List<RoadPiece> lanes, List<RoadPiece> dividers, int direction) {
        List<RoadPiece> output = new ArrayList<>();
        if (direction > 0) {
            for (int i = 0; i < lanes.size(); i++) {
                output.add(lanes.get(i));
                if (i < lanes.size() - 1) output.add(dividers.get(i));
            }
        } else {
            for (int i = lanes.size()-1; i >= 0; i--) {
                output.add(lanes.get(i));
                if (i != 0) output.add(dividers.get(i-1));
            }
        }
        return output;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for Processing Alignments and Angles (For clean connections between ways)">

    @Override
    public void updateAlignment() {
        // Recalculate alignment, this time using nearby ways for the angle.
        if (_isValid) {
            otherStartAngle = getOtherAngle(true);
            otherEndAngle = getOtherAngle(false);
            getPlacementInformation();
        } else {
            _alignment = getWay();
        }
    }

    @Override
    public Way getLeftEdge(Way alignmentPart, int segment) {
        double offsetStart = _leftRoadEdge._offsetStart + (_leftRoadEdge.getWidth(true) / 2.0);
        double offsetEnd = _leftRoadEdge._offsetEnd + (_leftRoadEdge.getWidth(false) / 2.0);
        return getEdgeFromOffset(alignmentPart, segment, offsetStart, offsetEnd);
    }

    @Override
    public Way getRightEdge(Way alignmentPart, int segment) {
        double offsetStart = _rightRoadEdge._offsetStart - (_rightRoadEdge.getWidth(true) / 2.0);
        double offsetEnd = _rightRoadEdge._offsetEnd - (_rightRoadEdge.getWidth(false) / 2.0);
        return getEdgeFromOffset(alignmentPart, segment, offsetStart, offsetEnd);
    }

    private Way getEdgeFromOffset(Way alignmentPart, int segment, double offsetStart, double offsetEnd) {
        double swt = startPoints.size() == 0 ? 0 : (Math.max(startPoints.get(segment), 0)/getAlignment().getLength());
        double startOffset = swt*offsetEnd + (1-swt)*offsetStart;
        double ewt = endPoints.size() == 0 ? getAlignment().getLength() + 100 : (Math.min(endPoints.get(segment), getAlignment().getLength())/getAlignment().getLength());
        double endOffset = ewt*offsetEnd + (1-ewt)*offsetStart;
        return Utils.getParallel(alignmentPart != null ? alignmentPart : _alignment, startOffset, endOffset, false,
                (startPoints.get(segment) < 0.1 || alignmentPart == null) ? otherStartAngle : Double.NaN,
                (endPoints.get(segment) > getAlignment().getLength()-0.1 || alignmentPart == null) ? otherEndAngle : Double.NaN);
    }

    public Pair<Integer, Integer> calculateLaneNumber(int directedLane, boolean isForward) {
        if (directedLane == 0) return new Pair<>(1, 0);
        int wayDir = isForward ? 1 : -1;
        Pair<Integer, Integer> output = new Pair<>(Math.abs(directedLane), wayDir * Integer.signum(directedLane));

        if (!Utils.isRightHand(_way)) {
            // OSM "left-to-right" lane numbering is outside-in on left hand drive roads.
            output.a = getLaneCount(output.b) - output.a + 1;
        }
        return output;
    }

    public int calculateDirectedLane(int laneNumber, boolean isForward) {
        int dir = isForward ? 1 : -1;
        if (!Utils.isRightHand(_way)) {
            // OSM "left-to-right" lane numbering is outside-in on left hand drive roads.
            laneNumber = getLaneCount(dir) - laneNumber + 1;
        }
        return laneNumber * dir;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for Converting Child Road Pieces into Tags"

    public void updateChangeTags() {
        // This method completely rebuilds the change tags based on the info stored in the child RoadPieces.
        List<Command> cmds = new LinkedList<>();

        // Set up change lists for each direction:
        String[] forward = new String[_forwardLanes.size()];
        String[] backward = new String[_backwardLanes.size()];
        for (RoadPiece p : getRoadPieces(false)) {
            if (p instanceof Lane) {
                if (p._direction == 1) {
                    forward[p._position] = ((Lane) p).getChange();
                }
                if (p._direction == -1) {
                    backward[p._position] = ((Lane) p).getChange();
                }
            }
        }

        // Convert forward to string.
        boolean forwardIsAllSame = true;
        StringBuilder forwardChange = new StringBuilder();
        for (int i = 0; i < forward.length; i++) {
            if (i != 0 && !forward[i-1].equals(forward[i])) forwardIsAllSame = false;
            if (i != 0) forwardChange.append("|");
            forwardChange.append(forward[i]);
        }

        // Convert backward to string.
        boolean backwardIsAllSame = true;
        StringBuilder backwardChange = new StringBuilder();
        for (int i = 0; i < backward.length; i++) {
            if (i != 0 && !backward[i-1].equals(backward[i])) backwardIsAllSame = false;
            if (i != 0) backwardChange.append("|");
            backwardChange.append(backward[i]);
        }

        if (forwardIsAllSame) forwardChange = new StringBuilder(forwardChange.toString().split("\\|")[0]);
        if (backwardIsAllSame) backwardChange = new StringBuilder(backwardChange.toString().split("\\|")[0]);

        removeTag(_way, "change", cmds);
        removeTag(_way, "change:forward", cmds);
        removeTag(_way, "change:backward", cmds);
        removeTag(_way, "change:lanes", cmds);
        removeTag(_way, "change:lanes:forward", cmds);
        removeTag(_way, "change:lanes:backward", cmds);

        boolean forwardEqualsBackward = forwardChange.toString().equals(backwardChange.toString());

        if (forwardIsAllSame && backwardIsAllSame && forwardEqualsBackward && !forwardChange.toString().equals("")) {
            addTag(_way, "change", forwardChange.toString(), cmds);
        }

        if (forwardIsAllSame && !(backwardIsAllSame && forwardEqualsBackward) && !forwardChange.toString().equals("")) {
            addTag(_way, "change" + (Utils.isOneway(_way) ? "" : ":forward"), forwardChange.toString(), cmds);

        }

        if (backwardIsAllSame && !(forwardIsAllSame && forwardEqualsBackward) && !forwardChange.toString().equals("")) {
            addTag(_way, "change:backward", backwardChange.toString(), cmds);
        }

        if (!forwardIsAllSame && !forwardChange.toString().equals("")) {
            addTag(_way, "change:lanes" + (Utils.isOneway(_way) ? "" : ":forward"), forwardChange.toString(), cmds);
        }

        if (!backwardIsAllSame && !backwardChange.toString().equals("")) {
            addTag(_way, "change:lanes:backward", backwardChange.toString(), cmds);
        }

        SequenceCommand c = new SequenceCommand("Change Divider Type", cmds);
        UndoRedoHandler.getInstance().add(c);
    }

    protected void removeTag(Way way, String key, java.util.List<Command> cmds) {
        addTag(way, key, null, cmds);
    }

    protected void addTag(Way way, String key, String value, List<Command> cmds) {
        Map<String, String> tags = way.getInterestingTags();
        if (value == null) {
            tags.remove(key);
        } else {
            tags.put(key, value);
        }
        Way way2 = new Way();
        way2.cloneFrom(way);
        way2.setKeys(tags);
        cmds.add(new ChangeCommand(way, way2));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for Handling Mouse Events">

    public boolean mouseEventIsInside(MouseEvent e) {
        for (Polygon p : getAsphaltOutlinePixels()) if (p.contains(e.getPoint())) return true;
        return false;
    }

    private RoadPiece getSubPieceInside(MouseEvent e) {
        for (RoadPiece r : getRoadPieces(false)) if (Utils.mouseEventIsInside(e, r.getAsphaltOutlines(), _mv)) return r;
        return null;
    }

    // </editor-fold>
}
