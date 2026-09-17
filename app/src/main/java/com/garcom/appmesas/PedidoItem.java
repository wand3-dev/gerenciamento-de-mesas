package com.garcom.appmesas;

import org.json.JSONException;
import org.json.JSONObject;

public class PedidoItem {
    private String id;
    private String codProd;
    private String descricao;
    private int quantidade;
    private String adicional;
    private String comanda;
    private long timestamp;
    private boolean entregue;

    // Campos do Servidor
    private String autonum;
    private String numItem;
    private String vlrPreco;
    private String documento;
    private String valorTotal;
    private String horaRegistro;
    private long tempoExtraAdiadoMs;
    private boolean alertadoAtraso;

    public PedidoItem(String codProd, String descricao, int quantidade, String adicional) {
        this(codProd, descricao, quantidade, adicional, "");
    }

    public PedidoItem(String codProd, String descricao, int quantidade, String adicional, String comanda) {
        this(codProd, descricao, quantidade, adicional, comanda, "", "0.00", "");
    }

    public PedidoItem(String codProd, String descricao, int quantidade, String adicional, String comanda,
                      String documento, String valorTotal, String horaRegistro) {
        this.id = String.valueOf(System.currentTimeMillis()) + "_" + codProd;
        this.autonum = "";
        this.numItem = "";
        this.vlrPreco = "0.00";
        this.codProd = codProd;
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.adicional = (adicional == null) ? "" : adicional.trim();
        this.comanda = (comanda == null) ? "" : comanda.trim();
        this.timestamp = System.currentTimeMillis();
        this.entregue = false;
        this.documento = (documento == null) ? "" : documento.trim();
        this.valorTotal = (valorTotal == null || valorTotal.isEmpty()) ? "0.00" : valorTotal.trim();
        this.horaRegistro = (horaRegistro == null) ? "" : horaRegistro.trim();
        this.tempoExtraAdiadoMs = 0;
        this.alertadoAtraso = false;
    }

    public PedidoItem(JSONObject obj) throws JSONException {
        this.id = obj.optString("id", String.valueOf(System.currentTimeMillis()));
        this.autonum = obj.optString("autonum", "");
        this.numItem = obj.optString("numItem", "");
        this.vlrPreco = obj.optString("vlrPreco", "0.00");
        this.codProd = obj.optString("codProd", "SERV");
        this.descricao = obj.optString("descricao", "");
        this.quantidade = obj.optInt("quantidade", 1);
        this.adicional = obj.optString("adicional", "");
        this.comanda = obj.optString("comanda", "");
        this.timestamp = obj.optLong("timestamp", System.currentTimeMillis());
        this.entregue = obj.optBoolean("entregue", false);
        this.documento = obj.optString("documento", "");
        this.valorTotal = obj.optString("valorTotal", "0.00");
        this.horaRegistro = obj.optString("horaRegistro", "");
        this.tempoExtraAdiadoMs = obj.optLong("tempoExtraAdiadoMs", 0);
        this.alertadoAtraso = obj.optBoolean("alertadoAtraso", false);
    }

    public static PedidoItem fromServerJson(JSONObject obj) {
        String autonum = obj.optString("AUTONUM", "").trim();
        String codProd = obj.optString("COD_PROD", "").trim();
        String descricao = obj.optString("DESCRICAO", "").trim();
        String numComanda = obj.optString("NUM_COMANDA", "").trim();
        String documento = obj.optString("DOCUMENTO", "").trim();
        String vlrQtdeStr = obj.optString("VLR_QTDE", "1").trim();
        String vlrTotalStr = obj.optString("VLR_TOTAL", "0.00").trim();
        String vlrPrecoStr = obj.optString("VLR_PRECO", "0.00").trim();
        String numItem = obj.optString("NUM_ITEM", "").trim();
        String dataStr = obj.optString("DATA", "").trim();
        String horaStr = obj.optString("HORA", "").trim();
        String obs = obj.optString("OBS", "").trim();
        if ("null".equalsIgnoreCase(obs)) obs = "";

        int qtd = 1;
        try {
            qtd = (int) Double.parseDouble(vlrQtdeStr.replace(",", "."));
            if (qtd <= 0) qtd = 1;
        } catch (Exception ignored) {}

        String horaApenas = horaStr;
        if (horaStr.contains(" ")) {
            horaApenas = horaStr.substring(horaStr.lastIndexOf(" ") + 1).trim();
        }

        long timestampCalculado = System.currentTimeMillis();
        try {
            String dataHoraStr = dataStr.trim() + " " + horaApenas;
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date d = sdf.parse(dataHoraStr);
            if (d != null) {
                timestampCalculado = d.getTime();
            }
        } catch (Exception ignored) {}

        PedidoItem item = new PedidoItem(codProd, descricao, qtd, obs, numComanda, documento, vlrTotalStr, horaApenas);
        if (!autonum.isEmpty()) {
            item.setId(autonum);
            item.setAutonum(autonum);
        }
        item.setNumItem(numItem);
        item.setVlrPreco(vlrPrecoStr);
        item.setTimestamp(timestampCalculado);
        return item;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("autonum", autonum);
        obj.put("numItem", numItem);
        obj.put("vlrPreco", vlrPreco);
        obj.put("codProd", codProd);
        obj.put("descricao", descricao);
        obj.put("quantidade", quantidade);
        obj.put("adicional", adicional);
        obj.put("comanda", comanda);
        obj.put("timestamp", timestamp);
        obj.put("entregue", entregue);
        obj.put("documento", documento);
        obj.put("valorTotal", valorTotal);
        obj.put("horaRegistro", horaRegistro);
        obj.put("tempoExtraAdiadoMs", tempoExtraAdiadoMs);
        obj.put("alertadoAtraso", alertadoAtraso);
        return obj;
    }

    public long getMinutosEfetivosEsperando() {
        long diffMs = (System.currentTimeMillis() - timestamp) - tempoExtraAdiadoMs;
        if (diffMs < 0) diffMs = 0;
        return (diffMs / 1000) / 60;
    }

    public boolean isAtrasado() {
        if (entregue) return false;
        long diffMs = (System.currentTimeMillis() - timestamp) - tempoExtraAdiadoMs;
        return diffMs >= 15 * 60 * 1000;
    }

    public void adiarMaisDoisMinutos() {
        long decorrido = System.currentTimeMillis() - timestamp - tempoExtraAdiadoMs;
        if (decorrido < 15 * 60 * 1000) {
            this.tempoExtraAdiadoMs += (2 * 60 * 1000);
        } else {
            long excesso = decorrido - (15 * 60 * 1000);
            this.tempoExtraAdiadoMs += excesso + (2 * 60 * 1000);
        }
        this.alertadoAtraso = false;
    }

    public long getTempoExtraAdiadoMs() { return tempoExtraAdiadoMs; }
    public void setTempoExtraAdiadoMs(long ms) { this.tempoExtraAdiadoMs = ms; }
    public boolean isAlertadoAtraso() { return alertadoAtraso; }
    public void setAlertadoAtraso(boolean alertado) { this.alertadoAtraso = alertado; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAutonum() { return autonum; }
    public void setAutonum(String autonum) { this.autonum = autonum; }
    public String getNumItem() { return numItem; }
    public void setNumItem(String numItem) { this.numItem = numItem; }
    public String getVlrPreco() { return vlrPreco; }
    public void setVlrPreco(String vlrPreco) { this.vlrPreco = vlrPreco; }

    public String getCodProd() { return codProd; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
    public String getAdicional() { return adicional; }
    public void setAdicional(String adicional) { this.adicional = adicional; }
    public String getComanda() { return comanda; }
    public void setComanda(String comanda) { this.comanda = comanda == null ? "" : comanda.trim(); }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public boolean isEntregue() { return entregue; }
    public void setEntregue(boolean entregue) { this.entregue = entregue; }

    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public String getValorTotal() { return valorTotal; }
    public void setValorTotal(String valorTotal) { this.valorTotal = valorTotal; }
    public String getHoraRegistro() { return horaRegistro; }
    public void setHoraRegistro(String horaRegistro) { this.horaRegistro = horaRegistro; }

    public double getValorTotalNumerico() {
        try {
            return Double.parseDouble(valorTotal.replace(",", "."));
        } catch (Exception e) {
            return 0.0;
        }
    }

    public String getTempoDecorridoFormatado() {
        long diffMs = System.currentTimeMillis() - timestamp;
        if (diffMs < 0) diffMs = 0;
        long diffSec = diffMs / 1000;
        long diffMin = diffSec / 60;
        long diffHoras = diffMin / 60;

        if (diffMin < 1) {
            return "agora mesmo";
        } else if (diffHoras < 1) {
            return "há " + diffMin + " min";
        } else {
            long minRest = diffMin % 60;
            return "há " + diffHoras + "h " + minRest + "m";
        }
    }

    public boolean isBebida() {
        if (descricao == null) return false;
        String desc = StringHelper.normalizar(descricao);

        String[] termosBebida = {
            "suco", "cafe", "cappuccino", "capuccino", "espresso", "expresso", "latte", "pingado",
            "leite", "achocolatado", "toddy", "nescau", "chocolatto", "chocolate quente",
            "refrigerante", "coca", "pepsi", "guarana", "fanta", "sprite", "schweppes", "soda",
            "tonica", "h2o", "agua", "aquarius",
            "cerveja", "chopp", "chope", "heineken", "brahma", "skol", "budweiser", "stella", "corona", "amstel", "eisenbahn",
            "vinho", "espumante", "champagne", "prosecco",
            "cha", "matte", "mate",
            "shake", "milkshake", "vitamina",
            "energetico", "red bull", "monster",
            "gatorade", "powerade",
            "dose", "whisky", "whiskey", "vodka", "gin", "rum", "tequila", "licor", "cachaca", "pinga", "caipirinha", "caipiroska", "alua",
            "kombucha", "garrafa", "jarra", "lata", "long neck", "copo"
        };

        for (String termo : termosBebida) {
            if (desc.contains(termo)) {
                if ("agua".equals(termo) && (desc.contains("na boca") || desc.contains("palha"))) continue;
                if ("chocolate".equals(termo) && (desc.contains("bolo") || desc.contains("torta") || desc.contains("barra") || desc.contains("biscoito") || desc.contains("ovo"))) continue;
                if ("leite".equals(termo) && (desc.contains("doce de leite") || desc.contains("pao de leite") || desc.contains("pudim") || desc.contains("bolo") || desc.contains("fatia"))) continue;
                return true;
            }
        }
        return false;
    }

    public boolean isComida() {
        return !isBebida();
    }
}
