package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.RightAndLefthandTraffic;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Utils {
    // <editor-fold defaultstate="collapsed" desc="Constants">
    public final static double WIDTH_LANES = 3.5; // Standard lane width.
    public final static double WIDTH_BIKE_LANES = 1.75; // Bike lane width.

    public final static double RENDERING_WIDTH_DIVIDER = 0.8;
    public final static double WIDTH_INVALID_METERS = 5.0;

    public final static double DIST_BETWEEN_TURNS = 24.0; // Distance in meters between turn arrows on rendered lanes.
    public final static double DIST_TO_FIRST_TURN = 13.5; // Distance in meters to first turn arrow on rendered lanes.

    public final static Color DEFAULT_ASPHALT_COLOR = new Color(40, 40, 50, 200);
    public final static Color DEFAULT_SELECTED_ASPHALT_COLOR = new Color(150, 40, 50, 200);
    public final static Color DEFAULT_DIVIDER_COLOR = Color.WHITE;
    public final static Color DEFAULT_CENTRE_DIVIDER_COLOR = Color.YELLOW;
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

    public enum LaneType {DRIVING, BICYCLE, BUS, HOV}
    public enum DividerType {DASHED, QUICK_DASHED, DASHED_FOR_RIGHT, DASHED_FOR_LEFT, SOLID, DOUBLE_SOLID,
        CENTRE_DIVIDER_WIDE, CENTRE_LANE, SOLID_DIVIDER_WIDE, BACKWARD_DIVIDER_WIDE, FORWARD_DIVIDER_WIDE}
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Methods for Finding Parallel Ways">

    public static Way getParallel(Way way, double offsetStart, double offsetEnd, boolean useAngleOffset) {
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
            // If points are at same location, return the way, since getting a parallel way would be hard.
            angle = points[0].bearing(points[1]);
        } catch (NullPointerException e) {
            return way;
        }
        output[0] = getLatLonRelative(points[0], angle-(Math.PI / 2.0) + angleOffset, offsetStart);

        // Deal with all other nodes
        for (int i = 1; i < points.length - 1; i++) {
            double angleToPrevPoint;
            double angleToNextPoint;
            try {
                // If points are at same location, return the way, since getting a parallel way would be hard.s
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
        double angleToPrev = points[points.length-1].bearing(points[points.length-2]);
        output[points.length-1] = getLatLonRelative(points[points.length-1], angleToPrev+(Math.PI / 2.0) + angleOffset, offsetEnd);
        // TODO handle loop roads.

        // Convert to way format and return
        List<Node> outputNodes = new ArrayList<>();
        for (LatLon ll : output) outputNodes.add(new Node(ll));

        Way outputWay = new Way();
        outputWay.setNodes(outputNodes);

        return outputWay;
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

    // </editor-fold>

    public static boolean isRightHand(Way location) {
        return RightAndLefthandTraffic.isRightHandTraffic(location.getNode(0).getCoor());
    }

    private static Image getImage(String location) {
        try {
            return ImageIO.read(new File(location));
        } catch (IOException e) {
            throw new RuntimeException("Couldn't find image " + location);
        }
    }

}
