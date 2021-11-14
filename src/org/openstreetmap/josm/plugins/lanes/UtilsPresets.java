package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.ImageProvider;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.*;
import java.util.List;

public class UtilsPresets {

    // <editor-fold defaultstate="collapsed" desc="Constants">

    public final static String[] onewaypresets = new String[] {"mo1", "mo2", "mo3", "mo4"};
    public final static String[] twowaylefthandpresets = new String[] {"mt101w", "u2", "mt202w", "u1.5", "mt303w", "u1"};
    public final static String[] twowayrighthandpresets = new String[] {"mt101y", "u2", "mt202y", "u1.5", "mt303y", "u1"};
    public final static String[] twowayrighthandpresetsUSCA = new String[] {"mt101y", "u2", "mt202y", "u1.5", "mt303y", "u1", "mt111y", "mt212y"};

    public final static Image preset_u1 = ImageProvider.get("75pxpresets", "u1.png").getImage();
    public final static Image preset_u15 = ImageProvider.get("75pxpresets", "u1.5.png").getImage();
    public final static Image preset_u2 = ImageProvider.get("75pxpresets", "u2.png").getImage();

    public final static Image preset_mo1 = ImageProvider.get("75pxpresets", "mo1.png").getImage();
    public final static Image preset_mo2 = ImageProvider.get("75pxpresets", "mo2.png").getImage();
    public final static Image preset_mo3 = ImageProvider.get("75pxpresets", "mo3.png").getImage();
    public final static Image preset_mo4 = ImageProvider.get("75pxpresets", "mo4.png").getImage();

    public final static Image preset_mt101w = ImageProvider.get("75pxpresets", "mt101w.png").getImage();
    public final static Image preset_mt101y = ImageProvider.get("75pxpresets", "mt101y.png").getImage();
    public final static Image preset_mt202w = ImageProvider.get("75pxpresets", "mt202w.png").getImage();
    public final static Image preset_mt202y = ImageProvider.get("75pxpresets", "mt202y.png").getImage();
    public final static Image preset_mt303w = ImageProvider.get("75pxpresets", "mt303w.png").getImage();
    public final static Image preset_mt303y = ImageProvider.get("75pxpresets", "mt303y.png").getImage();

    public final static Image preset_mt111y = ImageProvider.get("75pxpresets", "mt111y.png").getImage();
    public final static Image preset_mt212y = ImageProvider.get("75pxpresets", "mt212y.png").getImage();

    // </editor-fold>


    public static List<Preset> getPresets(boolean rightHand, boolean centerKnown, boolean oneway) {
        String[] presetStrings;
        if (!oneway && centerKnown) {
            presetStrings = twowayrighthandpresetsUSCA;
        } else if (!oneway && rightHand) {
            presetStrings = twowayrighthandpresets;
        } else if (!oneway){
            presetStrings = twowaylefthandpresets;
        } else {
            presetStrings = onewaypresets;
        }
        List<Preset> output = new ArrayList<>();
        for (String s : presetStrings) {
            Preset add = stringToPreset(s);
            if (add != null) output.add(add);
        }
        return output;
    }

    private static List<String> parsePresets(String path) {
        try {
        File f = getFile(path);
        Scanner s = new Scanner(f);
            List<String> output = new ArrayList<>();
            String yay = "";
            while(s.hasNext()) {
                String next = s.nextLine();
                yay += next + "\n";
                if (next.charAt(0) == '#') continue;
                String add = next.split("#")[0].trim();
                if (add.length() > 0) output.add(add);
            }
            if (true) {
                JOptionPane.showMessageDialog(MainApplication.getMainFrame(), yay);
            }
            return output;
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(), "File " + path + " not found.");
            return new ArrayList<>();
        }
    }

    private static File getFile(String path) throws FileNotFoundException {
        ClassLoader classLoader = Preset.class.getClassLoader();
        URL resource = classLoader.getResource(path);

        if (resource == null) {
            throw new FileNotFoundException("file was not found!");
        } else {
            return new File(resource.getFile());
        }
    }

    private static Preset stringToPreset(String s) {
        if (s.equals("mo1")) {
            return new Preset(1, 0, 0, preset_mo1);
        } else if (s.equals("mo2")) {
            return new Preset(2, 0, 0, preset_mo2);
        } else if (s.equals("mo3")) {
            return new Preset(3, 0, 0, preset_mo3);
        } else if (s.equals("mo4")) {
            return new Preset(4, 0, 0, preset_mo4);
        } else if (s.equals("mt101w")) {
            return new Preset(1, 1, 0, preset_mt101w);
        } else if (s.equals("mt101y")) {
            return new Preset(1, 1, 0, preset_mt101y);
        } else if (s.equals("mt202w")) {
            return new Preset(2, 2, 0, preset_mt202w);
        } else if (s.equals("mt202y")) {
            return new Preset(2, 2, 0, preset_mt202y);
        } else if (s.equals("mt303w")) {
            return new Preset(3, 3, 0, preset_mt303w);
        } else if (s.equals("mt303y")) {
            return new Preset(3, 3, 0, preset_mt303y);
        } else if (s.equals("u1")) {
            return new Preset(0, 0, 1, preset_u1);
        } else if (s.equals("u1.5")) {
            return new Preset(0, 0, 1.5, preset_u15);
        } else if (s.equals("u2")) {
            return new Preset(0, 0, 2, preset_u2);
        } else if (s.equals("mt111y")) {
            return new Preset(1, 1, 1, preset_mt111y);
        } else if (s.equals("mt212y")) {
            return new Preset(2, 2, 1, preset_mt212y);
        } else {
            return null;
        }
    }

    public static void applyPreset(Preset p, Way w, boolean undoPrevFirst) {
        if (undoPrevFirst) UndoRedoHandler.getInstance().undo();

        Collection<Command> cmds = new LinkedList<>();
        Map<String, String> keys = getPresetTagsApplied(p, w);

        cmds.add(new ChangePropertyCommand(Collections.singletonList(w), keys));

        Command c = new SequenceCommand("Apply road layout preset", cmds);
        UndoRedoHandler.getInstance().add(c);
    }

    private static Map<String, String> getPresetTagsApplied(Preset p, Way w) {
        Map<String, String> keys = w.getKeys();
        Map<String, String> output = new HashMap<>();
        if (p.getLanesBackward() == 0 && p.getLanesForward() == 0) {
            for (String key : keys.keySet()) {
                // Wipe all lane tags, since the road is now unmarked.
                if (key.contains(":lanes") && !key.contains("note")) {
                    output.put(key, "");
                }

                if (key.equals("lanes") || key.equals("lanes:forward") || key.equals("lanes:backward")) {
                    output.put(key, "");
                }
            }
            String lanesBothWays = p.getLanesBothWays() + "";
            if (lanesBothWays.endsWith(".0")) lanesBothWays = lanesBothWays.substring(0, lanesBothWays.length()-2);
            if (lanesBothWays.endsWith(".")) lanesBothWays = lanesBothWays.substring(0, lanesBothWays.length()-1);

            // Use either width or lanes depending on value.
            if (!lanesBothWays.contains(".")) {
                output.put("width", "");
                output.put("lanes", lanesBothWays);
                output.put("narrow", "");
            } else {
                output.put("width", (""+UtilsRender.WIDTH_LANES*1.5).substring(0, Math.min(4, (""+UtilsRender.WIDTH_LANES*1.5).length())));
                output.put("narrow", "yes");
            }
            output.put("lane_markings", "no");
        } else {
            if (p.getLanesBackward() != 0 || p.getLanesBothWays() != 0 || p.getLanesForward() != 1) output.put("lane_markings", "yes");
            output.put("width", "");
            output.put("narrow", "");
            output.putAll(setLanesInDirection(w, 1, p.getLanesForward()));
            output.putAll(setLanesInDirection(w, 0, p.getLanesBothWays() > 0.1 ? 1 : 0));
            output.putAll(setLanesInDirection(w, -1, p.getLanesBackward()));
            output.put("lanes", "" + (p.getLanesForward() + p.getLanesBackward() + (p.getLanesBothWays() > 0.01 ? 1 : 0)));
        }
        return output;
    }

    public static void changeLaneCount(Way w, int dir, int newCount, int existingBackward, int existingForward, int existingBothWays) {
        if (newCount == 0) return;
        Collection<Command> cmds = new LinkedList<>();
        Map<String, String> keys = setLanesInDirection(w, dir, newCount);

        int totalLanes = 0;
        try {
            if (keys.containsKey("lanes:forward")) {
                totalLanes += Integer.parseInt(keys.get("lanes:forward"));
            } else if (dir != 1) {
                totalLanes += existingForward;
                keys.put("lanes:forward", ""+(existingForward != 0 ? existingForward : ""));
            }
            if (keys.containsKey("lanes:backward")) {
                totalLanes += Integer.parseInt(keys.get("lanes:backward"));
            } else if (dir != -1) {
                totalLanes += existingBackward;
                keys.put("lanes:backward", ""+(existingBackward != 0 ? existingBackward : ""));
            }
            if (keys.containsKey("lanes:both_ways")) {
                totalLanes += Integer.parseInt(keys.get("lanes:both_ways"));
            } else if (dir != 0) {
                totalLanes += existingBothWays;
                keys.put("lanes:both_ways", ""+(existingBothWays != 0 ? existingBothWays : ""));
            }
        } catch (Exception ignored) {
            totalLanes = -1;
        }

        if (!UtilsGeneral.isOneway(w)) keys.put("lanes", "" + (totalLanes == -1 ? "" : totalLanes));

        cmds.add(new ChangePropertyCommand(Collections.singletonList(w), keys));
        Command c = new SequenceCommand("Change" + (dir == 1 ? " Forward" : dir == -1 ? " Backward" : "Both Ways")
                + " RoadPieceLane Count to " + newCount, cmds);
        UndoRedoHandler.getInstance().add(c);
    }

    private static Map<String, String> setLanesInDirection(Way w, int dir, int lanes) {
        Map<String, String> output = new HashMap<>();
        output.put(dir == 0 ? "lanes:both_ways" : dir == -1 ? "lanes:backward" : UtilsGeneral.isOneway(w) ? "lanes" : "lanes:forward", ""+(lanes != 0 ? lanes : ""));

        for (String key : w.getKeys().keySet()) {
            if (key.contains(":lanes") && !key.contains("note") && !key.contains("proposed")) {
                int thisDir = 1;
                if (key.contains(":both_ways")) thisDir = 0;
                if (key.contains(":backward")) thisDir = -1;
                if (thisDir != dir) continue;

                int numBars = numBars(w.getKeys().get(key));
                if (numBars+1 < lanes) {
                    int diff = lanes - numBars - 1;
                    output.put(key, w.getKeys().get(key));
                    for (int i = 0; i < diff; i++) output.put(key, output.get(key) + "|");
                } else if (numBars+1 > lanes) {
                    String[] pieces = w.getKeys().get(key).split("\\|");
                    StringBuilder newStr = new StringBuilder();
                    for (int i = 0; i < lanes; i++) {
                        if (i != 0) newStr.append("|");
                        newStr.append(i < pieces.length ? pieces[i] : "");
                    }
                    output.put(key, newStr.toString());
                }
            }
        }
        return output;
    }

    private static int numBars(String s) {
        int out = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '|') out++;
        return out;
    }
}
