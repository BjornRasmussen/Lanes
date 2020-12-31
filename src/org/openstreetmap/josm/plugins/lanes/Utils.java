package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.RightAndLefthandTraffic;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;

public class Utils {

    // <editor-fold defaultstate="collapsed" desc="Constants">
    public final static double WIDTH_LANES = 3.5; // Standard lane width.
    public final static double WIDTH_BIKE_LANES = 1.75; // Bike lane width.

    public final static double RENDERING_WIDTH_DIVIDER = 0.6;
    public final static double WIDTH_INVALID_METERS = 5.0;

    public final static double DIST_BETWEEN_TURNS = 24.0; // Distance in meters between turn arrows on rendered lanes.
    public final static double DIST_TO_FIRST_TURN = 13.5; // Distance in meters to first turn arrow on rendered lanes.

    public final static Color DEFAULT_ASPHALT_COLOR = new Color(40, 40, 50, 170);
    public final static Color POPUP_ASPHALT_COLOR = new Color(35, 35, 45);
    public final static Color DEFAULT_UNTAGGED_ASPHALT_COLOR = new Color(255, 40, 40, 140);
    public final static Color POPUP_UNTAGGED_ASPHALT_COLOR = new Color(200, 30, 30);
    public final static Color DEFAULT_DIVIDER_COLOR = Color.WHITE;
    public final static Color DEFAULT_CENTRE_DIVIDER_COLOR = Color.YELLOW;
    public final static Color DEFAULT_UNTAGGED_ROADEDGE_COLOR = Color.WHITE;
    public final static Color DEFAULT_INVALID_COLOR = new Color(255, 40, 0);

    public final static Image uTurnLeft = ImageProvider.get("roadmarkings", "u_turn_left.png").getImage();
    public final static Image uTurnRight = ImageProvider.get("roadmarkings", "u_turn_right.png").getImage();
    public final static Image left = ImageProvider.get("roadmarkings", "left.png").getImage();
    public final static Image right = ImageProvider.get("roadmarkings", "right.png").getImage();
    public final static Image mergeLeft = ImageProvider.get("roadmarkings", "merge_left.png").getImage();
    public final static Image mergeRight = ImageProvider.get("roadmarkings", "merge_right.png").getImage();
    public final static Image slightLeft = ImageProvider.get("roadmarkings", "slight_left.png").getImage();
    public final static Image slightRight = ImageProvider.get("roadmarkings", "slight_right.png").getImage();
    public final static Image through = ImageProvider.get("roadmarkings", "through.png").getImage();

    public final static Image lr_uTurnLeft = ImageProvider.get("lowresroadmarkings", "u_turn_left.png").getImage();
    public final static Image lr_uTurnRight = ImageProvider.get("lowresroadmarkings", "u_turn_right.png").getImage();
    public final static Image lr_left = ImageProvider.get("lowresroadmarkings", "left.png").getImage();
    public final static Image lr_right = ImageProvider.get("lowresroadmarkings", "right.png").getImage();
    public final static Image lr_mergeLeft = ImageProvider.get("lowresroadmarkings", "merge_left.png").getImage();
    public final static Image lr_mergeRight = ImageProvider.get("lowresroadmarkings", "merge_right.png").getImage();
    public final static Image lr_slightLeft = ImageProvider.get("lowresroadmarkings", "slight_left.png").getImage();
    public final static Image lr_slightRight = ImageProvider.get("lowresroadmarkings", "slight_right.png").getImage();
    public final static Image lr_through = ImageProvider.get("lowresroadmarkings", "through.png").getImage();

    public final static Image questionMark = ImageProvider.get("roadmarkings", "question_mark.png").getImage();
    public final static Image lr_questionMark = ImageProvider.get("lowresroadmarkings", "question_mark.png").getImage();
    public final static Image exclamationPoint = ImageProvider.get("roadmarkings", "exclamation_point.png").getImage();
    public final static Image lr_exclamationPoint = ImageProvider.get("lowresroadmarkings", "exclamation_point.png").getImage();

    public final static Image preset_u1 = ImageProvider.get("75pxpresets", "u1.png").getImage();
    public final static Image preset_u15 = ImageProvider.get("75pxpresets", "u1.5.png").getImage();
    public final static Image preset_u2 = ImageProvider.get("75pxpresets", "u2.png").getImage();

    public final static Image preset_mo1 = ImageProvider.get("75pxpresets", "mo1.png").getImage();
    public final static Image preset_mo2 = ImageProvider.get("75pxpresets", "mo2.png").getImage();
    public final static Image preset_mo3 = ImageProvider.get("75pxpresets", "mo3.png").getImage();
    public final static Image preset_mo4 = ImageProvider.get("75pxpresets", "mo4.png").getImage();

    public final static Image preset_mt101w = ImageProvider.get("75pxpresets", "mt101w.png").getImage();
    public final static Image preset_mt101y = ImageProvider.get("75pxpresets", "mt101y.png").getImage();
    public final static Image preset_mt202w = ImageProvider.get("75pxpresets", "mt202w.png").getImage();
    public final static Image preset_mt202y = ImageProvider.get("75pxpresets", "mt202y.png").getImage();
    public final static Image preset_mt303w = ImageProvider.get("75pxpresets", "mt303w.png").getImage();
    public final static Image preset_mt303y = ImageProvider.get("75pxpresets", "mt303y.png").getImage();

    public final static Image preset_mt111y = ImageProvider.get("75pxpresets", "mt111y.png").getImage();
    public final static Image preset_mt212y = ImageProvider.get("75pxpresets", "mt212y.png").getImage();

    public final static String[] onewaypresets = new String[] {"mo1", "mo2", "mo3", "mo4"};
    public final static String[] twowaylefthandpresets = new String[] {"mt101w", "u2", "mt202w", "u1.5", "mt303w", "u1"};
    public final static String[] twowayrighthandpresets = new String[] {"mt101y", "u2", "mt202y", "u1.5", "mt303y", "u1"};
    public final static String[] twowayrighthandpresetsUSCA = new String[] {"mt101y", "u2", "mt202y", "u1.5", "mt303y", "u1", "mt111y", "mt212y"};

    public final static Map<String, String> isCenterYellow = getYML("resources/renderinginfo/isCenterYellow");
    public final static Map<String, String> shoulderLineColor = getYML("resources/renderinginfo/shoulderLineColor");
    public Map<String, String> isCenterTurnLaneKnown = getYML("resources/renderinginfo/isCenterTurnLaneKnown");

    public enum LaneType {DRIVING, BICYCLE, BUS, HOV}
    public enum DividerType {DASHED, QUICK_DASHED, DASHED_FOR_RIGHT, DASHED_FOR_LEFT, SOLID, DOUBLE_SOLID, CENTRE_DIVIDER_WIDE,
        CENTRE_LANE, SOLID_DIVIDER_WIDE, BACKWARD_DIVIDER_WIDE, FORWARD_DIVIDER_WIDE, UNMARKED_ROAD_EDGE, UNTAGGED_ROAD_EDGE}
    // </editor-fold>


    // <editor-fold defaultstate="collapsed" desc="Methods for Finding Parallel Ways">

    public static Way getParallel(Way way, double offsetStart, double offsetEnd, boolean useAngleOffset, double angStart, double angEnd) {
        LatLon[] points = new LatLon[way.getNodesCount()];
        double[] distanceIntoWay = new double[way.getNodesCount()];
        double distanceOfWay = 0;
        for (int i = 0; i < points.length; i++) {
            points[i] = way.getNode(i).getCoor();
            if (i != 0) {
                try {
                    distanceOfWay += points[i - 1].greatCircleDistance(points[i]);
                } catch (Exception ignored) {}
            }
            distanceIntoWay[i] = distanceOfWay;
        }

        // Get angle offset:
        double angleOffset = (useAngleOffset ? -1 : 0) * Math.asin((offsetEnd-offsetStart)/distanceOfWay);

        LatLon[] output = new LatLon[points.length];

        // Deal with first node
        double angle;
        if (points.length < 2) return null;
        try {
            angle = points[0].bearing(points[1]);
        } catch (NullPointerException e) {
            return way;
        }

        double angleWithoutOtherWay = (angle - (Math.PI / 2.0)) % (2*Math.PI);
        double angleToUse = angleWithoutOtherWay;
        double multiplierToUse = 1.0;

        if (!Double.isNaN(angStart)) {
            double angleOfOtherWay = ((angStart + (Math.PI / 2)) % (Math.PI * 2));
            if (anglesAreWithinAngle(angleOfOtherWay, angleWithoutOtherWay, 1.8)) {
                angleToUse = getAngleAverage(angleWithoutOtherWay, angleOfOtherWay);

                multiplierToUse = 1 / Math.cos(Math.abs(angleToUse - angleWithoutOtherWay));
            }
        }

        output[0] = getLatLonRelative(points[0], angleToUse, offsetStart*multiplierToUse);

        // Deal with all other nodes
        for (int i = 1; i < points.length - 1; i++) {
            double angleToPrevPoint;
            double angleToNextPoint;
            try {
                // If points are at same location, return the way, since getting a parallel way would be hard.
                angleToPrevPoint = points[i].bearing(points[i - 1]);
                angleToNextPoint = points[i].bearing(points[i + 1]);
            } catch (NullPointerException e) {
                return way;
            }
            double angleBetween = (angleToNextPoint + angleToPrevPoint) / 2;
            if (angleToNextPoint < angleToPrevPoint) angleBetween = (angleBetween + Math.PI) % (Math.PI * 2.0);

            double amountThrough = distanceIntoWay[i]/distanceOfWay;
            double offsetAtNode = offsetStart * (1 - amountThrough) + offsetEnd * amountThrough;

            double anglePrevToNormal = (angleBetween - angleToNextPoint) % (2 * Math.PI);

            double offset = offsetAtNode / Math.abs(Math.sin(anglePrevToNormal));

            output[i] = getLatLonRelative(points[i], angleBetween + angleOffset, offset);
        }

        // Deal with last node
        double angleToPrev = points[points.length - 1].bearing(points[points.length - 2]);

        angleWithoutOtherWay = (angleToPrev + (Math.PI / 2.0)) % (2*Math.PI);
        angleToUse = angleWithoutOtherWay + angleOffset;
        multiplierToUse = 1.0;

        if (!Double.isNaN(angEnd)) {
            double angleOfOtherWay = ((angEnd - (Math.PI / 2)) % (Math.PI * 2));
            if (anglesAreWithinAngle(angleOfOtherWay, angleWithoutOtherWay + angleOffset, 1.8)) {
                angleToUse = getAngleAverage(angleWithoutOtherWay + angleOffset, angleOfOtherWay);
                multiplierToUse = 1 / Math.cos(Math.abs(angleToUse - angleWithoutOtherWay - angleOffset));
            }
        }

        output[points.length - 1] = getLatLonRelative(points[points.length - 1], angleToUse, offsetEnd*multiplierToUse);



        // Convert to way format and return
        List<Node> outputNodes = new ArrayList<>();
        for (LatLon ll : output) outputNodes.add(new Node(ll));

        Way outputWay = new Way();
        outputWay.setNodes(outputNodes);

        return outputWay;
    }

    private static double getAngleAverage(double a, double b) {
        a = a % (2*Math.PI);
        b = b % (2*Math.PI);
        double angleBetween = (a + b) / 2;
        if (Math.abs(a-b) > Math.PI) angleBetween = (angleBetween + Math.PI) % (Math.PI * 2.0);
        return angleBetween;
    }

    private static boolean anglesAreWithinAngle(double a, double b, double maxDiff) {
        a = a % (2*Math.PI);
        b = b % (2*Math.PI);
        return (Math.abs(a-b) < maxDiff) || (Math.abs(a+2*Math.PI-b) < maxDiff) || (Math.abs(a-2*Math.PI-b) < maxDiff);
    }

    public static LatLon getLatLonRelative(LatLon from, double bearing, double numMeters) {
        double metersPerDegreeLat = 111319.5;
        double metersPerDegreeLon = metersPerDegreeLat * Math.cos(from.getY() / 180.0 * Math.PI);
        double dx = Math.sin(bearing) * numMeters / metersPerDegreeLon;
        double dy = Math.cos(bearing) * numMeters / metersPerDegreeLat;
        return new LatLon(from.lat() + dy, from.lon() + dx);
    }

    public static double metersToProjectedUnits(double meters, Way location) {
        return meters / Math.cos(location.getNode(0).getCoor().getY() / 180.0 * Math.PI);
    }

    public static Way getSubPart(Way w, double startMeters, double endMeters) {
        List<Node> newNodes = new ArrayList<>();
        double distSoFar = 0;
        int nextNode = -1;
        if (startMeters <= 0.01) {
            newNodes.add(w.getNode(0));
            nextNode = 1;
        } else {
            for (int i = 1; i < w.getNodesCount(); i++) {
                // Look for start up to the node at pos i, including node at pos i.
                double distThis = w.getNode(i - 1).getCoor().greatCircleDistance(w.getNode(i).getCoor());
                distSoFar += distThis;

                if (startMeters < distSoFar-0.01) {
                    // Find place and set next node as i
                    double distBack = distSoFar-startMeters;
                    newNodes.add(new Node(new LatLon(distBack/distThis*w.getNode(i-1).lat() + (1-distBack/distThis)*w.getNode(i).lat(),
                            distBack/distThis*w.getNode(i-1).lon() + (1-distBack/distThis)*w.getNode(i).lon())));
                    nextNode = i;
                    distSoFar -= distThis;
                    break;
                } else if (startMeters < distSoFar+0.01) {
                    // add node i and set next as i+1.
                    newNodes.add(w.getNode(i));
                    nextNode = i+1;
                    break;
                }
            }
        }

        for (int i = nextNode; i < w.getNodesCount(); i++) {
            // Look for start up to the node at pos i, including node at pos i.
            double distThis = w.getNode(i - 1).getCoor().greatCircleDistance(w.getNode(i).getCoor());
            distSoFar += distThis;
            if (endMeters < distSoFar-0.01) {
                // Find place and set next node as i
                double distBack = distSoFar-endMeters;
                newNodes.add(new Node(new LatLon(distBack/distThis*w.getNode(i-1).lat() + (1-distBack/distThis)*w.getNode(i).lat(),
                        distBack/distThis*w.getNode(i-1).lon() + (1-distBack/distThis)*w.getNode(i).lon())));
                break;
            } else if (endMeters < distSoFar+0.01) {
                // add node i and set next as i+1.
                newNodes.add(w.getNode(i));
                break;
            }

            newNodes.add(w.getNode(i));
        }
        if (newNodes.size() >= 2) {
            Way output = new Way();
            output.setNodes(newNodes);
            return output;
        } else {
            return null;
        }
    }

    public static Way getSubPart(Way w, int startNode, int endNode) {
        List<Node> nodes = w.getNodes().subList(startNode, endNode+1);

        Way output = new Way();
        output.setNodes(nodes);
        return output;
    }

    public static Way reverseNodes(Way w) {
        List<Node> nodes = w.getNodes();
        List<Node> newNodes = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            newNodes.set(nodes.size()-1-i, nodes.get(i));
        }
        Way output = new Way();
        output.setNodes(newNodes);
        return output;
    }

    public static double getWayBearing(Way w) {
        double wayLen = w.getLength();
        Node prev = null;
        double len = 0;
        for (Node n : w.getNodes()) {
            if (prev == null) {
                prev = n;
                continue;
            }
            len += prev.getCoor().greatCircleDistance(n.getCoor());
            if (len > (wayLen / 2)) return prev.getCoor().bearing(n.getCoor());
            prev = n;
        }
        throw new RuntimeException("Error in getWayBearing method, entire way traversed but summed length never exceeded half of total length.");
    }

    // </editor-fold>

    // <editor-fold defaultstate=collapsed desc="Methods for Rendering">

    public static void renderRoadLine(Graphics2D g, MapView mv, RoadRenderer parent,
                                      double widthStart, double widthEnd, double offsetStart, double offsetEnd, DividerType type, Color color) {
        double pixelsPerMeter = 100.0 / mv.getDist100Pixel();
        double stripeWidth = 1.4/8;

        if (type == DividerType.DASHED) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 3, pixelsPerMeter * 9, 0));
        } else if (type == DividerType.QUICK_DASHED) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 1, pixelsPerMeter * 3, pixelsPerMeter*3));
        } else if (type == DividerType.SOLID) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 3, 0, 0));
        } else if (type == DividerType.UNTAGGED_ROAD_EDGE) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 1, 0, 0));
        } else if (type == DividerType.UNMARKED_ROAD_EDGE) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 3, 0, 0));
        } else if (type == DividerType.DOUBLE_SOLID) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + stripeWidth, offsetEnd + stripeWidth, DividerType.SOLID, color);
            renderRoadLine(g, mv, parent, widthStart, widthEnd,offsetStart - stripeWidth, offsetEnd - stripeWidth, DividerType.SOLID, color);
            return;
        } else if (type == DividerType.DASHED_FOR_RIGHT) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + stripeWidth, offsetEnd + stripeWidth, DividerType.SOLID, color);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - stripeWidth, offsetEnd - stripeWidth, DividerType.DASHED, color);
            return;
        } else if (type == DividerType.DASHED_FOR_LEFT) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - stripeWidth, offsetEnd - stripeWidth, DividerType.SOLID, color);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + stripeWidth, offsetEnd + stripeWidth, DividerType.DASHED, color);
            return;
        } else if (type == DividerType.CENTRE_DIVIDER_WIDE) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.DOUBLE_SOLID, color);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.DOUBLE_SOLID, color);
            return;
        } else if (type == DividerType.FORWARD_DIVIDER_WIDE || type == DividerType.BACKWARD_DIVIDER_WIDE) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.SOLID, color);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.SOLID, color);
            return;
        } else if (type == DividerType.CENTRE_LANE) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd,offsetStart + ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.DASHED_FOR_RIGHT, color);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.DASHED_FOR_LEFT, color);
            return;
        }
        List<Way> parentAlignments = parent.getAlignments();
        g.setColor(color);

        for (int i = 0; i < parentAlignments.size(); i++) {
            double swt = (Math.max(parent.startPoints.get(i), 0)/parent.getAlignment().getLength());
            double startOffset = swt*offsetEnd + (1-swt)*offsetStart;
            double ewt = (Math.min(parent.endPoints.get(i), parent.getAlignment().getLength())/parent.getAlignment().getLength());
            double endOffset = ewt*offsetEnd + (1-ewt)*offsetStart;
            Way alignment = getParallel(parentAlignments.get(i), startOffset, endOffset, false,
                    i==0 ? parent.otherStartAngle : Double.NaN, i==parentAlignments.size()-1 ? parent.otherEndAngle : Double.NaN);

            int[] xPoints = new int[alignment.getNodesCount()];
            int[] yPoints = new int[alignment.getNodesCount()];

            for (int j = 0; j < alignment.getNodesCount(); j++) {
                xPoints[j] = (int) (mv.getPoint(alignment.getNode(j).getCoor()).getX() + 0.5);
                yPoints[j] = (int) (mv.getPoint(alignment.getNode(j).getCoor()).getY() + 0.5);
            }

            g.drawPolyline(xPoints, yPoints, xPoints.length);
        }

        // THESE TWO LINES ARE FOR REMOVING THE WHITE BOX AROUND THE SCREEN... DON'T DELETE THESE
        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    public static void renderRoadLinePopup(Graphics2D g, Point start, Point end, double bearing,
                                           double widthStart, double widthEnd, double pixelsPerMeter, DividerType type, Color color) {
        double stripeWidth = 1.4/8*pixelsPerMeter;
        double widthStartHalf = ((widthStart-RENDERING_WIDTH_DIVIDER) / 2)*pixelsPerMeter;
        double widthEndHalf = ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2)*pixelsPerMeter;


        if (type == DividerType.DASHED) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 3, pixelsPerMeter * 9, 0));
        } else if (type == DividerType.QUICK_DASHED) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 1, pixelsPerMeter * 3, pixelsPerMeter*3));
        } else if (type == DividerType.SOLID) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 3, 0, 0));
        } else if (type == DividerType.UNTAGGED_ROAD_EDGE) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 1, 0, 0));
        } else if (type == DividerType.UNMARKED_ROAD_EDGE) {
            g.setStroke(getCustomStroke(pixelsPerMeter / 8 + 1, pixelsPerMeter * 3, 0, 0));
        } else if (type == DividerType.DOUBLE_SOLID) {
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing-Math.PI/2, stripeWidth), Utils.goInDirection(end, bearing-Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing+Math.PI/2, stripeWidth), Utils.goInDirection(end, bearing+Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            return;
        } else if (type == DividerType.DASHED_FOR_RIGHT) {
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing-Math.PI/2, stripeWidth), Utils.goInDirection(end, bearing-Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing+Math.PI/2, stripeWidth), Utils.goInDirection(end, bearing+Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DASHED, color);
            return;
        } else if (type == DividerType.DASHED_FOR_LEFT) {
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing-Math.PI/2, stripeWidth), Utils.goInDirection(end, bearing-Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DASHED, color);
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing+Math.PI/2, stripeWidth), Utils.goInDirection(end, bearing+Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            return;
        } else if (type == DividerType.CENTRE_DIVIDER_WIDE) {
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing-Math.PI/2, widthStartHalf),
                    Utils.goInDirection(end, bearing-Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DOUBLE_SOLID, color);
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing+Math.PI/2, widthStartHalf),
                    Utils.goInDirection(end, bearing+Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DOUBLE_SOLID, color);
            return;
        } else if (type == DividerType.FORWARD_DIVIDER_WIDE || type == DividerType.BACKWARD_DIVIDER_WIDE) {
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing-Math.PI/2, widthStartHalf),
                    Utils.goInDirection(end, bearing-Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing+Math.PI/2, widthStartHalf),
                    Utils.goInDirection(end, bearing+Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            return;
        } else if (type == DividerType.CENTRE_LANE) {
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing-Math.PI/2, widthStartHalf),
                    Utils.goInDirection(end, bearing-Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DASHED_FOR_RIGHT, color);
            renderRoadLinePopup(g, Utils.goInDirection(start, bearing+Math.PI/2, widthStartHalf),
                    Utils.goInDirection(end, bearing+Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DASHED_FOR_LEFT, color);
            return;
        }

        g.setColor(color);
        g.drawLine(start.x, start.y, end.x, end.y);

        // THESE TWO LINES ARE FOR REMOVING THE WHITE BOX AROUND THE SCREEN... DON'T DELETE THESE
        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    public static Stroke getCustomStroke(double width, double metersDash, double metersGap, double offset) {
        if (metersGap <= 0.01 && metersGap >= -0.01) {
            return new BasicStroke((float) width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1);
        } else {
            return new BasicStroke((float) width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1,
                    new float[] {(float) metersDash, (float) metersGap}, (float) offset);
        }
    }

    public static Polygon wayToPolygon(Way w, MapView mv) {
        int[] xPoints = new int[w.getNodesCount()];
        int[] yPoints = new int[xPoints.length];

        for (int i = 0; i < w.getNodesCount(); i++) {
            xPoints[i] = (int) (mv.getPoint(w.getNode(i).getCoor()).getX() + 0.5);
            yPoints[i] = (int) (mv.getPoint(w.getNode(i).getCoor()).getY() + 0.5);
        }

        return new Polygon(xPoints, yPoints, xPoints.length);
    }

    public static Point goInDirection(Point from, double bearing, double dist) {
        return new Point((int) (from.x + Math.sin(bearing)*dist + 0.5), (int) (from.y - Math.cos(bearing)*dist + 0.5));
    }

    // </editor-fold>

    // <editor-fold defaultstate=collapsed desc="Tools">

    public static boolean isRightHand(Way location) {
        return RightAndLefthandTraffic.isRightHandTraffic(location.getNode(0).getCoor());
    }

    public static double parseWidth(String value) {
        try {
            if (value == null || value.equals("")) {
                return 0;
            } else if (value.endsWith(" lane")) {
                return Utils.WIDTH_LANES * Double.parseDouble(value.substring(0, value.length() - 5));
            } else if (value.endsWith(" lanes")) {
                return Utils.WIDTH_LANES * Double.parseDouble(value.substring(0, value.length() - 6));
            } else if (value.endsWith(" m")) {
                return Double.parseDouble(value.substring(0, value.length() - 2));
            } else if (value.endsWith(" km")) {
                return 1000 * Double.parseDouble(value.substring(0, value.length() - 3));
            } else if (value.endsWith(" mi")) {
                return 1609.344 * Double.parseDouble(value.substring(0, value.length() - 3));
            } else if (value.endsWith("'")) {
                return 1 / 3.28084 * Double.parseDouble(value.substring(0, value.length() - 1));
            } else if (value.endsWith("\"") && !value.contains("'")) {
                return 1 / 3.28084 / 12 * Double.parseDouble(value.substring(0, value.length() - 1));
            } else if (value.endsWith("\"") && value.contains("'")) {
                String[] split = value.split("'");
                double feet = Double.parseDouble(split[0]);
                double inches = Double.parseDouble(split[1].substring(0, value.length() - 1));
                return 1 / 3.28084 * (feet + inches / 12);
            }
            return Double.parseDouble(value);
        } catch (Exception e) { return Double.NaN; }
    }

    private static Map<String, String> getYML(String path) {
        // This is a pretty basic parser that only supports very simple YML.
        try {
            Map<String, String> output = new HashMap<>();
            File f = new File(path);
            Scanner s = new Scanner(f);
            while (s.hasNext()) {
                String next = s.next();
                if (next.charAt(0) == '#') continue;
                output.put(next.split(":")[0].trim().toUpperCase(), next.split(":")[1].split("#")[0].trim());
            }
            return output;
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    public static boolean isOneway(Way w) {
        return w.isOneway() == 1 || w.hasTag("junction", "roundabout");
    }

    // </editor-fold>

    // <editor-fold defaultstate=collapsed desc="Methods for Intersections">

    public static boolean nodeShouldBeIntersection(Node n, LaneMappingMode m) {
        int total = 0;
        for (Way w : n.getParentWays()) {
            if (!m.wayIdToRSR.containsKey(w.getUniqueId())) continue;
            for (int i = 0; i < w.getNodesCount(); i++) {
                if (w.getNode(i).getUniqueId() == n.getUniqueId()) {
                    total += (i==0 || i == w.getNodesCount()-1) ? 1 : 2;
                }
                if (total > 2) return true;
            }
        }
        return false;
    }

    public static LatLon bezier(double wayThrough, LatLon... inputs) {
        return bezier(wayThrough, Arrays.asList(inputs));
    }

    private static LatLon bezier(double p, List<LatLon> l) {
        // p goes from 0 to 1.  l is the list of points.
        if (l.size() == 1) return l.get(0);
        LatLon s = bezier(p, l.subList(0, l.size()-1));
        LatLon e = bezier(p, l.subList(1, l.size()));
        return new LatLon(s.lat()*(1-p) + e.lat()*p, s.lon()*(1-p) + e.lon()*p);
    }

    public static LatLon intersect(Way A, Way B) {
        // Returns a latlon at the first intersection, or null if no intersection.
        // Only checks first 5 way segments into each, since beyond that would be terrible for performance.
        for (int i = 0; i < 5; i++) {
            for (int a = 0; a <= i; a++) {
                for (int b = 0; b <= i; b++) {
                    if (a < i && b < i) continue; // Don't check way segments that have already been checked.
                    // a is index of start node in way segment in A to check.
                    // b is for way segment in B.
                    // If the way segments from A and B intersect, return the point of intersection.
                    LatLon intersect = segmentIntersect(A.getNode(a).getCoor(), A.getNode(a+1).getCoor(),
                            B.getNode(b).getCoor(), B.getNode(b+1).getCoor());
                    if (intersect != null) return intersect;
                }
            }
        }
        return null;
    }

    private static LatLon segmentIntersect(LatLon A1, LatLon A2, LatLon B1, LatLon B2) {
        // Get "determinant" of a certain matrix used to solve the problem.
        double det = (B2.lon()-B1.lon())*(A1.lat()-A2.lat()) - (A1.lon()-A2.lon())*(B2.lat()-B1.lat());

        // Check if lines are roughly parallel.  If so, return null.
        if (Math.abs(det) < 0.0001) return null;

        // Get how far into A the intersect is (0 is beginning, 1 is end, anything outside isn't an intersect).
        double ap = ((B1.lat()-B2.lat())*(A1.lon()-B1.lon()) + (A1.lat()-B1.lat())*(B2.lon()-B1.lon())) / det;

        // If the intersect is outside of the way segment, return null.
        if (ap < -0.0001 || ap > 1.0001) return null;

        // Use percentage through segment A to find the point.
        return new LatLon(ap*A2.lat() + (1-ap)*A1.lat(), ap*A2.lon() + (1-ap)*A1.lon());
    }

    // </editor-fold>

    // <editor-fold defaultstate=collapsed desc="Methods for Mouse Handling">

    public static boolean mouseEventIsInside(MouseEvent e, List<Polygon> outlines, MapView mv) {
        for (Polygon outline : outlines) if (outline.contains(e.getPoint())) return true;
        return false;
    }

    public static void displayPopup(JPanel content, MouseEvent e, MapView mv, Way way, LaneMappingMode l) {
        if (content == null) return;

        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
        mainPanel.add(content);

        JWindow w = new JWindow(MainApplication.getMainFrame());
        w.setAutoRequestFocus(false);
        w.setFocusableWindowState(false);
        w.add(mainPanel);
        w.pack();

        Point aboveMouse = new Point(e.getXOnScreen() - (mainPanel.getWidth() / 2), e.getYOnScreen() - mainPanel.getHeight() - 10);
        Point belowMouse = new Point(e.getXOnScreen() - (mainPanel.getWidth() / 2), e.getYOnScreen() + 10);
        boolean goAbove = e.getY() - 30 > mainPanel.getHeight();
        w.setLocation(goAbove ? aboveMouse : belowMouse);
        w.setVisible(true);

        // <editor-fold defaultstate=collapsed desc="Things that close the Window">

        // * Map moved / zoom changed
        mv.addRepaintListener(new MapView.RepaintListener() {
            double scale = mv.getScale();
            EastNorth center = mv.getCenter();

            @Override
            public void repaint(long tm, int x, int y, int width, int height) {
                // This runs when something has changed.  Check if scale or map position have changed.
                if (Math.abs(mv.getScale() - scale) > 0.001 || Math.abs(mv.getCenter().getX() - center.getX()) > 0.001 ||
                        Math.abs(mv.getCenter().getY() - center.getY()) > 0.001) {
                    scale = mv.getScale();
                    center = mv.getCenter();
                    unClick(w, mv);
                }
            }
        });

        // * Mouse pressed down somewhere on the map outside of the window (just map clicks - editing the tags of selected way won't close it)
        mv.addMouseListener(new MouseListener() {
            @Override
            public void mousePressed(MouseEvent e) {
                unClick(w, mv);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
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
        });

        // * Way corresponding to Window no longer exists.
        UndoRedoHandler.getInstance().addCommandQueuePreciseListener(new UndoRedoHandler.CommandQueuePreciseListener() {
            @Override
            public void commandAdded(UndoRedoHandler.CommandAddedEvent e) {
                verifyExistence();
            }

            @Override
            public void cleaned(UndoRedoHandler.CommandQueueCleanedEvent e) {
                verifyExistence();
            }

            @Override
            public void commandUndone(UndoRedoHandler.CommandUndoneEvent e) {
                verifyExistence();
            }

            @Override
            public void commandRedone(UndoRedoHandler.CommandRedoneEvent e) {
                verifyExistence();
            }

            private void verifyExistence() {
                if (way.isDeleted()) unClick(w, mv);
            }
        });

        // * Map Mode Changes
        l.addQuitListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                unClick(w, mv);
            }
        });

        // </editor-fold>
    }

    private static void unClick(Window w, MapView mv) {
        w.setVisible(false);
        mv.repaint();
    }

    // </editor-fold>

    // <editor-fold defaultstate=collapsed desc="Methods for Presets">

    public static List<Preset> getPresets(boolean rightHand, boolean centerKnown, boolean oneway) {
        String[] presetStrings;
        if (!oneway && centerKnown) {
            presetStrings = twowayrighthandpresetsUSCA;
        } else if (!oneway && rightHand) {
            presetStrings = twowayrighthandpresets;
        } else if (!oneway){
            presetStrings = twowaylefthandpresets;
        } else {
            presetStrings = onewaypresets;
        }
        List<Preset> output = new ArrayList<>();
        for (String s : presetStrings) {
            Preset add = stringToPreset(s);
            if (add != null) output.add(add);
        }
        return output;
    }

    private static List<String> parsePresets(String path) {
        try {
        File f = getFile(path);
        Scanner s = new Scanner(f);
            List<String> output = new ArrayList<>();
            String yay = "";
            while(s.hasNext()) {
                String next = s.nextLine();
                yay += next + "\n";
                if (next.charAt(0) == '#') continue;
                String add = next.split("#")[0].trim();
                if (add.length() > 0) output.add(add);
            }
            if (true) {
                JOptionPane.showMessageDialog(MainApplication.getMainFrame(), yay);
            }
            return output;
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(), "File " + path + " not found.");
            return new ArrayList<>();
        }
    }

    private static File getFile(String path) throws FileNotFoundException {
        ClassLoader classLoader = Preset.class.getClassLoader();
        URL resource = classLoader.getResource(path);

        if (resource == null) {
            throw new FileNotFoundException("file was not found!");
        } else {
            return new File(resource.getFile());
        }
    }

    private static Preset stringToPreset(String s) {
        if (s.equals("mo1")) {
            return new Preset(1, 0, 0, preset_mo1);
        } else if (s.equals("mo2")) {
            return new Preset(2, 0, 0, preset_mo2);
        } else if (s.equals("mo3")) {
            return new Preset(3, 0, 0, preset_mo3);
        } else if (s.equals("mo4")) {
            return new Preset(4, 0, 0, preset_mo4);
        } else if (s.equals("mt101w")) {
            return new Preset(1, 1, 0, preset_mt101w);
        } else if (s.equals("mt101y")) {
            return new Preset(1, 1, 0, preset_mt101y);
        } else if (s.equals("mt202w")) {
            return new Preset(2, 2, 0, preset_mt202w);
        } else if (s.equals("mt202y")) {
            return new Preset(2, 2, 0, preset_mt202y);
        } else if (s.equals("mt303w")) {
            return new Preset(3, 3, 0, preset_mt303w);
        } else if (s.equals("mt303y")) {
            return new Preset(3, 3, 0, preset_mt303y);
        } else if (s.equals("u1")) {
            return new Preset(0, 0, 1, preset_u1);
        } else if (s.equals("u1.5")) {
            return new Preset(0, 0, 1.5, preset_u15);
        } else if (s.equals("u2")) {
            return new Preset(0, 0, 2, preset_u2);
        } else if (s.equals("mt111y")) {
            return new Preset(1, 1, 1, preset_mt111y);
        } else if (s.equals("mt212y")) {
            return new Preset(2, 2, 1, preset_mt212y);
        } else {
            return null;
        }
    }

    public static void applyPreset(Preset p, Way w, boolean undoPrevFirst) {
        if (undoPrevFirst) UndoRedoHandler.getInstance().undo();

        // w is existing way
        Collection<Command> cmds = new LinkedList<>();
        Map<String, String> keys = getPresetTagsApplied(w.getKeys(), p, w);

        cmds.add(new ChangePropertyCommand(Collections.singletonList(w), keys));

        Command c = new SequenceCommand("Apply road layout preset", cmds);
        UndoRedoHandler.getInstance().add(c);
    }

    private static Map<String, String> getPresetTagsApplied(Map<String, String> keys, Preset p, Way w) {
        Map<String, String> output = new HashMap<>();
        if (p.getLanesBackward() == 0 && p.getLanesForward() == 0) {
            for (String key : keys.keySet()) {
                // Wipe all lane tags, since the road is now unmarked.
                if (key.contains(":lanes") && !key.contains("note")) {
                    output.put(key, "");
                }

                if (key.equals("lanes") || key.equals("lanes:forward") || key.equals("lanes:backward")) {
                    output.put(key, "");
                }
            }
            String lanesBothWays = p.getLanesBothWays() + "";
            if (lanesBothWays.endsWith(".0")) lanesBothWays = lanesBothWays.substring(0, lanesBothWays.length()-2);
            if (lanesBothWays.endsWith(".")) lanesBothWays = lanesBothWays.substring(0, lanesBothWays.length()-1);

            // Use either width or lanes depending on value.
            if (!lanesBothWays.contains(".")) {
                output.put("width", "");
                output.put("lanes", lanesBothWays);
            } else {
                output.put("width", (""+WIDTH_LANES*1.5).substring(0, Math.min(4, (""+WIDTH_LANES*1.5).length())));
            }
            output.put("lane_markings", "no");
        } else {
            output.put("lane_markings", "yes");
            output.put("lanes", "");
            output.put("width", "");
            output.put(Utils.isOneway(w) ? "lanes" : "lanes:forward", "" + (p.getLanesForward() != 0 ? p.getLanesForward() : ""));
            output.put("lanes:backward", "" + (p.getLanesBackward() != 0 ? p.getLanesBackward() : ""));
            output.put("lanes:both_ways", "" + (((int) p.getLanesBothWays()) != 0 ? ((int) p.getLanesBothWays()) : ""));

            for (String key : keys.keySet()) {
                if (key.contains(":lanes") && !key.contains("note")) {
                    int dir = 1;
                    if (key.contains(":both_ways")) dir = 0;
                    if (key.contains(":backward")) dir = -1;

                    int newLanes = dir == 1 ? p.getLanesForward() : dir == 0 ? (int) p.getLanesBothWays() : p.getLanesBackward();
                    int numBars = numBars(key);
                    if (numBars < newLanes) {
                        int diff = numBars - newLanes;
                        output.put(key, keys.get(key));
                        for (int i = 0; i < diff; i++) output.put(key, output.get(key) + "|");
                    } else if (numBars > newLanes) {
                        String[] pieces = keys.get(key).split("\\|");
                        StringBuilder newStr = new StringBuilder();
                        for (int i = 0; i < newLanes; i++) {
                            if (i != 0) newStr.append("|");
                            newStr.append(pieces[i]);
                        }
                        output.put(key, newStr.toString());
                    }
                }
            }
        }
        return output;
    }

    private static int numBars(String s) {
        int out = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '|') out++;
        return out;
    }

    // </editor-fold>
}
