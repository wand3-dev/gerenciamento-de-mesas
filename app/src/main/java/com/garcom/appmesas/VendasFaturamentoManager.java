package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class VendasFaturamentoManager {
    private static final String PREF_VENDAS = "vendas_faturamento_prefs";
    private static final String KEY_PREFIX_FAT_DIA = "fat_dia_";
    private static final String KEY_PREFIX_QTD_DIA = "qtd_dia_";
    private static final String KEY_PREFIX_ITENS_DIA = "itens_dia_";
    private static final String KEY_PREFIX_CANC_DIA = "canc_dia_";

    public static class DiaFaturamento {
        public final String dataStr; // yyyy-MM-dd
        public final String labelExibicao; // Hoje, Ontem, 02/10
        public final double faturamento;
        public final int qtdItens;
        public final double cancelado;

        public DiaFaturamento(String dataStr, String labelExibicao, double faturamento, int qtdItens, double cancelado) {
            this.dataStr = dataStr;
            this.labelExibicao = labelExibicao;
            this.faturamento = faturamento;
            this.qtdItens = qtdItens;
            this.cancelado = cancelado;
        }

        public String getFaturamentoFormatado() {
            return formatarMoeda(faturamento);
        }

        public String getCanceladoFormatado() {
            return formatarMoeda(cancelado);
        }
    }

    private static VendasFaturamentoManager instance;
    private final Context context;

    // Valores em tempo real das mesas abertas no momento
    private double faturamentoAbertoAgora = 0.0;
    private int qtdItensAbertoAgora = 0;
    private int comandasAbertasAgora = 0;

    private VendasFaturamentoManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized VendasFaturamentoManager getInstance(Context context) {
        if (instance == null) {
            instance = new VendasFaturamentoManager(context);
        }
        return instance;
    }

    public synchronized void processarCicloPolling(String meuCodGarcom, List<ComandaCardModel> comandasAbertas) {
        if (meuCodGarcom == null || meuCodGarcom.trim().isEmpty()) {
            meuCodGarcom = "223";
        }
        final String codAlvo = GarcomManager.normalizarCodigo(meuCodGarcom);

        String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);

        String keyFatHoje = KEY_PREFIX_FAT_DIA + codAlvo + "_" + dataHoje;
        String keyQtdHoje = KEY_PREFIX_QTD_DIA + codAlvo + "_" + dataHoje;
        String keyItensHoje = KEY_PREFIX_ITENS_DIA + codAlvo + "_" + dataHoje;

        Set<String> itensHoje = new HashSet<>(sp.getStringSet(keyItensHoje, new HashSet<>()));
        double faturamentoHoje = Double.parseDouble(sp.getString(keyFatHoje, "0.0"));
        int qtdHoje = sp.getInt(keyQtdHoje, 0);

        double abertosValor = 0.0;
        int abertosQtd = 0;
        int abertasCmds = 0;
        boolean houveNovos = false;

        if (comandasAbertas != null) {
            for (ComandaCardModel cmd : comandasAbertas) {
                if (cmd == null || !cmd.hasItens()) continue;
                boolean temDesteGarcom = false;

                for (ItemComandaModel it : cmd.getItens()) {
                    if (it == null) continue;
                    String codVendIt = GarcomManager.normalizarCodigo(it.getCodVend());
                    if (!codAlvo.equals(codVendIt)) continue;

                    temDesteGarcom = true;
                    int q = Math.max(1, it.getVlrQtde());
                    double vlr = it.getValorTotalNumerico();

                    if (!cmd.isEntregue()) {
                        abertosValor += vlr;
                        abertosQtd += q;
                    }

                    // Chave única para registro permanente
                    String chaveItem;
                    if (it.getAutonum() != null && !it.getAutonum().isEmpty()) {
                        chaveItem = "AUT_" + it.getAutonum();
                    } else {
                        chaveItem = cmd.getId() + "_" + it.getItemKey(cmd.getId());
                    }

                    if (!itensHoje.contains(chaveItem)) {
                        itensHoje.add(chaveItem);
                        faturamentoHoje += vlr;
                        qtdHoje += q;
                        houveNovos = true;
                    }
                }

                if (temDesteGarcom && !cmd.isEntregue()) {
                    abertasCmds++;
                }
            }
        }

        // Garante que o total do dia nunca seja menor que o que está na mesa agora
        if (faturamentoHoje < abertosValor) faturamentoHoje = abertosValor;
        if (qtdHoje < abertosQtd) qtdHoje = abertosQtd;

        this.faturamentoAbertoAgora = abertosValor;
        this.qtdItensAbertoAgora = abertosQtd;
        this.comandasAbertasAgora = abertasCmds;

        if (houveNovos) {
            sp.edit()
                .putStringSet(keyItensHoje, itensHoje)
                .putString(keyFatHoje, String.valueOf(faturamentoHoje))
                .putInt(keyQtdHoje, qtdHoje)
                .apply();
        }
    }

    public synchronized void deduzirCancelamento(ItemCanceladoModel cancelado) {
        if (cancelado == null) return;
        String cod = cancelado.getCodVend();
        if (cod == null || cod.isEmpty()) cod = "223";
        cod = GarcomManager.normalizarCodigo(cod);

        String dataItem = cancelado.getDataStr();
        if (dataItem == null || dataItem.isEmpty()) {
            dataItem = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        }

        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        String keyFat = KEY_PREFIX_FAT_DIA + cod + "_" + dataItem;
        String keyQtd = KEY_PREFIX_QTD_DIA + cod + "_" + dataItem;
        String keyCanc = KEY_PREFIX_CANC_DIA + cod + "_" + dataItem;

        double faturamento = Double.parseDouble(sp.getString(keyFat, "0.0"));
        int qtd = sp.getInt(keyQtd, 0);
        double cancTotal = Double.parseDouble(sp.getString(keyCanc, "0.0"));

        faturamento -= cancelado.getValorTotal();
        if (faturamento < 0) faturamento = 0.0;

        qtd -= cancelado.getQtde();
        if (qtd < 0) qtd = 0;

        cancTotal += cancelado.getValorTotal();

        sp.edit()
            .putString(keyFat, String.valueOf(faturamento))
            .putInt(keyQtd, qtd)
            .putString(keyCanc, String.valueOf(cancTotal))
            .apply();
    }

    public synchronized double getFaturamentoHoje(String codGarcom) {
        String cod = (codGarcom != null && !codGarcom.isEmpty()) ? GarcomManager.normalizarCodigo(codGarcom) : "223";
        String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        return Double.parseDouble(sp.getString(KEY_PREFIX_FAT_DIA + cod + "_" + dataHoje, "0.0"));
    }

    public synchronized int getQtdItensHoje(String codGarcom) {
        String cod = (codGarcom != null && !codGarcom.isEmpty()) ? GarcomManager.normalizarCodigo(codGarcom) : "223";
        String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        return sp.getInt(KEY_PREFIX_QTD_DIA + cod + "_" + dataHoje, 0);
    }

    public synchronized double getFaturamentoMes(String codGarcom) {
        String cod = (codGarcom != null && !codGarcom.isEmpty()) ? GarcomManager.normalizarCodigo(codGarcom) : "223";
        String mesPrefixo = KEY_PREFIX_FAT_DIA + cod + "_" + new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        Map<String, ?> all = sp.getAll();

        double totalMes = 0.0;
        if (all != null) {
            for (Map.Entry<String, ?> e : all.entrySet()) {
                if (e.getKey().startsWith(mesPrefixo)) {
                    try {
                        totalMes += Double.parseDouble(String.valueOf(e.getValue()));
                    } catch (Exception ignored) {}
                }
            }
        }
        return totalMes;
    }

    public synchronized int getQtdItensMes(String codGarcom) {
        String cod = (codGarcom != null && !codGarcom.isEmpty()) ? GarcomManager.normalizarCodigo(codGarcom) : "223";
        String mesPrefixo = KEY_PREFIX_QTD_DIA + cod + "_" + new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        Map<String, ?> all = sp.getAll();

        int totalQtd = 0;
        if (all != null) {
            for (Map.Entry<String, ?> e : all.entrySet()) {
                if (e.getKey().startsWith(mesPrefixo)) {
                    try {
                        totalQtd += Integer.parseInt(String.valueOf(e.getValue()));
                    } catch (Exception ignored) {}
                }
            }
        }
        return totalQtd;
    }

    public synchronized List<DiaFaturamento> getHistoricoDiasMes(String codGarcom) {
        String cod = (codGarcom != null && !codGarcom.isEmpty()) ? GarcomManager.normalizarCodigo(codGarcom) : "223";
        String mesAtual = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // Calcula ontem
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
        String dataOntem = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

        String prefixoFat = KEY_PREFIX_FAT_DIA + cod + "_" + mesAtual;
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        Map<String, ?> all = sp.getAll();

        List<DiaFaturamento> lista = new ArrayList<>();
        if (all != null) {
            for (String key : all.keySet()) {
                if (key.startsWith(prefixoFat)) {
                    String dataStr = key.substring((KEY_PREFIX_FAT_DIA + cod + "_").length());
                    double fat = Double.parseDouble(sp.getString(key, "0.0"));
                    int qtd = sp.getInt(KEY_PREFIX_QTD_DIA + cod + "_" + dataStr, 0);
                    double canc = Double.parseDouble(sp.getString(KEY_PREFIX_CANC_DIA + cod + "_" + dataStr, "0.0"));

                    String label;
                    if (dataStr.equals(dataHoje)) {
                        label = "Hoje (" + formatarDataCurta(dataStr) + ")";
                    } else if (dataStr.equals(dataOntem)) {
                        label = "Ontem (" + formatarDataCurta(dataStr) + ")";
                    } else {
                        label = formatarDataCurta(dataStr);
                    }

                    lista.add(new DiaFaturamento(dataStr, label, fat, qtd, canc));
                }
            }
        }

        // Ordena por data decrescente (mais recente primeiro)
        Collections.sort(lista, (d1, d2) -> d2.dataStr.compareTo(d1.dataStr));
        return lista;
    }

    private static String formatarDataCurta(String yyyyMmDd) {
        try {
            String[] parts = yyyyMmDd.split("-");
            if (parts.length == 3) {
                return parts[2] + "/" + parts[1];
            }
        } catch (Exception ignored) {}
        return yyyyMmDd;
    }

    public double getFaturamentoAbertoAgora() {
        return faturamentoAbertoAgora;
    }

    public int getQtdItensAbertoAgora() {
        return qtdItensAbertoAgora;
    }

    public int getComandasAbertasAgora() {
        return comandasAbertasAgora;
    }

    public static String formatarMoeda(double valor) {
        try {
            return String.format(Locale.GERMANY, "R$ %.2f", valor);
        } catch (Exception e) {
            return "R$ " + valor;
        }
    }
}
