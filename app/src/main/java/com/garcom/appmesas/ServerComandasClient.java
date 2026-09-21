package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Cliente HTTP estritamente em MODO APENAS LEITURA (READ-ONLY).
 *
 * O aplicativo NUNCA envia comandos de gravação, inserção, alteração,
 * exclusão ou acionamento de impressão no servidor DataSnap.
 *
 * Todas as requisições ao DataSnap são 100% seguras, passivas e de consulta (GET):
 * - Consulta de Comandas Abertas
 * - Consulta de Itens da Mesa/Comanda
 * - Consulta de Configurações Mobile
 * - Consulta do Catálogo de Produtos
 */
public class ServerComandasClient {

    private static final String PREF_NAME = "server_config_prefs";
    private static final String KEY_SERVER_IP = "key_server_ip";
    private static final String KEY_SERVER_PORT = "key_server_port";

    public static final String DEFAULT_IP = "192.168.0.246";
    public static final String DEFAULT_PORT = "8075";
    public static final String DEFAULT_CNPJ = "07983311000167";

    // Credenciais de leitura DataSnap (preatend / preatend)
    private static final String AUTH_HEADER = "Basic " + Base64.encodeToString("preatend:preatend".getBytes(), Base64.NO_WRAP);
    private static final String USER_AGENT = "Dart/3.5 (dart:io)";
    private static final String CLOUD_URL_BASE = "http://serverrest.ommini.com.br/getdadosjson";

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

    // =========================================================================
    // MÉTODOS AUXILIARES DE CONEXÃO HTTP E TRATAMENTO DE RESPOSTA
    // =========================================================================

    private HttpURLConnection abrirConexaoLeitura(String urlStr, boolean isDataSnap) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept-Encoding", "gzip");

        if (isDataSnap) {
            conn.setRequestProperty("Authorization", AUTH_HEADER);
            conn.setRequestProperty("Accept", "application/json, text/html, */*");
        } else {
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        }

        return conn;
    }

    private String lerRespostaHttp(HttpURLConnection conn, boolean isDataSnap) throws Exception {
        int statusCode = conn.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new Exception("Erro HTTP " + statusCode);
        }

        InputStream rawStream = conn.getInputStream();
        String encoding = conn.getContentEncoding();
        InputStream inStream = ("gzip".equalsIgnoreCase(encoding)) ? new GZIPInputStream(rawStream) : rawStream;

        // O Delphi DataSnap codifica respostas em ISO-8859-1; respostas da nuvem em UTF-8
        String charset = isDataSnap ? "ISO-8859-1" : "UTF-8";
        String contentType = conn.getContentType();
        if (contentType != null) {
            String ctLower = contentType.toLowerCase(Locale.ROOT);
            if (ctLower.contains("charset=utf-8")) {
                charset = "UTF-8";
            } else if (ctLower.contains("charset=iso-8859-1")) {
                charset = "ISO-8859-1";
            }
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inStream, charset));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString().trim();
    }

    // =========================================================================
    // 1. REQUISIÇÕES DELPHI DATASNAP (APENAS LEITURA - GET)
    // =========================================================================

    /**
     * [HAR Req 1, 2, 3, 5, 8, 11] - APENAS LEITURA
     * GET /datasnap/rest/tpreatend/func_MostrarComandasAbertas/T/cmd_nota.NUM_COMANDA%20between%201%20and%2010000
     * Fallback: GET /datasnap/rest/tpreatend/func_MostrarComandasAbertas/T
     */
    public List<JSONObject> buscarComandasAbertasSync() throws Exception {
        Exception ultimoErro = null;
        // Prioriza a URL com o filtro da faixa padrão (1 a 10000) registrada no HAR
        String[] urlsParaTentar = new String[]{
                getBaseUrl() + "/func_MostrarComandasAbertas/T/cmd_nota.NUM_COMANDA%20between%201%20and%2010000",
                getBaseUrl() + "/func_MostrarComandasAbertas/T"
        };

        for (String urlStr : urlsParaTentar) {
            HttpURLConnection conn = null;
            try {
                conn = abrirConexaoLeitura(urlStr, true);
                String respStr = lerRespostaHttp(conn, true);

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

                List<JSONObject> lista = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    lista.add(arr.getJSONObject(i));
                }
                return lista;
            } catch (Exception e) {
                ultimoErro = e;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        if (ultimoErro != null) throw ultimoErro;
        return new ArrayList<>();
    }

    /**
     * [HAR Req de Itens] - APENAS LEITURA
     * GET /datasnap/rest/tpreatend/func_MostrarComandasItens/T/mesa={numeroMesa}
     */
    public List<JSONObject> buscarItensDaMesaSync(int numeroMesa) throws Exception {
        HttpURLConnection conn = null;
        try {
            String urlStr = getBaseUrl() + "/func_MostrarComandasItens/T/mesa=" + numeroMesa;
            conn = abrirConexaoLeitura(urlStr, true);
            String respStr = lerRespostaHttp(conn, true);

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
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * [HAR Req 15] - APENAS LEITURA
     * GET /datasnap/rest/tpreatend/func_GetConfigMobile/T
     * Retorna configurações do mobile (faixa de comandas, tempos, etc.) sem alterar nada no servidor.
     */
    public JSONObject buscarConfigMobileSync() throws Exception {
        HttpURLConnection conn = null;
        try {
            String urlStr = getBaseUrl() + "/func_GetConfigMobile/T";
            conn = abrirConexaoLeitura(urlStr, true);
            String respStr = lerRespostaHttp(conn, true);

            if (respStr.startsWith("[")) {
                JSONArray arr = new JSONArray(respStr);
                if (arr.length() > 0) {
                    return arr.getJSONObject(0);
                }
            } else if (respStr.startsWith("{")) {
                return new JSONObject(respStr);
            }
            return new JSONObject();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * [HAR Req 13] - APENAS LEITURA
     * GET /datasnap/rest/tpreatend/func_GetProdutos/T/COD_TIPO_PROD%20in%20(0,4)
     * Consulta o catálogo de produtos ativo no PDV.
     */
    public List<JSONObject> buscarProdutosCatalogoSync() throws Exception {
        HttpURLConnection conn = null;
        try {
            String urlStr = getBaseUrl() + "/func_GetProdutos/T/COD_TIPO_PROD%20in%20(0,4)";
            conn = abrirConexaoLeitura(urlStr, true);
            conn.setReadTimeout(12000);
            String respStr = lerRespostaHttp(conn, true);

            if (respStr.isEmpty() || respStr.equals("[]")) {
                return new ArrayList<>();
            }

            JSONArray arr = new JSONArray(respStr);
            List<JSONObject> lista = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                lista.add(arr.getJSONObject(i));
            }
            return lista;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // =========================================================================
    // 2. CONSULTA EM NUVEM OMMINI (AUTODESCOBERTA DE IP - APENAS LEITURA)
    // =========================================================================

    /**
     * [HAR Req 16] - APENAS CONSULTA
     * POST http://serverrest.ommini.com.br/getdadosjson/serverconfig
     * Consulta na nuvem o IP e Porta do servidor local sem alterar registros.
     */
    public JSONObject descobrirServidorPorCnpjSync(String cnpj) throws Exception {
        HttpURLConnection conn = null;
        try {
            String urlStr = CLOUD_URL_BASE + "/serverconfig";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("par1", "S05");
            body.put("par2", cnpj != null ? cnpj.trim() : DEFAULT_CNPJ);

            byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(b);
                os.flush();
            }

            String respStr = lerRespostaHttp(conn, false);
            if (respStr.startsWith("[")) {
                JSONArray arr = new JSONArray(respStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String host = obj.optString("hostname", "").trim();
                    String port = obj.optString("port", "").trim();
                    if (!host.isEmpty() && !port.isEmpty()) {
                        salvarConfiguracao(host, port);
                        return obj;
                    }
                }
            }
            throw new Exception("Servidor não localizado na nuvem para o CNPJ informado.");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // =========================================================================
    // 3. MÉTODOS ASSÍNCRONOS E VARREDURA INTELIGENTE DE COMANDAS (LEITURA LOCAL)
    // =========================================================================

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

    public void buscarComandaVarrendoMesas(String numeroComanda, int mesaPreferencial, OnComandaEncontradaListener listener) {
        executor.execute(() -> {
            String alvo = numeroComanda.trim();
            List<JSONObject> itensEncontrados = new ArrayList<>();
            int mesaOndeAchou = -1;

            // 1. Testa mesa com mesmo número da comanda
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

            // 2. Testa a mesa preferencial
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

            // 3. Varre demais mesas
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
                        break;
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

    public int detectarNovaMesaDaComandaSync(String comanda, int mesaAtual) {
        if (comanda == null || comanda.trim().isEmpty()) return -1;
        String alvo = comanda.trim();

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
