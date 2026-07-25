package com.croisenmoi.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/**
 * Mise à jour automatique : au démarrage, l'application compare sa version à
 * celle publiée, télécharge le nouvel APK et lance son installation sans que
 * l'utilisateur ait à demander quoi que ce soit. Seule la confirmation finale
 * reste à sa charge : Android l'impose à toute application non système.
 */
public class MainActivity extends Activity {

    private static final String VERSION_URL =
        "https://niakimbo22.github.io/crois-en-moi/version.json";
    private static final String APK_URL =
        "https://github.com/Niakimbo22/crois-en-moi/releases/download/latest/crois-en-moi.apk";
    private static final String RELEASE_PAGE =
        "https://github.com/Niakimbo22/crois-en-moi/releases/latest";
    private static final String INSTALL_ACTION = "com.croisenmoi.app.INSTALL_RESULT";

    private WebView webView;
    private long pendingDownloadId = -1;
    private BroadcastReceiver downloadReceiver;
    private boolean updateInProgress = false;
    private boolean installLaunched = false;
    private boolean viewFallbackUsed = false;
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

        handleInstallResult(getIntent());
        // Laisse l'application s'ouvrir avant de sonder le serveur.
        handler.postDelayed(this::checkForUpdate, 2500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInstallResult(intent);
    }

    // ---------------------------------------------------------------- Pont JS

    class UpdateInterface {
        /** Signale à la page que l'application se met à jour toute seule. */
        @android.webkit.JavascriptInterface
        public boolean hasAutoUpdate() {
            return true;
        }

        @android.webkit.JavascriptInterface
        public void downloadAndInstall(String url) {
            runOnUiThread(() -> startUpdate(url == null || url.isEmpty() ? APK_URL : url));
        }

        @android.webkit.JavascriptInterface
        public void openInBrowser(String url) {
            runOnUiThread(() -> openExternally(url));
        }
    }

    // ------------------------------------------------- Détection d'une version

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection)
                    new URL(VERSION_URL + "?t=" + System.currentTimeMillis()).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);

                StringBuilder body = new StringBuilder();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();
                connection.disconnect();

                String published = new JSONObject(body.toString()).optString("version", "");
                String installed = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
                if (isNewer(published, installed)) {
                    runOnUiThread(() -> startUpdate(APK_URL));
                }
            } catch (Exception ignored) {
                // Hors ligne ou serveur injoignable : on réessaiera au prochain lancement.
            }
        }).start();
    }

    /** Compare « 2.6 » à « 2.5 » nombre par nombre. */
    private boolean isNewer(String published, String installed) {
        if (published == null || published.isEmpty() || installed == null) return false;
        String[] a = published.split("\\.");
        String[] b = installed.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int left = i < a.length ? parse(a[i]) : 0;
            int right = i < b.length ? parse(b[i]) : 0;
            if (left != right) return left > right;
        }
        return false;
    }

    private int parse(String value) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return 0; }
    }

    // ----------------------------------------------------------- Téléchargement

    private void startUpdate(String url) {
        if (updateInProgress) return;
        updateInProgress = true;
        installLaunched = false;
        viewFallbackUsed = false;

        File apkFile = updateFile();
        if (apkFile == null) {
            toast("Stockage indisponible, ouverture du navigateur.");
            openExternally(RELEASE_PAGE);
            return;
        }
        try {
            if (apkFile.exists()) apkFile.delete();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("Crois en Moi — Mise à jour");
            request.setDescription("Téléchargement de la nouvelle version…");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDestinationUri(Uri.fromFile(apkFile));
            request.setMimeType("application/vnd.android.package-archive");

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            pendingDownloadId = manager.enqueue(request);
            toast("Mise à jour en cours de téléchargement…");

            registerDownloadReceiver(manager, apkFile);
            // Filet de sécurité : le broadcast du système n'arrive pas toujours.
            watchDownload(manager, apkFile, 0);
        } catch (Exception e) {
            updateInProgress = false;
            toast("Téléchargement impossible, ouverture du navigateur.");
            openExternally(RELEASE_PAGE);
        }
    }

    private File updateFile() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "update.apk");
    }

    private void registerDownloadReceiver(DownloadManager manager, File apkFile) {
        unregisterDownloadReceiver();
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == pendingDownloadId
                        && statusOf(manager, id) == DownloadManager.STATUS_SUCCESSFUL) {
                    installUpdate(apkFile);
                }
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // Le broadcast vient du gestionnaire de téléchargements du système :
                // en RECEIVER_NOT_EXPORTED il n'arriverait jamais.
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
                installUpdate(apkFile);
            } else if (status == DownloadManager.STATUS_FAILED) {
                updateInProgress = false;
                toast("Téléchargement échoué, ouverture du navigateur.");
                openExternally(RELEASE_PAGE);
            } else if (attempt < 300) {
                watchDownload(manager, apkFile, attempt + 1);
            } else {
                updateInProgress = false;
                toast("La mise à jour n'a pas abouti. Réessaie plus tard.");
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

    // -------------------------------------------------------------- Installation

    /**
     * Installe via PackageInstaller : l'APK est remis directement au système,
     * sans passer par un fichier partagé. Android affiche alors sa fenêtre de
     * confirmation, seule étape qu'une application ne peut pas contourner.
     */
    private void installUpdate(File apkFile) {
        if (installLaunched) return;
        installLaunched = true;
        unregisterDownloadReceiver();

        if (!apkFile.exists() || apkFile.length() < 100000) {
            updateInProgress = false;
            toast("Fichier de mise à jour incomplet.");
            openExternally(RELEASE_PAGE);
            return;
        }

        new Thread(() -> {
            PackageInstaller.Session session = null;
            try {
                PackageInstaller installer = getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                try { params.setAppPackageName(getPackageName()); } catch (Exception ignored) {}

                int sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);

                OutputStream out = session.openWrite("croisenmoi", 0, apkFile.length());
                InputStream in = new FileInputStream(apkFile);
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                session.fsync(out);
                in.close();
                out.close();

                Intent callback = new Intent(this, MainActivity.class).setAction(INSTALL_ACTION);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                PendingIntent pending = PendingIntent.getActivity(this, sessionId, callback, flags);

                session.commit(pending.getIntentSender());
                session.close();
                session = null;
                toast("Installation de la mise à jour…");
            } catch (Exception e) {
                if (session != null) {
                    try { session.abandon(); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> installWithViewIntent(apkFile));
            }
        }).start();
    }

    /** Résultat renvoyé par le système après commit() de la session. */
    private void handleInstallResult(Intent intent) {
        if (intent == null || !INSTALL_ACTION.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android demande la confirmation de l'utilisateur : on l'affiche.
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(confirm);
                    return;
                } catch (Exception ignored) {}
            }
            installWithViewIntent(updateFile());
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            updateInProgress = false;
            toast("Mise à jour installée.");
        } else {
            updateInProgress = false;
            installWithViewIntent(updateFile());
        }
    }

    /** Repli historique : ouverture de l'APK par une intention d'affichage. */
    private void installWithViewIntent(File apkFile) {
        if (viewFallbackUsed) return;
        viewFallbackUsed = true;
        if (apkFile == null || !apkFile.exists()) {
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

    // ------------------------------------------------------------------ Divers

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
