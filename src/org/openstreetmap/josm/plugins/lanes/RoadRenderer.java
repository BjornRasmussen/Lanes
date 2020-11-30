package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Way;

import java.awt.*;

public interface RoadRenderer {
    void render(Graphics2D g);
    Way getWay();
    Way getAlignment();
}
