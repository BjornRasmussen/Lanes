package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;

import java.awt.*;
import java.util.List;

public abstract class RoadRenderer {

    protected final Way _way;
    protected final MapView _mv;
    protected final LaneMappingMode _parent;
    protected Way _alignment;

    protected RoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        _way = w;
        _mv = mv;
        _parent = parent;
    }

    // Renders to road to g, always called by LaneMappingMode.
    abstract void render(Graphics2D g);

    // For getting the OSM way that the RoadRenderer is modeled after.
    public Way getWay() { return _way; }

    // For getting a different version of _way that's parallel to the lanes.
    // Only different from _way when the way has different placement at start/end.
    abstract Way getAlignment();

    // For getting alignment split up by road segment.
    abstract List<Way> getAlignments();

    abstract void updateAlignment();

    // Static constructor used by LaneMappingMode to create
    //  RoadRenderers without having to worry about which kind is created.
    public static RoadRenderer buildRoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        if (!wayHasRoadTags(w) && !wayHasLaneTags(w)) return null;

        if (w.hasTag("lane_markings", "no")) {
            return new UnmarkedRoadRenderer(w, mv, parent);
        } else if (wayHasLaneTags(w)) {
            return new MarkedRoadRenderer(w, mv, parent);
        } else {
            return new UntaggedRoadRenderer(w, mv, parent);
        }
    }

    private static boolean wayHasLaneTags(Way way) {
        return (!way.hasAreaTags() && way.isDrawable() &&
                (way.hasTag("lanes") || way.hasTag("lanes:forward") || way.hasTag("lanes:backward") ||
                way.hasTag("turn:lanes") || way.hasTag("turn:lanes:forward") || way.hasTag("turnlanes:backward") ||
                way.hasTag("change:lanes") || way.hasTag("change:lanes:forward") || way.hasTag("change:lanes:backward") ||
                way.hasTag("bicycle:lanes") || way.hasTag("bicycle:lanes:forward") || way.hasTag("bicycle:lanes:backward") ||
                way.hasTag("width:lanes") || way.hasTag("width:lanes:forward") || way.hasTag("width:lanes:backward") ||
                way.hasTag("access:lanes") || way.hasTag("access:lanes:forward") || way.hasTag("access:lanes:backward") ||
                way.hasTag("psv:lanes") || way.hasTag("psv:lanes:forward") || way.hasTag("psv:lanes:backward") ||
                way.hasTag("surface:lanes") || way.hasTag("surface:lanes:forward") || way.hasTag("surface:lanes:backward") ||
                way.hasTag("bus:lanes") || way.hasTag("bus:lanes:forward") || way.hasTag("bus:lanes:backward")));
    }

    private static boolean wayHasRoadTags(Way way) {
        return (!way.hasAreaTags() && way.isDrawable() &&
                (way.hasTag("highway", "motorway") || way.hasTag("highway", "motorway_link") ||
                        way.hasTag("highway", "trunk") || way.hasTag("highway", "trunk_link") ||
                        way.hasTag("highway", "primary") || way.hasTag("highway", "primary_link") ||
                        way.hasTag("highway", "secondary") || way.hasTag("highway", "secondary_link") ||
                        way.hasTag("highway", "tertiary") || way.hasTag("highway", "tertiary_link") ||
                        way.hasTag("highway", "residential") || way.hasTag("highway", "unclassified") ||
                        way.hasTag("highway", "bus_guideway")));
    }
}
