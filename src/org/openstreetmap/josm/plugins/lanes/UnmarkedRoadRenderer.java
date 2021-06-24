package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class UnmarkedRoadRenderer extends RoadRenderer {

    protected UnmarkedRoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        super(w, mv, parent);
    }

    @Override
    public void render(Graphics2D g) {
        renderAsphalt(g, Utils.DEFAULT_ASPHALT_COLOR);
        renderRoadEdges(g);
    }

    @Override
    void renderPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter) {
        renderAsphaltPopup(g, Utils.POPUP_ASPHALT_COLOR, center, bearing, distOut, pixelsPerMeter);
        renderRoadEdgesPopup(g, center, bearing, distOut, pixelsPerMeter, true);
    }

    private void renderRoadEdges(Graphics2D g) {
        Color left = Utils.isRightHand(getWay()) && Utils.isOneway(getWay()) ? Utils.DEFAULT_CENTRE_DIVIDER_COLOR : Utils.DEFAULT_DIVIDER_COLOR;
        Color right = !Utils.isRightHand(getWay()) && Utils.isOneway(getWay()) ? Utils.DEFAULT_CENTRE_DIVIDER_COLOR : Utils.DEFAULT_DIVIDER_COLOR;
        double hws = (getWidth(true)-Utils.RENDERING_WIDTH_DIVIDER)/2;
        double hwe = (getWidth(false)-Utils.RENDERING_WIDTH_DIVIDER)/2;
        Utils.renderRoadLine(g, _mv, this, 0, 0, hws, hwe, Utils.DividerType.UNMARKED_ROAD_EDGE, left);
        Utils.renderRoadLine(g, _mv, this, 0, 0, -hws, -hwe, Utils.DividerType.UNMARKED_ROAD_EDGE, right);
    }

    @Override
    public Way getAlignment() {
        return getWay();
    }

    @Override
    Way getLeftEdge(Way waySegment, int segment) {
        return Utils.getParallel(waySegment != null ? waySegment : getAlignment(), getWidth(true)/2,
                getWidth(false)/2, false,
                (startPoints.get(segment) < 0.1 || waySegment == null) ? otherStartAngle : Double.NaN,
                (endPoints.get(segment) > getAlignment().getLength()-0.1 || waySegment == null) ? otherEndAngle : Double.NaN);
    }

    @Override
    Way getRightEdge(Way waySegment, int segment) {
        return Utils.getParallel((waySegment != null) ? waySegment : getAlignment(), 0 - getWidth(true)/2,
                0 - getWidth(false)/2, false,
                (startPoints.get(segment) < 0.1 || waySegment == null) ? otherStartAngle : Double.NaN,
                (endPoints.get(segment) > getAlignment().getLength()-0.1 || waySegment == null) ? otherEndAngle : Double.NaN);
    }

    @Override
    public double getWidth(boolean start) {
        double defaultWidth = (Utils.isOneway(getWay()) ? 1 : 2) * Utils.WIDTH_LANES;
        try {
            double width;
            if (_way.hasTag("width")) {
                width = Utils.parseWidth(_way.get("width"));
            } else if (_way.hasTag("width:start") && start) {
                width = Utils.parseWidth(_way.get("width:start"));
            } else if (_way.hasTag("width:end") && !start) {
                width = Utils.parseWidth(_way.get("width:end"));
            } else if (_way.hasTag("lanes:forward") && _way.hasTag("lanes:backward")) {
                width = Utils.WIDTH_LANES * (Double.parseDouble(_way.get("lanes:forward")) + Double.parseDouble(_way.get("lanes:backward")));
            } else if (_way.hasTag("lanes")) {
                width = Utils.WIDTH_LANES * Double.parseDouble(_way.get("lanes"));
            } else {
                width = defaultWidth;
            }
            return width + Utils.RENDERING_WIDTH_DIVIDER;
        } catch (Exception e) {
            return defaultWidth + Utils.RENDERING_WIDTH_DIVIDER;
        }
    }
}
