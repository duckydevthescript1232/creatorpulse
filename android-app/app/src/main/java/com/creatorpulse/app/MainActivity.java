package com.creatorpulse.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private static final String UA = "CreatorPulseAndroid/1.2";
    private static final int FILE_PICKER = 2401;
    private static final int BG = Color.rgb(8,10,16), SURFACE = Color.rgb(18,20,30), LINE = Color.rgb(43,45,61);
    private static final int TEXT = Color.rgb(244,245,249), MUTED = Color.rgb(153,158,177), PURPLE = Color.rgb(128,91,246), PURPLE2 = Color.rgb(181,156,255);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private FrameLayout root, content, overlay;
    private WebView web;
    private LinearLayout top, bottom;
    private ProgressBar progress;
    private ValueCallback<Uri[]> chooser;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean nativeOpen, offline;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTheme(R.style.Theme_CreatorPulse);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        root = new FrameLayout(this); root.setBackgroundColor(BG);
        content = new FrameLayout(this); web = new WebView(this); web.setBackgroundColor(BG);
        content.addView(web, match());
        top = topBar(); bottom = bottomBar();
        overlay = new FrameLayout(this); overlay.setBackgroundColor(BG); overlay.setVisibility(View.GONE);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        root.addView(content, match());
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, dp(58)); tp.gravity = Gravity.TOP; root.addView(top,tp);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(68)); bp.gravity = Gravity.BOTTOM; root.addView(bottom,bp);
        root.addView(overlay, match());
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, dp(3)); pp.gravity = Gravity.TOP; root.addView(progress,pp);
        setContentView(root); chrome(false); configureWeb(); registerNetwork(); handleIntent(getIntent(), state == null);
    }

    @SuppressLint("SetJavaScriptEnabled") private void configureWeb() {
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false); s.setAllowContentAccess(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setMediaPlaybackRequiresUserGesture(false); s.setSupportZoom(false);
        s.setUserAgentString(s.getUserAgentString()+" "+UA); if(Build.VERSION.SDK_INT>=26)s.setSafeBrowsingEnabled(true);
        CookieManager cm=CookieManager.getInstance(); cm.setAcceptCookie(true); cm.setAcceptThirdPartyCookies(web,false);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){progress.setProgress(p);progress.setVisibility(p>=100?View.GONE:View.VISIBLE);}
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){if(chooser!=null)chooser.onReceiveValue(null);chooser=cb;try{startActivityForResult(p.createIntent(),FILE_PICKER);return true;}catch(ActivityNotFoundException e){chooser=null;return false;}}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return route(r.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String u){return route(Uri.parse(u));}
            @Override public void onPageFinished(WebView v,String u){
                Uri uri=Uri.parse(u); if(!isCp(uri))return; offline=false; CookieManager.getInstance().flush(); String path=uri.getPath()==null?"/dashboard":uri.getPath();
                if("/login".equals(path)){loginScreen(uri.getQueryParameter("returnTo"),null);return;} if("/channel-setup".equals(path)){channelScreen(null);return;}
                injectCss();
                v.evaluateJavascript("(function(){var t=(document.body&&document.body.innerText)||'';return t.includes('Sign in to open CreatorPulse')||t.includes('CreatorPulse account required')||t.includes('Sign in before adding a channel.');})()",x->{if("true".equals(x))runOnUiThread(()->loginScreen(path,null));else runOnUiThread(()->chrome(true));});
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()&&!hasNetwork())offlineScreen();}
            @Override public void onSafeBrowsingHit(WebView v,WebResourceRequest r,int t,SafeBrowsingResponse cb){cb.backToSafety(true);}
        });
        web.setDownloadListener((u,ua,cd,mime,len)->download(u,ua,cd,mime));
    }

    private void injectCss(){String js="(function(){document.documentElement.classList.add('creatorpulse-android-app');if(document.getElementById('cp-app-css'))return;var s=document.createElement('style');s.id='cp-app-css';s.textContent='.cp-topbar.app-topbar,.cp-sidebar.sidebar{display:none!important}.cp-shell-v3.app-shell{display:block!important}.cp-shell-v3 .dashboard-content,.cp-dashboard-content{width:calc(100% - 20px)!important;padding:14px 0 26px!important}.mobile-menu,.mobile-brand{display:none!important}body{background:#080a10!important;overscroll-behavior-y:contain}button,a,summary{touch-action:manipulation}';document.head.appendChild(s);})();";web.evaluateJavascript(js,null);}

    private LinearLayout topBar(){LinearLayout b=row();b.setPadding(dp(14),0,dp(10),0);b.setBackgroundColor(Color.rgb(11,13,20));
        TextView brand=new TextView(this);brand.setText("▮▮▮  CreatorPulse");brand.setTextColor(TEXT);brand.setTextSize(18);brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.addView(brand,new LinearLayout.LayoutParams(0,-1,1));
        TextView ch=pill("Channel");ch.setOnClickListener(v->channelScreen(null));b.addView(ch,new LinearLayout.LayoutParams(dp(82),dp(38)));
        TextView me=pill("CP");LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(42),dp(38));mp.setMargins(dp(7),0,0,0);me.setOnClickListener(v->go("/settings"));b.addView(me,mp);return b;}
    private LinearLayout bottomBar(){LinearLayout b=row();b.setBackgroundColor(Color.rgb(11,13,20));nav(b,"⌂","Home","/dashboard");nav(b,"✦","Ideas","/ideas");nav(b,"⚡","Tools","/tools");nav(b,"▥","Analytics","/analytics");nav(b,"●","Profile","/settings");return b;}
    private void nav(LinearLayout b,String icon,String label,String path){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER);TextView i=txt(icon,PURPLE2,19,true),t=txt(label,Color.rgb(177,181,197),10,false);i.setGravity(Gravity.CENTER);t.setGravity(Gravity.CENTER);x.addView(i,new LinearLayout.LayoutParams(-1,dp(30)));x.addView(t,new LinearLayout.LayoutParams(-1,dp(22)));x.setOnClickListener(v->go(path));b.addView(x,new LinearLayout.LayoutParams(0,-1,1));}
    private void chrome(boolean show){top.setVisibility(show?View.VISIBLE:View.GONE);bottom.setVisibility(show?View.VISIBLE:View.GONE);FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)content.getLayoutParams();p.topMargin=show?dp(58):0;p.bottomMargin=show?dp(68):0;content.setLayoutParams(p);}

    private void loginScreen(String returnTo,String initialError){nativeOpen=true;chrome(false);overlay.removeAllViews();overlay.setVisibility(View.VISIBLE);web.setVisibility(View.GONE);ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout p=page();brand(p);p.addView(tag("CREATOR WORKSPACE"),m(-1,-2,0,32,0,8));p.addView(title("Grow smarter on YouTube"));p.addView(body("Sign in to open your real CreatorPulse workspace, analytics, ideas and optimization tools."),m(-1,-2,0,8,0,20));
        LinearLayout features=row();features.addView(card("✦\nIdeas"),new LinearLayout.LayoutParams(0,dp(70),1));LinearLayout.LayoutParams mid=new LinearLayout.LayoutParams(0,dp(70),1);mid.setMargins(dp(7),0,dp(7),0);features.addView(card("↗\nGrowth"),mid);features.addView(card("▥\nAnalytics"),new LinearLayout.LayoutParams(0,dp(70),1));p.addView(features,m(-1,dp(70),0,0,0,22));
        p.addView(tag("SIGN IN WITH EMAIL"));TextView err=error(initialError);p.addView(err,m(-1,-2,0,8,0,0));EditText email=input("Email address",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),pass=input("Password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);pass.setImeOptions(EditorInfo.IME_ACTION_DONE);p.addView(email,m(-1,dp(56),0,11,0,7));p.addView(pass,m(-1,dp(56),0,0,0,5));CheckBox remember=new CheckBox(this);remember.setText("Keep me signed in");remember.setTextColor(MUTED);remember.setChecked(true);p.addView(remember,m(-1,dp(42),0,0,0,6));Button sign=button("Sign in to CreatorPulse");p.addView(sign,new LinearLayout.LayoutParams(-1,dp(56)));TextView safe=small("Your password is sent only to your CreatorPulse backend over HTTPS and is never stored in this app.");safe.setGravity(Gravity.CENTER);p.addView(safe,m(-1,-2,0,14,0,12));
        View.OnClickListener submit=v->{String e=email.getText().toString().trim(),pw=pass.getText().toString();if(e.isEmpty()||pw.isEmpty()){setError(err,"Enter your email and password.");return;}sign.setEnabled(false);sign.setText("Signing in…");setError(err,null);login(e,pw,remember.isChecked(),returnTo,(dest,fail)->runOnUiThread(()->{sign.setEnabled(true);sign.setText("Sign in to CreatorPulse");if(fail!=null){setError(err,fail);return;}if(dest!=null&&dest.startsWith("/channel-setup"))channelScreen(null);else{hideOverlay();chrome(true);load(dest==null?"/dashboard":dest);}}));};sign.setOnClickListener(submit);pass.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_DONE){submit.onClick(v);return true;}return false;});sc.addView(p);overlay.addView(sc,match());}

    private void channelScreen(String initialError){nativeOpen=true;chrome(false);overlay.removeAllViews();overlay.setVisibility(View.VISIBLE);web.setVisibility(View.GONE);ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout p=page();brand(p);p.addView(tag("CHANNEL SETUP"),m(-1,-2,0,32,0,8));p.addView(title("Connect your YouTube channel"));p.addView(body("Enter your @handle. CreatorPulse finds the real public channel and saves its permanent channel ID."),m(-1,-2,0,8,0,18));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(15),dp(8),dp(15),dp(8));info.setBackground(shape(SURFACE,12,LINE));info.addView(body("① Find your public channel"));info.addView(body("② Save the permanent channel ID"));info.addView(body("③ Build your personalized dashboard"));p.addView(info,m(-1,-2,0,0,0,20));TextView err=error(initialError);p.addView(err);EditText handle=input("@yourhandle",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);handle.setImeOptions(EditorInfo.IME_ACTION_GO);p.addView(handle,m(-1,dp(56),0,11,0,9));Button connect=button("Analyze & connect channel");p.addView(connect,new LinearLayout.LayoutParams(-1,dp(56)));TextView note=small("Public lookup does not prove ownership. Google/YouTube connection can be added later for private analytics.");note.setGravity(Gravity.CENTER);p.addView(note,m(-1,-2,0,14,0,10));TextView skip=link("I already connected a channel →");skip.setOnClickListener(v->{hideOverlay();chrome(true);load("/dashboard");});p.addView(skip);
        View.OnClickListener submit=v->{String h=handle.getText().toString().trim();if(h.isEmpty()){setError(err,"Enter a YouTube @handle, for example @CreatorPulse.");return;}connect.setEnabled(false);connect.setText("Finding channel…");setError(err,null);connectChannel(h,(dest,fail)->runOnUiThread(()->{connect.setEnabled(true);connect.setText("Analyze & connect channel");if(fail!=null){setError(err,fail);return;}hideOverlay();chrome(true);load(dest==null?"/dashboard":dest);}));};connect.setOnClickListener(submit);handle.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_GO){submit.onClick(v);return true;}return false;});sc.addView(p);overlay.addView(sc,match());}

    private interface Callback{void done(String destination,String error);}
    private void login(String email,String password,boolean remember,String returnTo,Callback cb){io.execute(()->{HttpURLConnection c=null;try{c=api("/api/auth/login","POST");c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setRequestProperty("Accept","application/json");JSONObject b=new JSONObject();b.put("email",email);b.put("password",password);b.put("remember",remember);if(returnTo!=null&&returnTo.startsWith("/")&&!returnTo.startsWith("//"))b.put("returnTo",returnTo);write(c,b.toString());int status=c.getResponseCode();saveCookies(c);String raw=read(c,status);JSONObject j=new JSONObject(raw==null||raw.trim().isEmpty()?"{}":raw);if(status>=200&&status<300&&j.optBoolean("ok",false))cb.done(j.optString("destination","/dashboard"),null);else cb.done(null,j.optString("error","Sign-in failed (HTTP "+status+")."));}catch(Exception e){cb.done(null,hasNetwork()?"CreatorPulse could not reach sign-in. Try again.":"You are offline.");}finally{if(c!=null)c.disconnect();}});}
    private void connectChannel(String channel,Callback cb){io.execute(()->{HttpURLConnection c=null;try{c=api("/api/channel/analyze","POST");c.setInstanceFollowRedirects(false);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");write(c,"channel="+Uri.encode(channel));int status=c.getResponseCode();saveCookies(c);String loc=c.getHeaderField("Location");if(status>=300&&status<400&&loc!=null){Uri t=Uri.parse(loc);String path=t.getPath()==null?"":t.getPath();if("/login".equals(path)){cb.done(null,"Your session expired. Sign in again.");runOnUiThread(()->loginScreen("/channel-setup","Your session expired."));return;}if("/channel-setup".equals(path)){cb.done(null,channelError(t.getQueryParameter("channel")));return;}cb.done(relative(t),null);return;}cb.done(null,status==403?"The app session was rejected. Sign in again.":status==401?"Your session expired. Sign in again.":"CreatorPulse could not connect that channel (HTTP "+status+").");}catch(Exception e){cb.done(null,hasNetwork()?"CreatorPulse could not reach YouTube right now.":"You are offline.");}finally{if(c!=null)c.disconnect();}});}
    private HttpURLConnection api(String path,String method)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(APP_URL+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setDoInput(true);c.setUseCaches(false);c.setRequestProperty("Accept","application/json, text/plain, */*");c.setRequestProperty("Origin",APP_URL);c.setRequestProperty("Referer",APP_URL+"/");c.setRequestProperty("User-Agent",UA);String cookie=CookieManager.getInstance().getCookie(APP_URL);if(cookie!=null&&!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);if(!"GET".equals(method))c.setDoOutput(true);return c;}
    private void write(HttpURLConnection c,String body)throws Exception{byte[] x=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(x.length);try(OutputStream out=c.getOutputStream()){out.write(x);}}
    private String read(HttpURLConnection c,int status)throws Exception{InputStream in=status>=400?c.getErrorStream():c.getInputStream();if(in==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line);}return b.toString();}
    private void saveCookies(HttpURLConnection c){CookieManager cm=CookieManager.getInstance();for(Map.Entry<String,List<String>> e:c.getHeaderFields().entrySet())if(e.getKey()!=null&&e.getKey().equalsIgnoreCase("Set-Cookie"))for(String v:e.getValue())if(v!=null)cm.setCookie(APP_URL,v);cm.flush();}
    private boolean session(){String c=CookieManager.getInstance().getCookie(APP_URL);return c!=null&&c.contains("creatorpulse_session=");}
    private String channelError(String c){if("invalid_url".equals(c))return"Use an @handle, youtube.com/@handle, or channel URL.";if("not_found".equals(c))return"CreatorPulse could not find that channel. Check the @handle.";if("temporarily_unavailable".equals(c))return"YouTube is temporarily unavailable. Try again shortly.";return"CreatorPulse could not connect that channel.";}
    private String relative(Uri u){if(isCp(u))return(u.getEncodedPath()==null?"/dashboard":u.getEncodedPath())+(u.getEncodedQuery()==null?"":"?"+u.getEncodedQuery());if(u.getScheme()==null)return u.toString().startsWith("/")?u.toString():"/"+u;return"/dashboard";}

    private LinearLayout page(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(22),dp(25),dp(22),dp(34));p.setBackgroundColor(BG);return p;}
    private void brand(LinearLayout p){TextView b=txt("▮▮▮  CreatorPulse",TEXT,20,true);p.addView(b,new LinearLayout.LayoutParams(-1,dp(38)));}
    private TextView tag(String s){TextView t=txt(s,PURPLE2,10,true);t.setLetterSpacing(.11f);return t;}
    private TextView title(String s){TextView t=txt(s,TEXT,30,true);t.setLineSpacing(0,1.04f);return t;}
    private TextView body(String s){TextView t=txt(s,MUTED,14,false);t.setLineSpacing(dp(2),1.16f);t.setPadding(0,dp(5),0,dp(5));return t;}
    private TextView small(String s){TextView t=body(s);t.setTextColor(Color.rgb(102,107,126));t.setTextSize(11);return t;}
    private TextView card(String s){TextView t=txt(s,Color.rgb(208,211,223),12,true);t.setGravity(Gravity.CENTER);t.setBackground(shape(SURFACE,11,LINE));return t;}
    private EditText input(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(102,107,126));e.setTextColor(TEXT);e.setTextSize(15);e.setInputType(type);e.setSingleLine(true);e.setPadding(dp(16),0,dp(16),0);e.setBackground(shape(Color.rgb(22,24,36),10,LINE));return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(shape(PURPLE,11,-1));return b;}
    private TextView pill(String s){TextView t=txt(s,Color.rgb(222,215,255),11,true);t.setGravity(Gravity.CENTER);t.setBackground(shape(Color.rgb(27,24,42),11,Color.rgb(67,57,101)));return t;}
    private TextView link(String s){TextView t=txt(s,PURPLE2,13,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),dp(12),dp(8),dp(12));return t;}
    private TextView error(String s){TextView t=txt(s==null?"":s,Color.rgb(248,126,126),12,false);t.setPadding(dp(12),dp(9),dp(12),dp(9));t.setBackground(shape(Color.rgb(44,20,28),9,Color.rgb(93,43,55)));setError(t,s);return t;}
    private void setError(TextView t,String s){t.setText(s==null?"":s);t.setVisibility(s==null||s.isEmpty()?View.GONE:View.VISIBLE);}
    private TextView txt(String s,int color,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable shape(int fill,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=-1)d.setStroke(dp(1),stroke);return d;}
    private LinearLayout.LayoutParams m(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private FrameLayout.LayoutParams match(){return new FrameLayout.LayoutParams(-1,-1);}

    private void go(String p){hideOverlay();chrome(true);load(p);}
    private void hideOverlay(){nativeOpen=false;overlay.setVisibility(View.GONE);overlay.removeAllViews();web.setVisibility(View.VISIBLE);}
    private void load(String t){web.loadUrl(t.startsWith("http")?t:APP_URL+(t.startsWith("/")?t:"/"+t));}
    private boolean route(Uri u){if(u==null)return false;String s=u.getScheme()==null?"":u.getScheme().toLowerCase(Locale.US);if("creatorpulse".equals(s)){oauth(u);return true;}if(isCp(u)){String p=u.getPath()==null?"":u.getPath();if("/login".equals(p)){loginScreen(u.getQueryParameter("returnTo"),null);return true;}if("/channel-setup".equals(p)){channelScreen(null);return true;}return false;}if("http".equals(s)||"https".equals(s)||"mailto".equals(s)||"tel".equals(s)){try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(ActivityNotFoundException e){}return true;}return true;}
    private boolean isCp(Uri u){String h=u.getHost();return h!=null&&APP_HOST!=null&&h.equalsIgnoreCase(APP_HOST)&&"https".equalsIgnoreCase(u.getScheme());}
    private void oauth(Uri u){String s=u.getQueryParameter("status");hideOverlay();chrome(true);load("/settings?youtube="+Uri.encode(s==null?"connection_finished":s));}
    private void handleIntent(Intent i,boolean first){Uri d=i==null?null:i.getData();if(d!=null&&"creatorpulse".equalsIgnoreCase(d.getScheme())){oauth(d);return;}if(!first)return;if(!hasNetwork()){offlineScreen();return;}if(session()){hideOverlay();chrome(true);load("/dashboard");}else loginScreen("/dashboard",null);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIntent(i,false);}
    private void offlineScreen(){offline=true;hideOverlay();chrome(false);web.loadUrl("file:///android_asset/offline.html");}
    private boolean hasNetwork(){ConnectivityManager m=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);Network n=m.getActiveNetwork();if(n==null)return false;NetworkCapabilities c=m.getNetworkCapabilities(n);return c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);}
    private void registerNetwork(){ConnectivityManager m=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);networkCallback=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network n){runOnUiThread(()->{if(!offline)return;offline=false;if(session()){chrome(true);load("/dashboard");}else loginScreen("/dashboard",null);});}};try{m.registerDefaultNetworkCallback(networkCallback);}catch(Exception ignored){}}
    private void download(String url,String ua,String cd,String mime){if(!url.startsWith("https://"))return;try{DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url));r.setMimeType(mime);r.addRequestHeader("User-Agent",ua);String c=CookieManager.getInstance().getCookie(url);if(c!=null)r.addRequestHeader("Cookie",c);String n=android.webkit.URLUtil.guessFileName(url,cd,mime);r.setTitle(n);r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,n);((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);}catch(Exception e){Toast.makeText(this,"Download could not be started.",Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(r==FILE_PICKER){Uri[] x=WebChromeClient.FileChooserParams.parseResult(result,data);if(chooser!=null)chooser.onReceiveValue(x);chooser=null;}}
    @Override protected void onPause(){CookieManager.getInstance().flush();if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override public void onBackPressed(){if(nativeOpen){if(session()){hideOverlay();chrome(true);load("/dashboard");}else super.onBackPressed();}else if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){io.shutdownNow();if(networkCallback!=null)try{((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}if(web!=null){CookieManager.getInstance().flush();web.stopLoading();web.setWebChromeClient(null);web.setWebViewClient(null);web.destroy();}super.onDestroy();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
