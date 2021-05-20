package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSourceChangeEvent;
import org.openstreetmap.josm.data.osm.DataSourceListener;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.Territories;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class LaneLayoutPopup extends JPanel implements ActionListener {
    private JScrollPane presetPanel;
    private JTabbedPane layoutPanel;
    private RoadRenderer _rr;
    private LaneMappingMode _parent;
    private boolean undo = false;
    private int changeTolerance = 0;

    // Store current state:
    private String selectedSubPart;

    /**
     * A pop-up that allows for quick editing of the layout of a road.
     * @param rr The parent RoadRenderer, used for rendering and editing purposes.
     */
    public LaneLayoutPopup(RoadRenderer rr) {
        super();
        _rr = rr;
        _parent = _rr._parent;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(600, 400));

        setPresetPanel();
        add(presetPanel, BorderLayout.LINE_START);
        setLayoutPanel();
        add(layoutPanel, BorderLayout.CENTER);

        // Add this as a listener for dataset changes
        _parent.addUpdateListener(this);
    }

    /**
     * Sets up the preset panel on the left side.
     */
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

    /**
     * Creates a button using the input preset and way.  This button will apply the preset when clicked.
     * @param preset The preset to apply when the button is clicked.
     * @param w The way to apply the changes to.
     * @return The button.
     */
    private JComponent presetToButton(Preset preset, Way w) {
        JPanel output =  new PresetButton(preset.getPresetImage());
        output.addMouseListener(new MouseListener() {
            @Override
            public void mousePressed(MouseEvent e) {
                changeTolerance++;
                _parent.forceUpdateIgnore(1);
                Utils.applyPreset(preset, w, undo);
                _parent.updateOneRoad(w.getUniqueId());
                dataChange();
                undo = true;
            }

            @Override
            public void mouseClicked(MouseEvent e) {}
            @Override
            public void mouseReleased(MouseEvent e) {}
            @Override
            public void mouseEntered(MouseEvent e) {}
            @Override
            public void mouseExited(MouseEvent e) {}
        });
        return output;
    }

    /**
     * Sets up the layout panel, which includes the diagram of the selected road.
     * Includes editing features for increasing or decreasing lanes in each direction.
     */
    private void setLayoutPanel() {
        // Need outer to be choose marked/unmarked
        // Unmarked is just simple width options.
        // Marked has checkbox for centre lane

        if (_rr == null) {
            _parent.removeUpdateListener(this);
            return;
        } // Don't let deleted ways cause problems before the popup closes.

        layoutPanel = new JTabbedPane();
        layoutPanel.addTab("Marked", getMarkedLayoutPanel());
        layoutPanel.addTab("Unmarked", getUnmarkedLayoutPanel());
    }

    /**
     * Generates the layout panel for marked roads (with `lane_markings=yes`).  Includes
     * editing features for increasing / decreasing the number of lanes in each direction.
     * @return The JPanel containing the road diagram for marked roads.
     */
    private JComponent getMarkedLayoutPanel() {
        JPanel output = new JPanel();
        output.setLayout(new BorderLayout());

        // Add marked version of dynamic layout thingy
        output.add(new RoadPanel(_rr, this), BorderLayout.CENTER);
        return output;
    }

    /**
     * Generates the layout panel for unmarked roads (with `lane_markings=no`).  Includes
     * editing features for the width / # lanes.
     * @return The JPanel containing the road diagram for unmarked roads.
     */
    private JComponent getUnmarkedLayoutPanel() {
//        JPanel output = new JPanel();
        return new RoadPanel(_rr, this);
    }

    /**
     * When the data changes, update the layout panel
     * and ensure future edits with the popup don't undo the most recent edit.
     */
    void dataChange() {
        remove(layoutPanel);
        _rr = _parent.wayIdToRSR.get(_rr.getWay().getUniqueId());
        setLayoutPanel();
        add(layoutPanel, BorderLayout.CENTER);
        validate();
        repaint();

        // Ensure that edits not done by this plugin don't get undone.
        // Applying a preset deletes some data, so usually, the previous
        // preset edit is undone when a preset is applied, keeping data that would be gone.
        // This stuff ensures that tag changes done when the popup is open don't get undone after it gets closed.
        changeTolerance--;
        if (changeTolerance < 0) {
            undo = false;
            changeTolerance = 0;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (_rr != null) dataChange();
    }
}

// This class is for the panel that contains the diagram of the selected road.
class RoadPanel extends JPanel {
    private final static double roadsPerWidth = 0.4;

    private final RoadRenderer _rr;
    private double _bearing;
    private ClickAreaManager _manager;
    private LaneLayoutPopup _p;

    public RoadPanel(RoadRenderer rr, LaneLayoutPopup p) {
        super();
        _rr = rr;
        _p = p;
        _bearing = Utils.getWayBearing(_rr.getAlignment());
        _manager = new ClickAreaManager();
        addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {}

            @Override
            public void mousePressed(MouseEvent e) {
                _manager.submitEvent(e, "press");
            }

            @Override
            public void mouseReleased(MouseEvent e) {}
            @Override
            public void mouseEntered(MouseEvent e) {}
            @Override
            public void mouseExited(MouseEvent e) {}
        });
    }

    @Override
    public void paintComponent(Graphics g) {
        // Draw other stuff (like +- buttons)
        super.paintComponent(g);

        // Draw road
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        _rr.renderPopup((Graphics2D) g,  new Point(getWidth()/2, getHeight()/2), _bearing, distOut(),
                roadWidth()*2/(_rr.getWidth(true)+_rr.getWidth(false)));

        // Draw lane changers
        Point center = new Point(getWidth()/2, getHeight()/2);
        double dist = (roadWidth()/2)*(1+(1-roadsPerWidth)/2/roadsPerWidth);
        Point right = Utils.goInDirection(center, _bearing+Math.PI/2, dist);
        Point left = Utils.goInDirection(center, _bearing-Math.PI/2, dist);
        int width = 55;
        int height = 40;
        if (_rr instanceof MarkedRoadRenderer) {
            boolean rh = Utils.isRightHand(_rr.getWay());
            if (Utils.isOneway(_rr.getWay())) {
                drawLaneChange((Graphics2D) g, rh ? right : left, width, height, 1, ((MarkedRoadRenderer) _rr)._forwardLanes.size(), _p);
            } else {
                drawLaneChange((Graphics2D) g, rh ? right : left, width, height, 1, ((MarkedRoadRenderer) _rr)._forwardLanes.size(), _p);
                drawLaneChange((Graphics2D) g, rh ? left : right, width, height, -1, ((MarkedRoadRenderer) _rr)._backwardLanes.size(), _p);
            }
        }
    }

    private double roadWidth() { return (Math.min(getWidth(), getHeight())) * roadsPerWidth; }

    private double distOut() { return (Math.max(getWidth(), getHeight())) * 0.71; /* 0.71 > sqrt(2)/2 */ }

    private void drawLaneChange(Graphics2D g, Point topLeft, int width, int height, int dir, int number, LaneLayoutPopup forDataChanges)  {
        boolean isDarkMode = getBackground().getRed() <= 127; // Assumes grayish background.
        Color brighter = getBackground().brighter();
        Color darker = new Color((getBackground().getRed()+getBackground().darker().getRed())/2,
                (getBackground().getGreen()+getBackground().darker().getGreen())/2,
                (getBackground().getBlue()+getBackground().darker().getBlue())/2);


        g.setColor(isDarkMode ? brighter : darker);
        g.fillRoundRect(topLeft.x-width/2, topLeft.y-height/2, width, height, 10, 10);


        int fontSize = 25;
        int plusMinus = 20;

        g.setPaint(isDarkMode ? Color.WHITE : Color.BLACK);

        g.setFont(new Font("Arial", Font.BOLD, fontSize));
        g.drawString(number+"", topLeft.x-17, topLeft.y + 9);


        g.setFont(new Font("Arial", Font.BOLD, plusMinus));

        g.drawString("+", topLeft.x+width-48, topLeft.y-2);
        g.drawString("−", topLeft.x+width-48, topLeft.y+16);

        Polygon plus = new Polygon(new int[] {topLeft.x+width-100, topLeft.x+width+10, topLeft.x+width+10, topLeft.x+width-100, topLeft.x+width-100},
                new int[] {topLeft.y, topLeft.y, topLeft.y-60, topLeft.y-60, topLeft.y}, 4);
        Polygon minus = new Polygon(new int[] {topLeft.x+width-100, topLeft.x+width+10, topLeft.x+width+10, topLeft.x+width-100, topLeft.x+width-100},
                new int[] {topLeft.y, topLeft.y, topLeft.y+60, topLeft.y+60, topLeft.y}, 4);

        ActionListener increase = e -> {
            int bothWays = 0;
            try {
                bothWays = Integer.parseInt(_rr.getWay().getInterestingTags().get("lanes:both_ways"));
            } catch (Exception ignored) {}
            _rr._parent.forceUpdateIgnore(1);
            Utils.changeLaneCount(_rr.getWay(), dir, number+1, ((MarkedRoadRenderer) _rr)._backwardLanes.size(),
                    ((MarkedRoadRenderer) _rr)._forwardLanes.size(), bothWays);
            _rr._parent.updateOneRoad(_rr.getWay().getUniqueId());
            forDataChanges.dataChange();
        };
        ActionListener decrease = e -> {
            int bothWays = 0;
            try {
                bothWays = Integer.parseInt(_rr.getWay().getInterestingTags().get("lanes:both_ways"));
            } catch (Exception ignored) {}
            _rr._parent.forceUpdateIgnore(1);
            Utils.changeLaneCount(_rr.getWay(), dir, number-1, ((MarkedRoadRenderer) _rr)._backwardLanes.size(),
                    ((MarkedRoadRenderer) _rr)._forwardLanes.size(), bothWays);
            _rr._parent.updateOneRoad(_rr.getWay().getUniqueId());
            forDataChanges.dataChange();
        };

        _manager.addBox(increase, plus);
        _manager.addBox(decrease, minus);
    }
}

// This class is for a preset button, aka one of those buttons with a road diagram on it.
class PresetButton extends JPanel {
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

// This class is for the floating lane count changer.
// THIS IS NOT CURRENTLY USED, SINCE IT WAS EASIER TO JUST SET THE BUTTON AS AN IMAGE AND RECORD WHERE CLICKS HAPPENED
class LaneCountChanger extends JPanel {
    private Way _way;
    private int _lanes;
    private int _direction;
    private JLabel _number;
    private JPanel _rightSide;

    public LaneCountChanger(Way way, int lanes, int direction) {
        _way = way;
        _lanes = lanes;
        _direction = direction;

        setPreferredSize(new Dimension(80, 50));
        setBackground(getBackground().brighter());
        setLayout(new BorderLayout());

        _number = new JLabel("" + lanes);
        _number.setFont( new Font("Verdana", Font.BOLD, 30));
        _number.setPreferredSize(new Dimension(40, 50));
        add(_number, BorderLayout.CENTER);

        _rightSide = new JPanel();
        _rightSide.setLayout(new BorderLayout());
        _rightSide.add(new JLabel("+"), BorderLayout.BEFORE_FIRST_LINE);
        _rightSide.add(new JLabel("-"), BorderLayout.AFTER_LAST_LINE);
        add(_rightSide, BorderLayout.LINE_END);
    }
}

// This class if for keeping track of all of the click boxes associated with a JPanel.
class ClickAreaManager {
    private List<ActionListener> listeners;
    private List<Polygon> boxes;

    public ClickAreaManager() {
        boxes = new ArrayList<>();
        listeners = new ArrayList<>();
    }

    public void addBox(ActionListener l, Polygon p) {
        listeners.add(l);
        boxes.add(p);
    }

    public void submitEvent(MouseEvent e, String command) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(e.getPoint())) {
                listeners.get(i).actionPerformed(new ActionEvent(e.getSource(), e.getID(), command));
            }
        }
    }
}