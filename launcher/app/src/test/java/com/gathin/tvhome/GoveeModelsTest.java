package com.gathin.tvhome;

import com.gathin.tvhome.govee.GoveeModels;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class GoveeModelsTest {
    @Test public void instanceGatingDoesNotMistakeRgbForTemperature() {
        String color = GoveeModels.CAP_COLOR_RGB;
        Set<String> types = new HashSet<>(Collections.singletonList(color));
        Set<String> instances = new HashSet<>(Collections.singletonList(color + "|colorRgb"));
        GoveeModels.Device d = new GoveeModels.Device(
                "H605C", "test-device", "Room Light", true,
                types, Collections.emptyMap(), instances);
        assertTrue(d.supports(color, "colorRgb"));
        assertFalse(d.supports(color, "colorTemperatureK"));
        assertFalse(d.supports(GoveeModels.CAP_POWER, "powerSwitch"));
    }

    @Test public void exactDeviceInstanceIsRequired() {
        String type = GoveeModels.CAP_BRIGHTNESS;
        GoveeModels.Device d = new GoveeModels.Device(
                "H6", "test-device", "Test", true,
                new HashSet<>(Arrays.asList(type)), Collections.emptyMap(),
                new HashSet<>(Collections.singletonList(type + "|brightness")));
        assertTrue(d.supports(type, "brightness"));
        assertFalse(d.supports(type, "humidity"));
    }
}
