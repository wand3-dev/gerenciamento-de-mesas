package com.garcom.appmesas;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

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
    private String codProd;
    private long timestampDetectado;
    private String horaFormatada;
    private String dataStr;
    private boolean confirmadoCancelado;
    private String motivoAlerta; // "REMOVIDO_COMANDA_ABERTA", "REDUCAO_QUANTIDADE"
    private int qtdeAnterior;
    private int qtdeNova;

    public ItemCanceladoModel(ItemComandaModel item, String comandaId) {
        this.codProd = item.getCodProd() != null ? item.getCodProd().trim() : "";
        this.autonum = item.getAutonum() != null ? item.getAutonum().trim() : "";
        this.documento = item.getDocumento() != null ? item.getDocumento().trim() : "";
        this.numComanda = item.getNumComanda() != null ? item.getNumComanda().trim() : "";
        this.mesa = item.getMesa();
        this.descricao = item.getDescricao() != null ? item.getDescricao().trim() : "Produto";
        this.valorTotal = item.getValorTotalNumerico();
        this.qtde = Math.max(1, item.getVlrQtde());
        this.qtdeAnterior = this.qtde;
        this.qtdeNova = 0;
        this.motivoAlerta = "REMOVIDO_COMANDA_ABERTA";
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

    public ItemCanceladoModel(ItemCanceladoModel itemOriginal, int qtdReduzida, double valorReduzido, String novaChave, int qtdAnterior, int qtdNova) {
        this.id = novaChave;
        this.autonum = itemOriginal.getAutonum();
        this.documento = itemOriginal.getDocumento();
        this.numComanda = itemOriginal.getNumComanda();
        this.mesa = itemOriginal.getMesa();
        this.descricao = itemOriginal.getDescricao();
        this.valorTotal = valorReduzido;
        this.qtde = qtdReduzida;
        this.qtdeAnterior = qtdAnterior;
        this.qtdeNova = qtdNova;
        this.motivoAlerta = "REDUCAO_QUANTIDADE";
        this.codVend = itemOriginal.getCodVend();
        this.codProd = itemOriginal.getCodProd();
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
        this.codProd = json.optString("codProd", "");
        this.timestampDetectado = json.optLong("timestampDetectado", System.currentTimeMillis());
        this.horaFormatada = json.optString("horaFormatada", "");
        this.dataStr = json.optString("dataStr", "");
        this.confirmadoCancelado = json.optBoolean("confirmadoCancelado", false);
        this.motivoAlerta = json.optString("motivoAlerta", "REMOVIDO_COMANDA_ABERTA");
        this.qtdeAnterior = json.optInt("qtdeAnterior", this.qtde);
        this.qtdeNova = json.optInt("qtdeNova", 0);
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
            obj.put("codProd", codProd);
            obj.put("timestampDetectado", timestampDetectado);
            obj.put("horaFormatada", horaFormatada);
            obj.put("dataStr", dataStr);
            obj.put("confirmadoCancelado", confirmadoCancelado);
            obj.put("motivoAlerta", motivoAlerta);
            obj.put("qtdeAnterior", qtdeAnterior);
            obj.put("qtdeNova", qtdeNova);
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
    public String getCodProd() { return codProd != null ? codProd : ""; }
    public long getTimestampDetectado() { return timestampDetectado; }
    public String getHoraFormatada() { return horaFormatada; }
    public String getDataStr() { return dataStr; }
    public boolean isConfirmadoCancelado() { return confirmadoCancelado; }
    public void setConfirmadoCancelado(boolean confirmadoCancelado) { this.confirmadoCancelado = confirmadoCancelado; }
    public String getMotivoAlerta() { return motivoAlerta; }
    public void setMotivoAlerta(String motivoAlerta) { this.motivoAlerta = motivoAlerta; }
    public int getQtdeAnterior() { return qtdeAnterior; }
    public int getQtdeNova() { return qtdeNova; }

    public boolean isReducaoQuantidade() {
        return "REDUCAO_QUANTIDADE".equals(motivoAlerta) || (qtdeAnterior > qtdeNova && qtdeNova > 0);
    }

    public boolean pertenceAComandaAberta(Set<String> comandaIdsAbertasSet) {
        if (comandaIdsAbertasSet == null || comandaIdsAbertasSet.isEmpty()) return false;
        if (numComanda != null && !numComanda.trim().isEmpty()) {
            String norm = ComandaCardModel.normalizarIdComanda(numComanda);
            if (comandaIdsAbertasSet.contains(norm) || comandaIdsAbertasSet.contains(numComanda.trim())) {
                return true;
            }
        }
        if (documento != null && !documento.trim().isEmpty()) {
            if (comandaIdsAbertasSet.contains("DOC_" + documento.trim()) || comandaIdsAbertasSet.contains(documento.trim())) {
                return true;
            }
        }
        if (mesa > 0 && comandaIdsAbertasSet.contains("MESA_" + mesa)) {
            return true;
        }
        return false;
    }

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
        if (isReducaoQuantidade()) {
            return qtde + "x " + descricao + " (Redução de " + qtdeAnterior + "x para " + qtdeNova + "x) — " + getValorFormatado();
        }
        return qtde + "x " + descricao + " — " + getValorFormatado();
    }
}
