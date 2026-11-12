package com.example.jjsmod;

import java.util.Map;

/** Move layout. Slots: 0-3 = keys 1-4, 4 = R (special), 5 = X (domain). */
public class Kits {
    public static final String HONORED = "honored";
    public static final String VESSEL = "vessel";
    public static final String TOJI = "toji";
    public static final String[] KEYS = {"1", "2", "3", "4", "R", "X"};

    public static String[] slots(String cls, boolean awakened) {
        String[] s;
        if (HONORED.equals(cls)) {
            s = awakened
                    ? new String[]{"lapse_blue_max", "reversal_red_max", "hollow_purple", "infinite_void", "limitless", ""}
                    : new String[]{"lapse_blue", "reversal_red", "rapid_punches", "twofold_kick", "limitless", ""};
        } else if (VESSEL.equals(cls)) {
            s = awakened
                    ? new String[]{"cleave", "dismantle", "open", "rush", "combat_instincts", "malevolent_shrine"}
                    : new String[]{"cursed_strikes", "crushing_blow", "divergent_fist", "manji_kick", "combat_instincts", ""};
        } else if (TOJI.equals(cls)) {
            s = tojiSlots(0);
        } else {
            s = new String[]{"", "", "", "", "", ""};
        }
        return s;
    }

    public static String[] tojiSlots(int weapon) {
        return switch (weapon) {
            case 1 -> new String[]{"", "transcend", "rupture", "", "hidden_inventory", ""};
            case 2 -> new String[]{"", "lasso", "whiplash", "", "hidden_inventory", ""};
            default -> new String[]{"", "flourish", "debilitate", "", "hidden_inventory", ""};
        };
    }

    // cooldowns in seconds (from the JJS fandom wiki figures)
    private static final Map<String, Integer> CD = Map.ofEntries(
            Map.entry("lapse_blue", 13), Map.entry("reversal_red", 20), Map.entry("rapid_punches", 15),
            Map.entry("twofold_kick", 18), Map.entry("limitless", 15),
            Map.entry("lapse_blue_max", 17), Map.entry("reversal_red_max", 10), Map.entry("hollow_purple", 40),
            Map.entry("infinite_void", 120),
            Map.entry("cursed_strikes", 14), Map.entry("crushing_blow", 15), Map.entry("divergent_fist", 18),
            Map.entry("manji_kick", 20), Map.entry("combat_instincts", 2),
            Map.entry("cleave", 12), Map.entry("dismantle", 13), Map.entry("open", 40),
            Map.entry("rush", 15), Map.entry("malevolent_shrine", 120),
            Map.entry("flourish", 16), Map.entry("debilitate", 15), Map.entry("transcend", 16),
            Map.entry("rupture", 15), Map.entry("lasso", 16), Map.entry("whiplash", 15),
            Map.entry("hidden_inventory", 5), Map.entry("the_wrath", 15), Map.entry("the_greed", 15),
            Map.entry("the_pride", 15), Map.entry("ascend", 14), Map.entry("paradise", 14),
            Map.entry("reincarnation", 14), Map.entry("twirling_strike", 15));

    public static int cd(String id) {
        return CD.getOrDefault(id, 10) * 20;
    }
}
