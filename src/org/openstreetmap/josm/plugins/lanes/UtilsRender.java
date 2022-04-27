package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.ImageProvider;

import java.awt.*;
import java.util.List;

public class UtilsRender {

    // <editor-fold defaultstate=collapsed desc="Constants">

    public final static double WIDTH_LANES = 3.5; // Standard lane width.
    public final static double WIDTH_BIKE_LANES = 1.75; // Bike lane width.

    public final static double RENDERING_WIDTH_DIVIDER = 0.6;
    public final static double WIDTH_INVALID_METERS = 5.0;

    public final static double DIST_BETWEEN_TURNS = 12.0; // Distance in meters between turn arrows on rendered lanes.
    public final static double DIST_TO_FIRST_TURN = 1.5; // Distance in meters to first turn arrow on rendered lanes.

    public final static Color DEFAULT_ASPHALT_COLOR = new Color(40, 40, 50, 170);
    public final static Color POPUP_ASPHALT_COLOR = new Color(35, 35, 45);
    public final static Color DEFAULT_UNTAGGED_ASPHALT_COLOR = new Color(255, 40, 40, 140);
    public final static Color POPUP_UNTAGGED_ASPHALT_COLOR = new Color(200, 30, 30);
    public final static Color DEFAULT_DIVIDER_COLOR = Color.WHITE;
    public final static Color DEFAULT_CENTRE_DIVIDER_COLOR = Color.YELLOW;
    public final static Color DEFAULT_UNTAGGED_ROADEDGE_COLOR = Color.WHITE;
    public final static Color DEFAULT_INVALID_COLOR = new Color(255, 40, 0);

    public final static Image uTurnLeft = ImageProvider.get("roadmarkings", "u_turn_left").getImage();
    public final static Image uTurnRight = ImageProvider.get("roadmarkings", "u_turn_right").getImage();
    public final static Image left = ImageProvider.get("roadmarkings", "left").getImage();
    public final static Image right = ImageProvider.get("roadmarkings", "right").getImage();
    public final static Image mergeLeft = ImageProvider.get("roadmarkings", "merge_left").getImage();
    public final static Image mergeRight = ImageProvider.get("roadmarkings", "merge_right").getImage();
    public final static Image slightLeft = ImageProvider.get("roadmarkings", "slight_left").getImage();
    public final static Image slightRight = ImageProvider.get("roadmarkings", "slight_right").getImage();
    public final static Image through = ImageProvider.get("roadmarkings", "through").getImage();

    public final static Image bike = ImageProvider.get("roadmarkings", "bike").getImage();
    public final static Image bus = ImageProvider.get("roadmarkings", "bus").getImage();
    public final static Image car = ImageProvider.get("roadmarkings", "car").getImage();
    public final static Image hov = ImageProvider.get("roadmarkings", "hov").getImage();
    public final static Image hov2 = ImageProvider.get("roadmarkings", "hov2").getImage();
    public final static Image hov3 = ImageProvider.get("roadmarkings", "hov3").getImage();
    public final static Image hov4 = ImageProvider.get("roadmarkings", "hov4").getImage();
    public final static Image hov5 = ImageProvider.get("roadmarkings", "hov5").getImage();
    public final static Image hov6 = ImageProvider.get("roadmarkings", "hov6").getImage();
    public final static Image moped = ImageProvider.get("roadmarkings", "moped").getImage();
    public final static Image motorcycle = ImageProvider.get("roadmarkings", "motorcycle").getImage();
    public final static Image pedestrian = ImageProvider.get("roadmarkings", "ped").getImage();
    public final static Image taxi = ImageProvider.get("roadmarkings", "taxi").getImage();
    public final static Image tram = ImageProvider.get("roadmarkings", "tram").getImage();
    public final static Image truck = ImageProvider.get("roadmarkings", "truck").getImage();

    public final static Image lr_uTurnLeft = ImageProvider.get("lowresroadmarkings", "u_turn_left").getImage();
    public final static Image lr_uTurnRight = ImageProvider.get("lowresroadmarkings", "u_turn_right").getImage();
    public final static Image lr_left = ImageProvider.get("lowresroadmarkings", "left").getImage();
    public final static Image lr_right = ImageProvider.get("lowresroadmarkings", "right").getImage();
    public final static Image lr_mergeLeft = ImageProvider.get("lowresroadmarkings", "merge_left").getImage();
    public final static Image lr_mergeRight = ImageProvider.get("lowresroadmarkings", "merge_right").getImage();
    public final static Image lr_slightLeft = ImageProvider.get("lowresroadmarkings", "slight_left").getImage();
    public final static Image lr_slightRight = ImageProvider.get("lowresroadmarkings", "slight_right").getImage();
    public final static Image lr_through = ImageProvider.get("lowresroadmarkings", "through").getImage();

    public final static Image lr_bike = ImageProvider.get("lowresroadmarkings", "bike").getImage();
    public final static Image lr_bus = ImageProvider.get("lowresroadmarkings", "bus").getImage();
    public final static Image lr_car = ImageProvider.get("lowresroadmarkings", "car").getImage();
    public final static Image lr_hov = ImageProvider.get("lowresroadmarkings", "hov").getImage();
    public final static Image lr_hov2 = ImageProvider.get("lowresroadmarkings", "hov2").getImage();
    public final static Image lr_hov3 = ImageProvider.get("lowresroadmarkings", "hov3").getImage();
    public final static Image lr_hov4 = ImageProvider.get("lowresroadmarkings", "hov4").getImage();
    public final static Image lr_hov5 = ImageProvider.get("lowresroadmarkings", "hov5").getImage();
    public final static Image lr_hov6 = ImageProvider.get("lowresroadmarkings", "hov6").getImage();
    public final static Image lr_moped = ImageProvider.get("lowresroadmarkings", "moped").getImage();
    public final static Image lr_motorcycle = ImageProvider.get("lowresroadmarkings", "motorcycle").getImage();
    public final static Image lr_pedestrian = ImageProvider.get("lowresroadmarkings", "ped").getImage();
    public final static Image lr_taxi = ImageProvider.get("lowresroadmarkings", "taxi").getImage();
    public final static Image lr_tram = ImageProvider.get("lowresroadmarkings", "tram").getImage();
    public final static Image lr_truck = ImageProvider.get("lowresroadmarkings", "truck").getImage();

    public final static Image questionMark = ImageProvider.get("roadmarkings", "question_mark").getImage();
    public final static Image lr_questionMark = ImageProvider.get("lowresroadmarkings", "question_mark").getImage();
    public final static Image exclamationPoint = ImageProvider.get("roadmarkings", "exclamation_point").getImage();
    public final static Image lr_exclamationPoint = ImageProvider.get("lowresroadmarkings", "exclamation_point").getImage();



    // </editor-fold>

    public static void drawOnMap(Graphics2D g, MapView mv, Way path, Color color,
                float[] dash, float width, boolean isArea, boolean roundEnd, boolean simplify) {
        // Coordinate based rendering, not pixel based.

        // Simplify path
        if (simplify) {
            final double minOffsetAllowed = mv.getDist100Pixel() / 200 / 40000000 * 360;
            while (path.getNodesCount() > 2) {
                int minId = -1;
                double minOffset = Double.POSITIVE_INFINITY;
                for (int i = 1; i < path.getNodesCount() - 1; i++) {
                    double dist = UtilsSpatial.distPointLine(path.getNode(i).lon(), path.getNode(i - 1).lon(), path.getNode(i + 1).lon(),
                            path.getNode(i).lat(), path.getNode(i - 1).lat(), path.getNode(i + 1).lat());
                    if (dist < minOffset) {
                        minOffset = dist;
                        minId = i;
                    }
                }
                if (minOffset < minOffsetAllowed) {
                    List<Node> nodes = path.getNodes();
                    nodes.remove(minId);
                    path.setNodes(nodes);
                } else {
                    break;
                }
            }
        }

        // Generate path
        int[] xPoints = new int[path.getNodesCount()];
        int[] yPoints = new int[path.getNodesCount()];
        for (int i = 0; i < path.getNodesCount(); i++) {
            xPoints[i] = (int) (mv.getPoint(path.getNode(i).getCoor()).getX() + 0.5);
            yPoints[i] = (int) (mv.getPoint(path.getNode(i).getCoor()).getY() + 0.5);
        }

        // Generate style:
        double pixelsPerMeter = 100.0 / mv.getDist100Pixel();
        g.setStroke(getCustomStroke(pixelsPerMeter*width + 1,
                (isArea || dash == null) ? new float[] {1} : dash, 0, roundEnd));
        g.setColor(color);

        // Draw line/area
        if (isArea) {
            g.fillPolygon(xPoints, yPoints, xPoints.length);
        } else {
            g.drawPolyline(xPoints, yPoints, xPoints.length);
        }

        // THESE TWO LINES ARE FOR REMOVING THE WHITE BOX AROUND THE SCREEN... DON'T DELETE THESE
        g.setColor(new Color(0, 0, 0, 0));
        g.setStroke(GuiHelper.getCustomizedStroke("0"));
    }

    public static Stroke getCustomStroke(double width, float[] dash, float offset, boolean roundEnd) {
        int cap = roundEnd ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT;
        int join = BasicStroke.JOIN_ROUND;
        if (dash.length == 1) {
            return new BasicStroke((float) width, cap, join, 1);
        } else {
            return new BasicStroke((float) width, cap, join, 1,
                    dash, offset);
        }
    }

    public static void renderRoadLine(Graphics2D g, MapView mv, RoadRenderer parent,
                                      double widthStart, double widthEnd, double offsetStart, double offsetEnd,
                                      DividerType type, Color color, boolean roundEnds) {
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
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + stripeWidth, offsetEnd + stripeWidth, DividerType.SOLID, color, false);
            renderRoadLine(g, mv, parent, widthStart, widthEnd,offsetStart - stripeWidth, offsetEnd - stripeWidth, DividerType.SOLID, color, false);
            return;
        } else if (type == DividerType.DASHED_FOR_RIGHT) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + stripeWidth, offsetEnd + stripeWidth, DividerType.SOLID, color, false);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - stripeWidth, offsetEnd - stripeWidth, DividerType.DASHED, color, false);
            return;
        } else if (type == DividerType.DASHED_FOR_LEFT) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - stripeWidth, offsetEnd - stripeWidth, DividerType.SOLID, color, false);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + stripeWidth, offsetEnd + stripeWidth, DividerType.DASHED, color, false);
            return;
        } else if (type == DividerType.CENTRE_DIVIDER_WIDE) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.DOUBLE_SOLID, color, false);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.DOUBLE_SOLID, color, false);
            return;
        } else if (type == DividerType.FORWARD_DIVIDER_WIDE || type == DividerType.BACKWARD_DIVIDER_WIDE) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart + ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.SOLID, color, false);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.SOLID, color, false);
            return;
        } else if (type == DividerType.CENTRE_LANE) {
            renderRoadLine(g, mv, parent, widthStart, widthEnd,offsetStart + ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd + ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.DASHED_FOR_RIGHT, color, false);
            renderRoadLine(g, mv, parent, widthStart, widthEnd, offsetStart - ((widthStart-RENDERING_WIDTH_DIVIDER) / 2),
                    offsetEnd - ((widthEnd-RENDERING_WIDTH_DIVIDER) / 2), DividerType.DASHED_FOR_LEFT, color, false);
            return;
        }
        List<Way> parentAlignments = parent.getAlignments();
        g.setColor(color);

        for (int i = 0; i < parentAlignments.size(); i++) {
            // runs for each section of road shared by the way.  A way gets split into two sections if it has an intersection in the middle.
            double swt = (Math.max(parent.segmentStartPoints.get(i), 0)/parent.getAlignment().getLength());
            double startOffset = swt*offsetEnd + (1-swt)*offsetStart;
            double ewt = (Math.min(parent.segmentEndPoints.get(i), parent.getAlignment().getLength())/parent.getAlignment().getLength());
            double endOffset = ewt*offsetEnd + (1-ewt)*offsetStart;
            Way alignment = UtilsSpatial.getParallel(parentAlignments.get(i), startOffset, endOffset, false,
                    parent.segmentStartPoints.get(i) < 0.1 ? parent.otherStartAngle : Double.NaN,
                    parent.segmentEndPoints.get(i) > parent.getAlignment().getLength() - 0.1 ? parent.otherEndAngle : Double.NaN);
//            UtilsRender.drawOnMap(g, mv, alignment, color, new float[] {1}, 0.125F, false, true, false);
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
            renderRoadLinePopup(g, goInDirection(start, bearing-Math.PI/2, stripeWidth), goInDirection(end, bearing-Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            renderRoadLinePopup(g, goInDirection(start, bearing+Math.PI/2, stripeWidth), goInDirection(end, bearing+Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            return;
        } else if (type == DividerType.DASHED_FOR_RIGHT) {
            renderRoadLinePopup(g, goInDirection(start, bearing-Math.PI/2, stripeWidth), goInDirection(end, bearing-Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            renderRoadLinePopup(g, goInDirection(start, bearing+Math.PI/2, stripeWidth), goInDirection(end, bearing+Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DASHED, color);
            return;
        } else if (type == DividerType.DASHED_FOR_LEFT) {
            renderRoadLinePopup(g, goInDirection(start, bearing-Math.PI/2, stripeWidth), goInDirection(end, bearing-Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DASHED, color);
            renderRoadLinePopup(g, goInDirection(start, bearing+Math.PI/2, stripeWidth), goInDirection(end, bearing+Math.PI/2, stripeWidth),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            return;
        } else if (type == DividerType.CENTRE_DIVIDER_WIDE) {
            renderRoadLinePopup(g, goInDirection(start, bearing-Math.PI/2, widthStartHalf),
                    goInDirection(end, bearing-Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DOUBLE_SOLID, color);
            renderRoadLinePopup(g, goInDirection(start, bearing+Math.PI/2, widthStartHalf),
                    goInDirection(end, bearing+Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DOUBLE_SOLID, color);
            return;
        } else if (type == DividerType.FORWARD_DIVIDER_WIDE || type == DividerType.BACKWARD_DIVIDER_WIDE) {
            renderRoadLinePopup(g, goInDirection(start, bearing-Math.PI/2, widthStartHalf),
                    goInDirection(end, bearing-Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            renderRoadLinePopup(g, goInDirection(start, bearing+Math.PI/2, widthStartHalf),
                    goInDirection(end, bearing+Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.SOLID, color);
            return;
        } else if (type == DividerType.CENTRE_LANE) {
            renderRoadLinePopup(g, goInDirection(start, bearing-Math.PI/2, widthStartHalf),
                    goInDirection(end, bearing-Math.PI/2, widthEndHalf),
                    bearing, widthStart, widthEnd, pixelsPerMeter, DividerType.DASHED_FOR_RIGHT, color);
            renderRoadLinePopup(g, goInDirection(start, bearing+Math.PI/2, widthStartHalf),
                    goInDirection(end, bearing+Math.PI/2, widthEndHalf),
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

}
