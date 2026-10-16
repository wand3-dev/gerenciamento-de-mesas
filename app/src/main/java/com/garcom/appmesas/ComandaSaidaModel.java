package com.garcom.appmesas;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ComandaSaidaModel {
    private String comandaId;
    private String numComanda;
    private int mesa;
    private long timestampEntrada;
    private long timestampSaida;
    private String horaSaida;
    private String dataSaida;
    private long duracaoMinutos;
    private boolean ehSaidaSuspeita;
    private String statusConferencia; // PENDENTE, PAGA_INTEGRAL, CANCELAMENTO_PARCIAL, CANCELAMENTO_TOTAL
    private final List<ItemCanceladoModel> itens = new ArrayList<>();

    public ComandaSaidaModel(ComandaCardModel card, String codGarcomAlvo) {
        this.comandaId = card.getId();
        this.numComanda = card.getNumComanda() != null ? card.getNumComanda().trim() : "";
        this.mesa = card.getMesa();
        this.timestampEntrada = card.getDetectedAt();
        this.timestampSaida = System.currentTimeMillis();
        this.horaSaida = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestampSaida));
        this.dataSaida = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestampSaida));

        long decorrido = timestampSaida - timestampEntrada;
        this.duracaoMinutos = decorrido > 0 ? (decorrido / 60000) : 0;

        // Adiciona apenas os itens pertencentes a este garçom
        String codAlvo = GarcomManager.normalizarCodigo(codGarcomAlvo);
        if (card.getItens() != null) {
            for (ItemComandaModel it : card.getItens()) {
                if (it == null) continue;
                String codVend = GarcomManager.normalizarCodigo(it.getCodVend());
                if (codAlvo.equals(codVend)) {
                    this.itens.add(new ItemCanceladoModel(it, card.getId()));
                }
            }
        }

        // Critério de Saída Suspeita: comanda com itens do garçom que sumiu em menos de 15 minutos!
        this.ehSaidaSuspeita = (duracaoMinutos <= 15) && !itens.isEmpty();
        this.statusConferencia = "PENDENTE";
    }

    public ComandaSaidaModel(JSONObject json) {
        this.comandaId = json.optString("comandaId", "");
        this.numComanda = json.optString("numComanda", "");
        this.mesa = json.optInt("mesa", 0);
        this.timestampEntrada = json.optLong("timestampEntrada", 0L);
        this.timestampSaida = json.optLong("timestampSaida", System.currentTimeMillis());
        this.horaSaida = json.optString("horaSaida", "");
        this.dataSaida = json.optString("dataSaida", "");
        this.duracaoMinutos = json.optLong("duracaoMinutos", 0L);
        this.ehSaidaSuspeita = json.optBoolean("ehSaidaSuspeita", false);
        this.statusConferencia = json.optString("statusConferencia", "PENDENTE");

        JSONArray arr = json.optJSONArray("itens");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                try {
                    itens.add(new ItemCanceladoModel(arr.getJSONObject(i)));
                } catch (Exception ignored) {}
            }
        }
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("comandaId", comandaId);
            obj.put("numComanda", numComanda);
            obj.put("mesa", mesa);
            obj.put("timestampEntrada", timestampEntrada);
            obj.put("timestampSaida", timestampSaida);
            obj.put("horaSaida", horaSaida);
            obj.put("dataSaida", dataSaida);
            obj.put("duracaoMinutos", duracaoMinutos);
            obj.put("ehSaidaSuspeita", ehSaidaSuspeita);
            obj.put("statusConferencia", statusConferencia);

            JSONArray arr = new JSONArray();
            for (ItemCanceladoModel it : itens) {
                arr.put(it.toJson());
            }
            obj.put("itens", arr);
        } catch (Exception ignored) {}
        return obj;
    }

    public String getComandaId() { return comandaId; }
    public String getNumComanda() { return numComanda; }
    public int getMesa() { return mesa; }
    public long getTimestampSaida() { return timestampSaida; }
    public String getHoraSaida() { return horaSaida; }
    public String getDataSaida() { return dataSaida; }
    public long getDuracaoMinutos() { return duracaoMinutos; }
    public boolean isEhSaidaSuspeita() { return ehSaidaSuspeita; }
    public String getStatusConferencia() { return statusConferencia; }
    public void setStatusConferencia(String statusConferencia) { this.statusConferencia = statusConferencia; }
    public List<ItemCanceladoModel> getItens() { return itens; }

    public double getTotalValor() {
        double total = 0.0;
        for (ItemCanceladoModel it : itens) {
            total += it.getValorTotal();
        }
        return total;
    }

    public String getIdentificadorComanda() {
        StringBuilder sb = new StringBuilder();
        if (mesa > 0) sb.append("Mesa ").append(mesa);
        if (!numComanda.isEmpty()) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append("CMD #").append(numComanda);
        }
        return sb.length() > 0 ? sb.toString() : "Comanda #" + comandaId;
    }

    public String getTotalValorFormatado() {
        return String.format(Locale.GERMANY, "R$ %.2f", getTotalValor());
    }

    public boolean isPendente() {
        return "PENDENTE".equals(statusConferencia);
    }
}
