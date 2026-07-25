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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.*;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

public class MainActivity extends Activity {

    private static final String RELEASE_PAGE =
        "https://github.com/Niakimbo22/crois-en-moi/releases/latest";

    private WebView webView;
    private long pendingDownloadId = -1;
    private BroadcastReceiver downloadReceiver;
    private boolean installLaunched = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

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
                    openExternally(url);
                    return true;
                }
                return false;
            }
        });
        webView.addJavascriptInterface(new UpdateInterface(), "Android");
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
            openExternally(url));
        webView.loadUrl("file:///android_asset/index.html");
    }

    class UpdateInterface {
        /** Télécharge la nouvelle version puis lance son installation. */
        @android.webkit.JavascriptInterface
        public void downloadAndInstall(String url) {
            // Appelé depuis le thread JavaScript : tout repasse par le thread principal.
            runOnUiThread(() -> startUpdate(url));
        }

        /** Ouvre une adresse dans le navigateur du téléphone. */
        @android.webkit.JavascriptInterface
        public void openInBrowser(String url) {
            runOnUiThread(() -> openExternally(url));
        }
    }

    private void startUpdate(String url) {
        // Sans cette autorisation, l'installateur Android refuse silencieusement.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            toast("Autorise l'installation d'applications, puis relance la mise à jour.");
            try {
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settings);
            } catch (Exception e) {
                openExternally(RELEASE_PAGE);
            }
            return;
        }

        File apkFile = new File(getExternalFilesDir(null), "update.apk");
        try {
            if (apkFile.exists()) apkFile.delete();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("Crois en Moi — Mise à jour");
            request.setDescription("Téléchargement en cours…");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDestinationUri(Uri.fromFile(apkFile));
            request.setMimeType("application/vnd.android.package-archive");

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            installLaunched = false;
            pendingDownloadId = manager.enqueue(request);
            toast("Téléchargement de la mise à jour…");

            registerDownloadReceiver(manager, apkFile);
            // Filet de sécurité : le broadcast du système n'arrive pas toujours.
            watchDownload(manager, apkFile, 0);
        } catch (Exception e) {
            toast("Téléchargement impossible, ouverture du navigateur.");
            openExternally(RELEASE_PAGE);
        }
    }

    private void registerDownloadReceiver(DownloadManager manager, File apkFile) {
        unregisterDownloadReceiver();
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == pendingDownloadId && statusOf(manager, id) == DownloadManager.STATUS_SUCCESSFUL) {
                    installApk(apkFile);
                }
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // Le broadcast vient du gestionnaire de téléchargements du système :
                // il doit être reçu en RECEIVER_EXPORTED, sinon il n'arrive jamais.
                registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(downloadReceiver, filter);
            }
        } catch (Exception e) {
            downloadReceiver = null; // La surveillance périodique prend le relais.
        }
    }

    /** Vérifie l'avancement chaque seconde, pendant 5 minutes au maximum. */
    private void watchDownload(DownloadManager manager, File apkFile, int attempt) {
        handler.postDelayed(() -> {
            if (installLaunched) return;
            int status = statusOf(manager, pendingDownloadId);
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                installApk(apkFile);
            } else if (status == DownloadManager.STATUS_FAILED) {
                toast("Téléchargement échoué, ouverture du navigateur.");
                openExternally(RELEASE_PAGE);
            } else if (attempt < 300) {
                watchDownload(manager, apkFile, attempt + 1);
            } else {
                toast("La mise à jour n'a pas abouti. Essaie depuis le navigateur.");
            }
        }, 1000);
    }

    private int statusOf(DownloadManager manager, long id) {
        if (id < 0) return -1;
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        Cursor cursor = null;
        try {
            cursor = manager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1;
    }

    private void installApk(File apkFile) {
        if (installLaunched) return;
        installLaunched = true;
        unregisterDownloadReceiver();

        if (!apkFile.exists() || apkFile.length() == 0) {
            toast("Fichier de mise à jour introuvable.");
            openExternally(RELEASE_PAGE);
            return;
        }
        try {
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
        } catch (Exception e) {
            toast("Installation impossible, ouverture du navigateur.");
            openExternally(RELEASE_PAGE);
        }
    }

    private void openExternally(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            toast("Aucun navigateur disponible.");
        }
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void unregisterDownloadReceiver() {
        if (downloadReceiver != null) {
            try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
            downloadReceiver = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        unregisterDownloadReceiver();
    }
}
