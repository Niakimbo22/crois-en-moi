package com.croisenmoi.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.*;
import androidx.core.content.FileProvider;
import java.io.File;

public class MainActivity extends Activity {
    private WebView webView;
    private long pendingDownloadId = -1;
    private BroadcastReceiver downloadReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
                return false;
            }
        });
        webView.addJavascriptInterface(new UpdateInterface(), "Android");
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    class UpdateInterface {
        @android.webkit.JavascriptInterface
        public void downloadAndInstall(String url) {
            File apkFile = new File(getExternalFilesDir(null), "update.apk");
            if (apkFile.exists()) apkFile.delete();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("Crois en Moi \u2014 Mise \u00e0 jour");
            request.setDescription("T\u00e9l\u00e9chargement en cours...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDestinationUri(Uri.fromFile(apkFile));
            request.setMimeType("application/vnd.android.package-archive");

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            pendingDownloadId = manager.enqueue(request);

            downloadReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == pendingDownloadId) {
                        DownloadManager.Query q = new DownloadManager.Query();
                        q.setFilterById(id);
                        Cursor c = manager.query(q);
                        if (c.moveToFirst()) {
                            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                installApk(apkFile);
                            }
                        }
                        c.close();
                        try { unregisterReceiver(this); } catch (Exception ignored) {}
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        }
    }

    private void installApk(File apkFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apkFile);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            apkUri = Uri.fromFile(apkFile);
        }
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadReceiver != null) {
            try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        }
    }
}
