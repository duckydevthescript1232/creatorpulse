package com.creatorpulse.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.SafeBrowsingResponse;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String APP_URL = BuildConfig.WEB_APP_URL;
    private static final String APP_HOST = Uri.parse(APP_URL).getHost();
    private static final String ANDROID_UA = "CreatorPulseAndroid/1.1";
    private static final int FILE_CHOOSER_REQUEST = 2401;

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private FrameLayout nativeLayer;
    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileChooserCallback;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean nativeLoginVisible;
    private boolean offline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(com.creatorpulse.app.R.style.Theme_CreatorPulse);
        getWindow().setStatusBarColor(Color.rgb(11, 12, 18));
        getWindow().setNavigationBarColor(Color.rgb(11, 12, 18));

        root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(11, 12, 18));
        nativeLayer = new FrameLayout(this);
        nativeLayer.setBackgroundColor(Color.rgb(11, 12, 18));
        nativeLayer.setVisibility(View.GONE);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(139, 92, 246)));

        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(nativeLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        p.gravity = Gravity.TOP;
        root.addView(progressBar, p);
        setContentView(root);

        configureWebView();
        registerNetworkRecovery();
        handleIntent(getIntent(), savedInstanceState == null);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString() + " " + ANDROID_UA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progressBar.setProgress(value);
                progressBar.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return route(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return route(Uri.parse(url)); }
            @Override public void onPageFinished(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (isCreatorPulse(uri)) {
                    offline = false;
                    CookieManager.getInstance().flush();
                    view.evaluateJavascript("document.documentElement.classList.add('creatorpulse-android-app');", null);
                    String path = uri.getPath() == null ? "" : uri.getPath();
                    if ("/login".equals(path) && !nativeLoginVisible) showLogin(uri.getQueryParameter("returnTo"), null);
                    else if (!"/login".equals(path)) hideNative();
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !hasNetwork()) showOffline();
            }
            @Override public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType, SafeBrowsingResponse callback) {
                callback.backToSafety(true);
                Toast.makeText(MainActivity.this, "CreatorPulse blocked an unsafe page.", Toast.LENGTH_LONG).show();
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> download(url, userAgent, contentDisposition, mimeType));
    }

    private void showLogin(String returnTo, String initialError) {
        nativeLoginVisible = true;
        nativeLayer.removeAllViews();
        nativeLayer.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = box();
        addHeading(box, "Sign in", "Sign in to your real CreatorPulse account. The Android app stores the secure session cookie, never your password.");
        TextView error = errorView(initialError);
        box.addView(error);
        EditText email = input("Email address", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = input("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        CheckBox remember = new CheckBox(this);
        remember.setText("Remember me");
        remember.setTextColor(Color.rgb(200, 203, 216));
        remember.setChecked(true);
        Button signIn = button("Sign in");
        TextView fallback = link("Use website sign-in instead");
        box.addView(email);
        box.addView(password);
        box.addView(remember, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 6, 0, 8));
        box.addView(signIn, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(54), 0, 12, 0, 0));
        box.addView(fallback, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 14, 0, 0));
        scroll.addView(box);
        nativeLayer.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View.OnClickListener submit = v -> {
            String e = email.getText().toString().trim();
            String p = password.getText().toString();
            if (e.isEmpty() || p.isEmpty()) { setError(error, "Enter your email and password."); return; }
            signIn.setEnabled(false); signIn.setText("Signing in…"); setError(error, null);
            login(e, p, remember.isChecked(), returnTo, (dest, failure) -> runOnUiThread(() -> {
                signIn.setEnabled(true); signIn.setText("Sign in");
                if (failure != null) { setError(error, failure); return; }
                nativeLoginVisible = false;
                if (dest != null && dest.startsWith("/channel-setup")) showChannelSetup();
                else { hideNative(); load(dest == null ? "/dashboard" : dest); }
            }));
        };
        signIn.setOnClickListener(submit);
        password.setOnEditorActionListener((v, actionId, event) -> { if (actionId == EditorInfo.IME_ACTION_DONE) { submit.onClick(v); return true; } return false; });
        fallback.setOnClickListener(v -> { nativeLoginVisible = false; hideNative(); });
    }

    private void showChannelSetup() {
        nativeLoginVisible = false;
        nativeLayer.removeAllViews();
        nativeLayer.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = box();
        addHeading(box, "Connect your YouTube channel", "Enter an @handle or YouTube channel URL. CreatorPulse resolves the real channel and saves its permanent channel ID to your account.");
        TextView error = errorView(null);
        EditText handle = input("@yourhandle", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        handle.setImeOptions(EditorInfo.IME_ACTION_GO);
        Button connect = button("Find & connect channel");
        TextView fullSetup = link("Open full channel setup");
        box.addView(error); box.addView(handle); box.addView(connect, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(54), 0, 12, 0, 0)); box.addView(fullSetup, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 14, 0, 0));
        scroll.addView(box); nativeLayer.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View.OnClickListener submit = v -> {
            String value = handle.getText().toString().trim();
            if (value.isEmpty()) { setError(error, "Enter a YouTube @handle or channel URL."); return; }
            connect.setEnabled(false); connect.setText("Connecting…"); setError(error, null);
            connectChannel(value, (dest, failure) -> runOnUiThread(() -> {
                connect.setEnabled(true); connect.setText("Find & connect channel");
                if (failure != null) { setError(error, failure); return; }
                hideNative(); load(dest == null ? "/dashboard" : dest);
            }));
        };
        connect.setOnClickListener(submit);
        handle.setOnEditorActionListener((v, actionId, event) -> { if (actionId == EditorInfo.IME_ACTION_GO) { submit.onClick(v); return true; } return false; });
        fullSetup.setOnClickListener(v -> { hideNative(); load("/channel-setup"); });
    }

    private interface Callback { void done(String destination, String error); }

    private void login(String email, String password, boolean remember, String returnTo, Callback callback) {
        network.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = api("/api/auth/login", "POST");
                c.setRequestProperty("Content-Type", "application/json");
                JSONObject body = new JSONObject();
                body.put("email", email); body.put("password", password); body.put("remember", remember);
                if (returnTo != null && returnTo.startsWith("/") && !returnTo.startsWith("//")) body.put("returnTo", returnTo);
                write(c, body.toString());
                int status = c.getResponseCode(); saveCookies(c);
                JSONObject json = new JSONObject(read(c, status).isEmpty() ? "{}" : read(c, status));
                if (status >= 200 && status < 300 && json.optBoolean("ok", false)) callback.done(json.optString("destination", "/dashboard"), null);
                else callback.done(null, json.optString("error", "CreatorPulse could not sign you in."));
            } catch (Exception e) { callback.done(null, hasNetwork() ? "CreatorPulse could not reach sign-in. Try again." : "You are offline."); }
            finally { if (c != null) c.disconnect(); }
        });
    }

    private void connectChannel(String channel, Callback callback) {
        network.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = api("/api/channel/analyze", "POST");
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                write(c, "channel=" + Uri.encode(channel));
                int status = c.getResponseCode(); saveCookies(c);
                String location = c.getHeaderField("Location");
                if (status >= 300 && status < 400 && location != null) {
                    Uri target = Uri.parse(location);
                    String path = target.getPath() == null ? "" : target.getPath();
                    if ("/login".equals(path)) { callback.done(null, "Your session expired. Sign in again."); runOnUiThread(() -> showLogin("/channel-setup", "Your session expired.")); return; }
                    if ("/channel-setup".equals(path)) { callback.done(null, channelError(target.getQueryParameter("channel"))); return; }
                    callback.done(relative(target), null); return;
                }
                callback.done(null, status == 403 ? "The app session was rejected. Sign in again." : "CreatorPulse could not connect that channel.");
            } catch (Exception e) { callback.done(null, hasNetwork() ? "CreatorPulse could not reach YouTube right now." : "You are offline."); }
            finally { if (c != null) c.disconnect(); }
        });
    }

    private HttpURLConnection api(String path, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(APP_URL + path).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setDoInput(true);
        c.setRequestProperty("Accept", "application/json, text/plain, */*"); c.setRequestProperty("Origin", APP_URL); c.setRequestProperty("Referer", APP_URL + "/"); c.setRequestProperty("User-Agent", ANDROID_UA);
        String cookie = CookieManager.getInstance().getCookie(APP_URL); if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        if (!"GET".equals(method)) c.setDoOutput(true);
        return c;
    }

    private void write(HttpURLConnection c, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
    }

    private String read(HttpURLConnection c, int status) throws Exception {
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream(); if (in == null) return "";
        StringBuilder b = new StringBuilder(); try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) { String line; while ((line = r.readLine()) != null) b.append(line); }
        return b.toString();
    }

    private void saveCookies(HttpURLConnection c) {
        CookieManager cm = CookieManager.getInstance();
        for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase("Set-Cookie")) for (String v : e.getValue()) if (v != null) cm.setCookie(APP_URL, v);
        cm.flush();
    }

    private String channelError(String code) {
        if ("invalid_url".equals(code)) return "Use an @handle, youtube.com/@handle, or YouTube channel URL.";
        if ("not_found".equals(code)) return "CreatorPulse could not find that channel. Check the handle.";
        if ("temporarily_unavailable".equals(code)) return "YouTube is temporarily unavailable. Try again shortly.";
        return "CreatorPulse could not connect that channel.";
    }

    private String relative(Uri uri) {
        if (isCreatorPulse(uri)) return (uri.getEncodedPath() == null ? "/dashboard" : uri.getEncodedPath()) + (uri.getEncodedQuery() == null ? "" : "?" + uri.getEncodedQuery());
        if (uri.getScheme() == null) return uri.toString().startsWith("/") ? uri.toString() : "/" + uri;
        return "/dashboard";
    }

    private LinearLayout box() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER_HORIZONTAL); box.setPadding(dp(24), dp(48), dp(24), dp(40)); return box;
    }
    private void addHeading(LinearLayout box, String title, String copy) {
        TextView brand = new TextView(this); brand.setText("CREATORPULSE"); brand.setTextColor(Color.rgb(167,139,250)); brand.setTextSize(13); brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD); brand.setLetterSpacing(.12f); box.addView(brand, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 24, 0, 8));
        TextView h = new TextView(this); h.setText(title); h.setTextColor(Color.WHITE); h.setTextSize(30); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); box.addView(h, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 8));
        TextView p = new TextView(this); p.setText(copy); p.setTextColor(Color.rgb(157,163,181)); p.setTextSize(15); p.setLineSpacing(0,1.2f); box.addView(p, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 20));
    }
    private EditText input(String hint, int type) {
        EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(Color.rgb(111,116,135)); e.setTextColor(Color.WHITE); e.setTextSize(16); e.setInputType(type); e.setSingleLine(true); e.setPadding(dp(16),0,dp(16),0); e.setBackgroundColor(Color.rgb(20,22,34)); e.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT,dp(56),0,7,0,7)); return e;
    }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(16); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackgroundColor(Color.rgb(124,78,235)); return b; }
    private TextView link(String text) { TextView t = new TextView(this); t.setText(text); t.setTextColor(Color.rgb(167,139,250)); t.setTextSize(14); t.setGravity(Gravity.CENTER); t.setPadding(dp(8),dp(12),dp(8),dp(12)); return t; }
    private TextView errorView(String text) { TextView t = new TextView(this); t.setTextColor(Color.rgb(248,113,113)); t.setTextSize(14); setError(t,text); return t; }
    private void setError(TextView t, String text) { t.setText(text == null ? "" : text); t.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE); }
    private LinearLayout.LayoutParams lp(int w,int h,int l,int top,int r,int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(top),dp(r),dp(bottom)); return p; }

    private void hideNative() { nativeLoginVisible = false; nativeLayer.setVisibility(View.GONE); nativeLayer.removeAllViews(); webView.setVisibility(View.VISIBLE); }
    private void load(String target) { webView.loadUrl(target.startsWith("http") ? target : APP_URL + (target.startsWith("/") ? target : "/" + target)); }
    private boolean route(Uri uri) {
        if (uri == null) return false; String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
        if ("creatorpulse".equals(scheme)) { oauthReturn(uri); return true; }
        if (isCreatorPulse(uri)) return false;
        if ("http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme) || "tel".equals(scheme)) { try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); } catch (ActivityNotFoundException e) { Toast.makeText(this,"No app can open this link.",Toast.LENGTH_LONG).show(); } return true; }
        return true;
    }
    private boolean isCreatorPulse(Uri uri) { String h = uri.getHost(); return h != null && APP_HOST != null && h.equalsIgnoreCase(APP_HOST) && "https".equalsIgnoreCase(uri.getScheme()); }
    private void oauthReturn(Uri uri) { String status = uri.getQueryParameter("status"); hideNative(); load("/settings?youtube=" + Uri.encode(status == null ? "connection_finished" : status)); }
    private void handleIntent(Intent intent, boolean initial) { Uri data = intent == null ? null : intent.getData(); if (data != null && "creatorpulse".equalsIgnoreCase(data.getScheme())) oauthReturn(data); else if (initial) { if (hasNetwork()) load("/dashboard"); else showOffline(); } }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent,false); }

    private void showOffline() { offline = true; hideNative(); webView.loadUrl("file:///android_asset/offline.html"); }
    private boolean hasNetwork() { ConnectivityManager m = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE); Network n = m.getActiveNetwork(); if (n == null) return false; NetworkCapabilities c = m.getNetworkCapabilities(n); return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET); }
    private void registerNetworkRecovery() { ConnectivityManager m = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE); networkCallback = new ConnectivityManager.NetworkCallback(){ @Override public void onAvailable(Network n){ runOnUiThread(() -> { if (offline) load("/dashboard"); }); }}; try { m.registerDefaultNetworkCallback(networkCallback); } catch (Exception ignored) {} }

    private void download(String url,String userAgent,String contentDisposition,String mimeType) {
        if (!url.startsWith("https://")) { Toast.makeText(this,"Only secure downloads are allowed.",Toast.LENGTH_LONG).show(); return; }
        try { DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url)); r.setMimeType(mimeType); r.addRequestHeader("User-Agent",userAgent); String cookie = CookieManager.getInstance().getCookie(url); if (cookie != null) r.addRequestHeader("Cookie",cookie); String name = android.webkit.URLUtil.guessFileName(url,contentDisposition,mimeType); r.setTitle(name); r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name); ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r); } catch (Exception e) { Toast.makeText(this,"Download could not be started.",Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){ super.onActivityResult(requestCode,resultCode,data); if(requestCode==FILE_CHOOSER_REQUEST){ Uri[] results=WebChromeClient.FileChooserParams.parseResult(resultCode,data); if(fileChooserCallback!=null) fileChooserCallback.onReceiveValue(results); fileChooserCallback=null; }}
    @Override protected void onPause(){ CookieManager.getInstance().flush(); if(webView!=null) webView.onPause(); super.onPause(); }
    @Override protected void onResume(){ super.onResume(); if(webView!=null) webView.onResume(); }
    @Override public void onBackPressed(){ if(nativeLayer.getVisibility()==View.VISIBLE){ hideNative(); load("/dashboard"); } else if(webView!=null&&webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy(){ network.shutdownNow(); if(networkCallback!=null) try{((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkCallback);}catch(Exception ignored){} if(webView!=null){CookieManager.getInstance().flush();webView.stopLoading();webView.setWebChromeClient(null);webView.setWebViewClient(null);webView.destroy();} super.onDestroy(); }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
