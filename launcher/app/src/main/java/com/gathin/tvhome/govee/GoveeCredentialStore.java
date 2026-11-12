package com.gathin.tvhome.govee;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * API-key storage backed by Android KeyStore AES/GCM (no extra dependencies).
 * The key is held encrypted in private SharedPreferences; a transient in-memory
 * copy avoids repeated KeyStore ops. Nothing is ever logged.
 */
public final class GoveeCredentialStore {
    private static final String PREFS = "quiet_govee";
    private static final String KEY_BLOB = "api_key_enc";
    private static final String KEY_IV = "api_key_iv";
    private static final String ALIAS = "quiet-govee-v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private String cache;

    public synchronized boolean has(Context c) {
        if (cache != null) return !cache.isEmpty();
        String blob = prefs(c).getString(KEY_BLOB, null);
        return blob != null && !blob.isEmpty();
    }

    public synchronized String get(Context c) {
        if (cache != null) return cache;
        try {
            SharedPreferences p = prefs(c);
            String blob = p.getString(KEY_BLOB, null);
            String iv = p.getString(KEY_IV, null);
            if (blob == null || iv == null) return "";
            SecretKey key = key();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT)));
            byte[] plain = cipher.doFinal(Base64.decode(blob, Base64.DEFAULT));
            cache = new String(plain, StandardCharsets.UTF_8);
            return cache;
        } catch (Exception e) {
            return "";
        }
    }

    public synchronized void set(Context c, String apiKey) throws Exception {
        String value = apiKey == null ? "" : apiKey.trim();
        if (value.isEmpty()) {
            clear(c);
            return;
        }
        SecretKey key = key();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] enc = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        prefs(c).edit()
                .putString(KEY_BLOB, Base64.encodeToString(enc, Base64.DEFAULT))
                .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.DEFAULT))
                .apply();
        cache = value;
    }

    public synchronized void clear(Context c) {
        cache = null;
        prefs(c).edit().remove(KEY_BLOB).remove(KEY_IV).apply();
    }

    private SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return (SecretKey) ks.getKey(ALIAS, null);
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true);
        if (Build.VERSION.SDK_INT >= 28) b.setIsStrongBoxBacked(false);
        gen.init(b.build());
        return gen.generateKey();
    }
}
