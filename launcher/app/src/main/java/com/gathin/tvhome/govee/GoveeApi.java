package com.gathin.tvhome.govee;

import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Minimal Govee cloud client over HttpURLConnection (no new dependencies, keeps
 * the APK under the size gate). All calls are blocking and must run off the UI
 * thread (see GoveeRepository). Respects rate limits with a client-side floor
 * between control calls; debouncing lives in GoveeRepository.
 *
 * Docs: https://developer.govee.com — Govee-API-Key header, POST bodies per the
 * capability schema. Errors never include the key.
 */
public final class GoveeApi {
    public static final String BASE = "https://openapi.api.govee.com";
    public static final String PATH_DEVICES = "/router/api/v1/user/devices";
    public static final String PATH_STATE = "/router/api/v1/device/state";
    public static final String PATH_CONTROL = "/router/api/v1/device/control";
    public static final String PATH_SCENES = "/router/api/v1/device/scenes";

    /** Floor between control POSTs (ms). Govee rate-limits; callers additionally debounce sliders. */
    public static final long CONTROL_FLOOR_MS = 1000L;

    private long lastControlAt;
    private final Executor net;

    public GoveeApi(Executor net) {
        this.net = net;
    }

    public interface Callback<T> {
        void ok(T value);
        void err(String message);
    }

    // ---- async wrappers (background thread) ----

    public void listDevicesAsync(final String apiKey, final Callback<List<GoveeModels.Device>> cb) {
        net.execute(() -> {
            try {
                cb.ok(listDevices(apiKey));
            } catch (Exception e) {
                cb.err(safe(e));
            }
        });
    }

    public void controlAsync(final String apiKey, final JSONObject body, final Callback<Void> cb) {
        net.execute(() -> {
            try {
                throttle();
                post(apiKey, PATH_CONTROL, body);
                cb.ok(null);
            } catch (Exception e) {
                cb.err(safe(e));
            }
        });
    }

    // ---- blocking primitives ----

    List<GoveeModels.Device> listDevices(String apiKey) throws Exception {
        JSONObject root = get(apiKey, PATH_DEVICES);
        List<GoveeModels.Device> out = new ArrayList<>();
        // The current Govee v2 devices endpoint returns data as an ARRAY,
        // not { devices: [...] }. Accept the wrapped shape as a compatibility fallback.
        JSONArray devices = root.optJSONArray("data");
        if (devices == null) {
            JSONObject wrapped = root.optJSONObject("data");
            devices = wrapped == null ? null : wrapped.optJSONArray("devices");
        }
        if (devices == null) throw new Exception("Unexpected Govee device-list response");
        for (int i = 0; i < devices.length(); i++) {
            JSONObject d = devices.optJSONObject(i);
            if (d == null) continue;
            String sku = d.optString("sku", "");
            String device = d.optString("device", "");
            String name = d.optString("deviceName", device);
            // Device-list responses do not always contain live online state.
            // Unknown status must not suppress otherwise supported controls.
            boolean online = !d.has("online") || d.optBoolean("online", true);
            Set<String> caps = new HashSet<>();
            Set<String> instances = new HashSet<>();
            Map<String, Object> fields = new HashMap<>();
            JSONArray list = d.optJSONArray("capabilities");
            if (list != null) {
                for (int j =  0; j < list.length(); j++) {
                    JSONObject c = list.optJSONObject(j);
                    if (c == null) continue;
                    String type = c.optString("type", "");
                    String instance = c.optString("instance", "");
                    if (type.isEmpty() || instance.isEmpty()) continue;
                    caps.add(type);
                    String id = type + "|" + instance;
                    instances.add(id);
                    JSONObject parameters = c.optJSONObject("parameters");
                    if (parameters != null) fields.put(id, parameters.toString());
                }
            }
            if (!device.isEmpty()) out.add(new GoveeModels.Device(
                    sku, device, name, online, caps, fields, instances));
        }
        return out;
    }

    // ---- request builders (pure, unit-tested) ----

    public static JSONObject controlBody(String sku, String device, String type,
                                         String capability, Object value) {
        try {
            JSONObject body = new JSONObject();
            body.put("requestId", java.util.UUID.randomUUID().toString());
            body.put("payload", new JSONObject()
                    .put("sku", sku)
                    .put("device", device)
                    .put("capability", new JSONObject()
                            .put("type", type)
                            .put("instance", capability)
                            .put("value", value == null ? JSONObject.NULL : value)));
            return body;
        } catch (org.json.JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /** The v2 API expects a primitive integer for these instances, NOT a JSON object. */
    public static int onOffValue(boolean on) {
        return on ? 1 : 0;
    }

    public static int brightnessValue(int pct) {
        return GoveePayloads.clampBrightness(pct);
    }

    public static int colorTempValue(int kelvin) {
        return kelvin;
    }

    public static int rgbValue(int r, int g, int b) {
        return GoveePayloads.rgb(r, g, b);
    }

    // ---- http ----

    private JSONObject get(String apiKey, String path) throws Exception {
        HttpURLConnection c = open(apiKey, "GET", path);
        try {
            return read(c);
        } finally {
            c.disconnect();
        }
    }

    private JSONObject post(String apiKey, String path, JSONObject body) throws Exception {
        HttpURLConnection c = open(apiKey, "POST", path);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = c.getOutputStream()) {
            os.write(bytes);
        }
        try {
            return read(c);
        } finally {
            c.disconnect();
        }
    }

    private HttpURLConnection open(String apiKey, String method, String path) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) throw new Exception("API key not set");
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Govee-API-Key", apiKey);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    private JSONObject read(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
        }
        JSONObject json;
        try {
            json = new JSONObject(sb.length() == 0 ? "{}" : sb.toString());
        } catch (Exception parse) {
            throw new Exception("HTTP " + code + ": unreadable response");
        }
        int apiCode = json.optInt("code", code >= 200 && code < 300 ? 200 : code);
        if (apiCode != 200) {
            throw new Exception("code " + apiCode + ": " + json.optString("message", "request failed"));
        }
        return json;
    }

    private synchronized void throttle() {
        long now = SystemClock.elapsedRealtime();
        long wait = CONTROL_FLOOR_MS - (now - lastControlAt);
        if (wait > 0) SystemClock.sleep(wait);
        lastControlAt = SystemClock.elapsedRealtime();
    }

    /** Never include key material, tokens or device ids in error text. */
    static String safe(Exception e) {
        return GoveePayloads.safe(e == null ? null : e.getMessage());
    }
}
