package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class QuietMachineModelTest {
    @Test public void photographsBoundLandscapeAndPortraitAllocations() {
        assertEquals(4, QuietMachineModel.sampleSize(4096, 2160));
        assertEquals(8, QuietMachineModel.sampleSize(800, 8192));
        assertEquals(1, QuietMachineModel.sampleSize(640, 480));
        assertEquals(1, QuietMachineModel.sampleSize(-1, -1));
    }
    @Test public void greetingsChangeAtLocalDayBoundaries() {
        assertEquals("Good night", QuietMachineModel.greeting(0));
        assertEquals("Good night", QuietMachineModel.greeting(4));
        assertEquals("Good morning", QuietMachineModel.greeting(5));
        assertEquals("Good afternoon", QuietMachineModel.greeting(12));
        assertEquals("Good evening", QuietMachineModel.greeting(17));
        assertEquals("Good night", QuietMachineModel.greeting(22));
    }
    @Test public void appSearchIsCaseInsensitiveAndDoesNotInventCatalogs() {
        assertTrue(QuietMachineModel.matches("Spotify", "com.spotify.tv", " SPOT ", "Music"));
        assertFalse(QuietMachineModel.matches("Netflix", "com.netflix.tv", "", "Music"));
        assertFalse(QuietMachineModel.matches("Netflix", "com.netflix.tv", "", "Movies"));
        assertTrue(QuietMachineModel.matches("Netflix", "com.netflix.tv", "netflix", "All"));
        assertFalse(QuietMachineModel.matches("Netflix", "com.netflix.tv", "missing", "All"));
    }
    @Test public void offRestoresSystemBrightness() {
        assertEquals(-1f, QuietMachineModel.brightness("Off"), 0f);
        assertEquals(-1f, QuietMachineModel.brightness("unknown"), 0f);
        assertTrue(QuietMachineModel.brightness("Night") < QuietMachineModel.brightness("Evening"));
        assertEquals(1f, QuietMachineModel.brightness("Day"), 0f);
    }
    @Test public void focusDimsOnlyInEveningAndNight() {
        assertEquals(0, QuietMachineModel.dim("Off"));
        assertEquals(0, QuietMachineModel.dim("Day"));
        assertTrue(Integer.compareUnsigned(QuietMachineModel.dim("Night"), QuietMachineModel.dim("Evening")) > 0);
    }
    @Test public void onlyAllAndMusicHaveACatalog() {
        assertTrue(QuietMachineModel.catalogAvailable("All"));
        assertTrue(QuietMachineModel.catalogAvailable("Music"));
        assertFalse(QuietMachineModel.catalogAvailable("Movies"));
        assertFalse(QuietMachineModel.catalogAvailable("Series"));
        assertFalse(QuietMachineModel.catalogAvailable("Documentaries"));
    }
    @Test public void recentPutsOpenedAppsFirstThenNames() {
        java.util.Map<String, Long> opened = new java.util.HashMap<>();
        opened.put("Netflix", 100L);
        opened.put("YouTube", 300L);
        java.util.List<String> apps = new java.util.ArrayList<>(java.util.Arrays.asList("spotify", "Netflix", "YouTube", "Arte"));
        apps.sort(QuietMachineModel.order(true, a -> opened.getOrDefault(a, 0L), a -> a));
        assertEquals(java.util.Arrays.asList("YouTube", "Netflix", "Arte", "spotify"), apps);
        apps.sort(QuietMachineModel.order(false, a -> opened.getOrDefault(a, 0L), a -> a));
        assertEquals(java.util.Arrays.asList("Arte", "Netflix", "spotify", "YouTube"), apps);
    }
    @Test public void resolutionNamesTheShortSide() {
        assertEquals("2160p", QuietMachineModel.resolution(3840, 2160));
        assertEquals("1080p", QuietMachineModel.resolution(1920, 1080));
        assertEquals("720p", QuietMachineModel.resolution(720, 1280));
        assertEquals("Unknown", QuietMachineModel.resolution(0, 0));
    }
    @Test public void volumeStepsStayInRange() {
        assertEquals(0, QuietMachineModel.step(0, -1, 100));
        assertEquals(100, QuietMachineModel.step(100, 1, 100));
        assertEquals(36, QuietMachineModel.step(35, 1, 100));
    }
}
