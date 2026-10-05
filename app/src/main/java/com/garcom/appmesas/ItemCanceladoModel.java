package com.garcom.appmesas;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ItemCanceladoModel {
    private String id; // Chave única (autonum ou chave do item)
    private String autonum;
    private String documento;
    private String numComanda;
    private int mesa;
    private String descricao;
    private double valorTotal;
    private int qtde;
    private String codVend;
    private long timestampDetectado;
    private String horaFormatada;
    private String dataStr;
    private boolean confirmadoCancelado;

    public ItemCanceladoModel(ItemComandaModel item, String comandaId) {
        this.autonum = item.getAutonum() != null ? item.getAutonum().trim() : "";
        this.documento = item.getDocumento() != null ? item.getDocumento().trim() : "";
        this.numComanda = item.getNumComanda() != null ? item.getNumComanda().trim() : "";
        this.mesa = item.getMesa();
        this.descricao = item.getDescricao() != null ? item.getDescricao().trim() : "Produto";
        this.valorTotal = item.getValorTotalNumerico();
        this.qtde = Math.max(1, item.getVlrQtde());
        this.codVend = item.getCodVend() != null ? item.getCodVend().trim() : "";

        if (!this.autonum.isEmpty()) {
            this.id = "AUT_" + this.autonum;
        } else {
            this.id = item.getItemKey(comandaId);
        }

        this.timestampDetectado = System.currentTimeMillis();
        this.horaFormatada = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(this.timestampDetectado));
        this.dataStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(this.timestampDetectado));
        this.confirmadoCancelado = false;
    }

    public ItemCanceladoModel(JSONObject json) {
        this.id = json.optString("id", "");
        this.autonum = json.optString("autonum", "");
        this.documento = json.optString("documento", "");
        this.numComanda = json.optString("numComanda", "");
        this.mesa = json.optInt("mesa", 0);
        this.descricao = json.optString("descricao", "Produto");
        this.valorTotal = json.optDouble("valorTotal", 0.0);
        this.qtde = json.optInt("qtde", 1);
        this.codVend = json.optString("codVend", "");
        this.timestampDetectado = json.optLong("timestampDetectado", System.currentTimeMillis());
        this.horaFormatada = json.optString("horaFormatada", "");
        this.dataStr = json.optString("dataStr", "");
        this.confirmadoCancelado = json.optBoolean("confirmadoCancelado", false);
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("autonum", autonum);
            obj.put("documento", documento);
            obj.put("numComanda", numComanda);
            obj.put("mesa", mesa);
            obj.put("descricao", descricao);
            obj.put("valorTotal", valorTotal);
            obj.put("qtde", qtde);
            obj.put("codVend", codVend);
            obj.put("timestampDetectado", timestampDetectado);
            obj.put("horaFormatada", horaFormatada);
            obj.put("dataStr", dataStr);
            obj.put("confirmadoCancelado", confirmadoCancelado);
        } catch (Exception ignored) {}
        return obj;
    }

    public String getId() { return id; }
    public String getAutonum() { return autonum; }
    public String getDocumento() { return documento; }
    public String getNumComanda() { return numComanda; }
    public int getMesa() { return mesa; }
    public String getDescricao() { return descricao; }
    public double getValorTotal() { return valorTotal; }
    public int getQtde() { return qtde; }
    public String getCodVend() { return codVend; }
    public long getTimestampDetectado() { return timestampDetectado; }
    public String getHoraFormatada() { return horaFormatada; }
    public String getDataStr() { return dataStr; }
    public boolean isConfirmadoCancelado() { return confirmadoCancelado; }
    public void setConfirmadoCancelado(boolean confirmadoCancelado) { this.confirmadoCancelado = confirmadoCancelado; }

    public String getValorFormatado() {
        return String.format(Locale.GERMANY, "R$ %.2f", valorTotal);
    }

    public String getIdentificadorComanda() {
        StringBuilder sb = new StringBuilder();
        if (mesa > 0) sb.append("Mesa ").append(mesa);
        if (!numComanda.isEmpty()) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append("CMD #").append(numComanda);
        }
        return sb.length() > 0 ? sb.toString() : "Comanda";
    }

    public String getLinhaResumo() {
        return qtde + "x " + descricao + " — " + getValorFormatado();
    }
}
