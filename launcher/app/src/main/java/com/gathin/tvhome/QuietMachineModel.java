package com.gathin.tvhome;

import java.util.Locale;

/** Pure policies shared by the TV view and regression tests. */
final class QuietMachineModel {
    private QuietMachineModel() { }
    /** Bound both dimensions: a portrait theme must not allocate a very tall panorama. */
    static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / sample > 1024 || height / sample > 1024) sample *= 2;
        return sample;
    }
    static String greeting(int hour) {
        return hour >= 5 && hour < 12 ? "Good morning" : hour < 17 && hour >= 12 ? "Good afternoon"
                : hour >= 17 && hour < 22 ? "Good evening" : "Good night";
    }
    static float brightness(String mode) {
        return "Day".equals(mode) ? 1f : "Evening".equals(mode) ? .65f : "Night".equals(mode) ? .35f : -1f;
    }
    static boolean matches(String label, String pkg, String query, String category) {
        String value = (label + " " + pkg).toLowerCase(Locale.ROOT);
        if (!value.contains(query.trim().toLowerCase(Locale.ROOT))) return false;
        // A music-app filter, never a claim to expose a streaming music catalog.
        return "All".equals(category) || "Music".equals(category)
                && (value.contains("music") || value.contains("spotify") || value.contains("tidal") || value.contains("deezer"));
    }
}
