package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

/*
 * LanesPlugin class - entry point to program.
 * -> Creates an instance of a LaneMappingMode when JOSM boots up.
 */

public class LanesPlugin extends Plugin {

    public LanesPlugin(PluginInformation info) {
        super(info);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) {
            MainApplication.getMap().addMapMode(new IconToggleButton(new LaneMappingMode()));
        }
    }
}
