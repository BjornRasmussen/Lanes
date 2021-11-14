package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UtilsSpatial {

    // <editor-fold defaultstate=collapsed desc="Methods for Intersection Generation">

    public static int numRoadsFromNode(Node n, LaneMappingMode m) { return numRoadsFromNode(n, m, false); }
    public static int numRoadsFromNode(Node n, LaneMappingMode m, boolean stopAtThree) {
        int total = 0;
        for (Way w : n.getParentWays()) {
            if (!m.wayIdToRSR.containsKey(w.getUniqueId())) continue;
            for (int i = 0; i < w.getNodesCount(); i++) {
                if (w.getNode(i).getUniqueId() == n.getUniqueId()) {
                    total += (i==0 || i == w.getNodesCount()-1) ? 1 : 2;
                }
                if (stopAtThree && total > 2) return 3;
            }
        }
        return total;
    }

    public static List<WayVector> getWaysFromNode(Node n, LaneMappingMode m) { return getWaysFromNode(n, m, Double.NaN); }
    public static List<WayVector> getWaysFromNode(Node n, LaneMappingMode m, double bearingStart) {
        List<WayVector> output = new ArrayList<>();

        for (Way w : n.getParentWays()) {
            if (!m.wayIdToRSR.containsKey(w.getUniqueId())) continue;
            if (w.getNodesCount() < 2) continue;

            for (int i = 0; i < w.getNodesCount(); i++) {
                // For each node, if it's the pivot, add a WayVector to output for both directions.
                if (w.getNode(i).getUniqueId() != n.getUniqueId()) continue;
                if (i != 0) output.add(new WayVector(i, i-1, w));
                if (i != w.getNodesCount()-1) output.add(new WayVector(i, i+1, w));
            }
        }

        for (int i = 0; i < output.size()-1; i++) {
            for (int j = 0; j < output.size()-1-i; j++) {
                if (output.get(j).bearing() > output.get(j+1).bearing()) {
                    WayVector temp = output.get(j);
                    output.set(j, output.get(j+1));
                    output.set(j+1, temp);
                }
            }
        }
        if (Double.isNaN(bearingStart)) return output;

        bearingStart = bearingStart % (2*Math.PI);
        for (int i = 0; i < output.size(); i++) {
            if (output.get(0).bearing() < bearingStart+0.0000001) {
                output.add(output.get(0));
                output.remove(0);
            } else {
                break;
            }
        }

        return output;
    }

    public static boolean nodeShouldBeIntersection(Node n, LaneMappingMode m) {
        boolean roadsCorrect = numRoadsFromNode(n, m, true) > 2;
        boolean anyRoundabouts = false;
        for (Way w : n.getParentWays()) if (m.wayIdToRSR.containsKey(w.getUniqueId()) && w.hasTag("junction", "roundabout")) anyRoundabouts = true;
        return roadsCorrect && !anyRoundabouts;
    }

    public static Way lowResOutline(Node n, LaneMappingMode m) {
        // Returns a really low quality outline of the intersection, only used to know if two intersections overlap.
        List<WayVector> wayVectors = getWaysFromNode(n, m);
        List<Double> oneSideDistances = new ArrayList<>();
        List<Node> outline = new ArrayList<>();

        for (int i = 0; i < wayVectors.size(); i++) {
//            try {
                // Get way at i right road edge (right going out from intersection, left going in)
                WayVector ith = wayVectors.get(i);
                if (ith == null) { throw new RuntimeException("ith is null"); }
                RoadRenderer ithrr = m.wayIdToRSR.get(ith.getParent().getUniqueId());
                if (ithrr == null) { throw new RuntimeException("ithrr is null"); }
                Way rightSubPart = getSubPart(ith.isForward() ? ithrr.getEdge(-1, true) : ithrr.getEdge(-1, false),
                        ith.isForward() ? Math.min(ith.getFrom(), ith.getTo()) : 0,
                        ith.isForward() ? ithrr.getWay().getNodesCount() - 1 : Math.max(ith.getFrom(), ith.getTo()));
                Way rightEdge = (ith.isForward() ? rightSubPart : reverseNodes(rightSubPart));

                // Get way at i+1 left road edge (left going out from intersection, right going in)
                WayVector ipoth = wayVectors.get((i == wayVectors.size() - 1) ? 0 : i + 1);
                if (ipoth == null) { throw new RuntimeException("ipoth is null"); }
                RoadRenderer ipothrr = m.wayIdToRSR.get(ipoth.getParent().getUniqueId());
                if (ipothrr == null) { throw new RuntimeException("ipothrr is null"); }
                Way leftSubPart = getSubPart(ipoth.isForward() ? ipothrr.getEdge(-1, false) : ipothrr.getEdge(-1, true),
                        ipoth.isForward() ? Math.min(ipoth.getFrom(), ipoth.getTo()) : 0,
                        ipoth.isForward() ? ipothrr.getWay().getNodesCount() - 1 : Math.max(ipoth.getFrom(), ipoth.getTo()));
                Way leftEdge = (ipoth.isForward() ? leftSubPart : reverseNodes(leftSubPart));

                // Get the intersect of the lines to get info about how far into each way that intersect is.
                double[] distances = new double[2];
                LatLon intersect = intersect(rightEdge, leftEdge, distances, true, 0, true, false);

                oneSideDistances.add(intersect == null ? 5 : Math.max(distances[0], 0));
                oneSideDistances.add(intersect == null ? 5 : Math.max(distances[1], 0));
//            } catch (Exception ignored) {}
        }

        // Generate outline using intersect data from above
        for (int i = 0; i < wayVectors.size(); i++) {
            WayVector ith = wayVectors.get(i);
            double distRight = oneSideDistances.get(2*i);
            double distLeft = oneSideDistances.get(i == 0 ? oneSideDistances.size()-1 : 2*i-1);
            double dist = ith.getParent().hasTag("in_a_junction", "yes") ? 20 : 5;
            double distToUse = Math.max(distLeft, distRight) + dist; // Dist out from intersection

            RoadRenderer rr = m.wayIdToRSR.get(ith.getParent().getUniqueId());
            Way wayGoingOutFromNode = getSubPart(ith.getParent(),
                    ith.isForward() ? ith.getFrom() : 0,
                    ith.isForward() ? ith.getParent().getNodesCount()-1 : ith.getFrom());
            if (!ith.isForward()) wayGoingOutFromNode = reverseNodes(wayGoingOutFromNode);
            wayGoingOutFromNode = extendWay(wayGoingOutFromNode, false, distToUse + 1 /* buffer in case dist is longer than road */);
            double offset = Math.max(rr.getWidth(false), rr.getWidth(true))/2;
            outline.add(new Node(getParallelPoint(wayGoingOutFromNode, distToUse, offset)));
            outline.add(new Node(getParallelPoint(wayGoingOutFromNode, distToUse, -offset)));
        }
        if (outline.size() != 0) outline.add(outline.get(0));
        Way output = new Way();
        output.setNodes(outline);
        return output;
    }

    public static LatLon bezier(double wayThrough, LatLon... inputs) {
        return bezier(wayThrough, Arrays.asList(inputs));
    }

    public static LatLon bezier(double p, List<LatLon> l) {
        // p goes from 0 to 1.  l is the list of points.
        if (l.size() == 1) return l.get(0);
        if (l.size() == 0) throw new RuntimeException("Zero length input cannot be used to make bezier curve.");
        LatLon s = bezier(p, l.subList(0, l.size()-1));
        LatLon e = bezier(p, l.subList(1, l.size()));
        return new LatLon(s.lat()*(1-p) + e.lat()*p, s.lon()*(1-p) + e.lon()*p);
    }

    /**
     * Finds the first intersect between two ways
     * @param A The first way
     * @param B The second Way
     * @return The first intersect between the two ways, or null if they don't intersect in the first 5 nodes of each.
     */
    public static LatLon intersect(Way A, Way B, double[] distances, boolean trim, double distToExtendTrimBy,
                                   boolean useMotorwayTrimDist, boolean checkAngle) {
        // Returns a latlon at the first intersection, or null if no intersection.
        // Only checks first 5 way segments into each, since beyond that would be terrible for performance.
        // Only checks first 25 meters into each, since beyond that causes weird problems.
        for (int i = 0; i < (trim ? 5 : Math.max(A.getNodesCount(), B.getNodesCount())); i++) {
            for (int a = 0; a <= i; a++) {
                for (int b = 0; b <= i; b++) {
                    if (a < i && b < i) continue; // Don't check way segments that have already been checked.
                    if (a > A.getNodesCount()-2 || b > B.getNodesCount()-2) continue; // Don't check where the nodes are out of bounds.
                    if (trim && !useMotorwayTrimDist && (nodeIdToDist(A, a) > 40+distToExtendTrimBy || nodeIdToDist(B, b) > 40+distToExtendTrimBy)) continue; // Don't go more than 40 meters looking for intersects.

                    // a is index of start node in way segment in A to check.
                    // b is for way segment in B.
                    // If the way segments from A and B intersect, return the point of intersection.
                    LatLon intersect = segmentIntersect(A.getNode(a).getCoor(), A.getNode(a+1).getCoor(),
                            B.getNode(b).getCoor(), B.getNode(b+1).getCoor());
                    double angA = A.getNode(a).getCoor().bearing(A.getNode(a+1).getCoor());
                    double angB = B.getNode(b).getCoor().bearing(B.getNode(b+1).getCoor());
                    if (intersect != null && (!checkAngle || (angA-angB)%(Math.PI*2) > Math.PI/2)) {
                        distances[0] = getSubPart(A, 0, a).getLength();
                        distances[1] = getSubPart(B, 0, b).getLength();
                        double Alen = (A.getNode(a).getCoor().greatCircleDistance(A.getNode(a+1).getCoor()));
                        double Blen = (B.getNode(b).getCoor().greatCircleDistance(B.getNode(b+1).getCoor()));

                        double dist0ext = ((intersect.lon()-A.getNode(a).getCoor().lon()) / (A.getNode(a+1).getCoor().lon()-A.getNode(a).getCoor().lon()))*Alen;
                        if (Double.isNaN(dist0ext)) dist0ext = ((intersect.lat()-A.getNode(a).getCoor().lat()) / (A.getNode(a+1).getCoor().lat()-A.getNode(a).getCoor().lat()))*Alen;
                        if (Double.isNaN(dist0ext)) dist0ext = 0;
                        distances[0] += dist0ext;

                        double dist1ext = ((intersect.lon()-B.getNode(b).getCoor().lon()) / (B.getNode(b+1).getCoor().lon()-B.getNode(b).getCoor().lon()))*Blen;
                        if (Double.isNaN(dist1ext)) dist1ext = ((intersect.lat()-B.getNode(b).getCoor().lat()) / (B.getNode(b+1).getCoor().lat()-B.getNode(b).getCoor().lat()))*Blen;
                        if (Double.isNaN(dist1ext)) dist1ext = 0;
                        distances[1] += dist1ext;
                        return intersect;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds point of intersection between two line segments.
     * @param A1 Start point of line segment A
     * @param A2 End point of line segment A
     * @param B1 Start point of line segment B
     * @param B2 End point of line segment B
     * @return The point if intersection if it exists, null if the line segments don't intersect.
     */
    private static LatLon segmentIntersect(LatLon A1, LatLon A2, LatLon B1, LatLon B2) {
        if (A1 == null || A2 == null || B1 == null | B2 == null) return null;

        double leftA = Math.min(A1.lon(), A2.lon());
        double rightA = Math.max(A1.lon(), A2.lon()) ;
        double topA =  Math.max(A1.lat(), A2.lat());
        double bottomA =  Math.min(A1.lat(), A2.lat());

        double leftB = Math.min(B1.lon(), B2.lon());
        double rightB = Math.max(B1.lon(), B2.lon());
        double topB =  Math.max(B1.lat(), B2.lat());
        double bottomB =  Math.min(B1.lat(), B2.lat());

        // Get the determinant of a certain matrix used to solve the problem.
        double det = (B2.lon() - B1.lon()) * (A1.lat() - A2.lat()) - (A1.lon() - A2.lon()) * (B2.lat() - B1.lat());

        // Check if lines are parallel.  If so, assume no intersect.
        if (Math.abs(det) < 0.0000000000001 /* please don't reduce num zeros */) return null;

        // Get how far into A the intersect is (0 is beginning, 1 is end, anything outside isn't an intersect).
        double ap = ((B1.lat() - B2.lat()) * (A1.lon() - B1.lon()) + (A1.lat() - B1.lat()) * (B2.lon() - B1.lon())) / det;

        // Use percentage through segment A to find the point.
        LatLon o = new LatLon(ap * A2.lat() + (1 - ap) * A1.lat(), ap * A2.lon() + (1 - ap) * A1.lon());

        // If the intersect is outside of the way segment, return null.
        if (o.lon() < leftA || o.lon() > rightA || o.lon() < leftB || o.lon() > rightB || o.lat() > topA || o.lat() > topB || o.lat() < bottomA || o.lat() < bottomB) {
            return null;
        }

        // Return the valid point.
        return o;
    }

    public static Way glue(Way a, Way b, double extension) {
        // Glue first half of a to second half of b.  Split into halves at intersect.
        double[] distances = new double[2];
        if (b == null || b.getNodesCount() < 2)return (a == null || a.getNodesCount() < 2) ? null : a;
        if (a == null || a.getNodesCount() < 2) return b;
        LatLon intersect = intersect(reverseNodes(a), b, distances, false, 0, false, false);
        double extensionUsed = 0;
        if (intersect == null) {
            try {
                intersect = intersect(extendWay(reverseNodes(a), true, extension),
                        extendWay(b, true, extension), distances, false, 0, false, false);
                extensionUsed = extension;
            } catch (Exception e) {
                return null;
            }
        }

        List<Node> nodes = new ArrayList<>();
        Way output = new Way();

        Way firstHalfA = getSubPart(a, 0.0, a.getLength() + extensionUsed - distances[0]);
        Way secondHalfB = getSubPart(b, distances[1]-extensionUsed, b.getLength());

        if (intersect == null || firstHalfA == null || secondHalfB == null) {
            nodes.addAll(a.getNodes());
            nodes.addAll(b.getNodes());
        } else {
            nodes.addAll(firstHalfA.getNodes());
            if (firstHalfA.getNodesCount() != 0 && secondHalfB.getNodesCount() != 0) nodes.remove(nodes.size()-1);
            nodes.addAll(secondHalfB.getNodes());
        }

        output.setNodes(nodes);
        return output;
    }

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

    public static boolean anglesAreWithinAngle(double a, double b, double maxDiff) {
        a = a % (2*Math.PI);
        b = b % (2*Math.PI);
        return (Math.abs(a-b) < maxDiff) || (Math.abs(a+2*Math.PI-b) < maxDiff) || (Math.abs(a-2*Math.PI-b) < maxDiff);
    }

    public static double angleBetween(double a, double b) {
        double ang1 = Math.abs(a-b);
        double ang2 = Math.abs(a+Math.PI-b);
        double ang3 = Math.abs(a-Math.PI-b);
        return Math.min(Math.min(ang1, ang2), ang3);
    }

    public static double distPointLine(double x0, double x1, double x2,
                                       double y0, double y1, double y2) {
        // 0 is point, 1 & 2 are endpoints of line
        double a = Math.abs((x2-x1)*(y1-y0) - (x1-x0)*(y2-y1));
        double b = Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1));
        return a/b;
    }

    public static double bearingAt(Way w, double metersIn) {
        double distSoFar = 0;
        for (int i = 1; i < w.getNodesCount(); i++) {
            distSoFar += w.getNode(i-1).getCoor().greatCircleDistance(w.getNode(i).getCoor());
            if (distSoFar >= metersIn || distSoFar + 0.01 > w.getLength()) return w.getNode(i-1).getCoor().bearing(w.getNode(i).getCoor());
        }
        return getWayBearing(w); // Backup, shouldn't ever run.
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
        if (startMeters >= w.getLength() || endMeters <= 0) return new Way();
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

        if (nextNode == -1) JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                "id: " + w.getUniqueId() + ", nodes: " + w.getNodesCount() + ", startMeters: " + startMeters + ", disSoFar:" + distSoFar);

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

    public static Node getPointAt(Way w, double metersIn) {
        double distSoFar = 0;
        if (metersIn <= 0.01) {
            return w.getNode(0);
        } else {
            for (int i = 1; i < w.getNodesCount(); i++) {
                double distThis = w.getNode(i - 1).getCoor().greatCircleDistance(w.getNode(i).getCoor());
                distSoFar += distThis;

                if (metersIn < distSoFar-0.01) {
                    // Return node between i-1 and i.
                    double distBack = distSoFar-metersIn;
                    return new Node(new LatLon(distBack/distThis*w.getNode(i-1).lat() + (1-distBack/distThis)*w.getNode(i).lat(),
                            distBack/distThis*w.getNode(i-1).lon() + (1-distBack/distThis)*w.getNode(i).lon()));
                } else if (metersIn < distSoFar+0.01) {
                    // return ith node.
                    return w.getNode(i);
                }
            }
        }
        return null;
    }

    public static LatLon getParallelPoint(Way w, double dist, double offsetToLeft) {
        if (dist < 0) dist = 0;
        if (dist > w.getLength()) dist = w.getLength();

        double distSoFar = 0;
        if (dist <= 0.01) {
            double bearing = w.getNode(0).getCoor().bearing(w.getNode(1).getCoor());
            return getLatLonRelative(w.getNode(0).getCoor(), bearing-Math.PI/2, offsetToLeft);
        } else {
            for (int i = 1; i < w.getNodesCount(); i++) {
                double distThis = w.getNode(i - 1).getCoor().greatCircleDistance(w.getNode(i).getCoor());
                distSoFar += distThis;
                double bearing = w.getNode(i-1).getCoor().bearing(w.getNode(i).getCoor());

                if (dist < distSoFar-0.01) {
                    // Return parallel from LatLon between i-1 and i.
                    double distBack = distSoFar-dist;

                    LatLon from = new LatLon(distBack/distThis*w.getNode(i-1).lat() + (1-distBack/distThis)*w.getNode(i).lat(),
                            distBack/distThis*w.getNode(i-1).lon() + (1-distBack/distThis)*w.getNode(i).lon());
                    return getLatLonRelative(from, bearing-Math.PI/2, offsetToLeft);
                } else if (dist < distSoFar+0.01) {
                    // return parallel from ith node.
                    return getLatLonRelative(w.getNode(i).getCoor(), bearing-Math.PI/2, offsetToLeft);
                }
            }
        }
        throw new RuntimeException("Shouldn't ever reach end.  Length of way: " + w.getLength() + ", distSoFar: " + distSoFar + ", dist of parallel: " + dist);
//        return null;
    }

    public static Way getSubPart(Way w, int startNode, int endNode) {
        if (startNode < 0) startNode = 0;
        if (endNode > w.getNodesCount()-1) endNode = w.getNodesCount()-1;
        List<Node> nodes = w.getNodes().subList(startNode, endNode+1);
        Way output = new Way();
        output.setNodes(nodes);
        return output;
    }

    public static Way reverseNodes(Way w) {
        List<Node> nodes = w.getNodes();
        List<Node> newNodes = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            newNodes.add(nodes.get(nodes.size()-1-i));
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

    public static Way extendWay(Way w, boolean start, double dist) {
        List<Node> nodes = w.getNodes();
        if (start) {
            double bearing = w.getNode(1).getCoor().bearing(w.getNode(0).getCoor());
            Node newNode = new Node(UtilsSpatial.getLatLonRelative(w.getNode(0).getCoor(), bearing, dist));
            nodes.remove(0);
            nodes.add(0, newNode);

        } else {
            double bearing = w.getNode(w.getNodesCount() - 2).getCoor().bearing(w.getNode(w.getNodesCount() - 1).getCoor());
            Node newNode = new Node(UtilsSpatial.getLatLonRelative(w.getNode(w.getNodesCount()-1).getCoor(), bearing, dist));
            nodes.remove(nodes.size()-1);
            nodes.add(newNode);
        }
        Way output = new Way();
        output.cloneFrom(w);
        output.setNodes(nodes);
        return output;
    }

    public static double nodeIdToDist(Way w, int id) {
        double distSoFar = 0.0;
        for (int i = 0; i < id; i++) {
            distSoFar += w.getNode(i).getCoor().greatCircleDistance(w.getNode(i+1).getCoor());
        }
        return distSoFar;
    }

    // </editor-fold>

}
