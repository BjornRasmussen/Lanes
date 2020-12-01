package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class MarkedRoadRenderer implements RoadRenderer {

    // <editor-fold defaultstate="collapsed" desc="Variables">

    private final Way _way;
    private Way _alignment;
    private final MapView _mv;

    private Way _outline;
    private Way _asphalt;

    public static String selected = "";

    private double _offsetToLeftStart; // Distance between left side of road and the centre of the way.
    private double _offsetToLeftEnd;

    public final List<RoadPiece> _forwardLanes = new ArrayList<>();
    public final List<RoadPiece> _forwardDividers = new ArrayList<>();
    public final List<RoadPiece> _backwardLanes = new ArrayList<>();
    public final List<RoadPiece> _backwardDividers = new ArrayList<>();
    private RoadPiece _bothWaysLane;
    private RoadPiece _leftRoadEdge;
    private RoadPiece _rightRoadEdge;

    public double otherStartAngle = Double.NaN;
    public double otherEndAngle = Double.NaN;

    public LaneMappingMode _parent;
    protected boolean _isValid = true; // Display red error line if tags are wrong.

    // </editor-fold>

    public MarkedRoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        _way = w;
        _mv = mv;
        _parent = parent;
//        try {
            createRoadLayout();
//        } catch (Exception e) {
//            _isValid = false;
//        }
    }

    @Override
    public Way getWay() { return _way; }

    @Override
    public Way getAlignment() { return _alignment; }

    // <editor-fold defaultstate="collapsed" desc="Methods for rendering">

    @Override
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

        renderAsphalt(g);

        List<RoadPiece> roadPieces = getRoadPieces(true);
        for (RoadPiece roadPiece : roadPieces) {
            roadPiece.render(g);
        }
    }


    private void renderAsphalt(Graphics2D g) {
        g.setColor(Utils.DEFAULT_ASPHALT_COLOR);

        g.fillPolygon(getAsphaltOutlinePixels());

        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    private Polygon getAsphaltOutlinePixels() {
        if (_asphalt == null) _asphalt = getAsphaltOutlineCoords();

        int[] xPoints = new int[_asphalt.getNodesCount()];
        int[] yPoints = new int[xPoints.length];

        for (int i = 0; i < _asphalt.getNodesCount(); i++) {
            xPoints[i] = (int) (_mv.getPoint(_asphalt.getNode(i).getCoor()).getX() + 0.5);
            yPoints[i] = (int) (_mv.getPoint(_asphalt.getNode(i).getCoor()).getY() + 0.5);
        }

        return new Polygon(xPoints, yPoints, xPoints.length);
    }

    private Way getAsphaltOutlineCoords() {
        double widthStart = getWidth(true);
        double widthEnd = getWidth(false);

        Way left = Utils.getParallel(getAlignment(), _leftRoadEdge._offsetStart + (_leftRoadEdge.getWidth(true) / 2.0),
                _leftRoadEdge._offsetEnd + (_leftRoadEdge.getWidth(false) / 2.0), false, otherStartAngle, otherEndAngle);
        Way right = Utils.getParallel(getAlignment(), _rightRoadEdge._offsetStart - (_rightRoadEdge.getWidth(true) / 2.0),
                _rightRoadEdge._offsetEnd - (_rightRoadEdge.getWidth(false) / 2.0), false, otherStartAngle, otherEndAngle);

        List<Node> points = new ArrayList<>();

        for (int i = 0; i < left.getNodesCount(); i++) points.add(left.getNode(i));

        for (int i = 0; i < right.getNodesCount(); i++) points.add(right.getNode(right.getNodesCount()-i-1));

        points.add(left.getNode(0));

        Way output = new Way();
        output.setNodes(points);
        return output;
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

        if (_way.isOneway() == -1) {
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

        if (tags.containsKey("lanes") && !Utils.isOneway(_way)) {
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


    private void getPlacementInformation() {
        _offsetToLeftStart = getPlacementAt(true, false);
        _offsetToLeftEnd = getPlacementAt(false, false);
        double placementDiff = getPlacementAt(false, true) - getPlacementAt(true, true);
        _alignment = Utils.getParallel(_way, 0, placementDiff, true, otherStartAngle, otherEndAngle);
        _offsetToLeftEnd -= placementDiff;
    }

    private double getPlacementAt(boolean start, boolean ignoreWidthTags) {
        try {
            // Get string representation of placement.
            String placement = getPlacementTag(start);
            int direction = placement.charAt(placement.length()-1) == 'f' ? 1 : placement.charAt(placement.length()-1) == 'b' ? -1 : 0;
            placement = placement.substring(0, placement.length()-1);

            String placementOther = getPlacementTag(!start);
            int directionOther = -2;
            if (placementOther != null) {
                directionOther = placementOther.charAt(placementOther.length()-1) == 'f' ? 1 :
                        placementOther.charAt(placementOther.length()-1) == 'b' ? -1 : 0;
                placementOther = placementOther.substring(0, placementOther.length()-1);
            }



            // Get offset
            List<RoadPiece> pieces = getRoadPieces(false);
            int lane = Integer.parseInt(placement.split(":")[1]);
            int laneOther = Integer.MIN_VALUE;
            if (placementOther != null) Integer.parseInt(placementOther.split(":")[1]);
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
                boolean correctLaneOther = false;
                if (placementOther != null) correctLaneOther = p._position+1 == laneOther && p._direction == directionOther && (p._direction == 0 || p instanceof Lane);

                offsetSoFar += (ignoreWidthTags && !correctLane && !correctLaneOther) ? (pieces.get(i-1) instanceof Lane ? (Utils.
                        WIDTH_LANES-Utils.RENDERING_WIDTH_DIVIDER) :
                        Utils.RENDERING_WIDTH_DIVIDER)/2 : (pieces.get(i-1).getWidth(start) / 2);
                offsetSoFar += (ignoreWidthTags && !correctLane && !correctLaneOther) ? (p instanceof Lane ? (Utils.
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

    private String getPlacementTag(boolean start) {
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


    private double getWidth(boolean start) {
        double output = -1 * Utils.RENDERING_WIDTH_DIVIDER; // To offset the shoulder width added to each side.
        for (RoadPiece piece : getRoadPieces(false)) {
            output += piece.getWidth(start);
        }
        return output;
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
    } // TODO MAKE PRIVATE

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

    public void updateAlignment() {
        // Recalculate alignment, this time using nearby ways for the angle.
        otherStartAngle = getOtherAngle(true);
        otherEndAngle = getOtherAngle(false);
        getPlacementInformation();
    }

    public double getOtherAngle(boolean start) {
        if (start && Double.isNaN(otherStartAngle)) {
            otherStartAngle = calculateOtherAngle(true);
        }

        if (!start && Double.isNaN(otherEndAngle)) {
            otherEndAngle = calculateOtherAngle(false);
        }

        return start ? otherStartAngle : otherEndAngle;
    }

    private double calculateOtherAngle(boolean start) {
        Node pivot = _way.getNode(start ? 0 : _way.getNodesCount()-1);
        int numValidWays = 0;
        boolean somethingIsNotValid = false;
        Way otherWay = null;
        boolean otherWayStartsHere = false;
        for (Way w : pivot.getParentWays()) {
            if (w.getUniqueId() == _way.getUniqueId() || !_parent.wayIdToRSR.containsKey(w.getUniqueId())) continue;
            otherWay = _parent.wayIdToRSR.get(w.getUniqueId())._alignment;
            numValidWays++;
//            JOptionPane.showMessageDialog(_mv, "Way with id " + _way.getUniqueId() + " at node " + pivot.getUniqueId() + " has found other way " + w.getUniqueId() + " to be valid");
            // Check to ensure that pivot is only part of w at one of the endpoints.
            int numConnections = 0;
            for (int i = 0; i < w.getNodesCount(); i++) {
                if (w.getNode(i).getUniqueId() != pivot.getUniqueId()) continue;
                numConnections++;
                if (i!=0 && i!=w.getNodesCount()-1) {
//                    JOptionPane.showMessageDialog(_mv, "Way with id " + _way.getUniqueId() + " at node " + pivot.getUniqueId() + " has found other way " + w.getUniqueId() + " to be not valid node pos");
                    somethingIsNotValid = true;
                }
                otherWayStartsHere = i==0;
            }
            if (numConnections > 1) {
                somethingIsNotValid = true;
//                JOptionPane.showMessageDialog(_mv, "Way with id " + _way.getUniqueId() + " at node " + pivot.getUniqueId() + " has found other way " + w.getUniqueId() +
//                        " to be not valid " + numConnections + " connections");
            }
        }
        if (numValidWays != 1) {
            somethingIsNotValid = true;
//            JOptionPane.showMessageDialog(_mv, "Way with id " + _way.getUniqueId() + " at node " + pivot.getUniqueId() + " has found too many other ways (" + numValidWays + ")");

        }
        if (somethingIsNotValid) {
            return (getThisAngle(start) + Math.PI) % (2*Math.PI);
        } else {
            Node secondToLast = otherWayStartsHere ? otherWay.getNode(1) : otherWay.getNode(otherWay.getNodesCount() - 2);
            Node last = otherWayStartsHere ? otherWay.getNode(0) : otherWay.getNode(otherWay.getNodesCount() - 1);
            return last.getCoor().bearing(secondToLast.getCoor());
        }
    }

    public double getThisAngle(boolean start) {
        Node first = start ? _alignment.getNode(0) : _alignment.getNode(_alignment.getNodesCount() - 1);
        Node second = start ? _alignment.getNode(1) : _alignment.getNode(_alignment.getNodesCount() - 2);
        return first.getCoor().bearing(second.getCoor());
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
        String forwardChange = "";
        for (int i = 0; i < forward.length; i++) {
            if (i != 0 && !forward[i-1].equals(forward[i])) forwardIsAllSame = false;
            if (i != 0) forwardChange += "|";
            forwardChange += forward[i];
        }

        // Convert backward to string.
        boolean backwardIsAllSame = true;
        String backwardChange = "";
        for (int i = 0; i < backward.length; i++) {
            if (i != 0 && !backward[i-1].equals(backward[i])) backwardIsAllSame = false;
            if (i != 0) backwardChange += "|";
            backwardChange += backward[i];
        }

        if (forwardIsAllSame) forwardChange = forwardChange.split("\\|")[0];
        if (backwardIsAllSame) backwardChange = backwardChange.split("\\|")[0];

        removeTag(_way, "change", cmds);
        removeTag(_way, "change:forward", cmds);
        removeTag(_way, "change:backward", cmds);
        removeTag(_way, "change:lanes", cmds);
        removeTag(_way, "change:lanes:forward", cmds);
        removeTag(_way, "change:lanes:backward", cmds);

        if (forwardIsAllSame && backwardIsAllSame && forwardChange.equals(backwardChange) && !forwardChange.equals("")) {
            addTag(_way, "change", forwardChange, cmds);
        }

        if (forwardIsAllSame && !(backwardIsAllSame && forwardChange.equals(backwardChange)) && !forwardChange.equals("")) {
            addTag(_way, "change" + (Utils.isOneway(_way) ? "" : ":forward"), forwardChange, cmds);

        }

        if (backwardIsAllSame && !(forwardIsAllSame && forwardChange.equals(backwardChange)) && !forwardChange.equals("")) {
            addTag(_way, "change:backward", backwardChange, cmds);
        }

        if (!forwardIsAllSame && !forwardChange.equals("")) {
            addTag(_way, "change:lanes" + (Utils.isOneway(_way) ? "" : ":forward"), forwardChange, cmds);
        }

        if (!backwardIsAllSame && !backwardChange.equals("")) {
            addTag(_way, "change:lanes:backward", backwardChange, cmds);
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

    private void generateClickBoxes() {
        Way leftEdge = Utils.getParallel(_alignment, _offsetToLeftStart, _offsetToLeftEnd, false, otherStartAngle, otherEndAngle);
        Way rightEdge = Utils.getParallel(_alignment, _offsetToLeftStart - getWidth(true),
                _offsetToLeftEnd - getWidth(false), false, otherStartAngle, otherEndAngle);
        List<Node> outline = leftEdge.getNodes();
        for (int i = rightEdge.getNodesCount()-1; i >= 0; i--) {
            outline.add(rightEdge.getNode(i));
        }
        outline.add(leftEdge.getNode(0));
        _outline = new Way();
        _outline.setNodes(outline);
    }

    public boolean mouseEventIsInside(MouseEvent e) {
        int[] xPoints = new int[_outline.getNodesCount()];
        int[] yPoints = new int[xPoints.length];

        for (int i = 0; i < _outline.getNodesCount(); i++) {
            xPoints[i] = (int) (_mv.getPoint(_outline.getNode(i).getCoor()).getX() + 0.5);
            yPoints[i] = (int) (_mv.getPoint(_outline.getNode(i).getCoor()).getY() + 0.5);
        }

        return new Polygon(xPoints, yPoints, xPoints.length).contains(e.getPoint());
    }

    public void mouseClicked(MouseEvent e) {
//        RoadPiece r = getSubPieceInside(e);
//        if (r != null) r.mouseClicked(e);
    }

//    private RoadPiece getSubPieceInside(MouseEvent e) {
//        for (RoadPiece r : getRoadPieces(false)) if (r.mouseEventIsInside(e)) return r;
//        return null;
//    }

    // </editor-fold>
}
