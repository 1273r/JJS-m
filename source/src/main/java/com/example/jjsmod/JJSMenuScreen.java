package com.example.jjsmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.Component;

/**
 * JJS-inspired pause overlay. Replaces the vanilla pause screen while a
 * moveset is active, while preserving normal Minecraft settings and controls.
 */
public final class JJSMenuScreen extends Screen {
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 286;

    public JJSMenuScreen() {
        super(Component.literal("Jujutsu Shenanigans"));
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int buttonWidth = 224;
        int buttonHeight = 24;
        int x = this.width / 2 - buttonWidth / 2;
        int y = top + 112;

        addRenderableWidget(Button.builder(Component.literal("RESUME"), button -> onClose())
                .bounds(x, y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), button ->
                        Minecraft.getInstance().setScreen(new OptionsScreen(this, Minecraft.getInstance().options)))
                .bounds(x, y + 32, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("CONTROLS"), button ->
                        Minecraft.getInstance().setScreen(new ControlsScreen(this, Minecraft.getInstance().options)))
                .bounds(x, y + 64, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("LEAVE GAME"), button ->
                        Minecraft.getInstance().disconnect(new TitleScreen()))
                .bounds(x, y + 96, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        // Dark Roblox-style pause backdrop and a crisp, layered central panel.
        graphics.fill(0, 0, this.width, this.height, 0xB9000000);
        graphics.fill(left - 3, top - 3, right + 3, bottom + 3, 0xFF08090D);
        graphics.fill(left, top, right, bottom, 0xFF171A22);
        graphics.fill(left, top, right, top + 3, 0xFFB18CFF);
        graphics.fill(left + 1, top + 44, right - 1, top + 45, 0xFF343846);

        graphics.drawCenteredString(this.font, "JUJUTSU SHENANIGANS", this.width / 2, top + 17, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, "MENU", this.width / 2, top + 58, 0xFFB18CFF);
        graphics.drawCenteredString(this.font, "Game paused", this.width / 2, top + 78, 0xFFB9BDCA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
