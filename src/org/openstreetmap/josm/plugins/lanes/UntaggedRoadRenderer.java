package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;

import java.awt.*;
import java.util.List;

public class UntaggedRoadRenderer extends RoadRenderer {

    protected UntaggedRoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        super(w, mv, parent);
    }

    @Override
    void render(Graphics2D g) {

    }

    @Override
    Way getAlignment() {
        return null;
    }

    @Override
    List<Way> getAlignments() {
        return null;
    }

    @Override
    void updateAlignment() {

    }
}
