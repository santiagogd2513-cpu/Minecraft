package com.nocturna.browser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final int REQ_FILE = 11;

    /** Una pestaña. */
    private static final class Tab {
        WebView web;
        String title = "Nueva pestaña";
        String url = HomePage.URL;
        boolean incognito;
        boolean desktop;
        ScriptHandler darkScript;
        /** Idioma al que está traducida la página actual (null = original). */
        String translated;
    }

    private Store store;
    private final List<Tab> tabs = new ArrayList<>();
    private Tab current;
    private FrameLayout content;
    private EditText urlBar;
    private ImageView urlIcon, reloadBtn;
    private ProgressBar progress;
    private TextView tabCount;
    private LinearLayout topBar, bottomBar;
    private FrameLayout fullscreen;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private ValueCallback<Uri[]> fileCallback;

    // ------------------------------------------------------------------ inicio

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        DarkMode.init(this);
        Translator.init(this);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        buildUi();
        CookieManager.getInstance().setAcceptCookie(true);

        List<String> saved = store.tabs();
        Intent in = getIntent();
        if (!saved.isEmpty()) {
            for (String u : saved) openTab(u.isEmpty() ? HomePage.URL : u, false, false);
            select(tabs.get(Math.min(Math.max(0, store.currentTab()), tabs.size() - 1)));
        }
        if (!handleIntent(in) && tabs.isEmpty()) openTab(HomePage.URL, true, false);

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
        // la primera vez: permiso para la carpeta Nocturna Downloads
        android.content.SharedPreferences sp = getSharedPreferences("nocturna", MODE_PRIVATE);
        if (!sp.getBoolean("pidio_almacenamiento", false)) {
            sp.edit().putBoolean("pidio_almacenamiento", true).apply();
            StoragePermission.ask(this, null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private boolean handleIntent(Intent in) {
        if (in == null) return false;
        String url = null;
        if (Intent.ACTION_VIEW.equals(in.getAction()) && in.getData() != null) url = in.getData().toString();
        if (Intent.ACTION_WEB_SEARCH.equals(in.getAction())) {
            String q = in.getStringExtra("query");
            if (q != null) url = store.searchUrl(q);
        }
        if (url == null) return false;
        openTab(url, true, false);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        List<String> urls = new ArrayList<>();
        int idx = 0;
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            if (t.incognito) continue;
            if (t == current) idx = urls.size();
            urls.add(t.url);
        }
        store.saveTabs(urls, idx);
        CookieManager.getInstance().flush();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // ------------------------------------------------------------------ interfaz

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        LinearLayout col = Ui.column(this);

        // barra de dirección
        topBar = Ui.row(this);
        topBar.setPadding(dp(10), dp(8), dp(6), dp(8));
        LinearLayout field = Ui.row(this);
        field.setBackground(Ui.roundStroke(Ui.SURFACE, dp(24), dp(1), Ui.STROKE));
        field.setPadding(dp(12), 0, dp(4), 0);
        urlIcon = new ImageView(this);
        urlIcon.setImageDrawable(Ui.icon(this, R.drawable.ic_search, Ui.SUB));
        field.addView(urlIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        urlBar = new EditText(this);
        urlBar.setSingleLine(true);
        urlBar.setHint("Busca o escribe una dirección");
        urlBar.setHintTextColor(0xFF5D6B83);
        urlBar.setTextColor(Ui.TEXT);
        urlBar.setTextSize(15);
        urlBar.setBackground(null);
        urlBar.setPadding(dp(10), dp(10), dp(6), dp(10));
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlBar.setSelectAllOnFocus(true);
        urlBar.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(urlBar.getText().toString());
                return true;
            }
            return false;
        });
        urlBar.setOnFocusChangeListener((v, has) -> {
            if (has) {
                urlBar.setText(current == null || isInternal(current.url) ? "" : current.url);
                urlBar.selectAll();
            } else {
                showUrl();
            }
        });
        field.addView(urlBar, Ui.weight(1));
        reloadBtn = Ui.iconButton(this, R.drawable.ic_refresh, 40, v -> {
            if (current == null) return;
            if (progress.getVisibility() == View.VISIBLE) current.web.stopLoading();
            else current.web.reload();
        });
        field.addView(reloadBtn);
        topBar.addView(field, Ui.weight(1));
        col.addView(topBar);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT2));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.BG));
        progress.setVisibility(View.INVISIBLE);
        col.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        content = new FrameLayout(this);
        content.setBackgroundColor(Ui.BG);
        col.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        // barra de abajo
        bottomBar = Ui.row(this);
        bottomBar.setBackgroundColor(Ui.BG);
        bottomBar.setPadding(dp(6), dp(2), dp(6), dp(2));
        bottomBar.addView(slot(Ui.iconButton(this, R.drawable.ic_back, 48, v -> goBack())));
        bottomBar.addView(slot(Ui.iconButton(this, R.drawable.ic_forward, 48, v -> {
            if (current != null && current.web.canGoForward()) current.web.goForward();
        })));
        bottomBar.addView(slot(Ui.iconButton(this, R.drawable.ic_home, 48, v -> load(current, HomePage.URL))));
        tabCount = Ui.text(this, "1", 13, Ui.TEXT, true);
        tabCount.setGravity(Gravity.CENTER);
        tabCount.setBackground(Ui.roundStroke(0x00000000, dp(7), dp(2), Ui.TEXT));
        FrameLayout tabBox = new FrameLayout(this);
        tabBox.addView(tabCount, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        tabBox.setBackground(Ui.ripple(null, Ui.round(Color.WHITE, dp(24))));
        tabBox.setOnClickListener(v -> showTabs());
        tabBox.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        bottomBar.addView(slot(tabBox));
        bottomBar.addView(slot(Ui.iconButton(this, R.drawable.ic_menu, 48, v -> showMenu())));
        col.addView(bottomBar);

        root.addView(col);
        fullscreen = new FrameLayout(this);
        fullscreen.setBackgroundColor(Color.BLACK);
        fullscreen.setVisibility(View.GONE);
        root.addView(fullscreen, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private View slot(View v) {
        FrameLayout f = new FrameLayout(this);
        f.addView(v, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        f.setLayoutParams(Ui.weight(1));
        return f;
    }

    private static boolean isInternal(String url) {
        return url == null || url.startsWith("nocturna:") || url.startsWith("about:") || url.startsWith("data:")
                || url.startsWith("https://nocturna.inicio");
    }

    private void showUrl() {
        if (current == null || urlBar.hasFocus()) return;
        String u = current.url;
        if (isInternal(u)) {
            urlBar.setText("");
            urlIcon.setImageDrawable(Ui.icon(this, current.incognito ? R.drawable.ic_incognito : R.drawable.ic_search, Ui.SUB));
            return;
        }
        Uri p = Uri.parse(u);
        String host = p.getHost() == null ? u : p.getHost().replaceFirst("^www\\.", "");
        urlBar.setText(host);
        boolean https = "https".equals(p.getScheme());
        urlIcon.setImageDrawable(Ui.icon(this, current.incognito ? R.drawable.ic_incognito : https ? R.drawable.ic_lock : R.drawable.ic_search,
                current.incognito ? 0xFFB58CFF : https ? Ui.GOOD : Ui.WARN));
    }

    private void updateTabCount() {
        tabCount.setText(tabs.size() > 99 ? ":D" : String.valueOf(tabs.size()));
    }

    // ------------------------------------------------------------------ pestañas

    private Tab openTab(String url, boolean select, boolean incognito) {
        Tab t = new Tab();
        t.incognito = incognito;
        t.web = createWebView(t);
        tabs.add(t);
        updateTabCount();
        if (select) select(t);
        load(t, url);
        return t;
    }

    private void select(Tab t) {
        current = t;
        content.removeAllViews();
        if (t.web.getParent() != null) ((ViewGroup) t.web.getParent()).removeView(t.web);
        content.addView(t.web, new FrameLayout.LayoutParams(-1, -1));
        t.web.requestFocus();
        showUrl();
        progress.setVisibility(View.INVISIBLE);
    }

    private void closeTab(Tab t) {
        int i = tabs.indexOf(t);
        tabs.remove(t);
        content.removeView(t.web);
        t.web.destroy();
        if (tabs.isEmpty()) {
            openTab(HomePage.URL, true, false);
        } else if (t == current) {
            select(tabs.get(Math.max(0, i - 1)));
        }
        updateTabCount();
    }

    private void load(Tab t, String url) {
        if (t == null) return;
        if (url == null || url.equals(HomePage.URL)) {
            t.url = HomePage.URL;
            t.title = "Inicio";
            t.web.loadDataWithBaseURL("https://nocturna.inicio/", HomePage.html(store), "text/html", "utf-8", HomePage.URL);
        } else {
            t.web.loadUrl(url);
        }
        if (t == current) showUrl();
    }

    private void navigate(String text) {
        String q = text.trim();
        if (q.isEmpty()) return;
        String url;
        if (q.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) url = q;
        else if (!q.contains(" ") && q.matches("^[^\\s]+\\.[^\\s]{2,}(/.*)?$")) url = "https://" + q;
        else url = store.searchUrl(q);
        urlBar.clearFocus();
        hideKeyboard();
        load(current, url);
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private WebView createWebView(Tab t) {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(true);
        // letra al 100 %: con la letra grande del sistema las páginas se desarman
        s.setTextZoom(100);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setOffscreenPreRaster(false);
        if (t.incognito) s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        applyDark(t, w);
        w.addJavascriptInterface(new BlobBridge(), "NocturnaDescargas");

        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return handleSpecialUrl(t, req.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                if (store.adBlock() && !req.isForMainFrame() && AdBlock.blocked(req.getUrl().getHost())) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                t.url = url;
                t.translated = null;
                if (t == current) {
                    showUrl();
                    progress.setVisibility(View.VISIBLE);
                    reloadBtn.setImageDrawable(Ui.icon(MainActivity.this, R.drawable.ic_close, Ui.TEXT));
                }
                if (t.darkScript == null) injectDark(view);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                if (t.darkScript == null) injectDark(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                t.url = url;
                injectDark(view);
                if (!t.incognito && !isInternal(url)) store.addHistory(view.getTitle(), url);
                if (t == current) {
                    showUrl();
                    progress.setVisibility(View.INVISIBLE);
                    reloadBtn.setImageDrawable(Ui.icon(MainActivity.this, R.drawable.ic_refresh, Ui.TEXT));
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (!req.isForMainFrame()) return;
                String u = req.getUrl().toString();
                view.loadDataWithBaseURL(null, HomePage.errorPage(u, String.valueOf(err.getDescription())), "text/html", "utf-8", u);
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                if (t == current) progress.setProgress(p);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                t.title = title;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message msg) {
                Tab n = new Tab();
                n.incognito = t.incognito;
                n.web = createWebView(n);
                n.url = "";
                tabs.add(n);
                updateTabCount();
                select(n);
                WebView.WebViewTransport tr = (WebView.WebViewTransport) msg.obj;
                tr.setWebView(n.web);
                msg.sendToTarget();
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                for (Tab x : new ArrayList<>(tabs)) if (x.web == window) closeTab(x);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) {
                    cb.onCustomViewHidden();
                    return;
                }
                customView = view;
                customCallback = cb;
                fullscreen.addView(view, new FrameLayout.LayoutParams(-1, -1));
                fullscreen.setVisibility(View.VISIBLE);
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });

        w.setDownloadListener((url, ua, disposition, mime, length) -> {
            if (url.startsWith("blob:")) {
                saveBlob(w, url, URLUtil.guessFileName(url, disposition, mime));
            } else if (url.startsWith("data:")) {
                saveDataUrl(url, URLUtil.guessFileName(url, disposition, mime));
            } else {
                askDownload(url, URLUtil.guessFileName(url, disposition, mime), mime, ua, t.url, length);
            }
        });

        w.setOnLongClickListener(v -> {
            WebView.HitTestResult r = w.getHitTestResult();
            String extra = r.getExtra();
            if (extra == null) return false;
            int type = r.getType();
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                    || type == WebView.HitTestResult.IMAGE_TYPE) {
                linkMenu(t, extra, type != WebView.HitTestResult.SRC_ANCHOR_TYPE);
                return true;
            }
            return false;
        });
        return w;
    }

    private boolean handleSpecialUrl(Tab t, String url) {
        if (url.equals(HomePage.URL) || url.startsWith("nocturna:")) {
            load(t, HomePage.URL);
            return true;
        }
        if (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("about:")
                || url.startsWith("data:") || url.startsWith("javascript:") || url.startsWith("blob:")) {
            return false;
        }
        try {
            Intent i;
            if (url.startsWith("intent:")) {
                i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
                try {
                    startActivity(i);
                } catch (Exception e) {
                    String fallback = i.getStringExtra("browser_fallback_url");
                    if (fallback != null) t.web.loadUrl(fallback);
                }
                return true;
            }
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No hay ninguna app para abrir este enlace", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    // ------------------------------------------------------------------ modo oscuro

    private void applyDark(Tab t, WebView w) {
        int mode = store.darkMode();
        DarkMode.apply(w, mode);
        if (t.darkScript != null) {
            t.darkScript.remove();
            t.darkScript = null;
        }
        String js = mode == DarkMode.OFF ? "" : DarkMode.fallbackFlag(DarkMode.engineCanDarken()) + DarkMode.script(mode);
        if (!js.isEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // se ejecuta antes de que la página se pinte: no hay destello blanco
            t.darkScript = WebViewCompat.addDocumentStartJavaScript(w, js, Collections.singleton("*"));
        }
    }

    private void injectDark(WebView w) {
        int mode = store.darkMode();
        if (mode == DarkMode.OFF) return;
        w.evaluateJavascript(DarkMode.fallbackFlag(DarkMode.engineCanDarken()) + DarkMode.script(mode), null);
    }

    private void setDarkMode(int mode) {
        store.setDarkMode(mode);
        for (Tab t : tabs) {
            applyDark(t, t.web);
            t.web.reload();
        }
        Toast.makeText(this, "Modo oscuro: " + DarkMode.NAMES[mode], Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ descargas

    private void askDownload(String url, String name, String mime, String ua, String referer, long length) {
        Dialog d = sheet();
        LinearLayout box = sheetBox(d);
        LinearLayout top = Ui.row(this);
        TextView badge = Ui.text(this, ext(name).toUpperCase(), 12, 0xFF0A0E16, true);
        badge.setGravity(Gravity.CENTER);
        int[] col = DownloadsActivity.typeColor(ext(name));
        badge.setBackground(Ui.gradient(dp(14), col[0], col[1]));
        top.addView(badge, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(dp(14), 0, 0, 0);
        EditText nameField = new EditText(this);
        nameField.setText(name);
        nameField.setTextColor(Ui.TEXT);
        nameField.setTextSize(15);
        nameField.setSingleLine(true);
        nameField.setBackground(Ui.round(Ui.SURFACE2, dp(10)));
        nameField.setPadding(dp(10), dp(8), dp(10), dp(8));
        texts.addView(nameField);
        TextView info = Ui.text(this, (length > 0 ? Ui.size(length) : "Tamaño desconocido") + "  ·  "
                + Uri.parse(url).getHost(), 12.5f, Ui.SUB, false);
        info.setPadding(dp(2), dp(6), 0, 0);
        texts.addView(info);
        top.addView(texts, Ui.weight(1));
        box.addView(top);

        TextView go = Ui.text(this, "Descargar", 16, 0xFF06101F, true);
        go.setGravity(Gravity.CENTER);
        go.setPadding(0, dp(14), 0, dp(14));
        go.setBackground(Ui.ripple(Ui.gradient(dp(24), Ui.ACCENT, Ui.ACCENT2), Ui.round(Color.WHITE, dp(24))));
        go.setOnClickListener(v -> {
            String n = nameField.getText().toString().trim();
            DownloadEngine.get(this).enqueue(url, n.isEmpty() ? name : n, mime, ua, referer, length);
            d.dismiss();
            snack("Descargando " + (n.isEmpty() ? name : n), "Ver", () -> startActivity(new Intent(this, DownloadsActivity.class)));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(18), 0, 0);
        box.addView(go, lp);
        d.show();
    }

    private static String ext(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "?" : name.substring(dot + 1).toLowerCase();
    }

    private void saveDataUrl(String url, String name) {
        try {
            int comma = url.indexOf(',');
            String meta = url.substring(5, comma);
            String mime = meta.split(";")[0];
            byte[] data = meta.contains(";base64") ? Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
                    : Uri.decode(url.substring(comma + 1)).getBytes();
            confirmBytes(data, name, mime);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo leer el archivo", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveBlob(WebView w, String url, String name) {
        String js = "(function(){fetch(" + DarkMode.jsString(url) + ").then(function(r){return r.blob()}).then(function(b){"
                + "var f=new FileReader();f.onloadend=function(){NocturnaDescargas.save(f.result," + DarkMode.jsString(name)
                + ")};f.readAsDataURL(b)}).catch(function(){NocturnaDescargas.fail()})})();";
        w.evaluateJavascript(js, null);
        Toast.makeText(this, "Preparando archivo…", Toast.LENGTH_SHORT).show();
    }

    private void confirmBytes(byte[] data, String name, String mime) {
        new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                .setTitle("¿Guardar archivo?")
                .setMessage(name + "  ·  " + Ui.size(data.length))
                .setPositiveButton("Guardar", (dlg, w) -> {
                    DownloadEngine.get(this).saveBytes(data, name, mime);
                    snack("Archivo guardado", "Ver", () -> startActivity(new Intent(this, DownloadsActivity.class)));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Recibe los archivos que la página arma en memoria (enlaces blob:). Siempre pide confirmación. */
    private final class BlobBridge {
        @JavascriptInterface
        public void save(String dataUrl, String name) {
            runOnUiThread(() -> saveDataUrl(dataUrl, name));
        }

        @JavascriptInterface
        public void fail() {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Esta página no dejó guardar el archivo", Toast.LENGTH_SHORT).show());
        }
    }

    private void linkMenu(Tab t, String url, boolean image) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Abrir en pestaña nueva");
        actions.add(() -> openTab(url, true, t.incognito));
        labels.add("Abrir en segundo plano");
        actions.add(() -> {
            openTab(url, false, t.incognito);
            Toast.makeText(this, "Pestaña abierta", Toast.LENGTH_SHORT).show();
        });
        labels.add("Copiar enlace");
        actions.add(() -> copy(url));
        labels.add(image ? "Descargar imagen" : "Descargar enlace");
        actions.add(() -> {
            if (url.startsWith("data:")) saveDataUrl(url, "imagen");
            else askDownload(url, URLUtil.guessFileName(url, null, null), null,
                    t.web.getSettings().getUserAgentString(), t.url, -1);
        });
        labels.add("Compartir");
        actions.add(() -> shareText(url));
        new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                .setTitle(url.length() > 80 ? url.substring(0, 80) + "…" : url)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void copy(String s) {
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("enlace", s));
        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
    }

    private void shareText(String s) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, s);
        startActivity(Intent.createChooser(i, "Compartir"));
    }

    // ------------------------------------------------------------------ hojas inferiores

    private Dialog sheet() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.55f);
        }
        return d;
    }

    private LinearLayout sheetBox(Dialog d) {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(18), dp(12), dp(18), dp(22));
        android.graphics.drawable.GradientDrawable bg = Ui.round(Ui.SURFACE, 0);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(dp(1), Ui.STROKE);
        box.setBackground(bg);
        View handle = new View(this);
        handle.setBackground(Ui.round(0xFF33405A, dp(3)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(40), dp(5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.setMargins(0, 0, 0, dp(14));
        box.addView(handle, hl);
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        return box;
    }

    private void showMenu() {
        Dialog d = sheet();
        LinearLayout box = sheetBox(d);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        boolean marked = current != null && store.isBookmarked(current.url);
        addTile(grid, R.drawable.ic_add, "Nueva\npestaña", Ui.ACCENT, () -> openTab(HomePage.URL, true, false), d);
        addTile(grid, R.drawable.ic_incognito, "Incógnito", 0xFFB58CFF, () -> openTab(HomePage.URL, true, true), d);
        addTile(grid, R.drawable.ic_download, "Descargas", Ui.ACCENT2,
                () -> startActivity(new Intent(this, DownloadsActivity.class)), d);
        addTile(grid, R.drawable.ic_moon, "Oscuro:\n" + shortDark(), 0xFF8FB3FF, this::chooseDark, d);
        addTile(grid, R.drawable.ic_star, marked ? "Quitar\nfavorito" : "Añadir\nfavorito", Ui.WARN, () -> {
            if (current == null || isInternal(current.url)) return;
            store.toggleBookmark(current.title, current.url);
            Toast.makeText(this, marked ? "Quitado de favoritos" : "Añadido a favoritos", Toast.LENGTH_SHORT).show();
        }, d);
        addTile(grid, R.drawable.ic_star, "Favoritos", Ui.WARN, () -> showList(true), d);
        addTile(grid, R.drawable.ic_history, "Historial", Ui.SUB, () -> showList(false), d);
        addTile(grid, R.drawable.ic_desktop, current != null && current.desktop ? "Versión\nmóvil" : "Versión\nescritorio",
                Ui.TEXT, this::toggleDesktop, d);
        addTile(grid, R.drawable.ic_share, "Compartir", Ui.TEXT, () -> {
            if (current != null && !isInternal(current.url)) shareText(current.url);
        }, d);
        addTile(grid, R.drawable.ic_copy, "Copiar\nenlace", Ui.TEXT, () -> {
            if (current != null && !isInternal(current.url)) copy(current.url);
        }, d);
        addTile(grid, R.drawable.ic_translate, current != null && current.translated != null ? "Traducida" : "Traducir",
                0xFF7CE0B8, this::showTranslate, d);
        addTile(grid, R.drawable.ic_shield, store.adBlock() ? "Bloqueo:\nsí" : "Bloqueo:\nno", Ui.GOOD, () -> {
            store.setAdBlock(!store.adBlock());
            if (current != null) current.web.reload();
        }, d);
        addTile(grid, R.drawable.ic_settings, "Ajustes", Ui.SUB, this::showSettings, d);
        box.addView(grid);
        d.show();
    }

    private String shortDark() {
        return switch (store.darkMode()) {
            case DarkMode.OFF -> "apagado";
            case DarkMode.MAX -> "máximo";
            default -> "forzado";
        };
    }

    private void addTile(GridLayout grid, int icon, String label, int color, Runnable action, Dialog d) {
        LinearLayout t = Ui.column(this);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(4), dp(10), dp(4), dp(10));
        t.setBackground(Ui.rippleRound(0x00000000, dp(16)));
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(Ui.round(Ui.SURFACE2, dp(16)));
        ImageView i = new ImageView(this);
        i.setImageDrawable(Ui.icon(this, icon, color));
        ic.addView(i, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        t.addView(ic, new LinearLayout.LayoutParams(dp(50), dp(50)));
        TextView l = Ui.text(this, label, 11.5f, Ui.TEXT, false);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(6), 0, 0);
        t.addView(l);
        t.setOnClickListener(v -> {
            d.dismiss();
            action.run();
        });
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        grid.addView(t, lp);
    }

    private void chooseDark() {
        new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                .setTitle("Modo oscuro de las páginas")
                .setSingleChoiceItems(DarkMode.NAMES, store.darkMode(), (dlg, which) -> {
                    dlg.dismiss();
                    setDarkMode(which);
                })
                .show();
    }

    private void toggleDesktop() {
        if (current == null) return;
        current.desktop = !current.desktop;
        WebSettings s = current.web.getSettings();
        s.setUserAgentString(current.desktop ? DESKTOP_UA : null);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        current.web.reload();
    }

    private void showSettings() {
        String[] items = {
                "Modo oscuro: " + DarkMode.NAMES[store.darkMode()],
                "Buscador: " + Store.ENGINES[store.engine()][0],
                "Conexiones por descarga: " + store.connections(),
                "Bloquear anuncios: " + (store.adBlock() ? "sí" : "no"),
                "Borrar historial",
                "Borrar cookies, caché y datos de páginas",
        };
        new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                .setTitle("Ajustes de Nocturna")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0 -> chooseDark();
                        case 1 -> {
                            String[] names = new String[Store.ENGINES.length];
                            for (int i = 0; i < names.length; i++) names[i] = Store.ENGINES[i][0];
                            new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog).setTitle("Buscador")
                                    .setSingleChoiceItems(names, store.engine(), (dd, w) -> {
                                        store.setEngine(w);
                                        dd.dismiss();
                                    }).show();
                        }
                        case 2 -> {
                            String[] opts = {"4", "8", "12", "16"};
                            int sel = java.util.Arrays.asList(opts).indexOf(String.valueOf(store.connections()));
                            new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                                    .setTitle("Conexiones por descarga (más = más rápido si el servidor lo permite)")
                                    .setSingleChoiceItems(opts, sel, (dd, w) -> {
                                        store.setConnections(Integer.parseInt(opts[w]));
                                        dd.dismiss();
                                    }).show();
                        }
                        case 3 -> store.setAdBlock(!store.adBlock());
                        case 4 -> {
                            store.clearHistory();
                            Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show();
                        }
                        case 5 -> {
                            CookieManager.getInstance().removeAllCookies(null);
                            WebStorage.getInstance().deleteAllData();
                            for (Tab t : tabs) t.web.clearCache(true);
                            Toast.makeText(this, "Datos borrados", Toast.LENGTH_SHORT).show();
                        }
                        default -> { }
                    }
                })
                .show();
    }

    private void showList(boolean bookmarks) {
        List<Store.Entry> list = bookmarks ? store.bookmarks() : store.history();
        Dialog d = sheet();
        LinearLayout box = sheetBox(d);
        TextView title = Ui.text(this, bookmarks ? "Favoritos" : "Historial", 20, Ui.TEXT, true);
        title.setPadding(dp(4), 0, 0, dp(10));
        box.addView(title);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        sv.addView(col);
        if (list.isEmpty()) {
            col.addView(Ui.text(this, bookmarks ? "Aún no tienes favoritos" : "El historial está vacío", 14, Ui.SUB, false));
        }
        for (Store.Entry e : list) {
            LinearLayout r = Ui.row(this);
            r.setPadding(dp(10), dp(10), dp(4), dp(10));
            r.setBackground(Ui.rippleRound(0x00000000, dp(14)));
            LinearLayout texts = Ui.column(this);
            TextView t = Ui.text(this, e.title(), 14.5f, Ui.TEXT, true);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            TextView u = Ui.text(this, e.url(), 12, Ui.SUB, false);
            u.setSingleLine(true);
            u.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            texts.addView(t);
            texts.addView(u);
            r.addView(texts, Ui.weight(1));
            if (bookmarks) {
                r.addView(Ui.iconButton(this, R.drawable.ic_delete, 40, v -> {
                    store.removeBookmark(e.url());
                    col.removeView(r);
                }));
            }
            r.setOnClickListener(v -> {
                d.dismiss();
                load(current, e.url());
            });
            col.addView(r);
        }
        box.addView(sv, new LinearLayout.LayoutParams(-1, Math.min(dp(460), dp(64) * Math.max(1, list.size()) + dp(8))));
        d.show();
    }

    private void showTabs() {
        Dialog d = sheet();
        LinearLayout box = sheetBox(d);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.text(this, tabs.size() + (tabs.size() == 1 ? " pestaña" : " pestañas"), 20, Ui.TEXT, true), Ui.weight(1));
        TextView closeAll = Ui.text(this, "Cerrar todas", 13, Ui.BAD, true);
        closeAll.setPadding(dp(10), dp(8), dp(10), dp(8));
        closeAll.setOnClickListener(v -> {
            d.dismiss();
            for (Tab t : new ArrayList<>(tabs)) closeTab(t);
        });
        head.addView(closeAll);
        box.addView(head);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        col.setPadding(0, dp(8), 0, dp(8));
        sv.addView(col);
        for (Tab t : tabs) {
            LinearLayout r = Ui.row(this);
            r.setPadding(dp(14), dp(12), dp(6), dp(12));
            r.setBackground(Ui.ripple(Ui.roundStroke(t == current ? 0xFF16264A : Ui.SURFACE2, dp(16), dp(t == current ? 2 : 1),
                    t == current ? Ui.ACCENT : Ui.STROKE), Ui.round(Color.WHITE, dp(16))));
            if (t.incognito) {
                ImageView inc = new ImageView(this);
                inc.setImageDrawable(Ui.icon(this, R.drawable.ic_incognito, 0xFFB58CFF));
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(20), dp(20));
                il.setMargins(0, 0, dp(10), 0);
                r.addView(inc, il);
            }
            LinearLayout texts = Ui.column(this);
            TextView title = Ui.text(this, t.title == null || t.title.isEmpty() ? "Sin título" : t.title, 14.5f, Ui.TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            TextView url = Ui.text(this, isInternal(t.url) ? "Inicio" : t.url, 12, Ui.SUB, false);
            url.setSingleLine(true);
            url.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            texts.addView(title);
            texts.addView(url);
            r.addView(texts, Ui.weight(1));
            r.addView(Ui.iconButton(this, R.drawable.ic_close, 40, v -> {
                closeTab(t);
                col.removeView(r);
                if (tabs.size() <= 1) d.dismiss();
            }));
            r.setOnClickListener(v -> {
                select(t);
                d.dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(8));
            col.addView(r, lp);
        }
        box.addView(sv, new LinearLayout.LayoutParams(-1, Math.min(dp(420), dp(76) * tabs.size() + dp(16))));
        TextView add = Ui.text(this, "+  Nueva pestaña", 15, 0xFF06101F, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(0, dp(13), 0, dp(13));
        add.setBackground(Ui.ripple(Ui.gradient(dp(24), Ui.ACCENT, Ui.ACCENT2), Ui.round(Color.WHITE, dp(24))));
        add.setOnClickListener(v -> {
            d.dismiss();
            openTab(HomePage.URL, true, false);
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2);
        al.setMargins(0, dp(10), 0, 0);
        box.addView(add, al);
        d.show();
    }

    // ------------------------------------------------------------------ traductor

    private void showTranslate() {
        Tab t = current;
        if (t == null || isInternal(t.url)) {
            Toast.makeText(this, "Abre una página para traducirla", Toast.LENGTH_SHORT).show();
            return;
        }
        if (t.translated != null) {
            String[] opts = {"Traducir lo nuevo de la página", "Traducir a otro idioma", "Ver el original"};
            new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                    .setTitle("Página traducida al " + Translator.name(t.translated))
                    .setItems(opts, (dd, w) -> {
                        if (w == 0) translatePage(t, t.translated);
                        else if (w == 1) chooseLanguage(t);
                        else restoreOriginal(t);
                    })
                    .show();
            return;
        }
        chooseLanguage(t);
    }

    private void chooseLanguage(Tab t) {
        String[] names = new String[Translator.TARGETS.length];
        int sel = 0;
        String last = getSharedPreferences("nocturna", MODE_PRIVATE).getString("idioma", "es");
        for (int i = 0; i < names.length; i++) {
            names[i] = Translator.TARGETS[i][1];
            if (Translator.TARGETS[i][0].equals(last)) sel = i;
        }
        new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                .setTitle("Traducir la página al…")
                .setSingleChoiceItems(names, sel, (dd, w) -> {
                    dd.dismiss();
                    String code = Translator.TARGETS[w][0];
                    getSharedPreferences("nocturna", MODE_PRIVATE).edit().putString("idioma", code).apply();
                    if (t.translated != null) {
                        restoreOriginal(t);
                    }
                    translatePage(t, code);
                })
                .show();
    }

    private void restoreOriginal(Tab t) {
        t.web.evaluateJavascript("window.__noctTr&&window.__noctTr.restore()", null);
        t.translated = null;
    }

    /** Junta los textos de la página, los traduce por tandas y los cambia ahí mismo (sin recargar). */
    private void translatePage(Tab t, String target) {
        Toast.makeText(this, "Traduciendo al " + Translator.name(target) + "…", Toast.LENGTH_SHORT).show();
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(5);
        t.web.evaluateJavascript(Translator.script() + ";window.__noctTr.collect()", value -> {
            int start;
            List<String> texts = new ArrayList<>();
            try {
                String json = new org.json.JSONArray("[" + value + "]").getString(0);
                org.json.JSONObject o = new org.json.JSONObject(json);
                start = o.getInt("start");
                org.json.JSONArray a = o.getJSONArray("texts");
                for (int i = 0; i < a.length(); i++) texts.add(a.getString(i));
            } catch (Exception e) {
                progress.setVisibility(View.INVISIBLE);
                Toast.makeText(this, "No se pudo leer el texto de la página", Toast.LENGTH_SHORT).show();
                return;
            }
            if (texts.isEmpty()) {
                progress.setVisibility(View.INVISIBLE);
                t.translated = target;
                Toast.makeText(this, "No hay texto nuevo para traducir", Toast.LENGTH_SHORT).show();
                return;
            }
            final int base = start;
            new Thread(() -> {
                String[] detected = {null};
                String error = null;
                int i = 0, done = 0;
                while (i < texts.size()) {
                    // tandas de hasta 90 textos o ~4500 letras
                    List<String> batch = new ArrayList<>();
                    int chars = 0, from = i;
                    while (i < texts.size() && batch.size() < 90 && (batch.isEmpty() || chars + texts.get(i).length() < 4500)) {
                        String s = texts.get(i);
                        if (s.length() > 4500) s = s.substring(0, 4500);
                        batch.add(s);
                        chars += s.length();
                        i++;
                    }
                    try {
                        List<String> tr = Translator.translate(batch, target, detected);
                        org.json.JSONArray arr = new org.json.JSONArray();
                        for (String s : tr) arr.put(s);
                        String code = "window.__noctTr&&window.__noctTr.apply(" + (base + from) + "," + arr + ")";
                        done += batch.size();
                        int pct = 5 + 95 * done / texts.size();
                        runOnUiThread(() -> {
                            t.web.evaluateJavascript(code, null);
                            if (t == current) progress.setProgress(pct);
                        });
                    } catch (java.io.IOException e) {
                        error = e.getMessage();
                        break;
                    }
                }
                String err = error, from = detected[0];
                final int translatedCount = done;
                runOnUiThread(() -> {
                    if (t == current) progress.setVisibility(View.INVISIBLE);
                    if (err != null && translatedCount == 0) {
                        translateFailed(t, target, err);
                        return;
                    }
                    t.translated = target;
                    if (from != null && from.split("-")[0].equals(target.split("-")[0]) && !target.contains("-")) {
                        Toast.makeText(this, "La página ya estaba en " + Translator.name(target), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Traducido" + (from != null ? " del " + Translator.name(from) : "") + " al "
                                + Translator.name(target) + (err != null ? " (en parte)" : ""), Toast.LENGTH_SHORT).show();
                    }
                });
            }, "traductor").start();
        });
    }

    private void translateFailed(Tab t, String target, String err) {
        String proxy = Translator.proxyUrl(t.url, target);
        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                .setTitle("No se pudo traducir")
                .setMessage((err == null ? "" : err + "\n\n") + "Puedes abrir la página traducida por Google (se abre en otra dirección).")
                .setNegativeButton("Cerrar", null);
        if (proxy != null) b.setPositiveButton("Abrir traducida", (dd, w) -> load(t, proxy));
        b.show();
    }

    /** Aviso corto abajo, con un botón. */
    private void snack(String text, String button, Runnable action) {
        FrameLayout root = (FrameLayout) findViewById(android.R.id.content);
        LinearLayout bar = Ui.row(this);
        bar.setPadding(dp(16), dp(12), dp(8), dp(12));
        bar.setBackground(Ui.roundStroke(Ui.SURFACE3, dp(16), dp(1), 0xFF33405A));
        TextView t = Ui.text(this, text, 14, Ui.TEXT, false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        bar.addView(t, Ui.weight(1));
        TextView b = Ui.text(this, button, 14, Ui.ACCENT2, true);
        b.setPadding(dp(12), dp(4), dp(12), dp(4));
        bar.addView(b);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(12), 0, dp(12), dp(72));
        root.addView(bar, lp);
        b.setOnClickListener(v -> {
            root.removeView(bar);
            action.run();
        });
        bar.postDelayed(() -> root.removeView(bar), 4000);
    }

    // ------------------------------------------------------------------ navegación y sistema

    private void goBack() {
        if (customView != null) {
            exitFullscreen();
        } else if (current != null && current.web.canGoBack()) {
            current.web.goBack();
        } else if (current != null && !HomePage.URL.equals(current.url)) {
            load(current, HomePage.URL);
        }
    }

    private void exitFullscreen() {
        if (customView == null) return;
        fullscreen.removeView(customView);
        fullscreen.setVisibility(View.GONE);
        customView = null;
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) c.show(WindowInsets.Type.systemBars());
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
        } else if (current != null && current.web.canGoBack()) {
            current.web.goBack();
        } else if (tabs.size() > 1 && current != null) {
            closeTab(current);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        for (Tab t : tabs) t.web.destroy();
        super.onDestroy();
    }
}
