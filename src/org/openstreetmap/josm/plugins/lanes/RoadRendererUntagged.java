package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;

import java.awt.*;

public class RoadRendererUntagged extends RoadRenderer {
    boolean _valid;

    protected RoadRendererUntagged(Way w, MapView mv, LaneMappingMode parent, boolean valid) {
        super(w, mv, parent);
        _valid = valid;
    }

    @Override
    void render(Graphics2D g) {
        renderAsphalt(g, UtilsRender.DEFAULT_UNTAGGED_ASPHALT_COLOR);
        renderRoadEdges(g);
        renderQuestionOrExclamationMarks(g);
    }

    @Override
    void renderPopup(Graphics2D g, Point center, double bearing, double distOut, double pixelsPerMeter) {
        renderAsphaltPopup(g, UtilsRender.POPUP_UNTAGGED_ASPHALT_COLOR, center, bearing, distOut, pixelsPerMeter);
        renderRoadEdgesPopup(g, center, bearing, distOut, pixelsPerMeter, false);
    }

    private void renderRoadEdges(Graphics2D g) {
        boolean o = _parent._mv.getScale() > 1;
        UtilsRender.renderRoadLine(g, _parent._mv, this, 0, 0, (UtilsGeneral.isOneway(getWay())?0.5:1)* UtilsRender.WIDTH_LANES,
                (UtilsGeneral.isOneway(getWay())?0.5:1)* UtilsRender.WIDTH_LANES, DividerType.UNTAGGED_ROAD_EDGE, o ? Color.RED : UtilsRender.DEFAULT_UNTAGGED_ROADEDGE_COLOR, false);
        UtilsRender.renderRoadLine(g, _parent._mv, this, 0, 0, -(UtilsGeneral.isOneway(getWay())?0.5:1)* UtilsRender.WIDTH_LANES,
                -(UtilsGeneral.isOneway(getWay())?0.5:1)* UtilsRender.WIDTH_LANES, DividerType.UNTAGGED_ROAD_EDGE, o ? Color.RED : UtilsRender.DEFAULT_UNTAGGED_ROADEDGE_COLOR, false);
    }

    @Override
    Way getAlignment() {
        return getWay();
    }

    @Override
    Way getEdge(int segment, boolean right) {
        Way alignmentPart = segment < 0 ? getAlignment() : getAlignments().get(segment);
        double offset = ((UtilsGeneral.isOneway(getWay()) ? 0.5 : 1)* UtilsRender.WIDTH_LANES+(UtilsRender.RENDERING_WIDTH_DIVIDER/2))*(right ? -1 : 1);
        return UtilsSpatial.getParallel(alignmentPart, offset, offset, false,
                (segment < 0 || segmentStartPoints.get(segment) < 0.1) ? otherStartAngle : Double.NaN,
                (segment < 0 || segmentEndPoints.get(segment) > getAlignment().getLength()-0.1) ? otherEndAngle : Double.NaN);
    }

    private void renderQuestionOrExclamationMarks(Graphics2D g) { // TODO extract part that finds WHERE to draw and merge with other marking drawers.
        if (_mv.getScale() > 1) return; // Don't render the question marks when the map is too zoomed out

        try {
            for (int h = 0; h < getAlignments().size(); h++) {
                // This runs for each sub part of a road (each segment)
                int numDrawn = 0;
                double distSoFar = 0;
                Way align = getAlignments().get(h);
                for (int i = 0; i < align.getNodesCount() - 1; i++) {
                    double distThisTime = align.getNode(i).getCoor().greatCircleDistance(align.getNode(i + 1).getCoor());

                    if (distSoFar + distThisTime > UtilsRender.DIST_TO_FIRST_TURN + 3* UtilsRender.DIST_BETWEEN_TURNS * (numDrawn)) {
                        double distIntoSegment = UtilsRender.DIST_TO_FIRST_TURN + 3* UtilsRender.DIST_BETWEEN_TURNS * (numDrawn) - distSoFar;
                        double portionFirst = (distThisTime - distIntoSegment) / distThisTime;
                        LatLon pos = new LatLon(align.getNode(i).lat() * portionFirst + (align.getNode(i + 1).lat() * (1 - portionFirst)),
                                align.getNode(i).lon() * portionFirst + (align.getNode(i + 1).lon() * (1 - portionFirst)));
                        Point point = _parent._mv.getPoint(pos);
                        double width = (UtilsGeneral.isOneway(getWay()) ? 0.7 : 1.4) * UtilsRender.WIDTH_LANES;

                        BBox bBox = _parent._mv.getRealBounds().toBBox();
                        if (!(((point.x < _parent._mv.getPoint(bBox.getTopLeft()).x) || (point.x > _parent._mv.getPoint(bBox.getBottomRight()).x)) &&
                                ((point.y < _parent._mv.getPoint(bBox.getTopLeft()).y) || (point.y > _parent._mv.getPoint(bBox.getBottomRight()).y))) ) {

                            int size = (int) (width * 100 / _parent._mv.getDist100Pixel()) + 1;
                            int offset = (int) (width * 50 / _parent._mv.getDist100Pixel());
                            g.drawImage(_mv.getScale() > (UtilsGeneral.isOneway(getWay()) ? 0.04 : 0.08) ?
                                    (_valid ? UtilsRender.lr_questionMark : UtilsRender.lr_exclamationPoint) :
                                    (_valid ? UtilsRender.questionMark : UtilsRender.exclamationPoint),
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

    @Override
    public double getWidth(boolean start) { return UtilsRender.WIDTH_LANES*(UtilsGeneral.isOneway(_way) ? 1 : 2) + UtilsRender.RENDERING_WIDTH_DIVIDER; }
}