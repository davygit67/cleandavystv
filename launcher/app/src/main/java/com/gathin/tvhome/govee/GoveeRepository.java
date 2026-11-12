package com.gathin.tvhome.govee;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Capability-aware transport: cloud API only. LAN control is intentionally not
 * attempted — the TV sits on a managed residential network where multicast
 * discovery is unreliable, and cloud operation must not depend on LAN.
 * A LanControl interface is reserved so a future per-model transport can plug in
 * without changing callers.
 */
public final class GoveeRepository {
    /** Reserved LAN transport hook (disabled by default; see class javadoc). */
    public interface LanControl {
        boolean available(GoveeModels.Device device);
    }

    private static final String PREFS = "quiet_govee_cache";
    private static final String KEY_NAMES = "device_names_v1";
    private static final long CACHE_TTL_MS = 15 * 60_1000L;

    private final GoveeApi api;
    private final GoveeCredentialStore store = new GoveeCredentialStore();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable pendingFlush = this::flushPending;
    /** Latest debounced request; only touched on the main thread. */
    private Pending pending;
    private List<GoveeModels.Device> cache = Collections.emptyList();
    private long cachedAt;

    /** One debounced request. The API key snapshot travels with its request, so a
     * key rotation between scheduling and sending can never leak one call's key
     * into another call's body. */
    private static final class Pending {
        String key;
        String sku, device, type, instance;
        JSONObject value;
        GoveeApi.Callback<Void> cb;
    }

    private final Executor net;

    public GoveeRepository(Executor net) {
        this.net = net;
        this.api = new GoveeApi(net);
    }

    /** Drops any pending debounced send and stops the background thread. Safe to call more than once. */
    public void shutdown() {
        main.removeCallbacks(pendingFlush);
        pending = null;
        if (net instanceof java.util.concurrent.ExecutorService) {
            ((java.util.concurrent.ExecutorService) net).shutdownNow();
        }
    }

    /** Production constructor with its own single background thread. */
    public GoveeRepository() {
        this(Executors.newSingleThreadExecutor());
    }

    public GoveeCredentialStore credentials() {
        return store;
    }

    public List<GoveeModels.Device> cached() {
        return cache;
    }

    public void refresh(Context c, GoveeApi.Callback<List<GoveeModels.Device>> cb) {
        String key = store.get(c);
        if (key.isEmpty()) {
            cb.err("API key not set — open Lights and add your Govee key.");
            return;
        }
        api.listDevicesAsync(key, new GoveeApi.Callback<List<GoveeModels.Device>>() {
            @Override public void ok(List<GoveeModels.Device> value) {
                cache = value == null ? Collections.emptyList() : new ArrayList<>(value);
                cachedAt = SystemClock.elapsedRealtime();
                persistNames(c, cache);
                main.post(() -> cb.ok(new ArrayList<>(cache)));
            }
            @Override public void err(String message) {
                // Serve a recent cache so a transient failure never blanks the UI.
                if (!cache.isEmpty()
                        && SystemClock.elapsedRealtime() - cachedAt < CACHE_TTL_MS) {
                    List<GoveeModels.Device> copy = new ArrayList<>(cache);
                    main.post(() -> cb.ok(copy));
                } else {
                    main.post(() -> cb.err(message));
                }
            }
        });
    }

    /**
     * Sends a control command. Must be called on the main thread.
     *
     * <p>Urgent commands (power toggles) send at once. Anything else is
     * debounced: rapid calls (e.g. brightness slider drags) replace the pending
     * request and restart a single {@link GoveeApi#CONTROL_FLOOR_MS} timer, so
     * exactly one POST — the latest values — goes out per burst. Superseded
     * requests are dropped silently; only the latest callback fires.
     */
    public void control(Context c, GoveeModels.Device d, String type, String instance,
                        JSONObject value, boolean urgent, GoveeApi.Callback<Void> cb) {
        String key = store.get(c);
        if (key.isEmpty()) {
            cb.err("API key not set.");
            return;
        }
        if (urgent) {
            api.controlAsync(key, GoveeApi.controlBody(d.sku, d.deviceId, type, instance, value),
                    onMain(cb));
            return;
        }
        Pending p = new Pending();
        p.key = key;
        p.sku = d.sku;
        p.device = d.deviceId;
        p.type = type;
        p.instance = instance;
        p.value = value;
        p.cb = onMain(cb);
        pending = p;
        main.removeCallbacks(pendingFlush);
        main.postDelayed(pendingFlush, GoveeApi.CONTROL_FLOOR_MS);
    }

    /** The debounced path of {@link #control}: identical, without the urgent flag. */
    public void controlDebounced(Context c, GoveeModels.Device d, String type, String instance,
                                 JSONObject value, GoveeApi.Callback<Void> cb) {
        control(c, d, type, instance, value, false, cb);
    }

    /** Sends the latest pending request, if any, with the key it was scheduled with. */
    private void flushPending() {
        Pending p = pending;
        pending = null;
        if (p == null) return;
        api.controlAsync(p.key, GoveeApi.controlBody(p.sku, p.device, p.type, p.instance, p.value), p.cb);
    }

    private GoveeApi.Callback<Void> onMain(GoveeApi.Callback<Void> cb) {
        return new GoveeApi.Callback<Void>() {
            @Override public void ok(Void v) {
                main.post(() -> cb.ok(null));
            }
            @Override public void err(String m) {
                main.post(() -> cb.err(m));
            }
        };
    }

    private void persistNames(Context c, List<GoveeModels.Device> devices) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONObject o = new JSONObject();
            for (GoveeModels.Device d : devices) o.put(d.deviceId, d.name);
            p.edit().putString(KEY_NAMES, o.toString()).apply();
        } catch (Exception ignored) {
            // Cache is best-effort; never fail discovery for it.
        }
    }
}
