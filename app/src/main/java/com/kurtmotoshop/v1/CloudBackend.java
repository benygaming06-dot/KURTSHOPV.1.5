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

public class CloudBackend {

    public interface Callback<T> {
        void onResult(T value, Exception error);
    }

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
        return !apiKey.isEmpty()
                && !projectId.isEmpty()
                && !apiKey.contains("YOUR_")
                && !projectId.contains("YOUR_");
    }

    public String token() {
        return idToken;
    }

    public String uid() {
        return localUid;
    }

    // =========================================================
    // FIREBASE AUTHENTICATION
    // =========================================================

    public void signIn(String username, String password, Callback<JSONObject> cb) {
        authRequest(
                "accounts:signInWithPassword",
                username,
                password,
                cb
        );
    }

    public void signUp(String username, String password, Callback<JSONObject> cb) {
        authRequest(
                "accounts:signUp",
                username,
                password,
                cb
        );
    }

    private void authRequest(
            String endpoint,
            String username,
            String password,
            Callback<JSONObject> cb
    ) {
        new Thread(() -> {
            try {

                if (!configured()) {
                    throw new Exception("Firebase is not configured.");
                }

                JSONObject body = new JSONObject();

                body.put("email", toEmail(username));
                body.put("password", password);
                body.put("returnSecureToken", true);

                JSONObject response = requestJson(
                        "https://identitytoolkit.googleapis.com/v1/"
                                + endpoint
                                + "?key="
                                + apiKey,
                        "POST",
                        body,
                        null
                );

                idToken = response.optString("idToken", "");
                localUid = response.optString("localId", "");

                cb.onResult(response, null);

            } catch (Exception e) {
                cb.onResult(null, e);
            }
        }).start();
    }

    public void restoreSession(
            String refreshToken,
            Callback<JSONObject> cb
    ) {
        new Thread(() -> {
            try {

                if (!configured()) {
                    throw new Exception("Firebase is not configured.");
                }

                JSONObject body = new JSONObject();

                body.put("grant_type", "refresh_token");
                body.put("refresh_token", refreshToken);

                JSONObject response = requestJson(
                        "https://securetoken.googleapis.com/v1/token?key="
                                + apiKey,
                        "POST",
                        body,
                        null
                );

                idToken = response.optString("id_token", "");
                localUid = response.optString("user_id", "");

                cb.onResult(response, null);

            } catch (Exception e) {
                cb.onResult(null, e);
            }
        }).start();
    }

    public static String toEmail(String username) {

        String safe = username == null
                ? ""
                : username.trim().toLowerCase(Locale.US);

        return safe + "@kurtdhylan.app";
    }

    // =========================================================
    // USERS
    // =========================================================

    public void writeMember(
            String uid,
            String username,
            String role,
            Callback<Boolean> cb
    ) {

        new Thread(() -> {

            try {

                requireAuth();

                JSONObject fields = new JSONObject();

                fields.put(
                        "username",
                        stringValue(username)
                );

                fields.put(
                        "role",
                        stringValue(role)
                );

                fields.put(
                        "shopId",
                        stringValue(shopId)
                );

                fields.put(
                        "active",
                        booleanValue(true)
                );

                JSONObject body = new JSONObject();

                body.put("fields", fields);

                String url =
                        "https://firestore.googleapis.com/v1/projects/"
                                + projectId
                                + "/databases/(default)/documents/users/"
                                + uid;

                requestJson(
                        url,
                        "PATCH",
                        body,
                        idToken
                );

                cb.onResult(true, null);

            } catch (Exception e) {

                cb.onResult(false, e);

            }
        }).start();
    }

    // =========================================================
    // READ COLLECTION
    // =========================================================

    public void readCollection(
            String collection,
            Callback<JSONArray> cb
    ) {

        new Thread(() -> {

            try {

                requireAuth();

                JSONArray result = new JSONArray();

                String pageToken = "";

                do {

                    String url =
                            "https://firestore.googleapis.com/v1/projects/"
                                    + projectId
                                    + "/databases/(default)/documents/"
                                    + collection
                                    + "?pageSize=100";

                    if (!pageToken.isEmpty()) {
                        url += "&pageToken=" + pageToken;
                    }

                    JSONObject response =
                            requestJson(
                                    url,
                                    "GET",
                                    null,
                                    idToken
                            );

                    JSONArray documents =
                            response.optJSONArray("documents");

                    if (documents != null) {

                        for (int i = 0; i < documents.length(); i++) {

                            JSONObject document =
                                    documents.getJSONObject(i);

                            JSONObject fields =
                                    document.optJSONObject("fields");

                            JSONObject item =
                                    decodeFields(fields);

                            String name =
                                    document.optString("name", "");

                            if (!name.isEmpty()) {

                                int slash =
                                        name.lastIndexOf('/');

                                if (slash >= 0) {

                                    item.put(
                                            "cloudId",
                                            name.substring(slash + 1)
                                    );
                                }
                            }

                            // Only load records belonging
                            // to this shop.
                            String recordShop =
                                    item.optString("shopId", "");

                            if (recordShop.equals(shopId)) {
                                result.put(item);
                            }
                        }
                    }

                    pageToken =
                            response.optString(
                                    "nextPageToken",
                                    ""
                            );

                } while (!pageToken.isEmpty());

                cb.onResult(result, null);

            } catch (Exception e) {

                cb.onResult(null, e);

            }
        }).start();
    }

    // =========================================================
    // WRITE ARRAY
    // =========================================================

    public void writeArray(
            String collection,
            JSONArray array,
            Callback<Boolean> cb
    ) {

        new Thread(() -> {

            try {

                requireAuth();

                for (int i = 0; i < array.length(); i++) {

                    JSONObject item =
                            array.getJSONObject(i);

                    item.put("shopId", shopId);

                    String cloudId =
                            item.optString("cloudId", "");

                    if (cloudId.isEmpty()) {

                        cloudId =
                                collection
                                        + "_"
                                        + System.currentTimeMillis()
                                        + "_"
                                        + i;
                    }

                    JSONObject fields =
                            encodeFields(item);

                    JSONObject body =
                            new JSONObject();

                    body.put(
                            "fields",
                            fields
                    );

                    String url =
                            "https://firestore.googleapis.com/v1/projects/"
                                    + projectId
                                    + "/databases/(default)/documents/"
                                    + collection
                                    + "/"
                                    + cloudId;

                    requestJson(
                            url,
                            "PATCH",
                            body,
                            idToken
                    );

                    item.put(
                            "cloudId",
                            cloudId
                    );
                }

                cb.onResult(true, null);

            } catch (Exception e) {

                cb.onResult(false, e);

            }
        }).start();
    }

    // =========================================================
    // COMPATIBILITY STATE
    // =========================================================

    public void readState(Callback<String> cb) {

        new Thread(() -> {

            try {

                requireAuth();

                JSONObject state =
                        new JSONObject();

                JSONArray products =
                        readCollectionSync("products");

                JSONArray services =
                        readCollectionSync("services");

                JSONArray sales =
                        readCollectionSync("sales");

                JSONArray stockHistory =
                        readCollectionSync("stockHistory");

                state.put(
                        "products",
                        products
                );

                state.put(
                        "services",
                        services
                );

                state.put(
                        "sales",
                        sales
                );

                state.put(
                        "stockHistory",
                        stockHistory
                );

                cb.onResult(
                        state.toString(),
                        null
                );

            } catch (Exception e) {

                cb.onResult(
                        null,
                        e
                );
            }
        }).start();
    }

    private JSONArray readCollectionSync(
            String collection
    ) throws Exception {

        JSONArray result =
                new JSONArray();

        String url =
                "https://firestore.googleapis.com/v1/projects/"
                        + projectId
                        + "/databases/(default)/documents/"
                        + collection
                        + "?pageSize=100";

        JSONObject response =
                requestJson(
                        url,
                        "GET",
                        null,
                        idToken
                );

        JSONArray documents =
                response.optJSONArray(
                        "documents"
                );

        if (documents == null) {
            return result;
        }

        for (int i = 0;
             i < documents.length();
             i++) {

            JSONObject document =
                    documents.getJSONObject(i);

            JSONObject fields =
                    document.optJSONObject(
                            "fields"
                    );

            JSONObject item =
                    decodeFields(fields);

            String recordShop =
                    item.optString(
                            "shopId",
                            ""
                    );

            if (!recordShop.equals(shopId)) {
                continue;
            }

            String name =
                    document.optString(
                            "name",
                            ""
                    );

            if (!name.isEmpty()) {

                int slash =
                        name.lastIndexOf('/');

                if (slash >= 0) {

                    item.put(
                            "cloudId",
                            name.substring(
                                    slash + 1
                            )
                    );
                }
            }

            result.put(item);
        }

        return result;
    }

    public void writeState(
            String stateJson,
            Callback<Boolean> cb
    ) {

        new Thread(() -> {

            try {

                requireAuth();

                JSONObject state =
                        new JSONObject(stateJson);

                writeCollectionSync(
                        "products",
                        state.optJSONArray("products")
                );

                writeCollectionSync(
                        "services",
                        state.optJSONArray("services")
                );

                writeCollectionSync(
                        "sales",
                        state.optJSONArray("sales")
                );

                writeCollectionSync(
                        "stockHistory",
                        state.optJSONArray("stockHistory")
                );

                cb.onResult(true, null);

            } catch (Exception e) {

                cb.onResult(false, e);

            }
        }).start();
    }

    private void writeCollectionSync(
            String collection,
            JSONArray array
    ) throws Exception {

        if (array == null) {
            return;
        }

        for (int i = 0;
             i < array.length();
             i++) {

            JSONObject item =
                    array.getJSONObject(i);

            item.put(
                    "shopId",
                    shopId
            );

            String cloudId =
                    item.optString(
                            "cloudId",
                            ""
                    );

            if (cloudId.isEmpty()) {

                cloudId =
                        collection
                                + "_"
                                + System.currentTimeMillis()
                                + "_"
                                + i;
            }

            JSONObject body =
                    new JSONObject();

            body.put(
                    "fields",
                    encodeFields(item)
            );

            String url =
                    "https://firestore.googleapis.com/v1/projects/"
                            + projectId
                            + "/databases/(default)/documents/"
                            + collection
                            + "/"
                            + cloudId;

            requestJson(
                    url,
                    "PATCH",
                    body,
                    idToken
            );
        }
    }

    // =========================================================
    // FIRESTORE ENCODING
    // =========================================================

    private JSONObject encodeFields(
            JSONObject source
    ) throws Exception {

        JSONObject fields =
                new JSONObject();

        java.util.Iterator<String> keys =
                source.keys();

        while (keys.hasNext()) {

            String key =
                    keys.next();

            if (key.equals("password")) {
                continue;
            }

            if (key.equals("cloudId")) {
                continue;
            }

            Object value =
                    source.get(key);

            if (value == JSONObject.NULL) {

                fields.put(
                        key,
                        new JSONObject()
                                .put("nullValue", JSONObject.NULL)
                );

            } else if (value instanceof Boolean) {

                fields.put(
                        key,
                        booleanValue(
                                (Boolean) value
                        )
                );

            } else if (value instanceof Number) {

                fields.put(
                        key,
                        new JSONObject()
                                .put(
                                        "doubleValue",
                                        ((Number) value).doubleValue()
                                )
                );

            } else if (value instanceof JSONArray) {

                fields.put(
                        key,
                        new JSONObject()
                                .put(
                                        "arrayValue",
                                        new JSONObject()
                                                .put(
                                                        "values",
                                                        encodeArray(
                                                                (JSONArray) value
                                                        )
                                                )
                                )
                );

            } else if (value instanceof JSONObject) {

                fields.put(
                        key,
                        new JSONObject()
                                .put(
                                        "mapValue",
                                        new JSONObject()
                                                .put(
                                                        "fields",
                                                        encodeFields(
                                                                (JSONObject) value
                                                        )
                                                )
                                )
                );

            } else {

                fields.put(
                        key,
                        stringValue(
                                String.valueOf(value)
                        )
                );
            }
        }

        return fields;
    }

    private JSONArray encodeArray(
            JSONArray array
    ) throws Exception {

        JSONArray result =
                new JSONArray();

        for (int i = 0;
             i < array.length();
             i++) {

            Object value =
                    array.get(i);

            JSONObject wrapper =
                    new JSONObject();

            if (value instanceof Number) {

                wrapper.put(
                        "doubleValue",
                        ((Number) value).doubleValue()
                );

            } else if (value instanceof Boolean) {

                wrapper.put(
                        "booleanValue",
                        value
                );

            } else {

                wrapper.put(
                        "stringValue",
                        String.valueOf(value)
                );
            }

            result.put(wrapper);
        }

        return result;
    }

    private JSONObject decodeFields(
            JSONObject fields
    ) throws Exception {

        JSONObject result =
                new JSONObject();

        if (fields == null) {
            return result;
        }

        java.util.Iterator<String> keys =
                fields.keys();

        while (keys.hasNext()) {

            String key =
                    keys.next();

            JSONObject value =
                    fields.getJSONObject(key);

            if (value.has("stringValue")) {

                result.put(
                        key,
                        value.optString(
                                "stringValue",
                                ""
                        )
                );

            } else if (value.has("booleanValue")) {

                result.put(
                        key,
                        value.optBoolean(
                                "booleanValue",
                                false
                        )
                );

            } else if (value.has("integerValue")) {

                result.put(
                        key,
                        value.optLong(
                                "integerValue",
                                0
                        )
                );

            } else if (value.has("doubleValue")) {

                result.put(
                        key,
                        value.optDouble(
                                "doubleValue",
                                0
                        )
                );

            } else if (value.has("timestampValue")) {

                result.put(
                        key,
                        value.optString(
                                "timestampValue",
                                ""
                        )
                );

            } else if (value.has("arrayValue")) {

                JSONObject arrayObject =
                        value.optJSONObject(
                                "arrayValue"
                        );

                JSONArray values =
                        arrayObject == null
                                ? new JSONArray()
                                : arrayObject.optJSONArray(
                                        "values"
                                );

                result.put(
                        key,
                        decodeArray(values)
                );

            } else if (value.has("mapValue")) {

                JSONObject map =
                        value.optJSONObject(
                                "mapValue"
                        );

                JSONObject mapFields =
                        map == null
                                ? null
                                : map.optJSONObject(
                                        "fields"
                                );

                result.put(
                        key,
                        decodeFields(mapFields)
                );
            }
        }

        return result;
    }

    private JSONArray decodeArray(
            JSONArray values
    ) throws Exception {

        JSONArray result =
                new JSONArray();

        if (values == null) {
            return result;
        }

        for (int i = 0;
             i < values.length();
             i++) {

            JSONObject value =
                    values.getJSONObject(i);

            if (value.has("stringValue")) {

                result.put(
                        value.optString(
                                "stringValue",
                                ""
                        )
                );

            } else if (value.has("booleanValue")) {

                result.put(
                        value.optBoolean(
                                "booleanValue",
                                false
                        )
                );

            } else if (value.has("integerValue")) {

                result.put(
                        value.optLong(
                                "integerValue",
                                0
                        )
                );

            } else if (value.has("doubleValue")) {

                result.put(
                        value.optDouble(
                                "doubleValue",
                                0
                        )
                );
            }
        }

        return result;
    }

    // =========================================================
    // FIRESTORE VALUE HELPERS
    // =========================================================

    private JSONObject stringValue(
            String value
    ) throws Exception {

        return new JSONObject()
                .put(
                        "stringValue",
                        value == null ? "" : value
                );
    }

    private JSONObject booleanValue(
            boolean value
    ) throws Exception {

        return new JSONObject()
                .put(
                        "booleanValue",
                        value
                );
    }

    // =========================================================
    // HTTP
    // =========================================================

    private void requireAuth()
            throws Exception {

        if (!configured()) {

            throw new Exception(
                    "Firebase is not configured."
            );
        }

        if (idToken.isEmpty()) {

            throw new Exception(
                    "Not signed in to Firebase."
            );
        }
    }

    private JSONObject requestJson(
            String urlString,
            String method,
            JSONObject body,
            String bearer
    ) throws Exception {

        HttpURLConnection c =
                (HttpURLConnection)
                        new URL(urlString)
                                .openConnection();

        c.setRequestMethod(method);

        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);

        c.setRequestProperty(
                "Accept",
                "application/json"
        );

        if (bearer != null
                && !bearer.isEmpty()) {

            c.setRequestProperty(
                    "Authorization",
                    "Bearer " + bearer
            );
        }

        if (body != null) {

            c.setDoOutput(true);

            c.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            byte[] bytes =
                    body.toString()
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );

            try (
                    OutputStream out =
                            c.getOutputStream()
            ) {
                out.write(bytes);
            }
        }

        int code =
                c.getResponseCode();

        InputStream stream =
                code >= 200 && code < 300
                        ? c.getInputStream()
                        : c.getErrorStream();

        String text =
                readAll(stream);

        if (code < 200 || code >= 300) {

            String message =
                    text;

            try {

                JSONObject err =
                        new JSONObject(text);

                JSONObject error =
                        err.optJSONObject(
                                "error"
                        );

                if (error != null) {

                    message =
                            error.optString(
                                    "message",
                                    text
                            );
                }

            } catch (Exception ignored) {
            }

            throw new Exception(
                    "HTTP "
                            + code
                            + ": "
                            + message
            );
        }

        return text.isEmpty()
                ? new JSONObject()
                : new JSONObject(text);
    }

    private String readAll(
            InputStream in
    ) throws Exception {

        if (in == null) {
            return "";
        }

        StringBuilder sb =
                new StringBuilder();

        try (
                BufferedReader r =
                        new BufferedReader(
                                new InputStreamReader(
                                        in,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while (
                    (line = r.readLine())
                            != null
            ) {

                sb.append(line);
            }
        }

        return sb.toString();
    }
}
