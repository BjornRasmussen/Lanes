package org.openstreetmap.josm.plugins.lanes;

import jdk.jshell.execution.Util;
import org.openstreetmap.josm.gui.MapView;

import java.awt.*;

public class RoadPieceEdge extends RoadPiece {

    protected RoadPieceEdge(int direction, int position, MapView mv, RoadRendererMarked parent) {
        super(direction, position, mv, parent);
    }

    @Override
    double getWidth(boolean start) {
        return UtilsRender.RENDERING_WIDTH_DIVIDER;
    }

    @Override
    String widthTag(boolean start) {
        return null;
    }

    @Override
    double getWidthTagged(boolean start) {
        return 0;
    }

    @Override
    void render(Graphics2D g) {
        String country = "US";
        String centerColor = UtilsGeneral.isCenterYellow.containsKey(country) ?
                UtilsGeneral.isCenterYellow.get(country) : UtilsGeneral.isCenterYellow.get("default");
        String roadEdge = UtilsGeneral.shoulderLineColor.containsKey(country) ?
                UtilsGeneral.shoulderLineColor.get(country) : UtilsGeneral.shoulderLineColor.get("default");

        UtilsRender.renderRoadLine(g, _mv, _parent, 0, 0, _offsetStart, _offsetEnd, DividerType.SOLID,
                (UtilsGeneral.isOneway(_way) && _direction == -1) ? Color.YELLOW : Color.WHITE, true);
    }

    @Override
    void renderPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter) {
        double offsetStart = _offsetStart-(_parent._offsetToLeftStart - (_parent.getWidth(true))/2);
        double offsetEnd = _offsetEnd-(_parent._offsetToLeftEnd - (_parent.getWidth(false))/2);

        Point lineStart = UtilsRender.goInDirection(UtilsRender.goInDirection(center, bearing+Math.PI, distOut), bearing-Math.PI/2, pixelsPerMeter*offsetStart);
        Point lineEnd = UtilsRender.goInDirection(UtilsRender.goInDirection(center, bearing, distOut), bearing-Math.PI/2, pixelsPerMeter*offsetEnd);

        UtilsRender.renderRoadLinePopup(g, lineStart, lineEnd, bearing, getWidth(true), getWidth(false),
                pixelsPerMeter, DividerType.SOLID, (UtilsGeneral.isOneway(_way) && _direction == -1) ? Color.YELLOW : Color.WHITE);
    }
}