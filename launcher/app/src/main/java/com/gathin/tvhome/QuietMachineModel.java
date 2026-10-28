package com.gathin.tvhome;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToLongFunction;

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
    /** Launcher-only dimming laid over the surface, so a mode shows even where firmware ignores brightness. */
    static int dim(String mode) {
        return "Night".equals(mode) ? 0x26000000 : "Evening".equals(mode) ? 0x12000000 : 0;
    }
    static boolean matches(String label, String pkg, String query, String category) {
        String value = (label + " " + pkg).toLowerCase(Locale.ROOT);
        if (!value.contains(query.trim().toLowerCase(Locale.ROOT))) return false;
        // A music-app filter, never a claim to expose a streaming music catalog.
        return "All".equals(category) || "Music".equals(category)
                && (value.contains("music") || value.contains("spotify") || value.contains("tidal") || value.contains("deezer"));
    }
    /** Movies, Series and Documentaries need a content catalog this launcher does not have. */
    static boolean catalogAvailable(String category) {
        return "All".equals(category) || "Music".equals(category);
    }
    /** Recent: apps Home opened most recently first, never-opened apps after them, each group A to Z. */
    static <T> Comparator<T> order(boolean recent, ToLongFunction<T> lastOpened, Function<T, String> label) {
        Comparator<T> byName = Comparator.comparing(t -> label.apply(t).toLowerCase(Locale.ROOT));
        if (!recent) return byName;
        return Comparator.<T>comparingLong(t -> -lastOpened.applyAsLong(t)).thenComparing(byName);
    }
    /** The output's short side as a TV calls it: 3840 x 2160 is 2160p. */
    static String resolution(int width, int height) {
        int lines = Math.min(width, height);
        return lines <= 0 ? "Unknown" : lines + "p";
    }
    static int step(int value, int delta, int max) {
        return Math.max(0, Math.min(max, value + delta));
    }
}
