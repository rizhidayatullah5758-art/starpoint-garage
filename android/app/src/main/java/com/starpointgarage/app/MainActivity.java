package com.starpointgarage.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.Arrays;

public class MainActivity extends Activity {
    private static final String PRIMARY_URL = "https://starpointgarage.com/";
    private static final String FALLBACK_BASE = "https://rizhidayatullah5758-art.github.io/starpoint-garage/";
    private static final int FILE_CHOOSER_REQUEST = 4101;
    private static final int CAMERA_PERMISSION_REQUEST = 4102;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingCameraRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(8, 8, 8));
        getWindow().setNavigationBarColor(Color.rgb(8, 8, 8));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 8, 8));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(8, 8, 8));
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        root.addView(progressBar, progressParams);

        setContentView(root);
        configureWebView();

        String launchUrl = resolveLaunchUrl(getIntent());
        webView.loadUrl(launchUrl);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " StarpointGarageAndroid/1.0.0");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webView.setWebViewClient(new StarpointWebViewClient());
        webView.setWebChromeClient(new StarpointWebChromeClient());
    }

    private String resolveLaunchUrl(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data != null && isTrustedWebUri(data)) {
            return data.toString();
        }
        return PRIMARY_URL;
    }

    private boolean isTrustedWebUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        if ("starpointgarage.com".equalsIgnoreCase(host) || "www.starpointgarage.com".equalsIgnoreCase(host)) {
            return true;
        }
        return "rizhidayatullah5758-art.github.io".equalsIgnoreCase(host)
                && uri.getPath() != null
                && uri.getPath().startsWith("/starpoint-garage");
    }

    private boolean isPrimaryHost(Uri uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost();
        return "starpointgarage.com".equalsIgnoreCase(host)
                || "www.starpointgarage.com".equalsIgnoreCase(host);
    }

    private String fallbackFor(String failedUrl) {
        try {
            Uri uri = Uri.parse(failedUrl);
            String path = uri.getEncodedPath();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                path = "";
            } else if (path.startsWith("/")) {
                path = path.substring(1);
            }
            StringBuilder out = new StringBuilder(FALLBACK_BASE).append(path);
            if (uri.getEncodedQuery() != null) {
                out.append("?").append(uri.getEncodedQuery());
            }
            if (uri.getEncodedFragment() != null) {
                out.append("#").append(uri.getEncodedFragment());
            }
            return out.toString();
        } catch (Exception ignored) {
            return FALLBACK_BASE;
        }
    }

    private void handleMainFrameFailure(String failedUrl) {
        Uri uri = Uri.parse(failedUrl == null ? "" : failedUrl);
        if (isPrimaryHost(uri)) {
            webView.loadUrl(fallbackFor(failedUrl));
        } else {
            showOfflinePage();
        }
    }

    private void showOfflinePage() {
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{margin:0;background:#080808;color:#fff;font-family:sans-serif;display:grid;place-items:center;min-height:100vh}"
                + ".c{padding:28px;text-align:center;max-width:420px}p{color:#999;line-height:1.6}button{background:#fff;color:#000;border:0;border-radius:12px;padding:13px 18px;font-weight:800}</style></head>"
                + "<body><div class='c'><h2>Starpoint Garage</h2><p>Koneksi belum tersedia. Pastikan internet aktif lalu coba lagi.</p>"
                + "<button onclick=\"location.href='" + PRIMARY_URL + "'\">Coba Lagi</button></div></body></html>";
        webView.loadDataWithBaseURL(PRIMARY_URL, html, "text/html", "UTF-8", null);
    }

    private void openExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Aplikasi untuk membuka tautan ini tidak ditemukan.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean handleNavigation(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return false;
        }

        if (rawUrl.startsWith("intent:")) {
            try {
                Intent intent = Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Tidak dapat membuka tautan.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        Uri uri = Uri.parse(rawUrl);
        String scheme = uri.getScheme();

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (isTrustedWebUri(uri)) {
                return false;
            }
            openExternal(uri);
            return true;
        }

        if ("about".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)
                || "javascript".equalsIgnoreCase(scheme) || "blob".equalsIgnoreCase(scheme)) {
            return false;
        }

        openExternal(uri);
        return true;
    }

    private boolean isTrustedPermissionOrigin(Uri origin) {
        return isTrustedWebUri(origin);
    }

    private boolean requestsVideoCapture(PermissionRequest request) {
        return Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
    }

    private void handleWebCameraPermission(PermissionRequest request) {
        if (!isTrustedPermissionOrigin(request.getOrigin()) || !requestsVideoCapture(request)) {
            request.deny();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            return;
        }

        if (pendingCameraRequest != null) {
            pendingCameraRequest.deny();
        }
        pendingCameraRequest = request;
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST || pendingCameraRequest == null) {
            return;
        }

        PermissionRequest request = pendingCameraRequest;
        pendingCameraRequest = null;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            request.deny();
            Toast.makeText(this, "Izin kamera diperlukan untuk scan QR.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class StarpointWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleNavigation(request.getUrl().toString());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleNavigation(url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                handleMainFrameFailure(request.getUrl().toString());
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                handleMainFrameFailure(request.getUrl().toString());
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            String url = error == null ? null : error.getUrl();
            if (url != null && isPrimaryHost(Uri.parse(url))) {
                webView.post(() -> webView.loadUrl(fallbackFor(url)));
            } else {
                webView.post(MainActivity.this::showOfflinePage);
            }
        }
    }

    private class StarpointWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> handleWebCameraPermission(request));
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            if (pendingCameraRequest == request) {
                pendingCameraRequest = null;
            }
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
        ) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }

            MainActivity.this.filePathCallback = filePathCallback;

            Intent intent;
            try {
                intent = fileChooserParams.createIntent();
            } catch (Exception e) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
            }

            try {
                startActivityForResult(Intent.createChooser(intent, "Pilih file"), FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException e) {
                MainActivity.this.filePathCallback = null;
                Toast.makeText(MainActivity.this, "File picker tidak tersedia.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }
}
