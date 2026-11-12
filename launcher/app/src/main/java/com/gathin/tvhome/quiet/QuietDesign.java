package com.gathin.tvhome.quiet;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/**
 * Quiet Machine 2.0 design tokens: glass, aluminum, slate.
 *
 * Goals: fewer boxes, soft continuous surfaces, selective frosted-glass and
 * brushed-aluminum treatments, muted charcoal/graphite/silver/slate tones with
 * one warm accent. Inexpensive techniques only: translucent fills, hairline
 * strokes and vertical gradients that suggest glass/metal without realtime blur
 * (too costly on a Fire TV GPU). All motion honours reduced-motion.
 */
public final class QuietDesign {
    private QuietDesign() {}

    /** Warm accent, retained sparingly (focus, active states). */
    public static final int ACCENT = 0xFFF36B21;
    public static final int ON_ACCENT = 0xFFFAF8F2;

    // Dark (default) slate.
    public static final int DARK_PAPER = 0xFF16161A;
    public static final int DARK_SURFACE = 0xE6222226; // translucent: glass over paper
    public static final int DARK_SURFACE_SOLID = 0xFF222226;
    public static final int DARK_INSET = 0xFF2A2A2E;
    public static final int DARK_INK = 0xFFECEAE2;
    public static final int DARK_MUTED = 0xFF9C9A90;
    public static final int DARK_LINE = 0xFF3A393F; // cooler hairline than before
    public static final int DARK_TRACK = 0xFF4B4A50;
    public static final int DARK_GREEN = 0xFF6FAE7D;

    // Light ivory / aluminum.
    public static final int LIGHT_PAPER = 0xFFF2F0E9;
    public static final int LIGHT_SURFACE = 0xE6EDEAE4;
    public static final int LIGHT_SURFACE_SOLID = 0xFFEDEAE4;
    public static final int LIGHT_INSET = 0xFFE2DFD5;
    public static final int LIGHT_INK = 0xFF222220;
    public static final int LIGHT_MUTED = 0xFF6C6A62;
    public static final int LIGHT_LINE = 0xFFC9C7BE;
    public static final int LIGHT_TRACK = 0xFFBCB9AF;
    public static final int LIGHT_GREEN = 0xFF5E9A6C;

    /** Continuous corner radii (dp). */
    public static final int RADIUS_SURFACE = 18;
    public static final int RADIUS_CARD = 14;
    public static final int RADIUS_PILL = 24;
    public static final int RADIUS_FOCUS = 16;

    /** Focus enlargement for D-pad visibility (subtle: 4%). */
    public static final float FOCUS_SCALE = 1.04f;
    /** Focused-tile spring is handled by MainActivity springs; this is the fallback duration. */
    public static final int FOCUS_MS = 140;
    public static final int FOCUS_MS_REDUCED = 60;

    /** Frosted-glass panel: translucent fill + hairline stroke + soft radius. No blur. */
    public static GradientDrawable glassPanel(Context c, int fill, int stroke) {
        float r = dp(c, RADIUS_CARD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(r);
        g.setStroke(Math.max(1, Math.round(c.getResources().getDisplayMetrics().density)), stroke);
        return g;
    }

    /**
     * Brushed-aluminum suggestion: a subtle vertical gradient (light top edge,
     * slightly darker base) drawn as a single GradientDrawable — one layer,
     * no overdraw beyond the view itself.
     */
    public static GradientDrawable aluminumPanel(Context c, int top, int bottom, int stroke) {
        float r = dp(c, RADIUS_CARD);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(r);
        g.setStroke(Math.max(1, Math.round(c.getResources().getDisplayMetrics().density)), stroke);
        return g;
    }

    /** Hairline divider colour for the active palette. */
    public static int line(boolean dark) {
        return dark ? DARK_LINE : LIGHT_LINE;
    }

    public static float dp(Context c, float n) {
        return n * c.getResources().getDisplayMetrics().density;
    }

    public static int dpInt(Context c, float n) {
        return Math.round(dp(c, n));
    }
}
