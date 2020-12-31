package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.gui.MapView;

import java.awt.*;

public class RoadEdge extends RoadPiece {

    protected RoadEdge(int direction, int position, MapView mv, MarkedRoadRenderer parent) {
        super(direction, position, mv, parent);
    }

    @Override
    double getWidth(boolean start) {
        return Utils.RENDERING_WIDTH_DIVIDER;
    }

    @Override
    void render(Graphics2D g) {
        String country = "US";
        String centerColor = Utils.isCenterYellow.containsKey(country) ?
                Utils.isCenterYellow.get(country) : Utils.isCenterYellow.get("default");
        String roadEdge = Utils.shoulderLineColor.containsKey(country) ?
                Utils.shoulderLineColor.get(country) : Utils.shoulderLineColor.get("default");

        Utils.renderRoadLine(g, _mv, _parent, 0, 0, _offsetStart, _offsetEnd, Utils.DividerType.SOLID,
                (Utils.isOneway(_way) && _direction == -1) ? Color.YELLOW : Color.WHITE);
    }

    @Override
    void renderPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter) {
        double offsetStart = _offsetStart-(_parent._offsetToLeftStart - (_parent.getWidth(true))/2);
        double offsetEnd = _offsetEnd-(_parent._offsetToLeftEnd - (_parent.getWidth(false))/2);

        Point lineStart = Utils.goInDirection(Utils.goInDirection(center, bearing+Math.PI, distOut), bearing-Math.PI/2, pixelsPerMeter*offsetStart);
        Point lineEnd = Utils.goInDirection(Utils.goInDirection(center, bearing, distOut), bearing-Math.PI/2, pixelsPerMeter*offsetEnd);

        Utils.renderRoadLinePopup(g, lineStart, lineEnd, bearing, getWidth(true), getWidth(false),
                pixelsPerMeter, Utils.DividerType.SOLID, (Utils.isOneway(_way) && _direction == -1) ? Color.YELLOW : Color.WHITE);
    }
}