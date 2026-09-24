package com.garcom.appmesas;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ComandaCardModel {
    private final String id; // Chave única estável: DOCUMENTO + "_" + NUM_COMANDA
    private int mesa;
    private String numComanda;
    private String documento;
    private String dataStr;
    private String horaStr;
    private long dataTimestamp;
    private long detectedAt; // Horário em que entrou no app para o contador individual (00:00:00)
    private int qtdeItens;
    private double totalValor;
    private String contato;
    private String cnpjCpf;
    private boolean entregue = false;
    private final List<ItemComandaModel> itens = new ArrayList<>();

    public ComandaCardModel(JSONObject objAbertas) {
        this.documento = objAbertas.optString("DOCUMENTO", "").trim();
        this.numComanda = objAbertas.optString("NUM_COMANDA", "").trim();

        int m = objAbertas.optInt("MESA", 0);
        if (m == 0) {
            try {
                m = Integer.parseInt(objAbertas.optString("MESA", "0").trim());
            } catch (Exception ignored) {}
        }
        this.mesa = m;

        // Chave única por número da comanda para evitar que se repitam
        if (!numComanda.isEmpty()) {
            this.id = normalizarIdComanda(numComanda);
        } else if (!documento.isEmpty()) {
            this.id = "DOC_" + documento;
        } else {
            this.id = "MESA_" + mesa;
        }

        this.dataStr = objAbertas.optString("DATA", "").trim();
        this.horaStr = objAbertas.optString("HORA", "").trim();
        this.dataTimestamp = normalizarDataHora(this.dataStr, this.horaStr);

        this.detectedAt = System.currentTimeMillis();

        this.contato = objAbertas.optString("CONTATO", "").trim();
        this.cnpjCpf = objAbertas.optString("CNPJ_CPF", "").trim();

        String qtdStr = objAbertas.optString("QtdeItens", "0").trim().replace(",", ".");
        try {
            this.qtdeItens = (int) Double.parseDouble(qtdStr);
        } catch (Exception e) {
            this.qtdeItens = 0;
        }

        String totalStr = objAbertas.optString("Total", "0.00").trim().replace(",", ".");
        try {
            this.totalValor = Double.parseDouble(totalStr);
        } catch (Exception e) {
            this.totalValor = 0.0;
        }
    }

    public static String normalizarIdComanda(String numComanda) {
        if (numComanda == null) return "";
        String limpo = numComanda.trim();
        try {
            return "CMD_" + Integer.parseInt(limpo);
        } catch (Exception e) {
            return "CMD_" + limpo;
        }
    }

    public String getId() { return id; }
    public int getMesa() { return mesa; }
    public void setMesa(int mesa) { this.mesa = mesa; }
    public String getNumComanda() { return numComanda; }
    public String getDocumento() { return documento; }
    public long getDetectedAt() { return detectedAt; }
    public void setDetectedAt(long detectedAt) { this.detectedAt = detectedAt; }
    public int getQtdeItens() { return qtdeItens; }
    public double getTotalValor() { return totalValor; }
    public List<ItemComandaModel> getItens() { return itens; }
    public boolean isEntregue() { return entregue; }
    public void setEntregue(boolean entregue) { this.entregue = entregue; }

    public void atualizarDados(JSONObject objAbertas) {
        String totalStr = objAbertas.optString("Total", "0.00").trim().replace(",", ".");
        try {
            this.totalValor = Double.parseDouble(totalStr);
        } catch (Exception ignored) {}

        String qtdStr = objAbertas.optString("QtdeItens", "0").trim().replace(",", ".");
        try {
            this.qtdeItens = (int) Double.parseDouble(qtdStr);
        } catch (Exception ignored) {}

        int m = objAbertas.optInt("MESA", 0);
        if (m == 0) {
            try {
                m = Integer.parseInt(objAbertas.optString("MESA", "0").trim());
            } catch (Exception ignored) {}
        }
        if (m > 0) this.mesa = m;
    }

    public void setItens(List<ItemComandaModel> novosItens) {
        itens.clear();
        if (novosItens != null) {
            itens.addAll(novosItens);
        }
        if (!itens.isEmpty()) {
            int somaQtd = 0;
            double somaTotal = 0.0;
            for (ItemComandaModel it : itens) {
                somaQtd += it.getVlrQtde();
                try {
                    somaTotal += Double.parseDouble(it.getVlrTotal().replace(",", "."));
                } catch (Exception ignored) {}
            }
            if (somaQtd > 0) this.qtdeItens = somaQtd;
            if (somaTotal > 0) this.totalValor = somaTotal;
        }
    }

    /**
     * Contador individual formatado em HH:mm:ss
     * Começa em 00:00:00 e continua subindo a cada segundo
     */
    public String getTempoFormatado() {
        long decorridoMs = System.currentTimeMillis() - detectedAt;
        if (decorridoMs < 0) decorridoMs = 0;
        long totalSegundos = decorridoMs / 1000;
        long horas = totalSegundos / 3600;
        long minutos = (totalSegundos % 3600) / 60;
        long segundos = totalSegundos % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", horas, minutos, segundos);
    }

    public long getMinutosDecorridos() {
        long decorridoMs = System.currentTimeMillis() - detectedAt;
        return decorridoMs > 0 ? (decorridoMs / 60000) : 0;
    }

    public String getTotalFormatado() {
        return String.format(Locale.GERMANY, "R$ %.2f", totalValor);
    }

    public String getItensCountFormatado() {
        return qtdeItens + (qtdeItens == 1 ? " item" : " itens");
    }

    /**
     * Regra 8: Normalização de Data/Hora do Delphi DataSnap
     * Ignora a data-base 30/12/1899 quando presente em HORA e combina com DATA
     */
    public static long normalizarDataHora(String dataStr, String horaStr) {
        if (dataStr == null) dataStr = "";
        if (horaStr == null) horaStr = "";
        dataStr = dataStr.trim();
        horaStr = horaStr.trim();

        // 1. DATA já tem data e hora completas (ex: "28/08/2026 18:58:51" ou "10-09-2026 09:55:18")
        if (dataStr.contains(" ") && dataStr.length() >= 16) {
            long ts = tentarParseDataHora(dataStr);
            if (ts > 0) return ts;
        }

        // 2. Extrai a hora limpa ignorando a data base do Delphi (30/12/1899)
        String apenasHora = horaStr;
        if (horaStr.contains(" ")) {
            apenasHora = horaStr.substring(horaStr.lastIndexOf(" ") + 1).trim();
        }

        // 3. Extrai apenas a data de DATA
        String apenasData = dataStr;
        if (dataStr.contains(" ")) {
            apenasData = dataStr.substring(0, dataStr.indexOf(" ")).trim();
        }

        if (!apenasData.isEmpty() && !apenasHora.isEmpty()) {
            long ts = tentarParseDataHora(apenasData + " " + apenasHora);
            if (ts > 0) return ts;
        }

        if (!apenasData.isEmpty()) {
            long tsData = tentarParseApenasData(apenasData);
            if (tsData > 0) return tsData;
        }

        return System.currentTimeMillis();
    }

    private static long tentarParseDataHora(String str) {
        String[] formatos = {
                "dd/MM/yyyy HH:mm:ss", "dd-MM-yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm", "dd-MM-yyyy HH:mm",
                "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss"
        };
        for (String f : formatos) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(f, Locale.getDefault());
                sdf.setLenient(false);
                Date d = sdf.parse(str);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private static long tentarParseApenasData(String str) {
        String[] formatos = { "dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd" };
        for (String f : formatos) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(f, Locale.getDefault());
                sdf.setLenient(false);
                Date d = sdf.parse(str);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return -1;
    }

    /**
     * Regra 8: Filtro das 10 horas
     * Retorna false se tiver mais de 10 horas
     */
    public static boolean isValidaMenosDe10Horas(String dataStr, String horaStr) {
        long ts = normalizarDataHora(dataStr, horaStr);
        long agora = System.currentTimeMillis();
        long diffMs = agora - ts;
        long dezHorasMs = 10L * 60L * 60L * 1000L; // 36.000.000 ms
        if (diffMs > dezHorasMs) {
            return false; // Mais velha que 10 horas -> ignorar!
        }
        return true;
    }
}
