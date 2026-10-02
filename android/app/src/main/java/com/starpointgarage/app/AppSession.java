package com.starpointgarage.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public final class AppSession {
    private static final String PREF = "starpoint_internal_session";
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

    public static void updateTokens(Context c, String access, String refresh) {
        SharedPreferences.Editor e = p(c).edit().putString("access", access);
        if (refresh != null && !refresh.isEmpty()) e.putString("refresh", refresh);
        e.apply();
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

    private static JSONObject jwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return new JSONObject();
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            return new JSONObject(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static String userId(Context c) {
        return jwtPayload(access(c)).optString("sub", "");
    }

    public static long expiresAt(Context c) {
        return jwtPayload(access(c)).optLong("exp", 0L);
    }

    public static synchronized String ensureFresh(Context c) throws Exception {
        if (!exists(c)) throw new Exception("HTTP_401");
        long exp = expiresAt(c);
        long now = System.currentTimeMillis() / 1000L;
        if (exp == 0 || exp - now > 300) return access(c);
        String rt = refresh(c);
        if (rt.isEmpty()) throw new Exception("HTTP_401");
        JSONObject out = ApiClient.refreshSession(rt);
        String at = out.optString("access_token", "");
        String newRt = out.optString("refresh_token", rt);
        if (at.isEmpty()) throw new Exception("HTTP_401");
        updateTokens(c, at, newRt);
        return at;
    }

    public static boolean exists(Context c) {
        return !access(c).isEmpty() && !type(c).isEmpty();
    }

    public static void clear(Context c) {
        p(c).edit().clear().apply();
    }
}