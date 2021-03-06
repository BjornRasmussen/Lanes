package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UntaggedRoadRenderer extends RoadRenderer {
    boolean _valid;

    protected UntaggedRoadRenderer(Way w, MapView mv, LaneMappingMode parent, boolean valid) {
        super(w, mv, parent);
        _valid = valid;
    }

    @Override
    void render(Graphics2D g) {
        renderAsphalt(g, Utils.DEFAULT_UNTAGGED_ASPHALT_COLOR);
        renderRoadEdges(g);
        renderQuestionMarks(g);
    }

    @Override
    void renderPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter) {
        renderAsphaltPopup(g, Utils.POPUP_UNTAGGED_ASPHALT_COLOR, center, bearing, distOut, pixelsPerMeter);
        renderRoadEdgesPopup(g, center, bearing, distOut, pixelsPerMeter, false);
    }

    private void renderRoadEdges(Graphics2D g) {
        boolean o = _mv.getScale() > 1;
        Utils.renderRoadLine(g, _mv, this, 0, 0, (oneway()?0.5:1)*Utils.WIDTH_LANES,
                (oneway()?0.5:1)*Utils.WIDTH_LANES, Utils.DividerType.UNTAGGED_ROAD_EDGE, o ? Color.RED : Utils.DEFAULT_UNTAGGED_ROADEDGE_COLOR);
        Utils.renderRoadLine(g, _mv, this, 0, 0, -(oneway()?0.5:1)*Utils.WIDTH_LANES,
                -(oneway()?0.5:1)*Utils.WIDTH_LANES, Utils.DividerType.UNTAGGED_ROAD_EDGE, o ? Color.RED : Utils.DEFAULT_UNTAGGED_ROADEDGE_COLOR);
    }

    @Override
    Way getAlignment() {
        return getWay();
    }

    @Override
    Way getLeftEdge(Way waySegment, int segment) {
        return Utils.getParallel((waySegment != null) ? waySegment : getAlignment(), (oneway() ? 0.5 : 1)*Utils.WIDTH_LANES+(Utils.RENDERING_WIDTH_DIVIDER/2),
                (oneway() ? 0.5 : 1)*Utils.WIDTH_LANES+(Utils.RENDERING_WIDTH_DIVIDER/2), false,
                startPoints.get(segment) < 0.1 || waySegment == null ? otherStartAngle : Double.NaN,
                endPoints.get(segment) > getAlignment().getLength()-0.1 || waySegment == null ? otherEndAngle : Double.NaN);
    }

    @Override
    Way getRightEdge(Way waySegment, int segment) {
        return Utils.getParallel((waySegment != null) ? waySegment : getAlignment(), 0 - (oneway()?0.5:1)*Utils.WIDTH_LANES - (Utils.RENDERING_WIDTH_DIVIDER/2),
                0 - (oneway()?0.5:1)*Utils.WIDTH_LANES - (Utils.RENDERING_WIDTH_DIVIDER/2), false,
                startPoints.get(segment) < 0.1 || waySegment == null ? otherStartAngle : Double.NaN,
                endPoints.get(segment) > getAlignment().getLength()-0.1 || waySegment == null ? otherEndAngle : Double.NaN);
    }

    private boolean oneway() {
        return Utils.isOneway(getWay());
    }

    private void renderQuestionMarks(Graphics2D g) {
        if (_mv.getScale() > 1) return; // Don't render the question marks when the map is too zoomed out

        try {
            for (int h = 0; h < getAlignments().size(); h++) {
                // This runs for each sub part of a road (each segment)
                int numDrawn = 0;
                double distSoFar = 0;
                Way align = getAlignments().get(h);
                for (int i = 0; i < align.getNodesCount() - 1; i++) {
                    double distThisTime = align.getNode(i).getCoor().greatCircleDistance(align.getNode(i + 1).getCoor());

                    if (distSoFar + distThisTime > Utils.DIST_TO_FIRST_TURN + 3*Utils.DIST_BETWEEN_TURNS * (numDrawn)) {
                        double distIntoSegment = Utils.DIST_TO_FIRST_TURN + 3*Utils.DIST_BETWEEN_TURNS * (numDrawn) - distSoFar;
                        double portionFirst = (distThisTime - distIntoSegment) / distThisTime;
                        LatLon pos = new LatLon(align.getNode(i).lat() * portionFirst + (align.getNode(i + 1).lat() * (1 - portionFirst)),
                                align.getNode(i).lon() * portionFirst + (align.getNode(i + 1).lon() * (1 - portionFirst)));
                        Point point = _mv.getPoint(pos);
                        double width = (oneway() ? 0.7 : 1.4) * Utils.WIDTH_LANES;

                        BBox bBox = _mv.getRealBounds().toBBox();
                        if (!(((point.x < _mv.getPoint(bBox.getTopLeft()).x) || (point.x > _mv.getPoint(bBox.getBottomRight()).x)) &&
                                ((point.y < _mv.getPoint(bBox.getTopLeft()).y) || (point.y > _mv.getPoint(bBox.getBottomRight()).y))) ) {

                            int size = (int) (width * 100 / _mv.getDist100Pixel()) + 1;
                            int offset = (int) (width * 50 / _mv.getDist100Pixel());
                            g.drawImage(_mv.getScale() > (oneway() ? 0.04 : 0.08) ? (_valid ? Utils.lr_questionMark : Utils.lr_exclamationPoint) :
                                    (_valid ? Utils.questionMark : Utils.exclamationPoint), point.x - offset, point.y - offset, size, size, null);
                        }
                        distSoFar -= distThisTime;
                        i--;
                        numDrawn++;
                    }

                    distSoFar += distThisTime;
                }
            }
        } catch (Exception ignored) {} // Just don't render the turn markings if they can't be rendered.
    }

    @Override
    public double getWidth(boolean start) { return Utils.WIDTH_LANES*(Utils.isOneway(_way) ? 1 : 2) + Utils.RENDERING_WIDTH_DIVIDER; }


}
