package com.gathin.tvhome;

import com.gathin.tvhome.quiet.MirroringPicker;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import static org.junit.Assert.*;

public class MirroringPickerTest {
    @Test public void firstAvailableCandidateWins() {
        int best = MirroringPicker.pickBest(MirroringPicker.CANDIDATES,
                new HashSet<>(Arrays.asList(
                        MirroringPicker.CANDIDATES.get(2),
                        MirroringPicker.CANDIDATES.get(0))));
        assertEquals(0, best);
    }
    @Test public void noCandidateFallsBackToSettings() {
        assertEquals(-1, MirroringPicker.pickBest(
                MirroringPicker.CANDIDATES, Collections.emptySet()));
        assertEquals("android.settings.SETTINGS", MirroringPicker.pickFallback(
                MirroringPicker.FALLBACK_ACTIONS, Collections.emptySet()));
    }
    @Test public void mostSpecificFallbackWins() {
        assertEquals("android.settings.CAST_SETTINGS", MirroringPicker.pickFallback(
                MirroringPicker.FALLBACK_ACTIONS,
                new HashSet<>(Arrays.asList("android.settings.SETTINGS", "android.settings.CAST_SETTINGS"))));
    }
}
