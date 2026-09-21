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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Cliente HTTP padronizado de alta precisão para comunicação com:
 * 1. Servidor local Delphi DataSnap (http://{IP}:{PORTA}/datasnap/rest/tpreatend/...)
 * 2. Servidor de nuvem Ommini Cloud (http://serverrest.ommini.com.br/getdadosjson/...)
 *
 * Mapeado e estruturado estritamente conforme o tráfego oficial registrado no arquivo HAR.
 */
public class ServerComandasClient {

    private static final String PREF_NAME = "server_config_prefs";
    private static final String KEY_SERVER_IP = "key_server_ip";
    private static final String KEY_SERVER_PORT = "key_server_port";
    private static final String KEY_COD_LOJA = "key_cod_loja";
    private static final String KEY_COD_VENDEDOR = "key_cod_vendedor";

    public static final String DEFAULT_IP = "192.168.0.246";
    public static final String DEFAULT_PORT = "8075";
    public static final String DEFAULT_CNPJ = "07983311000167";

    // Credenciais DataSnap extraídas do fluxo oficial: preatend / preatend
    private static final String AUTH_HEADER = "Basic " + Base64.encodeToString("preatend:preatend".getBytes(), Base64.NO_WRAP);
    private static final String USER_AGENT = "Dart/3.5 (dart:io)";
    private static final String CLOUD_URL_BASE = "http://serverrest.ommini.com.br/getdadosjson";

    private static ServerComandasClient instance;
    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(6);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Modelo de item para envio de comanda via func_InserirComanda
    public static class ItemLancamento {
        public String codProd;
        public String qtde;
        public String precoUnitario;
        public String precoTotal;
        public String observacao; // pode ser null
        public String codVendedor;

        public ItemLancamento(String codProd, String qtde, String precoUnitario, String precoTotal, String observacao, String codVendedor) {
            this.codProd = codProd;
            this.qtde = qtde;
            this.precoUnitario = precoUnitario;
            this.precoTotal = precoTotal;
            this.observacao = observacao;
            this.codVendedor = codVendedor;
        }
    }

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

    public interface OnOperacaoListener {
        void onSuccess(String resultado);
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

    public String getCodLoja() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_COD_LOJA, "1");
    }

    public String getCodVendedor() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_COD_VENDEDOR, "223");
    }

    public void salvarConfiguracao(String ip, String porta) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_SERVER_IP, ip.trim())
                .putString(KEY_SERVER_PORT, porta.trim())
                .apply();
    }

    public void salvarCredenciaisAtendimento(String codLoja, String codVendedor) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_COD_LOJA, codLoja.trim())
                .putString(KEY_COD_VENDEDOR, codVendedor.trim())
                .apply();
    }

    public String getBaseUrl() {
        return "http://" + getServerIp() + ":" + getServerPort() + "/datasnap/rest/tpreatend";
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE CONEXÃO HTTP E TRATAMENTO DE RESPOSTA
    // =========================================================================

    private HttpURLConnection abrirConexao(String urlStr, String metodo, boolean isDataSnap) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(metodo);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept-Encoding", "gzip");

        if (isDataSnap) {
            conn.setRequestProperty("Authorization", AUTH_HEADER);
            conn.setRequestProperty("Accept", "application/json, text/html, */*");
            if ("POST".equalsIgnoreCase(metodo)) {
                // Delphi DataSnap exige Content-Type: text/plain; charset=utf-8 conforme o trace HAR
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            }
        } else {
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            if ("POST".equalsIgnoreCase(metodo)) {
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            }
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

        // Determina charset: Delphi DataSnap usa ISO-8859-1 como padrão, Nuvem usa UTF-8
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
    // 1. REQUISIÇÕES DELPHI DATASNAP (SERVIDOR LOCAL)
    // =========================================================================

    /**
     * [HAR Req 1, 2, 3, 5, 8, 11]
     * GET /datasnap/rest/tpreatend/func_MostrarComandasAbertas/T/cmd_nota.NUM_COMANDA%20between%201%20and%2010000
     * Fallback: GET /datasnap/rest/tpreatend/func_MostrarComandasAbertas/T
     */
    public List<JSONObject> buscarComandasAbertasSync() throws Exception {
        Exception ultimoErro = null;
        // Prioriza a URL com o filtro da faixa padrão (1 a 10000) conforme disparado no HAR
        String[] urlsParaTentar = new String[]{
                getBaseUrl() + "/func_MostrarComandasAbertas/T/cmd_nota.NUM_COMANDA%20between%201%20and%2010000",
                getBaseUrl() + "/func_MostrarComandasAbertas/T"
        };

        for (String urlStr : urlsParaTentar) {
            HttpURLConnection conn = null;
            try {
                conn = abrirConexao(urlStr, "GET", true);
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
     * [HAR Req de itens]
     * GET /datasnap/rest/tpreatend/func_MostrarComandasItens/T/mesa={numeroMesa}
     */
    public List<JSONObject> buscarItensDaMesaSync(int numeroMesa) throws Exception {
        HttpURLConnection conn = null;
        try {
            String urlStr = getBaseUrl() + "/func_MostrarComandasItens/T/mesa=" + numeroMesa;
            conn = abrirConexao(urlStr, "GET", true);
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
     * [HAR Req 6]
     * POST /datasnap/rest/tpreatend/func_InserirComanda/T
     * Payload: Matriz de 2 arrays: [[ {header} ], [ {item1}, {item2} ]]
     * Resposta esperada de sucesso: {"result":["777"]}
     */
    public boolean inserirComandaSync(String numComanda, int mesa, String codLoja, String codVendedor, List<ItemLancamento> itens) throws Exception {
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item informado para inserir.");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US);
        String dataHoraAtual = sdf.format(new Date());

        // 1. Array do cabeçalho da comanda
        JSONArray arrayHeader = new JSONArray();
        JSONObject headerObj = new JSONObject();
        headerObj.put("NUM_COMANDA", numComanda.trim());
        headerObj.put("CHK_BAIXA", "F");
        headerObj.put("COD_LOJA", (codLoja != null && !codLoja.isEmpty()) ? codLoja : getCodLoja());
        headerObj.put("FLG_TIPO_DOC", "C");
        headerObj.put("DATA", dataHoraAtual);
        headerObj.put("HORA", dataHoraAtual);
        headerObj.put("CONTATO", "");
        headerObj.put("MESA", String.valueOf(mesa));
        arrayHeader.put(headerObj);

        // 2. Array dos itens
        JSONArray arrayItens = new JSONArray();
        for (ItemLancamento it : itens) {
            JSONObject itemObj = new JSONObject();
            itemObj.put("NUM_COMANDA", numComanda.trim());
            itemObj.put("DATA", dataHoraAtual);
            itemObj.put("HORA", dataHoraAtual);
            itemObj.put("CHK_BAIXA", "F");
            itemObj.put("COD_LOJA", (codLoja != null && !codLoja.isEmpty()) ? codLoja : getCodLoja());
            itemObj.put("COD_PROD", it.codProd);
            itemObj.put("VLR_QTDE", it.qtde);
            itemObj.put("VLR_PRECO", it.precoUnitario);
            itemObj.put("VLR_TOTAL", it.precoTotal);
            if (it.observacao != null && !it.observacao.trim().isEmpty()) {
                itemObj.put("OBS", it.observacao.trim());
            } else {
                itemObj.put("OBS", JSONObject.NULL);
            }
            itemObj.put("COD_VEND", (it.codVendedor != null && !it.codVendedor.isEmpty()) ? it.codVendedor : codVendedor);
            arrayItens.put(itemObj);
        }

        // 3. Matriz global: [ [header], [itens] ]
        JSONArray matrizPayload = new JSONArray();
        matrizPayload.put(arrayHeader);
        matrizPayload.put(arrayItens);
        String jsonPayload = matrizPayload.toString();

        HttpURLConnection conn = null;
        try {
            String urlStr = getBaseUrl() + "/func_InserirComanda/T";
            conn = abrirConexao(urlStr, "POST", true);
            conn.setDoOutput(true);

            byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bodyBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            String respStr = lerRespostaHttp(conn, true);
            return respStr.contains("777");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * [HAR Req 4]
     * GET /datasnap/rest/tpreatend/func_ImprimirCozinha/T/0/{numComanda}/{codLoja}
     * Disparado logo após inserir comanda se IMPRIMIR_APOS_INSERIR="T"
     */
    public boolean imprimirCozinhaSync(String numComanda, String codLoja) throws Exception {
        HttpURLConnection conn = null;
        try {
            String loja = (codLoja != null && !codLoja.isEmpty()) ? codLoja : getCodLoja();
            String urlStr = getBaseUrl() + "/func_ImprimirCozinha/T/0/" + numComanda.trim() + "/" + loja.trim();
            conn = abrirConexao(urlStr, "GET", true);
            String respStr = lerRespostaHttp(conn, true);
            return respStr.contains("777");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * [HAR Req 15]
     * GET /datasnap/rest/tpreatend/func_GetConfigMobile/T
     * Retorna configurações do mobile: FAIXA_INICIAL, FAIXA_FINAL, CARTAO_MESA, IMPRIMIR_AUTO, etc.
     */
    public JSONObject buscarConfigMobileSync() throws Exception {
        HttpURLConnection conn = null;
        try {
            String urlStr = getBaseUrl() + "/func_GetConfigMobile/T";
            conn = abrirConexao(urlStr, "GET", true);
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
     * [HAR Req 13]
     * GET /datasnap/rest/tpreatend/func_GetProdutos/T/COD_TIPO_PROD%20in%20(0,4)
     * Retorna o catálogo completo de produtos ativos no DataSnap
     */
    public List<JSONObject> buscarProdutosCatalogoSync() throws Exception {
        HttpURLConnection conn = null;
        try {
            String urlStr = getBaseUrl() + "/func_GetProdutos/T/COD_TIPO_PROD%20in%20(0,4)";
            conn = abrirConexao(urlStr, "GET", true);
            conn.setReadTimeout(12000); // Catálogo pode ter ~3MB
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

    /**
     * [HAR Req 12]
     * POST /datasnap/rest/tpreatend/func_LoginFunc/T
     * Payload: {"cod_func":"...","senha":"..."}
     */
    public JSONObject loginFuncionarioSync(String codFunc, String senha) throws Exception {
        HttpURLConnection conn = null;
        try {
            String urlStr = getBaseUrl() + "/func_LoginFunc/T";
            conn = abrirConexao(urlStr, "POST", true);
            conn.setDoOutput(true);

            JSONObject creds = new JSONObject();
            creds.put("cod_func", codFunc.trim());
            creds.put("senha", senha.trim());
            byte[] body = creds.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            String respStr = lerRespostaHttp(conn, true);
            if (respStr.startsWith("[")) {
                JSONArray arr = new JSONArray(respStr);
                if (arr.length() > 0) {
                    JSONObject user = arr.getJSONObject(0);
                    // Atualiza configurações persistidas com código de loja e vendedor retornados
                    String codLoja = user.optString("COD_LOJA", "1");
                    salvarCredenciaisAtendimento(codLoja, codFunc);
                    return user;
                }
            } else if (respStr.startsWith("{")) {
                return new JSONObject(respStr);
            }
            throw new Exception("Credenciais inválidas");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // =========================================================================
    // 2. REQUISIÇÕES EM NUVEM OMMINI (AUTODESCOBERTA E LICENÇA)
    // =========================================================================

    /**
     * [HAR Req 16]
     * POST http://serverrest.ommini.com.br/getdadosjson/serverconfig
     * Payload: {"par1":"S05","par2":"{CNPJ}"}
     * Descobre o IP e porta locais do DataSnap automaticamente via nuvem Ommini
     */
    public JSONObject descobrirServidorPorCnpjSync(String cnpj) throws Exception {
        HttpURLConnection conn = null;
        try {
            String urlStr = CLOUD_URL_BASE + "/serverconfig";
            conn = abrirConexao(urlStr, "POST", false);
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

    /**
     * [HAR Req 19]
     * POST http://serverrest.ommini.com.br/getdadosjson/getauth
     * Payload: {"par1":"12"}
     */
    public JSONObject obterAuthCloudSync() throws Exception {
        HttpURLConnection conn = null;
        try {
            String urlStr = CLOUD_URL_BASE + "/getauth";
            conn = abrirConexao(urlStr, "POST", false);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("par1", "12");

            byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(b);
                os.flush();
            }

            String respStr = lerRespostaHttp(conn, false);
            if (respStr.startsWith("[")) {
                JSONArray arr = new JSONArray(respStr);
                if (arr.length() > 0) return arr.getJSONObject(0);
            }
            return new JSONObject();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // =========================================================================
    // 3. MÉTODOS ASSÍNCRONOS E VARREDURA INTELIGENTE DE COMANDAS
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

            // 1. Testa mesa de número idêntico à comanda
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
