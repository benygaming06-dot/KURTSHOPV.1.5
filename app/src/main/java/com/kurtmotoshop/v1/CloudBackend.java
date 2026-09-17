package com.kurtmotoshop.v1;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Lightweight Firebase Authentication + Firestore REST client.
 * Put the Firebase Web API key and project id in BackendConfig.java.
 */
public class CloudBackend {
    public interface Callback<T> { void onResult(T value, Exception error); }

    private final String apiKey;
    private final String projectId;
    private final String shopId;
    private String idToken = "";
    private String localUid = "";

    public CloudBackend(String apiKey, String projectId, String shopId) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.projectId = projectId == null ? "" : projectId.trim();
        this.shopId = shopId == null ? "" : shopId.trim();
    }

    public boolean configured() {
        return !apiKey.isEmpty() && !projectId.isEmpty()
                && !apiKey.contains("YOUR_") && !projectId.contains("YOUR_");
    }

    public String token() { return idToken; }
    public String uid() { return localUid; }

    public void signIn(String username, String password, Callback<JSONObject> cb) {
        authRequest("accounts:signInWithPassword", username, password, cb);
    }

    public void signUp(String username, String password, Callback<JSONObject> cb) {
        authRequest("accounts:signUp", username, password, cb);
    }

    public void restoreSession(String refreshToken, Callback<JSONObject> cb) {
        new Thread(() -> {
            try {
                if (!configured()) throw new Exception("Firebase is not configured.");
                JSONObject body = new JSONObject();
                body.put("grant_type", "refresh_token");
                body.put("refresh_token", refreshToken);
                JSONObject response = requestJson("https://securetoken.googleapis.com/v1/token?key=" + apiKey,
                        "POST", body, null);
                idToken = response.optString("id_token", "");
                localUid = response.optString("user_id", "");
                cb.onResult(response, null);
            } catch (Exception e) { cb.onResult(null, e); }
        }).start();
    }

    private void authRequest(String endpoint, String username, String password, Callback<JSONObject> cb) {
        new Thread(() -> {
            try {
                if (!configured()) throw new Exception("Firebase is not configured.");
                JSONObject body = new JSONObject();
                body.put("email", toEmail(username));
                body.put("password", password);
                body.put("returnSecureToken", true);
                JSONObject response = requestJson("https://identitytoolkit.googleapis.com/v1/" + endpoint + "?key=" + apiKey,
                        "POST", body, null);
                idToken = response.optString("idToken", "");
                localUid = response.optString("localId", "");
                cb.onResult(response, null);
            } catch (Exception e) { cb.onResult(null, e); }
        }).start();
    }

    public void writeMember(String uid, String username, String role, Callback<Boolean> cb) {
        new Thread(() -> {
            try {
                requireAuth();
                JSONObject fields = new JSONObject();
                fields.put("username", new JSONObject().put("stringValue", username));
                fields.put("role", new JSONObject().put("stringValue", role));
                fields.put("active", new JSONObject().put("booleanValue", true));
                JSONObject body = new JSONObject().put("fields", fields);
                String url = "https://firestore.googleapis.com/v1/projects/" + projectId
                        + "/databases/(default)/documents/shops/" + shopId + "/members/" + uid;
                requestJson(url, "PATCH", body, idToken);
                cb.onResult(true, null);
            } catch (Exception e) { cb.onResult(false, e); }
        }).start();
    }

    public void readState(Callback<String> cb) {
        new Thread(() -> {
            try {
                requireAuth();
                String url = "https://firestore.googleapis.com/v1/projects/" + projectId
                        + "/databases/(default)/documents/shops/" + shopId;
                JSONObject doc = requestJson(url, "GET", null, idToken);
                JSONObject fields = doc.optJSONObject("fields");
                String state = fields == null ? "" : fields.optJSONObject("state") == null ? "" : fields.optJSONObject("state").optString("stringValue", "");
                cb.onResult(state, null);
            } catch (Exception e) { cb.onResult(null, e); }
        }).start();
    }

    public void writeState(String stateJson, Callback<Boolean> cb) {
        new Thread(() -> {
            try {
                requireAuth();
                JSONObject fields = new JSONObject();
                fields.put("state", new JSONObject().put("stringValue", stateJson));
                fields.put("updatedAt", new JSONObject().put("timestampValue", isoNow()));
                JSONObject body = new JSONObject().put("fields", fields);
                String url = "https://firestore.googleapis.com/v1/projects/" + projectId
                        + "/databases/(default)/documents/shops/" + shopId
                        + "?updateMask.fieldPaths=state&updateMask.fieldPaths=updatedAt";
                requestJson(url, "PATCH", body, idToken);
                cb.onResult(true, null);
            } catch (Exception e) { cb.onResult(false, e); }
        }).start();
    }

    public static String toEmail(String username) {
        String safe = username == null ? "" : username.trim().toLowerCase(Locale.US);
        return safe + "@kurtdhylan.app";
    }

    private void requireAuth() throws Exception {
        if (!configured()) throw new Exception("Firebase is not configured.");
        if (idToken.isEmpty()) throw new Exception("Not signed in to Firebase.");
    }

    private JSONObject requestJson(String urlString, String method, JSONObject body, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer != null && !bearer.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String text = readAll(stream);
        if (code < 200 || code >= 300) {
            String message = text;
            try {
                JSONObject err = new JSONObject(text);
                JSONObject e = err.optJSONObject("error");
                if (e != null) message = e.optString("message", text);
            } catch (Exception ignored) {}
            throw new Exception("HTTP " + code + ": " + message);
        }
        return text.isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String isoNow() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new java.util.Date());
    }
}
