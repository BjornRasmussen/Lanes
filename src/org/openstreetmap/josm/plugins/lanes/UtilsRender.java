package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class UtilsRender {
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
                    double dist = Utils.distPointLine(path.getNode(i).lon(), path.getNode(i - 1).lon(), path.getNode(i + 1).lon(),
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

}
