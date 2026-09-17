package com.garcom.appmesas;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.android.material.button.MaterialButton;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {

    public static final String FIREBASE_RTDB_BASE = "https://appmesas-238a0-default-rtdb.firebaseio.com";
    public static final String DEFAULT_DOWNLOAD_URL = "https://github.com/wand3-dev/gerenciamento-de-mesas/releases/download/vers%C3%A3o/app-debug.apk";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class UpdateInfo {
        public int currentVersionCode;
        public String currentVersionName;
        public int remoteVersionCode;
        public String remoteVersionName;
        public String downloadUrl;
        public String novidades;
        public boolean obrigatorio;

        public UpdateInfo(int currentVersionCode, String currentVersionName,
                          int remoteVersionCode, String remoteVersionName,
                          String downloadUrl, String novidades, boolean obrigatorio) {
            this.currentVersionCode = currentVersionCode;
            this.currentVersionName = currentVersionName;
            this.remoteVersionCode = remoteVersionCode;
            this.remoteVersionName = remoteVersionName;
            this.downloadUrl = (downloadUrl == null || downloadUrl.isEmpty()) ? DEFAULT_DOWNLOAD_URL : downloadUrl;
            this.novidades = (novidades == null) ? "" : novidades;
            this.obrigatorio = obrigatorio;
        }
    }

    public interface OnUpdateCheckListener {
        void onUpdateAvailable(UpdateInfo info);
        void onAlreadyUpToDate();
        void onNoVersionPublished();
        void onError(String erro);
    }

    public UpdateChecker(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Consulta o Firebase Realtime Database para verificar se há nova versão publicada
     */
    public void verificarAtualizacao(OnUpdateCheckListener listener) {
        executor.execute(() -> {
            try {
                // Obtém versão atual do app instalada no celular
                PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                int currentCode = pInfo.versionCode;
                String currentName = pInfo.versionName != null ? pInfo.versionName : "1.0";

                // Consulta raiz do Firebase RTDB
                String jsonStr = consultarUrl(FIREBASE_RTDB_BASE + "/.json");

                if (jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.trim().equalsIgnoreCase("null")) {
                    // Tenta em sub-nós comuns como /versao ou /app
                    jsonStr = consultarUrl(FIREBASE_RTDB_BASE + "/versao.json");
                }

                if (jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.trim().equalsIgnoreCase("null")) {
                    jsonStr = consultarUrl(FIREBASE_RTDB_BASE + "/app.json");
                }

                if (jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.trim().equalsIgnoreCase("null")) {
                    mainHandler.post(listener::onNoVersionPublished);
                    return;
                }

                jsonStr = jsonStr.trim();
                int remoteCode = currentCode;
                String remoteName = currentName;
                String downloadUrl = DEFAULT_DOWNLOAD_URL;
                String novidades = "";
                boolean obrigatorio = false;

                if (jsonStr.startsWith("{")) {
                    // Objeto JSON
                    JSONObject obj = new JSONObject(jsonStr);

                    // Verifica se o objeto tem um nó interno como "versao" ou "app"
                    if (obj.has("versao") && obj.optJSONObject("versao") != null) {
                        obj = obj.getJSONObject("versao");
                    } else if (obj.has("app") && obj.optJSONObject("app") != null) {
                        obj = obj.getJSONObject("app");
                    }

                    remoteCode = obj.optInt("versionCode", obj.optInt("codigo_versao", obj.optInt("codigo", 0)));
                    remoteName = obj.optString("versionName", obj.optString("versao", obj.optString("version", "")));
                    downloadUrl = obj.optString("downloadUrl", obj.optString("url", obj.optString("link", DEFAULT_DOWNLOAD_URL)));
                    novidades = obj.optString("novidades", obj.optString("changelog", obj.optString("mensagem", "")));
                    obrigatorio = obj.optBoolean("obrigatorio", false);

                    // Se remoteName é só número inteiro, sincroniza com code
                    if (remoteCode <= 0 && !remoteName.isEmpty()) {
                        try {
                            remoteCode = Integer.parseInt(remoteName);
                        } catch (Exception ignored) {}
                    }

                } else {
                    // Valor primitivo direto no Firebase (ex: "1.1" ou 2)
                    String raw = jsonStr.replace("\"", "").trim();
                    try {
                        remoteCode = Integer.parseInt(raw);
                        remoteName = raw;
                    } catch (Exception e) {
                        remoteName = raw;
                    }
                }

                boolean novaVersaoDisponivel = false;

                if (remoteCode > currentCode) {
                    novaVersaoDisponivel = true;
                } else if (!remoteName.isEmpty() && isNomeVersaoMaior(remoteName, currentName)) {
                    novaVersaoDisponivel = true;
                }

                if (novaVersaoDisponivel) {
                    final UpdateInfo info = new UpdateInfo(
                            currentCode, currentName,
                            remoteCode, (remoteName.isEmpty() ? String.valueOf(remoteCode) : remoteName),
                            downloadUrl, novidades, obrigatorio
                    );
                    mainHandler.post(() -> listener.onUpdateAvailable(info));
                } else {
                    mainHandler.post(listener::onAlreadyUpToDate);
                }

            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "Falha na conexão";
                mainHandler.post(() -> listener.onError(msg));
            }
        });
    }

    private String consultarUrl(String urlStr) {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == 200) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    private boolean isNomeVersaoMaior(String remote, String current) {
        try {
            String[] rParts = remote.replaceAll("[^0-9.]", "").split("\\.");
            String[] cParts = current.replaceAll("[^0-9.]", "").split("\\.");
            int length = Math.max(rParts.length, cParts.length);
            for (int i = 0; i < length; i++) {
                int r = i < rParts.length && !rParts[i].isEmpty() ? Integer.parseInt(rParts[i]) : 0;
                int c = i < cParts.length && !cParts[i].isEmpty() ? Integer.parseInt(cParts[i]) : 0;
                if (r > c) return true;
                if (r < c) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Exibe o diálogo decorado informando da nova versão
     */
    public static void exibirDialogoAtualizacao(Activity activity, UpdateInfo info) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_atualizacao);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvVersaoAtual = dialog.findViewById(R.id.tvVersaoAtualDialog);
        TextView tvNovaVersao = dialog.findViewById(R.id.tvNovaVersaoDialog);
        LinearLayout layoutNovidades = dialog.findViewById(R.id.layoutNovidadesUpdate);
        TextView tvNovidadesTexto = dialog.findViewById(R.id.tvNovidadesTexto);
        MaterialButton btnCancelar = dialog.findViewById(R.id.btnCancelarUpdate);
        MaterialButton btnBaixar = dialog.findViewById(R.id.btnBaixarUpdate);

        LinearLayout layoutProgresso = dialog.findViewById(R.id.layoutProgressoDownload);
        ProgressBar progressBar = dialog.findViewById(R.id.progressBarDownload);
        TextView tvStatusProgresso = dialog.findViewById(R.id.tvStatusProgressoDownload);
        TextView tvDescricao = dialog.findViewById(R.id.tvDescricaoDialogUpdate);

        if (tvVersaoAtual != null) {
            tvVersaoAtual.setText("v" + info.currentVersionName);
        }
        if (tvNovaVersao != null) {
            tvNovaVersao.setText("v" + info.remoteVersionName);
        }

        if (layoutNovidades != null && tvNovidadesTexto != null) {
            if (!info.novidades.trim().isEmpty()) {
                layoutNovidades.setVisibility(View.VISIBLE);
                tvNovidadesTexto.setText(info.novidades);
            } else {
                layoutNovidades.setVisibility(View.GONE);
            }
        }

        if (info.obrigatorio && btnCancelar != null) {
            btnCancelar.setVisibility(View.GONE);
            dialog.setCancelable(false);
        } else if (btnCancelar != null) {
            btnCancelar.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnBaixar != null) {
            btnBaixar.setOnClickListener(v -> {
                btnBaixar.setEnabled(false);
                if (btnCancelar != null) btnCancelar.setEnabled(false);
                if (layoutProgresso != null) layoutProgresso.setVisibility(View.VISIBLE);
                if (tvDescricao != null) tvDescricao.setVisibility(View.GONE);

                Executors.newSingleThreadExecutor().execute(() -> {
                    File apkFile = baixarArquivoApk(activity, info.downloadUrl, (progresso, bytesLidos, totalBytes) -> {
                        activity.runOnUiThread(() -> {
                            if (progressBar != null) {
                                if (totalBytes > 0) {
                                    progressBar.setIndeterminate(false);
                                    progressBar.setMax(100);
                                    progressBar.setProgress(progresso);
                                    if (tvStatusProgresso != null) {
                                        String mbLidos = String.format("%.1f", bytesLidos / (1024.0 * 1024.0));
                                        String mbTotal = String.format("%.1f", totalBytes / (1024.0 * 1024.0));
                                        tvStatusProgresso.setText("Baixando: " + progresso + "% (" + mbLidos + "MB / " + mbTotal + "MB)");
                                    }
                                } else {
                                    progressBar.setIndeterminate(true);
                                    if (tvStatusProgresso != null) {
                                        tvStatusProgresso.setText("Baixando arquivo de atualização...");
                                    }
                                }
                            }
                        });
                    });

                    activity.runOnUiThread(() -> {
                        if (apkFile != null && apkFile.exists() && apkFile.length() > 0) {
                            if (tvStatusProgresso != null) {
                                tvStatusProgresso.setText("✓ Download concluído! Abrindo instalador...");
                            }
                            instalarApkAutomatico(activity, apkFile);
                            dialog.dismiss();
                        } else {
                            if (btnBaixar != null) btnBaixar.setEnabled(true);
                            if (btnCancelar != null) btnCancelar.setEnabled(true);
                            if (layoutProgresso != null) layoutProgresso.setVisibility(View.GONE);
                            if (tvDescricao != null) tvDescricao.setVisibility(View.VISIBLE);
                            Toast.makeText(activity, "Erro no download direto. Abrindo pelo navegador...", Toast.LENGTH_LONG).show();

                            // Fallback pelo navegador
                            try {
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl));
                                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(browserIntent);
                            } catch (Exception ignored) {}
                        }
                    });
                });
            });
        }

        dialog.show();
    }

    private interface DownloadProgressListener {
        void onProgress(int percent, long bytesDownloaded, long totalBytes);
    }

    private static File baixarArquivoApk(Context context, String downloadUrl, DownloadProgressListener listener) {
        HttpURLConnection connection = null;
        InputStream input = null;
        OutputStream output = null;
        try {
            URL url = new URL(downloadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "AppMesas-Updater");

            // Segue redirects caso seja redirecionamento do GitHub Releases (302)
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 307 || responseCode == 308) {
                String newUrl = connection.getHeaderField("Location");
                connection.disconnect();
                url = new URL(newUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
            }

            long fileLength = connection.getContentLength();
            input = new BufferedInputStream(connection.getInputStream(), 8192);

            File outputDir = context.getExternalCacheDir();
            if (outputDir == null) outputDir = context.getCacheDir();
            File outputFile = new File(outputDir, "AppMesas_update.apk");
            if (outputFile.exists()) {
                outputFile.delete();
            }

            output = new FileOutputStream(outputFile);
            byte[] data = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                if (fileLength > 0 && listener != null) {
                    listener.onProgress((int) ((total * 100) / fileLength), total, fileLength);
                }
                output.write(data, 0, count);
            }
            output.flush();
            return outputFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try { if (output != null) output.close(); } catch (Exception ignored) {}
            try { if (input != null) input.close(); } catch (Exception ignored) {}
            if (connection != null) connection.disconnect();
        }
    }

    private static void instalarApkAutomatico(Activity activity, File apkFile) {
        try {
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Não foi possível abrir o instalador: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
