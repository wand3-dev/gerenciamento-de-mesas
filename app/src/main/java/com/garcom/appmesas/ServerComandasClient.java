package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerComandasClient {

    private static final String PREF_NAME = "server_config_prefs";
    private static final String KEY_SERVER_IP = "key_server_ip";
    private static final String KEY_SERVER_PORT = "key_server_port";

    private static final String DEFAULT_IP = "192.168.0.246";
    private static final String DEFAULT_PORT = "8075";
    private static final String AUTH_HEADER = "Basic " + Base64.encodeToString("preatend:preatend".getBytes(), Base64.NO_WRAP);

    private static ServerComandasClient instance;
    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(6);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnItensMesaLoadedListener {
        void onSuccess(List<JSONObject> itens);
        void onEmpty();
        void onError(String erro);
    }

    public interface OnComandaEncontradaListener {
        void onEncontrada(int mesaOrigem, List<JSONObject> itensComanda);
        void onNotFound(String mensagem);
        void onError(String erro);
    }

    private ServerComandasClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized ServerComandasClient getInstance(Context context) {
        if (instance == null) {
            instance = new ServerComandasClient(context);
        }
        return instance;
    }

    public String getServerIp() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_SERVER_IP, DEFAULT_IP);
    }

    public String getServerPort() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_SERVER_PORT, DEFAULT_PORT);
    }

    public void salvarConfiguracao(String ip, String porta) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_SERVER_IP, ip.trim())
                .putString(KEY_SERVER_PORT, porta.trim())
                .apply();
    }

    public String getBaseUrl() {
        return "http://" + getServerIp() + ":" + getServerPort() + "/datasnap/rest/tpreatend";
    }

    /**
     * Requisição Única Principal do Sistema:
     * GET /datasnap/rest/tpreatend/func_MostrarComandasItens/T/mesa={numeroMesa}
     */
    public List<JSONObject> buscarItensDaMesaSync(int numeroMesa) throws Exception {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            String urlStr = getBaseUrl() + "/func_MostrarComandasItens/T/mesa=" + numeroMesa;
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            conn.setRequestProperty("Authorization", AUTH_HEADER);
            conn.setRequestProperty("User-Agent", "Dart/3.5 (dart:io)");
            conn.setRequestProperty("Accept", "application/json, text/html, */*");

            int statusCode = conn.getResponseCode();
            if (statusCode == 200) {
                String charset = "ISO-8859-1"; // Padrão Delphi DataSnap
                String contentType = conn.getContentType();
                if (contentType != null && contentType.toLowerCase().contains("charset=utf-8")) {
                    charset = "UTF-8";
                }

                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), charset));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                String respStr = response.toString().trim();
                if (respStr.isEmpty() || respStr.equalsIgnoreCase("null") || respStr.equals("[]")) {
                    return new ArrayList<>();
                }

                JSONArray arr;
                if (respStr.startsWith("[")) {
                    arr = new JSONArray(respStr);
                } else if (respStr.startsWith("{")) {
                    arr = new JSONArray();
                    arr.put(new JSONObject(respStr));
                } else {
                    return new ArrayList<>();
                }

                List<JSONObject> itens = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    itens.add(arr.getJSONObject(i));
                }
                return itens;

            } else if (statusCode == 404) {
                return new ArrayList<>();
            } else {
                throw new Exception("Erro HTTP " + statusCode);
            }
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Consulta assíncrona dos itens de uma mesa específica
     */
    public void buscarItensDaMesa(int numeroMesa, OnItensMesaLoadedListener listener) {
        executor.execute(() -> {
            try {
                List<JSONObject> itens = buscarItensDaMesaSync(numeroMesa);
                if (itens.isEmpty()) {
                    mainHandler.post(listener::onEmpty);
                } else {
                    mainHandler.post(() -> listener.onSuccess(itens));
                }
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : "Erro de conexão";
                mainHandler.post(() -> listener.onError(err));
            }
        });
    }

    /**
     * Procura a comanda varrendo as mesas 1 por 1 com a requisição 1:
     * Faz GET /func_MostrarComandasItens/T/mesa={M} iterando mesa a mesa até encontrar a comanda procurada!
     */
    /**
     * Procura a comanda varrendo o servidor:
     * 1. Testa a mesa de número igual à comanda (caso mesa == comanda, ex: 239)
     * 2. Testa a mesa preferencial
     * 3. Varre as mesas abertas e mesas padrão
     */
    public void buscarComandaVarrendoMesas(String numeroComanda, int mesaPreferencial, OnComandaEncontradaListener listener) {
        executor.execute(() -> {
            String alvo = numeroComanda.trim();
            List<JSONObject> itensEncontrados = new ArrayList<>();
            int mesaOndeAchou = -1;

            // 1. Testa primeiro a mesa com o mesmo número da comanda (se numérico, ex: comanda 239 -> mesa 239)
            int mesaIgualComanda = -1;
            try {
                mesaIgualComanda = Integer.parseInt(alvo);
                if (mesaIgualComanda > 0) {
                    List<JSONObject> itensMesa = buscarItensDaMesaSync(mesaIgualComanda);
                    for (JSONObject obj : itensMesa) {
                        String cmd = obj.optString("NUM_COMANDA", "").trim();
                        if (cmd.equals(alvo) || alvo.equals(String.valueOf(mesaIgualComanda))) {
                            itensEncontrados.add(obj);
                        }
                    }
                    if (!itensEncontrados.isEmpty()) {
                        mesaOndeAchou = mesaIgualComanda;
                        final int fMesa = mesaOndeAchou;
                        final List<JSONObject> fItens = itensEncontrados;
                        mainHandler.post(() -> listener.onEncontrada(fMesa, fItens));
                        return;
                    }
                }
            } catch (Exception ignored) {}

            // 2. Testa a mesa indicada pelo garçom (se informada)
            if (mesaPreferencial > 0 && mesaPreferencial != mesaIgualComanda) {
                try {
                    List<JSONObject> itensMesa = buscarItensDaMesaSync(mesaPreferencial);
                    for (JSONObject obj : itensMesa) {
                        String cmd = obj.optString("NUM_COMANDA", "").trim();
                        if (cmd.equals(alvo)) {
                            itensEncontrados.add(obj);
                        }
                    }
                    if (!itensEncontrados.isEmpty()) {
                        mesaOndeAchou = mesaPreferencial;
                        final int fMesa = mesaOndeAchou;
                        final List<JSONObject> fItens = itensEncontrados;
                        mainHandler.post(() -> listener.onEncontrada(fMesa, fItens));
                        return;
                    }
                } catch (Exception ignored) {}
            }

            // 3. Se não achou, varre mesas conhecidas cadastradas no MesaManager + 1 a 34
            boolean houveErro = false;
            String ultimoErro = "";

            java.util.LinkedHashSet<Integer> mesasParaTestar = new java.util.LinkedHashSet<>();
            if (MesaManager.getInstance(context) != null) {
                for (Mesa m : MesaManager.getInstance(context).getMesas()) {
                    mesasParaTestar.add(m.getNumero());
                }
            }
            for (int i = 1; i <= 34; i++) {
                mesasParaTestar.add(i);
            }

            for (int m : mesasParaTestar) {
                if (m == mesaPreferencial || m == mesaIgualComanda) continue;

                try {
                    List<JSONObject> itensMesa = buscarItensDaMesaSync(m);
                    for (JSONObject obj : itensMesa) {
                        String cmd = obj.optString("NUM_COMANDA", "").trim();
                        if (cmd.equals(alvo)) {
                            itensEncontrados.add(obj);
                        }
                    }

                    if (!itensEncontrados.isEmpty()) {
                        mesaOndeAchou = m;
                        break; // ENCONTROU!
                    }
                } catch (Exception e) {
                    houveErro = true;
                    ultimoErro = e.getMessage() != null ? e.getMessage() : "Erro";
                }
            }

            if (mesaOndeAchou != -1 && !itensEncontrados.isEmpty()) {
                final int fMesa = mesaOndeAchou;
                final List<JSONObject> fItens = itensEncontrados;
                mainHandler.post(() -> listener.onEncontrada(fMesa, fItens));
            } else if (houveErro && itensEncontrados.isEmpty()) {
                final String err = ultimoErro;
                mainHandler.post(() -> listener.onError("Falha na comunicação: " + err));
            } else {
                mainHandler.post(() -> listener.onNotFound("Comanda #" + alvo + " não encontrada aberta em nenhuma mesa."));
            }
        });
    }

    /**
     * Busca síncrona para detectar se uma comanda mudou para outra mesa no servidor
     * Retorna o número da nova mesa onde a comanda foi localizada, ou -1 se não encontrada
     */
    public int detectarNovaMesaDaComandaSync(String comanda, int mesaAtual) {
        if (comanda == null || comanda.trim().isEmpty()) return -1;
        String alvo = comanda.trim();

        // 1. Testa mesa com mesmo número da comanda
        try {
            int numIgual = Integer.parseInt(alvo);
            if (numIgual != mesaAtual && numIgual > 0) {
                List<JSONObject> itens = buscarItensDaMesaSync(numIgual);
                for (JSONObject obj : itens) {
                    if (alvo.equals(obj.optString("NUM_COMANDA", "").trim())) {
                        return numIgual;
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Varre as outras mesas ativas no app
        java.util.LinkedHashSet<Integer> mesas = new java.util.LinkedHashSet<>();
        if (MesaManager.getInstance(context) != null) {
            for (Mesa m : MesaManager.getInstance(context).getMesas()) {
                if (m.getNumero() != mesaAtual) mesas.add(m.getNumero());
            }
        }
        for (int i = 1; i <= 34; i++) {
            if (i != mesaAtual) mesas.add(i);
        }

        for (int m : mesas) {
            try {
                List<JSONObject> itens = buscarItensDaMesaSync(m);
                for (JSONObject obj : itens) {
                    if (alvo.equals(obj.optString("NUM_COMANDA", "").trim())) {
                        return m;
                    }
                }
            } catch (Exception ignored) {}
        }

        return -1;
    }
}
