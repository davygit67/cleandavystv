package com.gathin.tvhome;

import com.gathin.tvhome.govee.GoveePayloads;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoveePayloadsTest {
    @Test public void endpointsAndHeaderAreDocumented() {
        assertEquals("https://openapi.api.govee.com", GoveePayloads.BASE);
        assertEquals("/router/api/v1/user/devices", GoveePayloads.PATH_DEVICES);
        assertEquals("/router/api/v1/device/control", GoveePayloads.PATH_CONTROL);
        assertEquals("Govee-API-Key", GoveePayloads.HEADER_KEY);
    }
    @Test public void brightnessIsClamped() {
        assertEquals(1, GoveePayloads.clampBrightness(0));
        assertEquals(100, GoveePayloads.clampBrightness(150));
        assertEquals(60, GoveePayloads.clampBrightness(60));
    }
    @Test public void rgbPacksChannels() {
        assertEquals(0xFFBE78, GoveePayloads.rgb(255, 190, 120));
    }
    @Test public void errorsNeverCarryTheKey() {
        String redacted = GoveePayloads.safe("401 with Govee-API-Key SECRET123");
        assertFalse(redacted.contains("SECRET123"));
        assertTrue(redacted.contains("[redacted]"));
    }
}
