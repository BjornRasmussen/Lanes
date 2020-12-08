package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UntaggedRoadRenderer extends RoadRenderer {

    protected UntaggedRoadRenderer(Way w, MapView mv, LaneMappingMode parent) {
        super(w, mv, parent);
    }

    @Override
    void render(Graphics2D g) {
        renderAsphalt(g, Utils.DEFAULT_UNTAGGED_ASPHALT_COLOR);
        renderRoadEdges(g);
        renderQuestionMarks(g);
    }

    private void renderRoadEdges(Graphics2D g) {
        Utils.renderRoadLine(g, _mv, this, 0, 0, (oneway()?0.5:1)*Utils.WIDTH_LANES,
                (oneway()?0.5:1)*Utils.WIDTH_LANES, Utils.DividerType.UNTAGGED_ROAD_EDGE, Utils.DEFAULT_UNTAGGED_ROADEDGE_COLOR);
        Utils.renderRoadLine(g, _mv, this, 0, 0, -(oneway()?0.5:1)*Utils.WIDTH_LANES,
                -(oneway()?0.5:1)*Utils.WIDTH_LANES, Utils.DividerType.UNTAGGED_ROAD_EDGE, Utils.DEFAULT_UNTAGGED_ROADEDGE_COLOR);
    }

    @Override
    Way getAlignment() {
        return getWay();
    }

    @Override
    Way getLeftEdge(Way waySegment, int segment) {
        return Utils.getParallel(waySegment, (oneway() ? 0.5 : 1)*Utils.WIDTH_LANES+(Utils.RENDERING_WIDTH_DIVIDER/2),
                (oneway() ? 0.5 : 1)*Utils.WIDTH_LANES+(Utils.RENDERING_WIDTH_DIVIDER/2), false,
                segment==0 ? otherStartAngle : Double.NaN, segment==startPoints.size()-1 ? otherEndAngle : Double.NaN);
    }

    @Override
    Way getRightEdge(Way waySegment, int segment) {
        return Utils.getParallel(waySegment, 0 - (oneway()?0.5:1)*Utils.WIDTH_LANES - (Utils.RENDERING_WIDTH_DIVIDER/2),
                0 - (oneway()?0.5:1)*Utils.WIDTH_LANES - (Utils.RENDERING_WIDTH_DIVIDER/2), false,
                segment==0 ? otherStartAngle : Double.NaN, segment==startPoints.size()-1 ? otherEndAngle : Double.NaN);
    }

    @Override
    protected void makePopup(MouseEvent e) {
        Utils.displayPopup(getLayoutPopupContent(), e, _mv, getWay());
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

                for (int i = 0; i < getWay().getNodesCount() - 1; i++) {
                    double distThisTime = getWay().getNode(i).getCoor().greatCircleDistance(getWay().getNode(i + 1).getCoor());

                    if (distSoFar + distThisTime > Utils.DIST_TO_FIRST_TURN + 3*Utils.DIST_BETWEEN_TURNS * (numDrawn)) {
                        double distIntoSegment = Utils.DIST_TO_FIRST_TURN + 3*Utils.DIST_BETWEEN_TURNS * (numDrawn) - distSoFar;
                        double portionFirst = (distThisTime - distIntoSegment) / distThisTime;
                        LatLon pos = new LatLon(getWay().getNode(i).lat() * portionFirst + (getWay().getNode(i + 1).lat() * (1 - portionFirst)),
                                getWay().getNode(i).lon() * portionFirst + (getWay().getNode(i + 1).lon() * (1 - portionFirst)));
                        Point point = _mv.getPoint(pos);
                        double width = (oneway() ? 0.7 : 1.4) * Utils.WIDTH_LANES;

                        BBox bBox = _mv.getRealBounds().toBBox();
                        if (!(((point.x < _mv.getPoint(bBox.getTopLeft()).x) || (point.x > _mv.getPoint(bBox.getBottomRight()).x)) &&
                                ((point.y < _mv.getPoint(bBox.getTopLeft()).y) || (point.y > _mv.getPoint(bBox.getBottomRight()).y))) ) {

                            int size = (int) (width * 100 / _mv.getDist100Pixel()) + 1;
                            int offset = (int) (width * 50 / _mv.getDist100Pixel());
                            g.drawImage(_mv.getScale() > (oneway() ? 0.04 : 0.08) ? Utils.lr_questionMark : Utils.questionMark,
                                    point.x - offset, point.y - offset, size, size, null);
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
}
