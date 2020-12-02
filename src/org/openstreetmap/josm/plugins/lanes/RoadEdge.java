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

        renderRoadLine(g, _offsetStart, _offsetEnd, Utils.DividerType.SOLID,
                (_way.isOneway() == 1 && _direction == -1) ? Color.YELLOW : Color.WHITE);
    }
}