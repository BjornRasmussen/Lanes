package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.gui.MapView;

import java.awt.*;

public class RoadEdge extends RoadPiece {

    protected RoadEdge(int direction, int position, MapView mv, RoadSegmentRenderer parent) {
        super(direction, position, mv, parent);
    }

    @Override
    double getWidth(boolean start) {
        return Utils.RENDERING_WIDTH_DIVIDER;
    }

    @Override
    void render(Graphics2D g) {
        renderAsphalt(g);
        renderRoadLine(g, _offsetStart, _offsetEnd, Utils.DividerType.SOLID, _way.isOneway() == 1 && _direction == -1);
    }
}