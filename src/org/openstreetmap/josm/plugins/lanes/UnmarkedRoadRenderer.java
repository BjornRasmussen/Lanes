package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;

import java.awt.*;

public class UnmarkedRoadRenderer implements RoadRenderer {


//    // <editor-fold defaultstate="collapsed" desc="Variables">
//
//    private final Way _way;
//    private Way _alignment;
//    private final MapView _mv;
//
//    private Way _outline;
//    private Way _asphalt;
//
//    public static String selected = "";
//
//    private double _offsetToLeftStart; // Distance between left side of road and the centre of the way.
//    private double _offsetToLeftEnd;
//
//    public final List<RoadPiece> _forwardLanes = new ArrayList<>();
//    public final List<RoadPiece> _forwardDividers = new ArrayList<>();
//    public final List<RoadPiece> _backwardLanes = new ArrayList<>();
//    public final List<RoadPiece> _backwardDividers = new ArrayList<>();
//    private RoadPiece _bothWaysLane;
//    private RoadPiece _leftRoadEdge;
//    private RoadPiece _rightRoadEdge;
//
//    public double startAngle = Double.NaN;
//    public double endAngle = Double.NaN;
//
//    public double otherStartAngle = Double.NaN;
//    public double otherEndAngle = Double.NaN;
//
//    public LaneMappingMode _parent;
//    protected boolean _isValid = true; // Display red error line if tags are wrong.
//
//    // </editor-fold>

    @Override
    public void render(Graphics2D g) {

    }

    @Override
    public Way getWay() {
        return null;
    }

    @Override
    public Way getAlignment() {
        return null;
    }
}
