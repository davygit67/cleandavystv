package com.gathin.tvhome.govee;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Capability-aware device model. Only capabilities returned by the API are exposed. */
public final class GoveeModels {
    private GoveeModels() {}

    /** Capability keys as returned by GET /router/api/v1/user/devices (capabilities[].type). */
    public static final String CAP_POWER = "devices.capabilities.on_off";
    public static final String CAP_BRIGHTNESS = "devices.capabilities.range";
    public static final String CAP_COLOR_TEMP = "devices.capabilities.color_setting";
    public static final String CAP_COLOR_RGB = "devices.capabilities.color_setting";
    public static final String CAP_SCENE = "devices.capabilities.dynamic_scene";
    public static final String CAP_MUSIC = "devices.capabilities.music_setting";
    public static final String CAP_SEGMENT = "devices.capabilities.segment_color_setting";

    public static final class Device {
        public final String sku;
        public final String deviceId;
        public final String name;
        public final boolean online;
        /** Capability type strings reported by the API. */
        public final Set<String> capabilities;
        /** Exact (type, instance) pairs exposed by this particular device. */
        public final Set<String> instances;
        /** "type|instance" -> parameters JSON (min/max/options), if supplied. */
        public final Map<String, Object> fields;

        public Device(String sku, String deviceId, String name, boolean online,
                      Set<String> capabilities, Map<String, Object> fields,
                      Set<String> instances) {
            this.sku = sku == null ? "" : sku;
            this.deviceId = deviceId == null ? "" : deviceId;
            this.name = name == null ? deviceId : name;
            this.online = online;
            this.capabilities = capabilities == null
                    ? Collections.emptySet() : Collections.unmodifiableSet(
                            new java.util.HashSet<>(capabilities));
            this.instances = instances == null
                    ? Collections.emptySet() : Collections.unmodifiableSet(
                            new java.util.HashSet<>(instances));
            this.fields = fields == null
                    ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        public boolean supports(String capability) {
            return capabilities.contains(capability);
        }

        /** Require the exact advertised instance, not just its broader capability type.
         * Example: an RGB light is not necessarily color-temperature capable. */
        public boolean supports(String capability, String instance) {
            return instances.contains(capability + "|" + instance);
        }

        /** Heuristic: TV backlight SKUs commonly start with H61/H65/H66/H70 series; never trusted alone. */
        public boolean maybeTvBacklight() {
            String s = sku.toUpperCase(java.util.Locale.ROOT);
            return s.startsWith("H61") || s.startsWith("H65") || s.startsWith("H66")
                    || s.startsWith("H70") || s.contains("BACKLIGHT") || name.toLowerCase(java.util.Locale.ROOT).contains("tv");
        }
    }
}
