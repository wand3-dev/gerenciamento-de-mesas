package com.garcom.appmesas;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlertaHistorico {

    private String id;
    private String tipo; // NOVO_PEDIDO, TRANSFERENCIA, ATRASO, COMANDA_VINCULADA, MESA_LIBERADA
    private String titulo;
    private String mensagem;
    private long timestamp;
    private int numeroMesa;
    private String comanda;
    private boolean lido;

    public AlertaHistorico(String tipo, String titulo, String mensagem, int numeroMesa, String comanda) {
        this.id = System.currentTimeMillis() + "_" + ((int) (Math.random() * 1000));
        this.tipo = tipo != null ? tipo : "INFO";
        this.titulo = titulo != null ? titulo : "";
        this.mensagem = mensagem != null ? mensagem : "";
        this.timestamp = System.currentTimeMillis();
        this.numeroMesa = numeroMesa;
        this.comanda = comanda != null ? comanda : "";
        this.lido = false;
    }

    public AlertaHistorico(JSONObject json) {
        if (json != null) {
            this.id = json.optString("id", String.valueOf(System.currentTimeMillis()));
            this.tipo = json.optString("tipo", "INFO");
            this.titulo = json.optString("titulo", "");
            this.mensagem = json.optString("mensagem", "");
            this.timestamp = json.optLong("timestamp", System.currentTimeMillis());
            this.numeroMesa = json.optInt("numeroMesa", -1);
            this.comanda = json.optString("comanda", "");
            this.lido = json.optBoolean("lido", false);
        }
    }

    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("tipo", tipo);
            obj.put("titulo", titulo);
            obj.put("mensagem", mensagem);
            obj.put("timestamp", timestamp);
            obj.put("numeroMesa", numeroMesa);
            obj.put("comanda", comanda);
            obj.put("lido", lido);
        } catch (Exception ignored) {}
        return obj;
    }

    public String getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getNumeroMesa() {
        return numeroMesa;
    }

    public String getComanda() {
        return comanda;
    }

    public boolean isLido() {
        return lido;
    }

    public void setLido(boolean lido) {
        this.lido = lido;
    }

    public String getHoraFormatada() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return "";
        }
    }

    public String getIconeTexto() {
        if ("TRANSFERENCIA".equalsIgnoreCase(tipo)) return "🔄";
        if ("ATRASO".equalsIgnoreCase(tipo)) return "🚨";
        if ("COMANDA_VINCULADA".equalsIgnoreCase(tipo)) return "🥐";
        if ("MESA_LIBERADA".equalsIgnoreCase(tipo)) return "✓";
        return "🔔";
    }

    public String getCorTag() {
        if ("TRANSFERENCIA".equalsIgnoreCase(tipo)) return "#2563EB"; // Azul
        if ("ATRASO".equalsIgnoreCase(tipo)) return "#DC2626"; // Vermelho
        if ("COMANDA_VINCULADA".equalsIgnoreCase(tipo)) return "#D97706"; // Âmbar
        if ("MESA_LIBERADA".equalsIgnoreCase(tipo)) return "#059669"; // Verde
        return "#EA580C"; // Laranja
    }
}
