package com.example.jjsmod;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** The JJS-style HUD: move icons with cooldowns, custom health bar, block meter, awakening meter. */
public class Hud {
    private static final Map<String, ResourceLocation> ICONS = new HashMap<>();

    private static ResourceLocation icon(String id) {
        return ICONS.computeIfAbsent(id,
                k -> ResourceLocation.fromNamespaceAndPath(JJSMod.MODID, "textures/gui/skills/" + k + ".png"));
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int fill, int back) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, back);
        int fw = (int) (w * Math.max(0f, Math.min(1f, frac)));
        if (fw > 0) g.fill(x, y, x + fw, y + h, fill);
    }

    public static void render(GuiGraphics g, DeltaTracker dt) {
        if (!ClientState.active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();

        // ---- move slots
        String[] slots = Kits.slots(ClientState.cls, ClientState.awakened);
        int n = 0;
        for (String s : slots) if (!s.isEmpty()) n++;
        int size = 42, gap = 5;
        int total = n * size + Math.max(0, n - 1) * gap;
        int x = (w - total) / 2;
        int y = h - size - 15;
        for (int i = 0; i < slots.length; i++) {
            String id = slots[i];
            if (id.isEmpty()) continue;
            int left = ClientState.left[i];
            int tot = Math.max(1, ClientState.total[i]);
            boolean ready = left <= 0;

            // Layered charcoal tile, violet edge light, and an inset icon frame.
            g.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xD9000000);
            g.fill(x - 1, y - 1, x + size + 1, y + size + 1, ready ? 0xFFB18CFF : 0xFF4B4658);
            g.fill(x, y, x + size, y + size, 0xFF171720);
            g.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xFF090A10);
            g.blit(icon(id), x + 3, y + 3, 36, 36, 0f, 0f, 64, 64, 64, 64);

            if (!ready) {
                int overlay = (int) (size * Math.min(1f, left / (float) tot));
                g.fill(x, y, x + size, y + overlay, 0xB8000000);
                String timer = left >= 200 ? Integer.toString((left + 19) / 20) : String.format("%.1f", left / 20f);
                g.drawCenteredString(font, timer, x + size / 2, y + size / 2 - 4, 0xFFFFFFFF);
            } else {
                g.fill(x + 3, y + size - 3, x + size - 3, y + size - 2, 0xFFB18CFF);
            }

            // Small keycap sits above the icon instead of obscuring its artwork.
            g.fill(x + 2, y + 2, x + 13, y + 13, 0xE6000000);
            g.drawCenteredString(font, Kits.KEYS[i], x + 7, y + 3, 0xFFFFFFFF);
            x += size + gap;
        }

        // ---- bars above the slots
        int bw = Math.max(total, 150);
        int bx = (w - bw) / 2;
        int hy = y - 18;
        float hpFrac = ClientState.maxHp <= 0 ? 0 : ClientState.hp / ClientState.maxHp;
        bar(g, bx, hy, bw, 9, hpFrac, hpFrac > 0.3f ? 0xFFD83A3A : 0xFFFF7A1A, 0xFF2A0A0A);
        g.drawCenteredString(font, (int) Math.ceil(ClientState.hp) + " / " + (int) ClientState.maxHp, w / 2, hy + 1, 0xFFFFFFFF);

        if (ClientState.guard < 99.9f || ClientState.blocking) {
            bar(g, bx, hy + 11, bw, 3, ClientState.guard / JState.MAX_GUARD,
                    ClientState.guardBroken ? 0xFFFF4040 : 0xFF58B8FF, 0xFF0A1A2A);
        }

        float aw = ClientState.awake / 100f;
        boolean ready = !ClientState.awakened && ClientState.awake >= 99.9f;
        int ay = hy - 9;
        bar(g, bx, ay, bw, 4, aw, ClientState.awakened ? 0xFFFFD84A : 0xFFB05CFF, 0xFF1A0A2A);
        if (ready) g.drawCenteredString(font, "G - AWAKEN", w / 2, ay - 10, 0xFFE9B8FF);
        else if (ClientState.awakened) g.drawCenteredString(font, "AWAKENED", w / 2, ay - 10, 0xFFFFD84A);

        // ---- control hints
        g.drawString(font, "M1 Attack   Q Dash   F Block", 6, h - 12, 0xB0FFFFFF, true);

        // ---- Black Flash cue (press the Divergent Fist key again now)
        if (ClientState.bfCue) {
            g.blit(icon("black_flash"), w / 2 - 24, h / 2 - 24, 48, 48, 0f, 0f, 64, 64, 64, 64);
        }
    }
}
