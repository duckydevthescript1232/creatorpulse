package com.creatorpulse.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LauncherActivity extends Activity {
    private static final String APP_URL = BuildConfig.WEB_APP_URL;
    private static final String UA = "CreatorPulseAndroid/1.3";
    private static final int BG = Color.rgb(7, 9, 15);
    private static final int LINE = Color.rgb(45, 49, 67);
    private static final int TEXT = Color.rgb(247, 248, 252);
    private static final int MUTED = Color.rgb(151, 157, 178);
    private static final int PURPLE = Color.rgb(132, 82, 247);
    private static final int PURPLE2 = Color.rgb(185, 159, 255);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTheme(R.style.Theme_CreatorPulse);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        CookieManager.getInstance().setAcceptCookie(true);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        showSplash();
    }

    private void showSplash() {
        root.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(34), dp(30), dp(34), dp(44));
        box.setAlpha(0f);

        TextView mark = mark(42);
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(116), dp(116));
        markLp.bottomMargin = dp(24);
        box.addView(mark, markLp);

        TextView name = wordmark(32);
        name.setGravity(Gravity.CENTER);
        box.addView(name, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView tagline = text("Analyze. Optimize. Grow.", MUTED, 13, false);
        tagline.setGravity(Gravity.CENTER);
        box.addView(tagline, new LinearLayout.LayoutParams(-1, dp(32)));

        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(42), dp(42));
        sp.topMargin = dp(36);
        box.addView(spinner, sp);

        TextView loading = text("Loading your creator workspace…", Color.rgb(107, 113, 134), 11, false);
        loading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, dp(34));
        llp.topMargin = dp(10);
        box.addView(loading, llp);

        root.addView(box, new FrameLayout.LayoutParams(-1, -1));
        box.animate().alpha(1f).setDuration(300).start();
        handler.postDelayed(() -> box.animate().alpha(0f).setDuration(260).withEndAction(this::afterSplash).start(), 1000);
    }

    private void afterSplash() {
        if (!hasNetwork()) {
            showMessage("You're offline", "CreatorPulse needs a connection for account sign-in and channel data.", "Try again", v -> showSplash());
            return;
        }
        if (hasSessionCookie()) openApp();
        else showLogin(null);
    }

    private void showLogin(String initialError) {
        ScrollView scroll = screen();
        LinearLayout p = page();
        brand(p);
        p.addView(kicker("WELCOME BACK"), margin(-1, -2, 0, 28, 0, 8));
        p.addView(title("Log in to CreatorPulse"));
        p.addView(copy("Your creator workspace, ideas, optimization tools and analytics in one app."), margin(-1, -2, 0, 8, 0, 20));
        TextView error = error(initialError);
        p.addView(error, margin(-1, -2, 0, 0, 0, 5));

        EditText email = input("Email address", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = input("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        p.addView(email, margin(-1, dp(56), 0, 8, 0, 8));
        p.addView(passwordField(password), margin(-1, dp(56), 0, 0, 0, 6));

        CheckBox remember = checkbox("Keep me signed in", true);
        p.addView(remember, margin(-1, dp(44), 0, 0, 0, 8));
        Button login = primary("Log in");
        p.addView(login, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView divider = text("────────  NEW TO CREATORPULSE?  ────────", Color.rgb(92, 98, 118), 10, false);
        divider.setGravity(Gravity.CENTER);
        p.addView(divider, margin(-1, -2, 0, 22, 0, 10));
        Button register = secondary("Create free account");
        register.setOnClickListener(v -> showRegister(null));
        p.addView(register, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView secure = text("Secure session • your password is never stored in this app", Color.rgb(95, 101, 121), 10, false);
        secure.setGravity(Gravity.CENTER);
        p.addView(secure, margin(-1, -2, 0, 16, 0, 10));

        View.OnClickListener submit = v -> {
            String e = email.getText().toString().trim();
            String pw = password.getText().toString();
            if (e.isEmpty() || pw.isEmpty()) { setError(error, "Enter your email and password."); return; }
            login.setEnabled(false);
            login.setText("Signing in…");
            setError(error, null);
            login(e, pw, remember.isChecked(), (ok, message) -> runOnUiThread(() -> {
                login.setEnabled(true);
                login.setText("Log in");
                if (!ok) { setError(error, message); return; }
                openApp();
            }));
        };
        login.setOnClickListener(submit);
        password.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { submit.onClick(v); return true; }
            return false;
        });

        scroll.addView(p);
        swap(scroll);
    }

    private void showRegister(String initialError) {
        ScrollView scroll = screen();
        LinearLayout p = page();
        brand(p);
        p.addView(kicker("FREE CREATOR ACCOUNT"), margin(-1, -2, 0, 28, 0, 8));
        p.addView(title("Create your account"));
        p.addView(copy("Join CreatorPulse, connect your @handle, and start building your personalized creator workspace."), margin(-1, -2, 0, 8, 0, 18));
        TextView error = error(initialError);
        p.addView(error, margin(-1, -2, 0, 0, 0, 5));

        EditText name = input("Full name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        EditText email = input("Email address", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = input("Password (8+ characters)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirm = input("Confirm password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm.setImeOptions(EditorInfo.IME_ACTION_DONE);
        p.addView(name, margin(-1, dp(56), 0, 8, 0, 8));
        p.addView(email, margin(-1, dp(56), 0, 0, 0, 8));
        p.addView(passwordField(password, confirm), margin(-1, dp(56), 0, 0, 0, 8));
        p.addView(confirm, margin(-1, dp(56), 0, 0, 0, 8));

        CheckBox terms = checkbox("I agree to the Terms and Privacy Policy", false);
        p.addView(terms, margin(-1, dp(48), 0, 0, 0, 8));
        Button create = primary("Create free account");
        p.addView(create, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView back = link("Already have an account? Log in");
        back.setOnClickListener(v -> showLogin(null));
        p.addView(back, margin(-1, -2, 0, 13, 0, 4));

        View.OnClickListener submit = v -> {
            String n = name.getText().toString().trim();
            String e = email.getText().toString().trim();
            String pw = password.getText().toString();
            String cpw = confirm.getText().toString();
            if (n.length() < 2) { setError(error, "Enter your full name."); return; }
            if (!e.contains("@")) { setError(error, "Enter a valid email address."); return; }
            if (pw.length() < 8) { setError(error, "Password must be at least 8 characters."); return; }
            if (!pw.equals(cpw)) { setError(error, "Passwords do not match."); return; }
            if (!terms.isChecked()) { setError(error, "Accept the Terms and Privacy Policy to continue."); return; }
            create.setEnabled(false);
            create.setText("Creating account…");
            setError(error, null);
            register(n, e, pw, (ok, message) -> runOnUiThread(() -> {
                create.setEnabled(true);
                create.setText("Create free account");
                if (!ok) { setError(error, message); return; }
                openApp();
            }));
        };
        create.setOnClickListener(submit);
        confirm.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { submit.onClick(v); return true; }
            return false;
        });

        scroll.addView(p);
        swap(scroll);
    }

    private LinearLayout passwordField(EditText password, EditText... linked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(password, new LinearLayout.LayoutParams(0, -1, 1));
        TextView toggle = pill("Show");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(72), dp(42));
        tlp.setMargins(dp(8), 0, 0, 0);
        row.addView(toggle, tlp);
        final boolean[] visible = { false };
        toggle.setOnClickListener(v -> {
            visible[0] = !visible[0];
            setPasswordVisible(password, visible[0]);
            for (EditText field : linked) setPasswordVisible(field, visible[0]);
            toggle.setText(visible[0] ? "Hide" : "Show");
        });
        return row;
    }

    private void setPasswordVisible(EditText field, boolean visible) {
        int pos = field.getSelectionStart();
        field.setInputType(InputType.TYPE_CLASS_TEXT | (visible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        field.setTypeface(Typeface.DEFAULT);
        if (pos >= 0 && pos <= field.length()) field.setSelection(pos);
    }

    private interface Result { void done(boolean ok, String message); }

    private void login(String email, String password, boolean remember, Result result) {
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = api("/api/auth/login");
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                body.put("remember", remember);
                write(c, body.toString());
                int status = c.getResponseCode();
                saveCookies(c);
                JSONObject json = json(c, status);
                if (status >= 200 && status < 300 && json.optBoolean("ok", false)) result.done(true, null);
                else result.done(false, json.optString("error", "Incorrect email or password."));
            } catch (Exception error) {
                result.done(false, hasNetwork() ? "CreatorPulse could not reach sign-in. Try again." : "You are offline.");
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private void register(String name, String email, String password, Result result) {
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = api("/api/auth/register");
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("email", email);
                body.put("password", password);
                body.put("acceptedTerms", true);
                write(c, body.toString());
                int status = c.getResponseCode();
                saveCookies(c);
                JSONObject json = json(c, status);
                if (status >= 200 && status < 300 && json.optBoolean("ok", false)) result.done(true, null);
                else if (status == 404) result.done(false, "Registration is not enabled on the CreatorPulse server yet.");
                else result.done(false, json.optString("error", "CreatorPulse could not create your account."));
            } catch (Exception error) {
                result.done(false, hasNetwork() ? "CreatorPulse could not create your account. Try again." : "You are offline.");
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private HttpURLConnection api(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(APP_URL + path).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setDoInput(true);
        c.setDoOutput(true);
        c.setUseCaches(false);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Origin", APP_URL);
        c.setRequestProperty("Referer", APP_URL + "/");
        c.setRequestProperty("User-Agent", UA);
        String cookie = CookieManager.getInstance().getCookie(APP_URL);
        if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        return c;
    }

    private void write(HttpURLConnection c, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
    }

    private JSONObject json(HttpURLConnection c, int status) throws Exception {
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) return new JSONObject();
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        return new JSONObject(text.length() == 0 ? "{}" : text.toString());
    }

    private void saveCookies(HttpURLConnection c) {
        CookieManager manager = CookieManager.getInstance();
        for (Map.Entry<String, List<String>> entry : c.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                for (String cookie : entry.getValue()) if (cookie != null) manager.setCookie(APP_URL, cookie);
            }
        }
        manager.flush();
    }

    private boolean hasSessionCookie() {
        String cookie = CookieManager.getInstance().getCookie(APP_URL);
        return cookie != null && cookie.contains("creatorpulse_session=");
    }

    private void openApp() {
        CookieManager.getInstance().flush();
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private ScrollView screen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        return scroll;
    }

    private LinearLayout page() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(22), dp(24), dp(22), dp(36));
        p.setBackgroundColor(BG);
        return p;
    }

    private void brand(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(mark(18), new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView name = wordmark(21);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, dp(44), 1);
        nlp.setMargins(dp(11), 0, 0, 0);
        row.addView(name, nlp);
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private TextView mark(int size) {
        TextView t = text("▮▮▮", Color.WHITE, size, true);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(.05f);
        t.setBackground(shape(Color.rgb(24, 17, 47), 23, Color.rgb(74, 51, 128)));
        t.setShadowLayer(dp(10), 0, 0, PURPLE);
        return t;
    }

    private TextView wordmark(int size) {
        TextView t = new TextView(this);
        SpannableString s = new SpannableString("CreatorPulse");
        s.setSpan(new ForegroundColorSpan(TEXT), 0, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(PURPLE2), 7, 12, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, 12, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        t.setText(s);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView kicker(String value) { TextView t = text(value, PURPLE2, 10, true); t.setLetterSpacing(.12f); return t; }
    private TextView title(String value) { TextView t = text(value, TEXT, 30, true); t.setLineSpacing(0, 1.04f); return t; }
    private TextView copy(String value) { TextView t = text(value, MUTED, 14, false); t.setLineSpacing(dp(2), 1.16f); return t; }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(103, 109, 130));
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setPadding(dp(16), 0, dp(16), 0);
        e.setBackground(shape(Color.rgb(22, 25, 37), 11, LINE));
        return e;
    }

    private CheckBox checkbox(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(MUTED);
        box.setTextSize(12);
        box.setChecked(checked);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        return box;
    }

    private Button primary(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(shape(PURPLE, 12, -1));
        return b;
    }

    private Button secondary(String value) {
        Button b = primary(value);
        b.setTextColor(Color.rgb(231, 225, 255));
        b.setBackground(shape(Color.rgb(23, 21, 39), 12, Color.rgb(70, 59, 108)));
        return b;
    }

    private TextView pill(String value) {
        TextView t = text(value, Color.rgb(224, 217, 255), 11, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(Color.rgb(27, 24, 42), 11, Color.rgb(69, 58, 104)));
        return t;
    }

    private TextView link(String value) {
        TextView t = text(value, PURPLE2, 13, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(12), dp(8), dp(12));
        return t;
    }

    private TextView error(String value) {
        TextView t = text(value == null ? "" : value, Color.rgb(248, 128, 135), 12, false);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setBackground(shape(Color.rgb(44, 20, 29), 9, Color.rgb(94, 43, 56)));
        setError(t, value);
        return t;
    }

    private void setError(TextView view, String value) {
        view.setText(value == null ? "" : value);
        view.setVisibility(value == null || value.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showMessage(String heading, String body, String buttonText, View.OnClickListener action) {
        LinearLayout p = page();
        p.setGravity(Gravity.CENTER);
        p.addView(mark(28), new LinearLayout.LayoutParams(dp(80), dp(80)));
        TextView h = title(heading);
        h.setGravity(Gravity.CENTER);
        p.addView(h, margin(-1, -2, 0, 24, 0, 8));
        TextView c = copy(body);
        c.setGravity(Gravity.CENTER);
        p.addView(c, margin(-1, -2, 0, 0, 0, 18));
        Button b = primary(buttonText);
        b.setOnClickListener(action);
        p.addView(b, new LinearLayout.LayoutParams(-1, dp(54)));
        swap(p);
    }

    private void swap(View next) {
        root.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            root.removeAllViews();
            root.addView(next, new FrameLayout.LayoutParams(-1, -1));
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(220).start();
        }).start();
    }

    private TextView text(String value, int color, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != -1) d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams margin(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private boolean hasNetwork() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
