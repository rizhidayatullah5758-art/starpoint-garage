package com.starpointgarage.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public final class AppSession {
    private static final String PREF = "starpoint_native_session";
    private AppSession() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context c, String type, String access, String refresh, JSONObject profile) {
        p(c).edit()
                .putString("type", type)
                .putString("access", access)
                .putString("refresh", refresh)
                .putString("profile", profile == null ? "{}" : profile.toString())
                .apply();
    }

    public static String type(Context c) { return p(c).getString("type", ""); }
    public static String access(Context c) { return p(c).getString("access", ""); }
    public static String refresh(Context c) { return p(c).getString("refresh", ""); }

    public static JSONObject profile(Context c) {
        try { return new JSONObject(p(c).getString("profile", "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    public static void updateProfile(Context c, JSONObject profile) {
        p(c).edit().putString("profile", profile == null ? "{}" : profile.toString()).apply();
    }

    public static String userId(Context c) {
        String token = access(c);
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "";
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JSONObject payload = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
            return payload.optString("sub", "");
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean exists(Context c) {
        return !access(c).isEmpty() && !type(c).isEmpty();
    }

    public static void clear(Context c) {
        p(c).edit().clear().apply();
    }
}
