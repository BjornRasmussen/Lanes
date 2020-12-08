package org.openstreetmap.josm.plugins.lanes;

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

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
    public final static Color DEFAULT_UNTAGGED_ASPHALT_COLOR = new Color(255, 40, 40, 140);
    public final static Color DEFAULT_SELECTED_ASPHALT_COLOR = new Color(150, 40, 50, 170);
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

    public final static Map<String, String> isCenterYellow = getYML("resources/renderinginfo/isCenterYellow");
    public final static Map<String, String> shoulderLineColor = getYML("resources/renderinginfo/shoulderLineColor");
    public Map<String, String> isCenterTurnLaneKnown = getYML("resources/renderinginfo/isCenterTurnLaneKnown");

    public enum LaneType {DRIVING, BICYCLE, BUS, HOV}
    public enum DividerType {DASHED, QUICK_DASHED, DASHED_FOR_RIGHT, DASHED_FOR_LEFT, SOLID, DOUBLE_SOLID, CENTRE_DIVIDER_WIDE,
        CENTRE_LANE, SOLID_DIVIDER_WIDE, BACKWARD_DIVIDER_WIDE, FORWARD_DIVIDER_WIDE, UNMARKED_ROAD_EDGE, UNTAGGED_ROAD_EDGE}
    // </editor-fold>

    private static JFrame deleterFrame = null;

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
        } else if (type == DividerType.FORWARD_DIVIDER_WIDE) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.SOLID, color);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.SOLID, color);
            return;
        } else if (type == DividerType.BACKWARD_DIVIDER_WIDE) {
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

    public static Stroke getCustomStroke(double width, double metersDash, double metersGap, double offset) {
        if (metersGap <= 0.01 && metersGap >= -0.01) {
            return new BasicStroke((float) width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1);
        } else {
            return new BasicStroke((float) width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1,
                    new float[] {(float) metersDash, (float) metersGap}, (float) offset);
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate=collapsed desc="Tools">

    public static boolean isRightHand(Way location) {
        return RightAndLefthandTraffic.isRightHandTraffic(location.getNode(0).getCoor());
    }

    public static double parseWidth(String value) {
        try {
            if (value.endsWith(" lane")) {
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

    // <editor-fold defaultstate=collapsed desc="Methods for Mouse Handling">

    public static boolean mouseEventIsInside(MouseEvent e, List<Polygon> outlines, MapView mv) {
        for (Polygon outline : outlines) if (outline.contains(e.getPoint())) return true;
        return false;
    }

    public static void displayPopup(JPanel content, MouseEvent e, MapView mv, Way way) {
        if (content == null) return;

        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.add(content);

        JWindow w = new JWindow(MainApplication.getMainFrame());

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

        // </editor-fold>
    }

    private static void unClick(Window w, MapView mv) {
        w.setVisible(false);
        mv.repaint();
    }

    // </editor-fold>
}
