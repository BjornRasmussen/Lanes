package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;

public class UtilsClicksAndPopups {

    // <editor-fold defaultstate=collapsed desc="Methods for Mouse Handling">

    public static boolean mouseEventIsInside(MouseEvent e, List<Polygon> outlines, MapView mv) {
        for (Polygon outline : outlines) if (outline.contains(e.getPoint())) return true;
        return false;
    }

    public static void displayPopup(JPanel content, MouseEvent e, MapView mv, Way way, LaneMappingMode l) {
        if (content == null) return;

        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
        mainPanel.add(content);

        JWindow w = new JWindow(MainApplication.getMainFrame());
        w.setAutoRequestFocus(false);
        w.setFocusableWindowState(false);
        w.add(mainPanel);
        w.pack();

        Point aboveMouse = new Point(e.getXOnScreen() - (mainPanel.getWidth() / 2), e.getYOnScreen() - mainPanel.getHeight() - 10);
        Point belowMouse = new Point(e.getXOnScreen() - (mainPanel.getWidth() / 2), e.getYOnScreen() + 10);
        boolean goAbove = e.getY() - 30 > mainPanel.getHeight();
        w.setLocation(goAbove ? aboveMouse : belowMouse);
        w.setVisible(true);
        long timeSetVisible = System.currentTimeMillis();
        // <editor-fold defaultstate=collapsed desc="Things that close the Window">

        // * Map moved / zoom changed
        mv.addRepaintListener(new MapView.RepaintListener() {
            double scale = mv.getScale();
            EastNorth center = mv.getCenter();

            @Override
            public void repaint(long tm, int x, int y, int width, int height) {
                // This runs when something has changed.  Check if scale or map position have changed.
                if (Math.abs(mv.getScale() - scale) > 0.001 || Math.abs(mv.getCenter().getX() - center.getX()) > 0.001 ||
                        Math.abs(mv.getCenter().getY() - center.getY()) > 0.001) {
                    scale = mv.getScale();
                    center = mv.getCenter();
                    unClick(w, mv, timeSetVisible);
                }
            }
        });

        // * Mouse pressed down somewhere on the map outside of the window (just map clicks - editing the tags of selected way won't close it)
        mv.addMouseListener(new MouseListener() {
            @Override
            public void mousePressed(MouseEvent e) {
                unClick(w, mv, timeSetVisible);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        });

        // * Way corresponding to Window no longer exists.
        UndoRedoHandler.getInstance().addCommandQueuePreciseListener(new UndoRedoHandler.CommandQueuePreciseListener() {
            @Override
            public void commandAdded(UndoRedoHandler.CommandAddedEvent e) {
                verifyExistence();
            }

            @Override
            public void cleaned(UndoRedoHandler.CommandQueueCleanedEvent e) {
                verifyExistence();
            }

            @Override
            public void commandUndone(UndoRedoHandler.CommandUndoneEvent e) {
                verifyExistence();
            }

            @Override
            public void commandRedone(UndoRedoHandler.CommandRedoneEvent e) {
                verifyExistence();
            }

            private void verifyExistence() {
                if (way.isDeleted()) unClick(w, mv, timeSetVisible);
            }
        });

        // * Map Mode Changes
        l.addQuitListener(e1 -> unClick(w, mv, timeSetVisible));

        // </editor-fold>
    }

    private static void unClick(Window w, MapView mv, long timeSetVisible) {
        int wait = 200;
        if (timeSetVisible + wait > System.currentTimeMillis()) {
            try {
                Thread.sleep(Math.max(wait - (System.currentTimeMillis()-timeSetVisible), 10));
            } catch (InterruptedException ignored) {}
        }
        w.setVisible(false);
        mv.repaint();
    }

    // </editor-fold>

}
