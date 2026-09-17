package com.kurtmotoshop.v1;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

public class MainActivity extends android.app.Activity {
    private LinearLayout root;
    private TextView productCount, serviceCount;
    private SharedPreferences prefs;
    private String currentUsername = "";
    private String currentRole = "";
    private TextView userBadge;
    private CloudBackend cloud;
    private boolean cloudConfigured = false;
    private boolean applyingCloudState = false;

    private ImageView pickerPreview;
    private String pendingImageUri = "";

    private final int BG = Color.rgb(5, 6, 8);
    private final int CARD = Color.rgb(14, 17, 21);
    private final int RED = Color.rgb(235, 25, 35);
    private final int BLUE = Color.rgb(20, 120, 235);
    private final int GREEN = Color.rgb(30, 210, 95);
    private final int ORANGE = Color.rgb(255, 145, 25);
    private final int WHITE = Color.WHITE;
    private final int MUTED = Color.rgb(165, 169, 176);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("inventory", MODE_PRIVATE);
        cloud = new CloudBackend(BackendConfig.FIREBASE_API_KEY, BackendConfig.FIREBASE_PROJECT_ID, BackendConfig.SHOP_ID);
        cloudConfigured = cloud.configured();
        ensureDefaultAccounts();
        if (prefs.getBoolean("loggedIn", false)) {
            currentUsername = prefs.getString("currentUsername", "");
            currentRole = prefs.getString("currentRole", "");
            String refreshToken = prefs.getString("firebaseRefreshToken", "");
            if (cloudConfigured && !refreshToken.isEmpty()) {
                cloud.restoreSession(refreshToken, (r, err) -> runOnUiThread(() -> {
                    if (err != null) {
                        prefs.edit().putBoolean("loggedIn", false).remove("firebaseRefreshToken").apply();
                        showLoginScreen();
                    } else {
                        cloud.readState((state, readErr) -> runOnUiThread(() -> {
                            if (readErr == null && state != null && !state.trim().isEmpty()) applyCloudState(state);
                            buildUI();
                        }));
                    }
                }));
            } else {
                buildUI();
            }
        } else {
            showLoginScreen();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            pendingImageUri = uri.toString();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            if (pickerPreview != null) {
                pickerPreview.setImageURI(uri);
                pickerPreview.setVisibility(View.VISIBLE);
            }
        }
    }

    private void pickImage(ImageView preview) {
        pickerPreview = preview;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, 1001);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView tv(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable bg(int color, float radius, int stroke, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke > 0) g.setStroke(dp(stroke), strokeColor);
        return g;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout box(int color, int strokeColor) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        l.setBackground(bg(color, 28, 2, strokeColor));
        return l;
    }

    private TextView button(String text, int color) {
        TextView b = tv(text, 13, WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(6), dp(11), dp(6), dp(11));
        b.setBackground(bg(color, 18, 0, color));
        return b;
    }

    private void ensureDefaultAccounts() {
        if (prefs.getBoolean("accountsInitialized", false)) return;
        try {
            JSONArray accounts = new JSONArray();
            accounts.put(new JSONObject().put("username", "owner").put("password", "owner123").put("name", "Owner Shop Acc").put("role", "OWNER"));
            accounts.put(new JSONObject().put("username", "assistant").put("password", "assistant123").put("name", "Assistant").put("role", "ASSISTANT"));
            accounts.put(new JSONObject().put("username", "mechanic").put("password", "mechanic123").put("name", "Mechanic Employee").put("role", "MECHANIC"));
            prefs.edit().putString("accounts", accounts.toString()).putBoolean("accountsInitialized", true).apply();
        } catch (Exception ignored) {}
    }

    private boolean canEdit() {
        return "OWNER".equals(currentRole) || "ASSISTANT".equals(currentRole);
    }

    private boolean isMechanic() {
        return "MECHANIC".equals(currentRole);
    }

    private String roleLabel() {
        if ("OWNER".equals(currentRole)) return "OWNER SHOP ACC";
        if ("ASSISTANT".equals(currentRole)) return "ASSISTANT";
        return "MECHANIC EMPLOYEE";
    }

    private JSONArray getAccounts() {
        try { return new JSONArray(prefs.getString("accounts", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void saveAccounts(JSONArray a) {
        prefs.edit().putString("accounts", a.toString()).apply();
        syncCloud("accounts");
    }

    private void showLoginScreen() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        outer.setPadding(dp(24), dp(40), dp(24), dp(24));
        outer.setBackgroundColor(BG);

        TextView logo = tv("K", 42, RED, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(bg(Color.rgb(18,20,24), 24, 2, RED));
        outer.addView(logo, new LinearLayout.LayoutParams(dp(76), dp(76)));

        TextView title = tv("KURT DHYLAN", 25, WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(14), 0, 0);
        outer.addView(title);
        TextView sub = tv("MOTO SHOP INVENTORY", 12, MUTED, true);
        sub.setGravity(Gravity.CENTER);
        outer.addView(sub);

        TextView loginTitle = tv("ACCOUNT LOGIN", 20, WHITE, true);
        loginTitle.setGravity(Gravity.CENTER);
        loginTitle.setPadding(0, dp(42), 0, dp(12));
        outer.addView(loginTitle);

        EditText username = field("Username");
        EditText password = field("Password");
        password.setInputType(0x81);
        outer.addView(username);
        outer.addView(password);

        TextView login = button("LOGIN", RED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(10), 0, dp(12));
        outer.addView(login, lp);

        TextView defaults = tv("Default accounts:\nowner / owner123\nassistant / assistant123\nmechanic / mechanic123", 11, MUTED, false);
        defaults.setGravity(Gravity.CENTER);
        defaults.setPadding(0, dp(10), 0, dp(10));
        outer.addView(defaults);

        TextView info = tv("Owner & Assistant: full inventory control\nMechanic: cart, checkout & sales history only", 11, MUTED, false);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(20), 0, dp(20));
        outer.addView(info);

        login.setOnClickListener(v -> {
            String u = username.getText().toString().trim();
            String pw = password.getText().toString();
            if (u.isEmpty() || pw.isEmpty()) { password.setError("Enter username and password"); return; }
            if (cloudConfigured) {
                loginCloud(u, pw, password);
            } else {
                loginLocal(u, pw, password);
            }
        });
        setContentView(outer);
    }

    private void loginLocal(String u, String pw, EditText password) {
        JSONArray accounts = getAccounts();
        for (int i=0;i<accounts.length();i++) {
            JSONObject a = accounts.optJSONObject(i);
            if (a != null && a.optString("username").equalsIgnoreCase(u) && a.optString("password").equals(pw)) {
                currentUsername = a.optString("username");
                currentRole = a.optString("role");
                prefs.edit().putBoolean("loggedIn", true).putString("currentUsername", currentUsername).putString("currentRole", currentRole).apply();
                buildUI();
                return;
            }
        }
        password.setError("Invalid username or password");
        Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
    }

    private void loginCloud(String u, String pw, EditText password) {
        Toast.makeText(this, "Connecting to cloud...", Toast.LENGTH_SHORT).show();
        cloud.signIn(u, pw, (auth, err) -> runOnUiThread(() -> {
            if (err != null) {
                password.setError("Cloud login failed");
                Toast.makeText(this, err.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            String refreshToken = auth.optString("refreshToken", "");
            if (!refreshToken.isEmpty()) prefs.edit().putString("firebaseRefreshToken", refreshToken).apply();
            cloud.readState((state, readErr) -> runOnUiThread(() -> {
                if (readErr != null) {
                    Toast.makeText(this, "Database read failed: " + readErr.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                if (state == null || state.trim().isEmpty()) {
                    if (!u.equalsIgnoreCase("owner")) {
                        Toast.makeText(this, "Shop database is not initialized. Login as owner first.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    bootstrapCloudOwner(u, pw);
                    return;
                }
                try {
                    JSONObject root = new JSONObject(state);
                    JSONArray accounts = root.optJSONArray("accounts");
                    JSONObject account = null;
                    for (int i=0;i<(accounts==null?0:accounts.length());i++) {
                        JSONObject a = accounts.optJSONObject(i);
                        if (a != null && a.optString("username").equalsIgnoreCase(u)) { account=a; break; }
                    }
                    if (account == null) {
                        Toast.makeText(this, "Account is authenticated but not assigned to this shop.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    applyCloudState(state);
                    currentUsername = account.optString("username");
                    currentRole = account.optString("role");
                    prefs.edit().putBoolean("loggedIn", true).putString("currentUsername", currentUsername).putString("currentRole", currentRole).apply();
                    buildUI();
                    Toast.makeText(this, "Cloud sync complete", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Invalid cloud database state", Toast.LENGTH_LONG).show();
                }
            }));
        }));
    }

    private void applyCloudState(String state) {
        try {
            JSONObject root = new JSONObject(state);
            JSONArray accounts = root.optJSONArray("accounts");
            applyingCloudState = true;
            prefs.edit()
                    .putString("products", root.optJSONArray("products")==null?"[]":root.optJSONArray("products").toString())
                    .putString("services", root.optJSONArray("services")==null?"[]":root.optJSONArray("services").toString())
                    .putString("salesHistory", root.optJSONArray("salesHistory")==null?"[]":root.optJSONArray("salesHistory").toString())
                    .putString("stockHistory", root.optJSONArray("stockHistory")==null?"[]":root.optJSONArray("stockHistory").toString())
                    .putString("accounts", accounts==null?"[]":accounts.toString())
                    .putBoolean("accountsInitialized", true).apply();
            applyingCloudState = false;
        } catch (Exception e) {
            applyingCloudState = false;
        }
    }

    private void bootstrapCloudOwner(String username, String password) {
        try {
            JSONArray accounts = new JSONArray();
            accounts.put(new JSONObject().put("username", username).put("name", "Owner Shop Acc").put("role", "OWNER"));
            // Seed the V1.4 default accounts in Firebase Auth and register their shop membership.
            seedCloudAccount("assistant", "assistant123", "ASSISTANT");
            seedCloudAccount("mechanic", "mechanic123", "MECHANIC");
            accounts.put(new JSONObject().put("username", "assistant").put("name", "Assistant").put("role", "ASSISTANT"));
            accounts.put(new JSONObject().put("username", "mechanic").put("name", "Mechanic Employee").put("role", "MECHANIC"));
            cloud.writeMember(cloud.uid(), username, "OWNER", (ok, ignored) -> {});
            JSONObject state = new JSONObject();
            state.put("products", new JSONArray()); state.put("services", new JSONArray());
            state.put("salesHistory", new JSONArray()); state.put("stockHistory", new JSONArray()); state.put("accounts", accounts);
            cloud.writeState(state.toString(), (ok, err) -> runOnUiThread(() -> {
                if (err != null) { Toast.makeText(this, "Could not initialize shop: "+err.getMessage(), Toast.LENGTH_LONG).show(); return; }
                prefs.edit().putString("accounts", accounts.toString()).putBoolean("accountsInitialized", true).apply();
                currentUsername=username; currentRole="OWNER";
                prefs.edit().putBoolean("loggedIn",true).putString("currentUsername",currentUsername).putString("currentRole",currentRole).apply();
                buildUI(); Toast.makeText(this,"Cloud shop initialized",Toast.LENGTH_SHORT).show();
            }));
        } catch(Exception e) { Toast.makeText(this,"Initialization failed",Toast.LENGTH_LONG).show(); }
    }

    private void seedCloudAccount(String username, String password, String role) {
        CloudBackend seedClient = new CloudBackend(BackendConfig.FIREBASE_API_KEY, BackendConfig.FIREBASE_PROJECT_ID, BackendConfig.SHOP_ID);
        seedClient.signUp(username, password, (r,e) -> {
            if (e == null && r != null) cloud.writeMember(r.optString("localId"), username, role, (ok, ignored) -> {});
        });
    }

    private String buildCloudState() {
        try {
            JSONObject root = new JSONObject();
            root.put("products", getArray("products"));
            root.put("services", getArray("services"));
            root.put("salesHistory", getArray("salesHistory"));
            root.put("stockHistory", getArray("stockHistory"));
            JSONArray safeAccounts = new JSONArray();
            JSONArray accounts = getAccounts();
            for (int i=0;i<accounts.length();i++) {
                JSONObject a = accounts.optJSONObject(i);
                if (a == null) continue;
                JSONObject safe = new JSONObject();
                safe.put("username", a.optString("username"));
                safe.put("name", a.optString("name"));
                safe.put("role", a.optString("role"));
                safeAccounts.put(safe);
            }
            root.put("accounts", safeAccounts);
            return root.toString();
        } catch (Exception e) { return "{}"; }
    }

    private void syncCloud(String reason) {
        if (!cloudConfigured || applyingCloudState) return;
        cloud.writeState(buildCloudState(), (ok, err) -> runOnUiThread(() -> {
            if (err != null) Toast.makeText(this, "Cloud sync failed: "+reason, Toast.LENGTH_SHORT).show();
        }));
    }

    private void logout() {
        prefs.edit().putBoolean("loggedIn", false).remove("currentUsername").remove("currentRole").remove("firebaseRefreshToken").apply();
        currentUsername = ""; currentRole = "";
        showLoginScreen();
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(24));
        root.setBackgroundColor(BG);
        scroll.addView(root);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(dp(16), dp(10) + insets.getSystemWindowInsetTop(),
                    dp(16), dp(24) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);

        LinearLayout header = row();
        header.setPadding(dp(4), dp(8), dp(4), dp(8));
        TextView logo = tv("K", 32, RED, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(bg(Color.rgb(18,20,24), 18, 2, RED));
        header.addView(logo, new LinearLayout.LayoutParams(dp(58),dp(58)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12),0,0,0);
        brand.addView(tv("KURT DHYLAN", 21, WHITE, true));
        brand.addView(tv("MOTO SHOP INVENTORY", 10, MUTED, true));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout accountBox = new LinearLayout(this);
        accountBox.setOrientation(LinearLayout.VERTICAL);
        accountBox.setGravity(Gravity.CENTER);
        TextView who = tv(roleLabel(), 9, GREEN, true); who.setGravity(Gravity.CENTER);
        TextView uname = tv(currentUsername, 10, MUTED, false); uname.setGravity(Gravity.CENTER);
        accountBox.addView(who); accountBox.addView(uname);
        header.addView(accountBox, new LinearLayout.LayoutParams(dp(105), -2));

        TextView settings = tv("⚙", 26, WHITE, false);
        settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(v -> showSettings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(55),dp(55)));
        root.addView(header);

        TextView dash = tv("DASHBOARD", 28, WHITE, true);
        dash.setPadding(dp(4), dp(18), dp(4), dp(6));
        root.addView(dash);

        TextView offline = tv(cloudConfigured ? "●  CLOUD SYNC READY     Shared shop database across phones" : "●  OFFLINE MODE     Products & services are saved on this phone", 12, GREEN, true);
        offline.setPadding(dp(14), dp(12), dp(14), dp(12));
        offline.setBackground(bg(Color.rgb(13,27,19), 20, 1, GREEN));
        root.addView(offline);

        LinearLayout stats = row();
        stats.setPadding(0, dp(12), 0, 0);
        LinearLayout pStat = statCard("📦", "PRODUCTS / PARTS", RED, true);
        LinearLayout sStat = statCard("🔧", "SERVICES / LABOR", BLUE, false);
        stats.addView(pStat, new LinearLayout.LayoutParams(0,dp(102),1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,dp(102),1);
        sp.setMargins(dp(12),0,0,0);
        stats.addView(sStat, sp);
        root.addView(stats);

        TextView title = tv("QUICK ACTIONS", 20, WHITE, true);
        title.setPadding(dp(4), dp(28), dp(4), dp(14));
        root.addView(title);

        LinearLayout grid1 = row();
        LinearLayout prod = actionCard("▣", "PRODUCTS / PARTS", "View, search and manage stock", RED);
        LinearLayout serv = actionCard("⚒", "SERVICES / LABOR", "View, search and manage services", BLUE);
        grid1.addView(prod, new LinearLayout.LayoutParams(0,dp(146),1));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0,dp(146),1);
        mp.setMargins(dp(12),0,0,0);
        grid1.addView(serv, mp);
        root.addView(grid1);

        LinearLayout grid2 = row();
        LinearLayout addP = actionCard("+", "ADD PRODUCT", "Name • Brand • Model • Price • Stock", GREEN);
        LinearLayout addS = actionCard("+", "ADD SERVICE", "Labor name • Price", ORANGE);
        LinearLayout.LayoutParams g2a = new LinearLayout.LayoutParams(0,dp(146),1);
        g2a.setMargins(0,dp(12),0,0);
        grid2.addView(addP, g2a);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0,dp(146),1);
        ap.setMargins(dp(12),dp(12),0,0);
        grid2.addView(addS, ap);
        root.addView(grid2);

        TextView toolsTitle = tv("TOOLS", 20, WHITE, true);
        toolsTitle.setPadding(dp(4), dp(28), dp(4), dp(14));
        root.addView(toolsTitle);

        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = row();
        String[] labels = {"Products", "Services", "Cart", "History", "Settings"};
        String[] icons = {"▣", "⚒", "🛒", "▤", "⚙"};
        int[] colors = {RED, BLUE, ORANGE, GREEN, GREEN};
        for (int i=0;i<labels.length;i++) {
            final int idx=i;
            LinearLayout c = box(CARD, Color.rgb(35,39,45));
            c.setGravity(Gravity.CENTER);
            TextView ic = tv(icons[i], 23, colors[i], true); ic.setGravity(Gravity.CENTER);
            TextView lb = tv(labels[i], 11, WHITE, false); lb.setGravity(Gravity.CENTER);
            c.addView(ic, new LinearLayout.LayoutParams(-1,dp(40)));
            c.addView(lb, new LinearLayout.LayoutParams(-1,dp(28)));
            if (idx==0) c.setOnClickListener(v -> showProducts());
            if (idx==1) c.setOnClickListener(v -> showServices());
            if (idx==2) c.setOnClickListener(v -> showCart());
            if (idx==3) c.setOnClickListener(v -> showSalesHistory());
            if (idx==4) c.setOnClickListener(v -> showSettings());
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(108),dp(82));
            if(i>0) tp.setMargins(dp(8),0,0,0);
            tools.addView(c,tp);
        }
        toolsScroll.addView(tools);
        root.addView(toolsScroll);

        TextView footer = tv("KURT DHYLAN MOTO SHOP\nInventory • Services • Sales\n\nBUILT FOR BIKERS", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(10), dp(28), dp(10), dp(20));
        root.addView(footer);

        refreshCounts();
        prod.setOnClickListener(v -> showProducts());
        serv.setOnClickListener(v -> showServices());
        addP.setOnClickListener(v -> { if (canEdit()) addProduct(); else accessDenied(); });
        addS.setOnClickListener(v -> { if (canEdit()) addService(); else accessDenied(); });
    }

    private LinearLayout statCard(String icon, String title, int accent, boolean product) {
        LinearLayout c = box(CARD, accent);
        LinearLayout r = row();
        TextView ic = tv(icon, 25, WHITE, false);
        ic.setGravity(Gravity.CENTER);
        r.addView(ic, new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10),0,0,0);
        texts.addView(tv(title,10,MUTED,true));
        TextView n=tv("0",29,WHITE,true);
        if(product) productCount=n; else serviceCount=n;
        texts.addView(n);
        r.addView(texts, new LinearLayout.LayoutParams(0,-2,1));
        c.addView(r);
        return c;
    }

    private LinearLayout actionCard(String icon, String title, String desc, int accent) {
        LinearLayout c = box(Color.rgb(12,15,18), accent);
        TextView i = tv(icon, 38, accent, true); i.setGravity(Gravity.CENTER);
        TextView t = tv(title, 15, WHITE, true); t.setPadding(0,dp(8),0,dp(3));
        TextView d = tv(desc, 11, MUTED, false); d.setMaxLines(3);
        c.addView(i, new LinearLayout.LayoutParams(-1,dp(44)));
        c.addView(t); c.addView(d);
        return c;
    }

    private JSONArray getArray(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void saveArray(String key, JSONArray a) {
        prefs.edit().putString(key, a.toString()).apply();
        refreshCounts();
        if (key.equals("products") || key.equals("services")) syncCloud(key);
    }

    private void refreshCounts() {
        if (productCount != null) productCount.setText(String.valueOf(getArray("products").length()));
        if (serviceCount != null) serviceCount.setText(String.valueOf(getArray("services").length()));
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setSingleLine(true);
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        e.setBackground(bg(Color.rgb(25,28,33), 14, 1, Color.rgb(55,60,68)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0,dp(5),0,dp(5));
        e.setLayoutParams(lp);
        return e;
    }

    private LinearLayout imagePickerRow() {
        LinearLayout r = row();
        r.setPadding(0, dp(8), 0, dp(8));
        pickerPreview = new ImageView(this);
        pickerPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pickerPreview.setBackground(bg(Color.rgb(25,28,33), 14, 1, Color.rgb(55,60,68)));
        pickerPreview.setVisibility(View.GONE);
        r.addView(pickerPreview, new LinearLayout.LayoutParams(dp(82),dp(82)));

        TextView choose = button("SELECT IMAGE", Color.rgb(45,50,58));
        choose.setOnClickListener(v -> pickImage(pickerPreview));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,dp(52),1);
        cp.setMargins(dp(12),0,0,0);
        r.addView(choose, cp);
        return r;
    }

    private void addProduct() {
        pendingImageUri = "";
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4),0,dp(4),0);
        EditText name=field("Product / Part Name");
        EditText brand=field("Brand");
        EditText model=field("Model");
        EditText price=field("Price (₱)");
        EditText stock=field("Stocks");
        price.setInputType(2|8192);
        stock.setInputType(2);
        form.addView(name); form.addView(brand); form.addView(model); form.addView(price); form.addView(stock);
        form.addView(imagePickerRow());

        AlertDialog d = new AlertDialog.Builder(this).setTitle("ADD PRODUCT / PART")
                .setView(form).setNegativeButton("CANCEL",null)
                .setPositiveButton("SAVE",null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if(name.getText().toString().trim().isEmpty()) { name.setError("Required"); return; }
            try {
                JSONObject o=new JSONObject();
                o.put("name",name.getText().toString().trim());
                o.put("brand",brand.getText().toString().trim());
                o.put("model",model.getText().toString().trim());
                o.put("price",price.getText().toString().trim());
                o.put("stock",Integer.parseInt(stock.getText().toString().trim().isEmpty()?"0":stock.getText().toString().trim()));
                o.put("imageUri", pendingImageUri);
                JSONArray a=getArray("products"); a.put(o); saveArray("products",a);
                Toast.makeText(this,"Product saved",Toast.LENGTH_SHORT).show(); d.dismiss();
            } catch(Exception e) { stock.setError("Enter a valid stock number"); }
        }));
        d.show();
    }

    private void addService() {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        EditText name=field("Labor / Service Name");
        EditText price=field("Price (₱)"); price.setInputType(2|8192);
        form.addView(name); form.addView(price);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("ADD SERVICE / LABOR")
                .setView(form).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if(name.getText().toString().trim().isEmpty()){name.setError("Required");return;}
            try {
                JSONObject o=new JSONObject(); o.put("name",name.getText().toString().trim()); o.put("price",price.getText().toString().trim());
                JSONArray a=getArray("services"); a.put(o); saveArray("services",a);
                Toast.makeText(this,"Service saved",Toast.LENGTH_SHORT).show(); d.dismiss();
            } catch(Exception e) { price.setError("Invalid price"); }
        }));
        d.show();
    }

    private void showProducts() { showList(true); }
    private void showServices() { showList(false); }

    private void showList(boolean products) {
        final String key=products?"products":"services";
        final JSONArray all=getArray(key);
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setPadding(dp(12),dp(4),dp(12),dp(4));
        EditText search=field(products?"Search products...":"Search services...");
        outer.addView(search);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(0,dp(6),0,dp(6));
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.addView(list); outer.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(products?"PRODUCTS / PARTS":"SERVICES / LABOR")
                .setView(outer).setPositiveButton("CLOSE",null).create();

        Runnable render=() -> {
            list.removeAllViews();
            String q=search.getText().toString().toLowerCase(Locale.US);
            int shown=0;
            for(int i=0;i<all.length();i++) {
                try {
                    JSONObject o=all.getJSONObject(i);
                    String text=products ? o.optString("name")+" "+o.optString("brand")+" "+o.optString("model") : o.optString("name");
                    if(!text.toLowerCase(Locale.US).contains(q)) continue;
                    shown++;
                    list.addView(itemCard(o,i,products,dialog));
                } catch(Exception ignored){}
            }
            if(shown==0) list.addView(tv("No items found.",15,MUTED,false));
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){render.run();} public void afterTextChanged(Editable e){}});
        render.run();
        dialog.show();
    }

    private ImageView productImage(String uriString) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(bg(Color.rgb(25,28,33), 16, 1, Color.rgb(55,60,68)));
        image.setPadding(dp(4),dp(4),dp(4),dp(4));
        if(uriString != null && !uriString.isEmpty()) {
            try { image.setImageURI(Uri.parse(uriString)); } catch(Exception ignored) {}
        }
        return image;
    }

    private View itemCard(JSONObject o,int index,boolean product,AlertDialog dialog) {
        LinearLayout c=box(CARD,product?RED:BLUE);

        if (product) {
            LinearLayout top = row();
            LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL);
            TextView title=tv(o.optString("name"),17,WHITE,true); title.setMaxLines(2);
            details.addView(title);
            details.addView(tv(o.optString("brand")+"  •  "+o.optString("model"),12,MUTED,false));
            details.addView(tv("₱"+o.optString("price","0")+"    |    STOCK: "+o.optInt("stock",0),13,WHITE,true));
            top.addView(details, new LinearLayout.LayoutParams(0,dp(92),1));
            ImageView image = productImage(o.optString("imageUri",""));
            top.addView(image, new LinearLayout.LayoutParams(dp(88),dp(88)));
            c.addView(top);
        } else {
            TextView title=tv(o.optString("name"),17,WHITE,true); c.addView(title);
            c.addView(tv("LABOR PRICE: ₱"+o.optString("price","0"),13,WHITE,true));
        }

        LinearLayout actions=row();
        actions.setPadding(0,dp(12),0,0);
        TextView edit=button("EDIT",BLUE), del=button("DELETE",RED);
        if (!canEdit()) {
            edit.setText("VIEW");
            del.setText("LOCKED");
            del.setAlpha(0.45f);
        }
        actions.addView(edit,new LinearLayout.LayoutParams(0,dp(46),1));
        LinearLayout.LayoutParams delp=new LinearLayout.LayoutParams(0,dp(46),1); delp.setMargins(dp(8),0,0,0); actions.addView(del,delp);

        if (product) {
            TextView cart=button("ADD TO CART",GREEN);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1.25f); cp.setMargins(dp(8),0,0,0); actions.addView(cart,cp);
            cart.setOnClickListener(v -> addToCart(o));
        }
        c.addView(actions);

        edit.setOnClickListener(v -> {
            if (canEdit()) editItem(index,product,dialog);
            else Toast.makeText(this, "Mechanic account is view-only for inventory", Toast.LENGTH_SHORT).show();
        });
        del.setOnClickListener(v -> {
            if (!canEdit()) { accessDenied(); return; }
            new AlertDialog.Builder(this).setTitle("DELETE ITEM?").setMessage(o.optString("name"))
                    .setNegativeButton("CANCEL",null).setPositiveButton("DELETE",(x,w)->{
                        JSONArray a=getArray(product?"products":"services");
                        if(index<a.length()){a.remove(index);saveArray(product?"products":"services",a);dialog.dismiss();showList(product);}
                    }).show();
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(7),0,dp(7)); c.setLayoutParams(lp);
        return c;
    }

    private void addToCart(JSONObject product) {
        try {
            int stock = product.optInt("stock",0);
            if(stock <= 0) {
                Toast.makeText(this,"OUT OF STOCK",Toast.LENGTH_SHORT).show();
                return;
            }
            JSONArray cart=getArray("cart");
            String name=product.optString("name");
            boolean found=false;
            for(int i=0;i<cart.length();i++) {
                JSONObject item=cart.optJSONObject(i);
                if(item != null && item.optString("name").equals(name)) {
                    item.put("qty", item.optInt("qty",1)+1);
                    found=true; break;
                }
            }
            if(!found) {
                JSONObject item=new JSONObject();
                item.put("name",name);
                item.put("price",Double.parseDouble(product.optString("price","0").replace(",","")));
                item.put("qty",1);
                cart.put(item);
            }
            prefs.edit().putString("cart",cart.toString()).apply();
            Toast.makeText(this,"Added to cart: "+name,Toast.LENGTH_SHORT).show();
        } catch(Exception e) {
            Toast.makeText(this,"Unable to add item to cart",Toast.LENGTH_SHORT).show();
        }
    }

    private void editItem(int index,boolean product,AlertDialog oldDialog) {
        JSONArray a=getArray(product?"products":"services");
        if(index>=a.length()) return;
        try {
            JSONObject o=a.getJSONObject(index);
            LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
            EditText name=field(product?"Product / Part Name":"Labor / Service Name"); name.setText(o.optString("name"));
            EditText brand=null,model=null,price=field("Price (₱)");
            price.setText(o.optString("price"));
            EditText stock=null;
            ImageView imagePreview=null;
            if(product){
                brand=field("Brand");brand.setText(o.optString("brand"));
                model=field("Model");model.setText(o.optString("model"));
                stock=field("Stocks");stock.setText(String.valueOf(o.optInt("stock",0)));stock.setInputType(2);
                form.addView(name);form.addView(brand);form.addView(model);form.addView(price);form.addView(stock);
                pendingImageUri=o.optString("imageUri","");
                LinearLayout imageRow=imagePickerRow();
                imagePreview=pickerPreview;
                if(!pendingImageUri.isEmpty()) { imagePreview.setImageURI(Uri.parse(pendingImageUri)); imagePreview.setVisibility(View.VISIBLE); }
                form.addView(imageRow);
                LinearLayout stockActions = row();
                TextView stockIn = button("STOCK IN +", GREEN);
                TextView stockOut = button("STOCK OUT −", RED);
                if (!canEdit()) { stockIn.setEnabled(false); stockOut.setEnabled(false); stockIn.setAlpha(0.35f); stockOut.setAlpha(0.35f); }
                stockActions.addView(stockIn, new LinearLayout.LayoutParams(0,dp(46),1));
                LinearLayout.LayoutParams sop = new LinearLayout.LayoutParams(0,dp(46),1); sop.setMargins(dp(8),0,0,0);
                stockActions.addView(stockOut, sop);
                form.addView(stockActions);
                final EditText stockField = stock;
                stockIn.setOnClickListener(v -> showStockAdjustDialog(o, index, true, stockField));
                stockOut.setOnClickListener(v -> showStockAdjustDialog(o, index, false, stockField));
            } else {
                form.addView(name); form.addView(price);
            }
            price.setInputType(2|8192);
            AlertDialog d=new AlertDialog.Builder(this).setTitle("EDIT "+(product?"PRODUCT":"SERVICE")).setView(form)
                    .setNegativeButton("CANCEL",null).setPositiveButton("SAVE",null).create();
            EditText finalBrand=brand, finalModel=model, finalStock=stock;
            d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{
                    if(name.getText().toString().trim().isEmpty()){name.setError("Required");return;}
                    o.put("name",name.getText().toString().trim()); o.put("price",price.getText().toString().trim());
                    if(product){
                        o.put("brand",finalBrand.getText().toString().trim());
                        o.put("model",finalModel.getText().toString().trim());
                        o.put("stock",Integer.parseInt(finalStock.getText().toString().trim()));
                        o.put("imageUri",pendingImageUri);
                    }
                    a.put(index,o);saveArray(product?"products":"services",a);d.dismiss();oldDialog.dismiss();showList(product);
                }catch(Exception e){if(finalStock!=null)finalStock.setError("Invalid stock");}
            }));
            d.show();
        }catch(Exception ignored){}
    }

    private void showStockAdjustDialog(JSONObject product, int index, boolean stockIn, EditText stockField) {
        EditText amount = field(stockIn ? "Quantity to add" : "Quantity to remove");
        amount.setInputType(2);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4),dp(4),dp(4),0);
        form.addView(tv(stockIn ? "STOCK IN" : "STOCK OUT", 14, stockIn ? GREEN : RED, true));
        form.addView(tv(product.optString("name") + "\nCurrent stock: " + product.optInt("stock",0), 12, MUTED, false));
        form.addView(amount);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(stockIn ? "ADD STOCK" : "REMOVE STOCK")
                .setView(form).setNegativeButton("CANCEL",null).setPositiveButton("CONFIRM",null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int qty = Integer.parseInt(amount.getText().toString().trim());
                if(qty <= 0) { amount.setError("Enter a quantity"); return; }
                int current = product.optInt("stock",0);
                if(!stockIn && qty > current) { amount.setError("Cannot remove more than current stock"); return; }
                int next = stockIn ? current + qty : current - qty;
                JSONArray products = getArray("products");
                if(index < products.length()) {
                    JSONObject p = products.getJSONObject(index);
                    p.put("stock", next);
                    saveArray("products", products);
                    saveStockHistory(p.optString("name"), stockIn ? "STOCK IN" : "STOCK OUT", qty, next);
                    stockField.setText(String.valueOf(next));
                    Toast.makeText(this, (stockIn ? "Stock added: " : "Stock removed: ") + qty, Toast.LENGTH_SHORT).show();
                    d.dismiss();
                }
            } catch(Exception e) { amount.setError("Invalid quantity"); }
        }));
        d.show();
    }

    private void saveStockHistory(String product, String type, int qty, int balance) {
        try {
            JSONArray history = getArray("stockHistory");
            JSONObject h = new JSONObject();
            h.put("product", product);
            h.put("type", type);
            h.put("qty", qty);
            h.put("balance", balance);
            h.put("date", new java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US).format(new java.util.Date()));
            history.put(h);
            prefs.edit().putString("stockHistory", history.toString()).apply();
            syncCloud("stock history");
        } catch(Exception ignored) {}
    }

    private void showSalesHistory() {
        JSONArray history = getArray("salesHistory");
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(10),dp(4),dp(10),dp(4));
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this); sv.addView(list); outer.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        if(history.length()==0) {
            list.addView(tv("No completed sales yet.",15,MUTED,false));
        } else {
            for(int i=history.length()-1;i>=0;i--) {
                JSONObject sale=history.optJSONObject(i); if(sale==null) continue;
                LinearLayout card=box(CARD,GREEN);
                TextView head=tv(sale.optString("receiptNo","RECEIPT"),15,WHITE,true);
                card.addView(head);
                card.addView(tv("Customer: "+sale.optString("customer","Walk-in Customer"),12,MUTED,false));
                card.addView(tv("Date: "+sale.optString("date"),12,MUTED,false));
                card.addView(tv("Total: ₱"+String.format(Locale.US,"%.2f",sale.optDouble("total",0)),16,WHITE,true));
                TextView view=button("VIEW RECEIPT",BLUE);
                LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(44)); vp.setMargins(0,dp(8),0,0); card.addView(view,vp);
                final JSONObject selectedSale=sale;
                view.setOnClickListener(v -> showSavedReceipt(selectedSale));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,dp(6),0,dp(6)); list.addView(card,cp);
            }
        }
        new AlertDialog.Builder(this).setTitle("SALES HISTORY").setView(outer).setPositiveButton("CLOSE",null).show();
    }

    private void showSavedReceipt(JSONObject sale) {
        try {
            showReceipt(sale.optJSONArray("items") == null ? new JSONArray() : sale.optJSONArray("items"),
                    sale.optString("customer","Walk-in Customer"), sale.optString("date"), sale.optDouble("total",0),
                    sale.optString("receiptNo","RECEIPT"));
        } catch(Exception ignored) {}
    }

    private void showCart() {
        final JSONArray cart=getArray("cart");
        if(cart.length()==0){
            new AlertDialog.Builder(this).setTitle("SHOPPING CART")
                    .setMessage("Your cart is empty.\n\nUse ADD TO CART from Products to add items.")
                    .setPositiveButton("CLOSE",null).show();
            return;
        }

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(10),dp(4),dp(10),dp(4));

        TextView hint=tv("SELECT ITEMS TO CHECK OUT",12,GREEN,true);
        hint.setPadding(dp(4),dp(6),dp(4),dp(8));
        outer.addView(hint);

        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(list);
        outer.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        TextView totalText=tv("TOTAL: ₱0.00",19,WHITE,true);
        totalText.setGravity(Gravity.CENTER);
        totalText.setPadding(dp(8),dp(12),dp(8),dp(12));
        totalText.setBackground(bg(Color.rgb(13,27,19),18,1,GREEN));
        LinearLayout.LayoutParams totalLp=new LinearLayout.LayoutParams(-1,dp(58));
        totalLp.setMargins(0,dp(10),0,dp(8));
        outer.addView(totalText,totalLp);

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("SHOPPING CART")
                .setView(outer).setNegativeButton("CLEAR CART",null)
                .setPositiveButton("CHECK OUT",null).create();

        java.util.ArrayList<CheckBox> checks=new java.util.ArrayList<>();
        java.util.ArrayList<JSONObject> items=new java.util.ArrayList<>();

        Runnable updateTotal=()->{
            double total=0;
            for(int i=0;i<checks.size();i++) if(checks.get(i).isChecked()){
                JSONObject o=items.get(i); total+=o.optDouble("price",0)*o.optInt("qty",1);
            }
            totalText.setText("TOTAL: ₱"+String.format(Locale.US,"%.2f",total));
        };

        for(int i=0;i<cart.length();i++){
            JSONObject o=cart.optJSONObject(i); if(o==null)continue;
            items.add(o);
            LinearLayout card=box(CARD,Color.rgb(55,60,68));
            LinearLayout r=row();
            CheckBox cb=new CheckBox(this);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
            checks.add(cb);
            r.addView(cb,new LinearLayout.LayoutParams(dp(48),dp(70)));

            String name=o.optString("name");
            int qty=o.optInt("qty",1);
            double price=o.optDouble("price",0);
            LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
            info.addView(tv(name,15,WHITE,true));
            info.addView(tv("₱"+String.format(Locale.US,"%.2f",price)+" × "+qty+"   =   ₱"+String.format(Locale.US,"%.2f",price*qty),13,MUTED,false));
            r.addView(info,new LinearLayout.LayoutParams(0,-2,1));

            TextView minus=button("−",Color.rgb(45,50,58));
            TextView plus=button("+",Color.rgb(45,50,58));
            minus.setTextSize(16); plus.setTextSize(16);
            r.addView(minus,new LinearLayout.LayoutParams(dp(42),dp(42)));
            r.addView(plus,new LinearLayout.LayoutParams(dp(42),dp(42)));
            card.addView(r);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
            cp.setMargins(0,dp(5),0,dp(5)); list.addView(card,cp);

            final JSONObject item=o;
            minus.setOnClickListener(v->{
                int q=item.optInt("qty",1);
                if(q>1){try{item.put("qty",q-1);}catch(Exception ignored){}}
                saveCartArray(itemsToArray(items));
                dialog.dismiss(); showCart();
            });
            plus.setOnClickListener(v->{
                int q=item.optInt("qty",1);
                int stock=findProductStock(item.optString("name"));
                if(q>=stock){Toast.makeText(this,"Maximum available stock: "+stock,Toast.LENGTH_SHORT).show();return;}
                try{item.put("qty",q+1);}catch(Exception ignored){}
                saveCartArray(itemsToArray(items));
                dialog.dismiss(); showCart();
            });
            cb.setOnCheckedChangeListener((buttonView,isChecked)->updateTotal.run());
        }

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            JSONArray selected=new JSONArray();
            for(int i=0;i<checks.size();i++) if(checks.get(i).isChecked()) selected.put(items.get(i));
            if(selected.length()==0){Toast.makeText(this,"Select at least one item to check out",Toast.LENGTH_SHORT).show();return;}
            showCheckoutCustomer(selected, dialog);
        }));
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("CLEAR CART?").setMessage("Remove all items from cart?")
                        .setNegativeButton("CANCEL",null).setPositiveButton("CLEAR",(a,b)->{prefs.edit().remove("cart").apply();dialog.dismiss();}).show();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                JSONArray selected=new JSONArray();
                for(int i=0;i<checks.size();i++) if(checks.get(i).isChecked()) selected.put(items.get(i));
                if(selected.length()==0){Toast.makeText(this,"Select at least one item to check out",Toast.LENGTH_SHORT).show();return;}
                showCheckoutCustomer(selected,dialog);
            });
        });
        dialog.show();
    }

    private JSONArray itemsToArray(java.util.ArrayList<JSONObject> items){
        JSONArray a=new JSONArray(); for(JSONObject o:items) a.put(o); return a;
    }

    private void saveCartArray(JSONArray a){prefs.edit().putString("cart",a.toString()).apply();}

    private int findProductStock(String name){
        JSONArray products=getArray("products");
        for(int i=0;i<products.length();i++){
            JSONObject p=products.optJSONObject(i);
            if(p!=null && p.optString("name").equals(name)) return p.optInt("stock",0);
        }
        return 0;
    }

    private void showCheckoutCustomer(JSONArray selected, AlertDialog cartDialog){
        EditText customer=field("Customer name");
        customer.setText("Walk-in Customer");
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4),dp(4),dp(4),0);
        form.addView(tv("CUSTOMER NAME",12,MUTED,true));
        form.addView(customer);
        String date=new java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a",Locale.US).format(new java.util.Date());
        TextView dateText=tv("DATE: "+date,13,MUTED,false); dateText.setPadding(dp(4),dp(8),dp(4),dp(4));
        form.addView(dateText);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("CHECK OUT")
                .setView(form).setNegativeButton("CANCEL",null).setPositiveButton("GENERATE RECEIPT",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=customer.getText().toString().trim();
            if(n.isEmpty()){customer.setError("Required");return;}
            processCheckout(selected,n,date,cartDialog,d);
        }));
        d.show();
    }

    private void processCheckout(JSONArray selected,String customer,String date,AlertDialog cartDialog,AlertDialog customerDialog){
        try{
            JSONArray cart=getArray("cart");
            JSONArray remaining=new JSONArray();
            double total=0;
            for(int i=0;i<selected.length();i++){
                JSONObject s=selected.getJSONObject(i);
                total+=s.optDouble("price",0)*s.optInt("qty",1);
            }
            for(int i=0;i<cart.length();i++){
                JSONObject c=cart.getJSONObject(i); boolean isSelected=false;
                for(int j=0;j<selected.length();j++) if(c.optString("name").equals(selected.getJSONObject(j).optString("name"))){isSelected=true;break;}
                if(!isSelected) remaining.put(c);
            }

            JSONArray products=getArray("products");
            for(int i=0;i<selected.length();i++){
                JSONObject s=selected.getJSONObject(i); String name=s.optString("name"); int qty=s.optInt("qty",1);
                for(int j=0;j<products.length();j++){
                    JSONObject p=products.optJSONObject(j);
                    if(p!=null && p.optString("name").equals(name)){
                        int stock=Math.max(0,p.optInt("stock",0)-qty); p.put("stock",stock); break;
                    }
                }
            }
            saveArray("products",products); saveCartArray(remaining);
            String receiptNo = "KS-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new java.util.Date());
            saveSaleHistory(receiptNo, customer, date, selected, total);
            customerDialog.dismiss(); cartDialog.dismiss();
            showReceipt(selected,customer,date,total,receiptNo);
        }catch(Exception e){Toast.makeText(this,"Checkout failed",Toast.LENGTH_SHORT).show();}
    }

    private void saveSaleHistory(String receiptNo, String customer, String date, JSONArray selected, double total) {
        try {
            JSONArray history = getArray("salesHistory");
            JSONObject sale = new JSONObject();
            sale.put("receiptNo", receiptNo);
            sale.put("customer", customer);
            sale.put("date", date);
            sale.put("total", total);
            sale.put("items", new JSONArray(selected.toString()));
            history.put(sale);
            prefs.edit().putString("salesHistory", history.toString()).apply();
            syncCloud("sales history");
        } catch(Exception ignored) {}
    }

    private void showReceipt(JSONArray selected,String customer,String date,double total){ showReceipt(selected,customer,date,total,"RECEIPT"); }

    private void showReceipt(JSONArray selected,String customer,String date,double total,String receiptNo){
        LinearLayout receipt=new LinearLayout(this); receipt.setOrientation(LinearLayout.VERTICAL);
        receipt.setPadding(dp(18),dp(18),dp(18),dp(18)); receipt.setBackground(bg(Color.WHITE,8,1,Color.rgb(210,210,210)));
        TextView shop=tv("KURT DHYLAN MOTO SHOP",20,Color.BLACK,true); shop.setGravity(Gravity.CENTER); receipt.addView(shop);
        TextView sub=tv("OFFICIAL SALES RECEIPT",12,Color.DKGRAY,true); sub.setGravity(Gravity.CENTER); receipt.addView(sub);
        receipt.addView(tv("--------------------------------",12,Color.DKGRAY,false));
        receipt.addView(tv("CUSTOMER: "+customer,13,Color.BLACK,true));
        receipt.addView(tv("RECEIPT NO: "+receiptNo,12,Color.DKGRAY,false));
        receipt.addView(tv("DATE: "+date,12,Color.DKGRAY,false));
        receipt.addView(tv("--------------------------------",12,Color.DKGRAY,false));
        for(int i=0;i<selected.length();i++){
            JSONObject o=selected.optJSONObject(i); if(o==null)continue;
            double p=o.optDouble("price",0); int q=o.optInt("qty",1);
            TextView line=tv(o.optString("name")+" ×"+q+"    ₱"+String.format(Locale.US,"%.2f",p*q),13,Color.BLACK,false);
            line.setPadding(0,dp(5),0,dp(5)); receipt.addView(line);
        }
        receipt.addView(tv("--------------------------------",12,Color.DKGRAY,false));
        TextView grand=tv("TOTAL AMOUNT     ₱"+String.format(Locale.US,"%.2f",total),18,Color.BLACK,true); grand.setGravity(Gravity.RIGHT); receipt.addView(grand);
        TextView thanks=tv("Thank you for your business!\nBUILT FOR BIKERS",12,Color.DKGRAY,false); thanks.setGravity(Gravity.CENTER); thanks.setPadding(0,dp(16),0,0); receipt.addView(thanks);
        ScrollView scroll=new ScrollView(this); scroll.addView(receipt);
        new AlertDialog.Builder(this).setTitle("RECEIPT").setView(scroll).setPositiveButton("DONE",null).show();
    }

    private void accessDenied() {
        Toast.makeText(this, "Mechanic account cannot edit inventory", Toast.LENGTH_SHORT).show();
    }

    private void showUserManagement() {
        if (!canEdit()) { accessDenied(); return; }
        JSONArray accounts = getAccounts();
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this); sv.addView(list); outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i=0;i<accounts.length();i++) {
            JSONObject a=accounts.optJSONObject(i); if(a==null) continue;
            LinearLayout card=box(CARD, a.optString("role").equals("MECHANIC") ? BLUE : GREEN);
            card.addView(tv(a.optString("name"),16,WHITE,true));
            card.addView(tv("Username: "+a.optString("username"),12,MUTED,false));
            card.addView(tv("Role: "+a.optString("role"),12,MUTED,false));
            TextView change=button("CHANGE PASSWORD",BLUE);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44)); cp.setMargins(0,dp(8),0,0); card.addView(change,cp);
            final int idx=i;
            change.setOnClickListener(v->changeUserPassword(idx));
            list.addView(card,new LinearLayout.LayoutParams(-1,-2));
        }
        TextView add=button("ADD USER",GREEN);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(48)); ap.setMargins(0,dp(10),0,0); outer.addView(add,ap);
        add.setOnClickListener(v->addUser());
        new AlertDialog.Builder(this).setTitle("USER ACCOUNTS").setView(outer).setPositiveButton("CLOSE",null).show();
    }

    private void addUser() {
        if (!canEdit()) { accessDenied(); return; }
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        EditText name=field("Display name"); EditText user=field("Username"); EditText pass=field("Password"); pass.setInputType(0x81);
        Spinner role=new Spinner(this); String[] roles={"ASSISTANT","MECHANIC"}; role.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,roles));
        form.addView(name); form.addView(user); form.addView(pass); form.addView(role);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("ADD USER").setView(form).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=name.getText().toString().trim(), u=user.getText().toString().trim(), p=pass.getText().toString();
            if(n.isEmpty()||u.isEmpty()||p.isEmpty()){Toast.makeText(this,"Complete all fields",Toast.LENGTH_SHORT).show();return;}
            JSONArray a=getAccounts(); for(int i=0;i<a.length();i++) if(a.optJSONObject(i).optString("username").equalsIgnoreCase(u)){user.setError("Username already exists");return;}
            if (cloudConfigured) {
                TextView saveButton = d.getButton(AlertDialog.BUTTON_POSITIVE);
                saveButton.setEnabled(false);
                Toast.makeText(this,"Creating cloud account...",Toast.LENGTH_SHORT).show();
                CloudBackend creator = new CloudBackend(BackendConfig.FIREBASE_API_KEY, BackendConfig.FIREBASE_PROJECT_ID, BackendConfig.SHOP_ID);
                creator.signUp(u,p,(auth,err)->runOnUiThread(()->{
                    saveButton.setEnabled(true);
                    if(err!=null){Toast.makeText(this,"Cloud account creation failed: "+err.getMessage(),Toast.LENGTH_LONG).show();return;}
                    try {
                        String newRole = role.getSelectedItem().toString();
                        String newUid = auth == null ? "" : auth.optString("localId", "");
                        if (!newUid.isEmpty()) cloud.writeMember(newUid, u, newRole, (ok, memberErr) -> {});
                        a.put(new JSONObject().put("name",n).put("username",u).put("password",p).put("role",newRole));
                        saveAccounts(a); d.dismiss(); Toast.makeText(this,"Cloud user added",Toast.LENGTH_SHORT).show();
                    } catch(Exception ignored) {}
                }));
            } else {
                try { a.put(new JSONObject().put("name",n).put("username",u).put("password",p).put("role",role.getSelectedItem().toString())); saveAccounts(a); d.dismiss(); Toast.makeText(this,"User added",Toast.LENGTH_SHORT).show(); } catch(Exception ignored){}
            }
        })); d.show();
    }

    private void changeUserPassword(int index) {
        JSONArray a=getAccounts(); JSONObject account=a.optJSONObject(index); if(account==null)return;
        if (cloudConfigured) {
            new AlertDialog.Builder(this).setTitle("CLOUD PASSWORD")
                    .setMessage("V1.5 cloud authentication keeps passwords inside Firebase Authentication. To change another user's cloud password, use the Firebase Console or a future admin backend. The shop database never stores passwords.")
                    .setPositiveButton("OK",null).show();
            return;
        }
        EditText pass=field("New password"); pass.setInputType(0x81);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("CHANGE PASSWORD").setMessage(account.optString("name")).setView(pass).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String p=pass.getText().toString();if(p.trim().isEmpty()){pass.setError("Required");return;}try{account.put("password",p);a.put(index,account);saveAccounts(a);d.dismiss();}catch(Exception ignored){}})); d.show();
    }

    private void manualCloudSync() {
        if (!cloudConfigured) return;
        Toast.makeText(this, "Downloading latest shop data...", Toast.LENGTH_SHORT).show();
        cloud.readState((state, err) -> runOnUiThread(() -> {
            if (err != null) {
                Toast.makeText(this, "Sync failed: " + err.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            if (state == null || state.trim().isEmpty()) {
                Toast.makeText(this, "Cloud shop is empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            applyCloudState(state);
            refreshCounts();
            Toast.makeText(this, "Latest cloud data loaded.", Toast.LENGTH_SHORT).show();
        }));
    }

    private void showSettings() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8),dp(4),dp(8),dp(4));
        form.addView(tv("SIGNED IN AS",11,MUTED,true));
        form.addView(tv(currentUsername+"  •  "+roleLabel(),16,WHITE,true));
        form.addView(tv("\nVersion 1.5\nCloud database: " + (cloudConfigured ? "ON" : "OFF") + "\nOffline cache: ON\nProducts: "+getArray("products").length()+"\nServices: "+getArray("services").length()+"\nCompleted sales: "+getArray("salesHistory").length()+"\nStock movements: "+getArray("stockHistory").length(),12,MUTED,false));
        if (canEdit()) {
            TextView users=button("MANAGE USER ACCOUNTS",BLUE); form.addView(users,new LinearLayout.LayoutParams(-1,dp(48))); users.setOnClickListener(v->showUserManagement());
        }
        TextView sync=button(cloudConfigured ? "SYNC NOW" : "CLOUD SYNC NOT CONFIGURED",GREEN);
        sync.setEnabled(cloudConfigured);
        form.addView(sync,new LinearLayout.LayoutParams(-1,dp(48)));
        sync.setOnClickListener(v->manualCloudSync());
        TextView logout=button("LOG OUT",RED); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48)); lp.setMargins(0,dp(10),0,0); form.addView(logout,lp); logout.setOnClickListener(v->logout());
        new AlertDialog.Builder(this).setTitle("SETTINGS").setView(form).setPositiveButton("CLOSE",null).show();
    }
}
