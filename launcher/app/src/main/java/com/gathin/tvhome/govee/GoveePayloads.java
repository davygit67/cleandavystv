package com.gathin.tvhome.govee;

/**
 * Pure, JVM-testable Govee request builders. No Android imports so plain unit
 * tests can verify endpoint paths, header name and JSON shapes without the
 * Android stub. GoveeApi delegates to these shapes (via org.json on-device).
 */
public final class GoveePayloads {
    private GoveePayloads() {}

    public static final String BASE = "https://openapi.api.govee.com";
    public static final String PATH_DEVICES = "/router/api/v1/user/devices";
    public static final String PATH_STATE = "/router/api/v1/device/state";
    public static final String PATH_CONTROL = "/router/api/v1/device/control";
    public static final String PATH_SCENES = "/router/api/v1/device/scenes";
    public static final String HEADER_KEY = "Govee-API-Key";

    /** Redacts a message that might contain key material; never returns the key. */
    public static String safe(String message) {
        if (message == null) return "request failed";
        String m = message.length() > 160 ? message.substring(0, 160) : message;
        return m.replaceAll("(?i)(api[-_ ]?key[^a-z0-9]{0,8}).+", "$1 [redacted]");
    }

    public static int clampBrightness(int pct) {
        return Math.max(1, Math.min(100, pct));
    }

    public static int rgb(int r, int g, int b) {
        return ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    /** Minimal JSON string escaping for device-supplied names in test fixtures. */
    public static String quote(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
