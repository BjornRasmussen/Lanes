package org.openstreetmap.josm.plugins.lanes;

import java.awt.*;

public class Preset {
    private final int lanesForward;
    private final int lanesBackward;
    private final double lanesBothWays;
    private final Image presetImage;
    public Preset(int forward, int backward, double bothWays, Image image) {
        lanesForward = forward;
        lanesBackward = backward;
        lanesBothWays = bothWays;
        presetImage = image;
    }
    public int getLanesForward() { return lanesForward; }
    public int getLanesBackward() { return lanesBackward; }
    public double getLanesBothWays() { return lanesBothWays; }
    public Image getPresetImage() { return presetImage; }
}
