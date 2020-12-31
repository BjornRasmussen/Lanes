package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.DataSourceChangeEvent;
import org.openstreetmap.josm.data.osm.DataSourceListener;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.DataSetListener;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener;
import org.openstreetmap.josm.tools.Territories;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.ImageView;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class LaneLayoutPopup extends JPanel implements DataSourceListener {
    private JScrollPane presetPanel;
    private JTabbedPane layoutPanel;
    private RoadRenderer _rr;
    private boolean undo = false;

    public LaneLayoutPopup(RoadRenderer rr) {
        super();
        _rr = rr;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(600, 400));

        setPresetPanel();
        add(presetPanel, BorderLayout.LINE_START);
        setLayoutPanel();
        add(layoutPanel, BorderLayout.CENTER);

        // Add this as a listener for dataset changes
        MainApplication.getLayerManager().getActiveDataSet().addDataSourceListener(this);
    }

    private void setPresetPanel() {
        int m = 12;
        JPanel buffer = new JPanel();
        buffer.setLayout(new BorderLayout());
        JPanel inner = new JPanel();
        inner.setBorder(new EmptyBorder(m, m, m, m));
        buffer.add(inner, BorderLayout.BEFORE_FIRST_LINE);

        presetPanel = new JScrollPane();
        presetPanel.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        presetPanel.getVerticalScrollBar().setUnitIncrement(16);
        presetPanel.setViewportView(buffer);

        List<Preset> presets = Utils.getPresets(Utils.isRightHand(_rr.getWay()),
                Territories.isIso3166Code("US", _rr.getWay().getNode(0).getCoor()) ||
                        Territories.isIso3166Code("CA", _rr.getWay().getNode(0).getCoor()), Utils.isOneway(_rr.getWay()));
        inner.setLayout(new GridLayout((presets.size()+1)/2, 2, m, m));
        inner.setPreferredSize(new Dimension(3*m + 2*75, m  +  (75+m) * ((presets.size()+1)/2)));
        for (Preset preset : presets) {
            inner.add(presetToButton(preset, _rr.getWay()));
        }
    }

    private JComponent presetToButton(Preset preset, Way w) {
        JButton output =  new PresetButton(preset.getPresetImage());
        output.addActionListener(e -> {
            Utils.applyPreset(preset, w, undo);
            undo = true;
        });
        return output;
    }

    private void setLayoutPanel() {
        // Need outer to be choose marked/unmarked
        // Unmarked is just simple width options.
        // Marked has checkbox for centre lane
        layoutPanel = new JTabbedPane();
        layoutPanel.addTab("Marked", getMarkedLayoutPanel());
        layoutPanel.addTab("Unmarked", getUnmarkedLayoutPanel());
    }

    private JComponent getMarkedLayoutPanel() {
        JPanel output = new JPanel();
        output.setLayout(new BorderLayout());
        // Add checkbox for center lane at bottom if in USA or Canada (from list)
        // TODO use yml file instead of this easy solution
        if (Territories.isIso3166Code("US", _rr.getWay().getNode(0).getCoor()) || Territories.isIso3166Code("CA", _rr.getWay().getNode(0).getCoor())) {
            output.add(getBothWaysCheckbox(), BorderLayout.AFTER_LAST_LINE);
        }
        // Add marked version of dynamic layout thingy
        output.add(new RoadPanel(_rr), BorderLayout.CENTER);
        return output;
    }

    private JComponent getUnmarkedLayoutPanel() {
//        JPanel output = new JPanel();
        return new RoadPanel(_rr);
    }

    private JComponent getBothWaysCheckbox() {
        return new JLabel("[ ] Centre left turn lane");
    }

    @Override
    public void dataSourceChange(DataSourceChangeEvent event) {
        setLayoutPanel();
        add(layoutPanel, BorderLayout.CENTER);
    }
}

class RoadPanel extends JPanel {
    private RoadRenderer _rr;
    double _bearing;

    public RoadPanel(RoadRenderer rr) {
        super();
        _rr = rr;
        _bearing = Utils.getWayBearing(_rr.getAlignment());
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Draw road
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        _rr.renderPopup((Graphics2D) g, new Point(getWidth()/2, getHeight()/2), _bearing, distOut(),
                roadWidth()*2/(_rr.getWidth(true)+_rr.getWidth(false)));
    }

    private double roadWidth() { return (Math.min(getWidth(), getHeight())) / 2.5; }
    private double distOut() { return (Math.max(getWidth(), getHeight())) * 0.71; /* 0.71 > sqrt(2)/2 */ }
}

class PresetButton extends JButton {
    Image _image;
    public PresetButton(Image image) {
        super();
        _image = image;
        setPreferredSize(new Dimension(75, 75));
    }

    @Override
    public void paintComponent(Graphics g) {
        g.drawImage(_image, 0, 0, 75, 75, null);
    }

}