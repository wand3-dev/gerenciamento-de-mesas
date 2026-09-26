package com.garcom.appmesas;

import org.json.JSONObject;

public class ItemComandaModel {
    private String autonum;
    private String documento;
    private String numComanda;
    private String numItem;
    private String codProd;
    private String descricao;
    private String vlrPreco;
    private int vlrQtde;
    private String vlrTotal;
    private String obs;
    private int mesa;
    private String codVend;

    public ItemComandaModel(JSONObject obj) {
        this.autonum = obj.optString("AUTONUM", "").trim();
        this.documento = obj.optString("DOCUMENTO", "").trim();
        this.numComanda = obj.optString("NUM_COMANDA", "").trim();
        this.numItem = obj.optString("NUM_ITEM", "").trim();
        this.codProd = obj.optString("COD_PROD", "").trim();
        this.descricao = obj.optString("DESCRICAO", "").trim();
        this.vlrPreco = obj.optString("VLR_PRECO", "0.00").trim();

        String qtdeStr = obj.optString("VLR_QTDE", "1").trim().replace(",", ".");
        int q = 1;
        try {
            q = (int) Double.parseDouble(qtdeStr);
            if (q <= 0) q = 1;
        } catch (Exception ignored) {}
        this.vlrQtde = q;

        this.vlrTotal = obj.optString("VLR_TOTAL", "0.00").trim();
        String observacao = obj.optString("OBS", "").trim();
        if ("null".equalsIgnoreCase(observacao)) observacao = "";
        this.obs = observacao;

        this.mesa = obj.optInt("MESA", 0);
        if (this.mesa == 0) {
            try {
                this.mesa = Integer.parseInt(obj.optString("MESA", "0").trim());
            } catch (Exception ignored) {}
        }

        this.codVend = obj.optString("COD_VEND", "").trim();
        if (this.codVend.isEmpty()) {
            this.codVend = obj.optString("cod_vend", "").trim();
        }
    }

    public String getAutonum() { return autonum; }
    public String getDocumento() { return documento; }
    public String getNumComanda() { return numComanda; }
    public String getNumItem() { return numItem; }
    public String getCodProd() { return codProd; }
    public String getDescricao() { return descricao; }
    public String getVlrPreco() { return vlrPreco; }
    public int getVlrQtde() { return vlrQtde; }
    public String getVlrTotal() { return vlrTotal; }
    public String getObs() { return obs; }
    public int getMesa() { return mesa; }
    public String getCodVend() { return codVend; }

    public boolean hasGarcom() {
        return codVend != null && !codVend.isEmpty();
    }

    public boolean hasObs() {
        return obs != null && !obs.isEmpty();
    }

    /**
     * Formatação do item requisitada:
     * 1x PF FRANGO GRELHADO UND — R$ 22,99
     * Se VLR_QTDE for maior que 1:
     * 3x PRODUTO — R$ XX,XX
     */
    public String getTextoLinha() {
        String totalFormatado = (vlrTotal != null && !vlrTotal.isEmpty()) ? vlrTotal : "0,00";
        try {
            if (vlrTotal != null && !vlrTotal.trim().isEmpty()) {
                double totalNum = Double.parseDouble(vlrTotal.trim().replace(",", "."));
                totalFormatado = String.format(java.util.Locale.GERMANY, "%.2f", totalNum);
            }
        } catch (Exception ignored) {}
        String desc = (descricao != null && !descricao.isEmpty()) ? descricao : "Item";
        return vlrQtde + "x " + desc + " — R$ " + totalFormatado;
    }

    private boolean checado = false;
    public boolean isChecado() { return checado; }
    public void setChecado(boolean checado) { this.checado = checado; }

    public String getItemKey(String comandaId) {
        if (autonum != null && !autonum.isEmpty()) {
            return comandaId + "_AUT_" + autonum;
        }
        if (numItem != null && !numItem.isEmpty()) {
            return comandaId + "_ITEM_" + numItem + "_" + codProd;
        }
        return comandaId + "_PROD_" + codProd + "_" + descricao;
    }
}
