package com.starpointgarage.app;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    public static final String BASE = "https://msvdxyicostwztisvbvl.supabase.co";
    public static final String KEY = "sb_publishable_ihIG0dcPUHF9egWHaRlfvQ_E_Bn8zBk";

    private ApiClient() {}

    public static Object function(String slug, JSONObject body) throws Exception {
        return request("POST", BASE + "/functions/v1/" + slug,
                body.toString().getBytes(StandardCharsets.UTF_8), "application/json", null);
    }

    public static Object rpc(String name, JSONObject body, String token) throws Exception {
        return request("POST", BASE + "/rest/v1/rpc/" + name,
                body.toString().getBytes(StandardCharsets.UTF_8), "application/json", token);
    }

    public static JSONArray getArray(String pathAndQuery, String token) throws Exception {
        return asArray(request("GET", BASE + "/rest/v1/" + pathAndQuery, null, "application/json", token));
    }

    public static Object patch(String pathAndQuery, JSONObject body, String token) throws Exception {
        return request("PATCH", BASE + "/rest/v1/" + pathAndQuery,
                body.toString().getBytes(StandardCharsets.UTF_8), "application/json", token,
                "return=representation");
    }

    public static JSONObject refreshSession(String refreshToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("refresh_token", refreshToken);
        Object out = request("POST", BASE + "/auth/v1/token?grant_type=refresh_token",
                body.toString().getBytes(StandardCharsets.UTF_8), "application/json", null);
        return asObject(out);
    }

    public static String createSignedUrl(String bucket, String path, int expiresIn, String token) throws Exception {
        JSONObject body = new JSONObject();
        body.put("expiresIn", expiresIn);
        String safePath = Uri.encode(path == null ? "" : path, "/");
        Object out = request("POST", BASE + "/storage/v1/object/sign/" + bucket + "/" + safePath,
                body.toString().getBytes(StandardCharsets.UTF_8), "application/json", token);
        JSONObject o = asObject(out);
        String signed = o.optString("signedURL", o.optString("signedUrl", ""));
        if (signed.isEmpty()) throw new Exception("SIGNED_URL_FAILED");
        if (signed.startsWith("http")) return signed;
        if (signed.startsWith("/storage/v1")) return BASE + signed;
        if (signed.startsWith("/object/")) return BASE + "/storage/v1" + signed;
        return BASE + "/storage/v1/" + signed;
    }

    public static byte[] downloadBytes(String absoluteUrl, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(absoluteUrl).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("apikey", KEY);
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        byte[] bytes = stream == null ? new byte[0] : readAll(stream);
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP_" + code);
        return bytes;
    }

    public static JSONObject asObject(Object out) {
        if (out instanceof JSONObject) return (JSONObject) out;
        if (out instanceof JSONArray && ((JSONArray) out).length() > 0) {
            JSONObject o = ((JSONArray) out).optJSONObject(0);
            return o == null ? new JSONObject() : o;
        }
        return new JSONObject();
    }

    public static JSONArray asArray(Object out) {
        if (out instanceof JSONArray) return (JSONArray) out;
        JSONArray a = new JSONArray();
        if (out instanceof JSONObject) a.put(out);
        return a;
    }

    public static String q(String value) {
        return Uri.encode(value == null ? "" : value);
    }

    public static void upload(String bucket, String path, byte[] data, String mime, String token) throws Exception {
        requestRaw("POST", BASE + "/storage/v1/object/" + bucket + "/" + path, data,
                mime == null ? "application/octet-stream" : mime, token, null);
    }

    public static byte[] readAll(InputStream in) throws Exception {
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = source.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static Object request(String method, String url, byte[] body, String contentType, String token) throws Exception {
        return request(method, url, body, contentType, token, null);
    }

    private static Object request(String method, String url, byte[] body, String contentType, String token, String prefer) throws Exception {
        String raw = requestRaw(method, url, body, contentType, token, prefer);
        if (raw == null || raw.trim().isEmpty()) return new JSONObject();
        Object parsed = new JSONTokener(raw).nextValue();
        return parsed == null ? new JSONObject() : parsed;
    }

    private static String requestRaw(String method, String url, byte[] body, String contentType, String token, String prefer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("apikey", KEY);
        c.setRequestProperty("Accept", "application/json");
        if (prefer != null) c.setRequestProperty("Prefer", prefer);
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
        }

        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String raw = stream == null ? "" : new String(readAll(stream), StandardCharsets.UTF_8);
        c.disconnect();

        if (code < 200 || code >= 300) {
            String message = "HTTP_" + code;
            try {
                JSONObject e = new JSONObject(raw);
                message = e.optString("error", e.optString("message", message));
                String codeText = e.optString("code", "");
                if (!codeText.isEmpty() && !message.contains(codeText)) message = message + ":" + codeText;
            } catch (Exception ignored) {}
            throw new Exception(message);
        }
        return raw;
    }
}