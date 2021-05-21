package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;

import java.awt.*;

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
                Utils.renderRoadLine(g, _mv, this, 0, 0, hws, hwe, DividerType.UNMARKED_ROAD_EDGE, left, false);
        Utils.renderRoadLine(g, _mv, this, 0, 0, -hws, -hwe, DividerType.UNMARKED_ROAD_EDGE, right, false);
    }

    @Override
    public Way getAlignment() {
        return getWay();
    }

    @Override
    public Way getEdge(int segment, boolean rightSide) {
        Way alignmentPart = (segment < 0) ? getAlignment() : getAlignments().get(segment);

        // Calculate start/end offsets for this segment.
        double swt = Math.max(segmentStartPoints.get(segment < 0 ? 0 : segment), 0)/getAlignment().getLength();
        double offsetStart = swt*(getWidth(false)/2) + (1-swt)*(getWidth(true)/2);
        double ewt = Math.min(segmentEndPoints.get(segment < 0 ? segmentEndPoints.size()-1 : segment), getAlignment().getLength())/getAlignment().getLength();
        double offsetEnd = ewt*(getWidth(false)/2) + (1-ewt)*(getWidth(true)/2);

        // If right side, then the values need to be negative to get same result.
        if (rightSide) {
            offsetStart = 0 - offsetStart;
            offsetEnd = 0 - offsetEnd;
        }

        return Utils.getParallel(alignmentPart, offsetStart, offsetEnd, false,
                (segment < 0 || segmentStartPoints.get(segment) < 0.1) ? otherStartAngle : Double.NaN,
                (segment < 0 || segmentEndPoints.get(segment) > getAlignment().getLength()-0.1) ? otherEndAngle : Double.NaN);
    }

    @Override
    public double getWidth(boolean start) {
        double defaultWidth = (Utils.isOneway(getWay()) ? 1 : 2) * Utils.WIDTH_LANES;
        try {
            double width;
            if (_way.hasTag("width:start") && start) {
                width = Utils.parseWidth(_way.get("width:start"));
            } else if (_way.hasTag("width:end") && !start) {
                width = Utils.parseWidth(_way.get("width:end"));
            } else if (_way.hasTag("width")) {
                width = Utils.parseWidth(_way.get("width"));
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
