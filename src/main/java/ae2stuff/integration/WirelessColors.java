/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.integration;

import net.minecraft.util.text.TextFormatting;

import appeng.api.util.AEColor;

/**
 * The chat colour nearest each network colour, so a device's colour is read rather than spelled out.
 */
public final class WirelessColors {

    private WirelessColors() {
    }

    public static TextFormatting of(final AEColor color) {
        return switch (color) {
            case WHITE -> TextFormatting.WHITE;
            case ORANGE -> TextFormatting.GOLD;
            case MAGENTA -> TextFormatting.LIGHT_PURPLE;
            case LIGHT_BLUE -> TextFormatting.AQUA;
            case YELLOW -> TextFormatting.YELLOW;
            case LIME -> TextFormatting.GREEN;
            case PINK -> TextFormatting.RED;
            case GRAY -> TextFormatting.DARK_GRAY;
            case LIGHT_GRAY -> TextFormatting.GRAY;
            case CYAN -> TextFormatting.DARK_AQUA;
            case PURPLE -> TextFormatting.DARK_PURPLE;
            case BLUE -> TextFormatting.BLUE;
            case BROWN, RED -> TextFormatting.DARK_RED;
            case GREEN -> TextFormatting.DARK_GREEN;
            case BLACK -> TextFormatting.BLACK;
            default -> TextFormatting.WHITE;
        };
    }
}
