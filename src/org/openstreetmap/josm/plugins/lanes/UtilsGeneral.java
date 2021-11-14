package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.RightAndLefthandTraffic;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class UtilsGeneral {
    // <editor-fold defaultstate=collapsed desc="Constants">

    public final static Map<String, String> isCenterYellow = getYML("resources/renderinginfo/isCenterYellow");
    public final static Map<String, String> shoulderLineColor = getYML("resources/renderinginfo/shoulderLineColor");
    public Map<String, String> isCenterTurnLaneKnown = getYML("resources/renderinginfo/isCenterTurnLaneKnown");

    // </editor-fold>

    public static boolean isRightHand(Way location) {
        return RightAndLefthandTraffic.isRightHandTraffic(location.getNode(0).getCoor());
    }

    public static double parseWidth(String value) {
        try {
            if (value == null || value.equals("")) {
                return 0;
            } else if (value.endsWith(" lane")) {
                return UtilsRender.WIDTH_LANES * Double.parseDouble(value.substring(0, value.length() - 5));
            } else if (value.endsWith(" lanes")) {
                return UtilsRender.WIDTH_LANES * Double.parseDouble(value.substring(0, value.length() - 6));
            } else if (value.endsWith(" m")) {
                return Double.parseDouble(value.substring(0, value.length() - 2));
            } else if (value.endsWith(" km")) {
                return 1000 * Double.parseDouble(value.substring(0, value.length() - 3));
            } else if (value.endsWith(" mi")) {
                return 1609.344 * Double.parseDouble(value.substring(0, value.length() - 3));
            } else if (value.endsWith("'")) {
                return 1 / 3.28084 * Double.parseDouble(value.substring(0, value.length() - 1));
            } else if (value.endsWith("\"") && !value.contains("'")) {
                return 1 / 3.28084 / 12 * Double.parseDouble(value.substring(0, value.length() - 1));
            } else if (value.endsWith("\"") && value.contains("'")) {
                String[] split = value.split("'");
                double feet = Double.parseDouble(split[0]);
                double inches = Double.parseDouble(split[1].substring(0, value.length() - 1));
                return 1 / 3.28084 * (feet + inches / 12);
            }
            return Double.parseDouble(value);
        } catch (Exception e) { return Double.NaN; }
    }

    private static Map<String, String> getYML(String path) {
        // This is a pretty basic parser that only supports very simple YML.
        try {
            Map<String, String> output = new HashMap<>();
            File f = new File(path);
            Scanner s = new Scanner(f);
            while (s.hasNext()) {
                String next = s.next();
                if (next.charAt(0) == '#') continue;
                output.put(next.split(":")[0].trim().toUpperCase(), next.split(":")[1].split("#")[0].trim());
            }
            return output;
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    public static boolean isOneway(Way w) {
        return w.isOneway() == 1 || w.hasTag("junction", "roundabout") || w.hasTag("junction", "circular");
    }

    public static boolean wayHasOneOfKeys(Way w, String... keys) {
        Map<String, String> tags = w.getInterestingTags();
        Iterator<String> iterator = Arrays.stream(keys).iterator();
        while (iterator.hasNext()) if (tags.containsKey(iterator.next())) return true;
        return false;
    }

    public static boolean wayHasTags(Way w, String key, String... values) {
        if (!w.hasTag(key)) return false;
        Iterator<String> iterator = Arrays.stream(values).iterator();
        while (iterator.hasNext()) if (w.hasTag(key, iterator.next())) return true;
        return false;
    }

}
