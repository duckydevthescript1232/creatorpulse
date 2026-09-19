package nl.creatorforge.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "creatorforge";
    private static final String KEY_URL = "server_url";
    private static final String DEFAULT_URL = "http://192.168.1.67:3005";

    private FrameLayout root;
    private WebView webView;
    private LinearLayout connectPanel;
    private EditText urlField;
    private View loadingView;
    private SharedPreferences prefs;
    private String serverUrl;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11,12,15));
        getWindow().setNavigationBarColor(Color.rgb(11,12,15));
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverUrl = normalize(prefs.getString(KEY_URL, DEFAULT_URL));
        buildUi();
        setupWebView();
        load(serverUrl);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(11,12,15));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(11,12,15));
        root.addView(webView, new FrameLayout.LayoutParams(-1,-1));

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setBackgroundColor(Color.rgb(11,12,15));
        TextView brand = text("CreatorForge", 30, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        loading.addView(brand);
        TextView sub = text("Opening CreatorForge…", 14, Color.rgb(163,167,176), false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2,-2);
        slp.topMargin = dp(10);
        loading.addView(sub, slp);
        ProgressBar bar = new ProgressBar(this);
        if (android.os.Build.VERSION.SDK_INT >= 21) bar.getIndeterminateDrawable().setTint(Color.rgb(255,107,44));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(36),dp(36));
        blp.topMargin = dp(18);
        loading.addView(bar, blp);
        loadingView = loading;
        root.addView(loading, new FrameLayout.LayoutParams(-1,-1));

        connectPanel = new LinearLayout(this);
        connectPanel.setOrientation(LinearLayout.VERTICAL);
        connectPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        connectPanel.setPadding(dp(26), dp(46), dp(26), dp(30));
        connectPanel.setBackgroundColor(Color.rgb(11,12,15));
        connectPanel.setVisibility(View.GONE);

        TextView cf = text("CF", 36, Color.rgb(255,107,44), true);
        cf.setGravity(Gravity.CENTER);
        connectPanel.addView(cf);

        TextView title = text("Connect to CreatorForge", 25, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1,-2);
        tlp.topMargin = dp(14);
        connectPanel.addView(title, tlp);

        TextView help = text("Run the CreatorForge phone-test server on your PC, keep both devices on the same Wi-Fi, then enter the PC address below.", 14, Color.rgb(163,167,176), false);
        help.setGravity(Gravity.CENTER);
        help.setLineSpacing(0,1.2f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1,-2);
        hlp.topMargin = dp(14);
        connectPanel.addView(help, hlp);

        urlField = new EditText(this);
        urlField.setSingleLine(true);
        urlField.setText(serverUrl);
        urlField.setTextColor(Color.WHITE);
        urlField.setHintTextColor(Color.rgb(110,114,124));
        urlField.setHint("http://192.168.x.x:3005");
        urlField.setBackgroundColor(Color.rgb(23,25,31));
        urlField.setPadding(dp(16),dp(14),dp(16),dp(14));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(-1,-2);
        ulp.topMargin = dp(24);
        connectPanel.addView(urlField, ulp);

        Button connect = new Button(this);
        connect.setText("CONNECT");
        connect.setTextColor(Color.WHITE);
        connect.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        connect.setBackgroundColor(Color.rgb(255,107,44));
        connect.setOnClickListener(v -> {
            String u = normalize(urlField.getText().toString());
            if (u.isEmpty()) return;
            serverUrl = u;
            prefs.edit().putString(KEY_URL,u).apply();
            load(u);
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1,dp(54));
        clp.topMargin = dp(14);
        connectPanel.addView(connect, clp);

        Button wifi = new Button(this);
        wifi.setText("WI-FI SETTINGS");
        wifi.setTextColor(Color.WHITE);
        wifi.setBackgroundColor(Color.rgb(23,25,31));
        wifi.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1,dp(50));
        wlp.topMargin = dp(10);
        connectPanel.addView(wifi, wlp);

        root.addView(connectPanel, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                if (p >= 100 && connectPanel.getVisibility() != View.VISIBLE) loadingView.setVisibility(View.GONE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return handle(r.getUrl());
            }
            @Override @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String u) {
                return handle(Uri.parse(u));
            }
            @Override public void onPageFinished(WebView v, String u) {
                if (u != null && !u.startsWith("about:")) {
                    loadingView.setVisibility(View.GONE);
                    connectPanel.setVisibility(View.GONE);
                }
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) showConnect();
            }
        });
    }

    private boolean handle(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();

        if ("market".equals(scheme)) {
            external(uri); return true;
        }
        if ("intent".equals(scheme)) {
            try { startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)); }
            catch (Exception e) { Toast.makeText(this,"Could not open app link.",Toast.LENGTH_SHORT).show(); }
            return true;
        }
        if (("http".equals(scheme) || "https".equals(scheme)) && "play.google.com".equals(host)) {
            String id = uri.getQueryParameter("id");
            if (id != null && !id.isEmpty()) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Uri.encode(id)))); }
                catch (ActivityNotFoundException e) { external(uri); }
            } else external(uri);
            return true;
        }
        if (!("http".equals(scheme) || "https".equals(scheme) || "about".equals(scheme))) {
            external(uri); return true;
        }
        return false;
    }

    private void external(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException e) { Toast.makeText(this,"No app can open this link.",Toast.LENGTH_SHORT).show(); }
    }

    private void load(String u) {
        serverUrl = normalize(u);
        urlField.setText(serverUrl);
        connectPanel.setVisibility(View.GONE);
        loadingView.setVisibility(View.VISIBLE);
        loadingView.bringToFront();
        webView.loadUrl(serverUrl);
    }

    private String normalize(String raw) {
        if (raw == null) return "";
        String u = raw.trim();
        if (u.isEmpty()) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0,u.length()-1);
        return u;
    }

    private void showConnect() {
        runOnUiThread(() -> {
            loadingView.setVisibility(View.GONE);
            urlField.setText(serverUrl);
            connectPanel.setVisibility(View.VISIBLE);
            connectPanel.bringToFront();
        });
    }

    @Override @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (connectPanel.getVisibility() == View.VISIBLE) {
            if (webView.getUrl() == null) return;
            connectPanel.setVisibility(View.GONE);
            return;
        }
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
    }
}
