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

    private static final String KEY_PREFIX_FAT_COMIDAS = "fat_comidas_dia_";
    private static final String KEY_PREFIX_QTD_COMIDAS = "qtd_comidas_dia_";
    private static final String KEY_PREFIX_FAT_TERCEIROS = "fat_terceiros_dia_";
    private static final String KEY_PREFIX_QTD_TERCEIROS = "qtd_terceiros_dia_";

    private static final String KEY_META_DIARIA = "meta_diaria_comidas";
    public static final double DEFAULT_META_DIARIA = 500.00;

    public static class DiaFaturamento {
        public final String dataStr; // yyyy-MM-dd
        public final String labelExibicao; // Hoje, Ontem, 02/10
        public final double faturamentoTotal;
        public final double faturamentoComidas;
        public final double faturamentoTerceiros;
        public final int qtdItens;
        public final double cancelado;

        public DiaFaturamento(String dataStr, String labelExibicao, double faturamentoTotal,
                              double faturamentoComidas, double faturamentoTerceiros,
                              int qtdItens, double cancelado) {
            this.dataStr = dataStr;
            this.labelExibicao = labelExibicao;
            this.faturamentoTotal = faturamentoTotal;
            this.faturamentoComidas = faturamentoComidas;
            this.faturamentoTerceiros = faturamentoTerceiros;
            this.qtdItens = qtdItens;
            this.cancelado = cancelado;
        }

        public String getFaturamentoFormatado() {
            return formatarMoeda(faturamentoTotal);
        }

        public String getComidasFormatado() {
            return formatarMoeda(faturamentoComidas);
        }

        public String getTerceirosFormatado() {
            return formatarMoeda(faturamentoTerceiros);
        }

        public String getCanceladoFormatado() {
            return formatarMoeda(cancelado);
        }
    }

    private static VendasFaturamentoManager instance;
    private final Context context;

    // Valores em tempo real das mesas abertas no momento
    private double faturamentoAbertoAgora = 0.0;
    private double faturamentoAbertoComidas = 0.0;
    private double faturamentoAbertoTerceiros = 0.0;
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

    public double getMetaDiaria() {
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        return (double) sp.getFloat(KEY_META_DIARIA, (float) DEFAULT_META_DIARIA);
    }

    public void setMetaDiaria(double novaMeta) {
        if (novaMeta <= 0) novaMeta = DEFAULT_META_DIARIA;
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        sp.edit().putFloat(KEY_META_DIARIA, (float) novaMeta).apply();
    }

    public int getPercentualMetaHoje(String codGarcom) {
        double com = getFaturamentoComidasHoje(codGarcom);
        double meta = getMetaDiaria();
        if (meta <= 0) return 100;
        return (int) Math.round((com / meta) * 100);
    }

    public double getValorFaltanteMeta(String codGarcom) {
        double com = getFaturamentoComidasHoje(codGarcom);
        double meta = getMetaDiaria();
        return Math.max(0.0, meta - com);
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

        String keyFatComHoje = KEY_PREFIX_FAT_COMIDAS + codAlvo + "_" + dataHoje;
        String keyQtdComHoje = KEY_PREFIX_QTD_COMIDAS + codAlvo + "_" + dataHoje;
        String keyFatTercHoje = KEY_PREFIX_FAT_TERCEIROS + codAlvo + "_" + dataHoje;
        String keyQtdTercHoje = KEY_PREFIX_QTD_TERCEIROS + codAlvo + "_" + dataHoje;

        Set<String> itensHoje = new HashSet<>(sp.getStringSet(keyItensHoje, new HashSet<>()));
        double faturamentoHoje = Double.parseDouble(sp.getString(keyFatHoje, "0.0"));
        int qtdHoje = sp.getInt(keyQtdHoje, 0);

        double fatComidasHoje = Double.parseDouble(sp.getString(keyFatComHoje, "0.0"));
        int qtdComidasHoje = sp.getInt(keyQtdComHoje, 0);
        double fatTercHoje = Double.parseDouble(sp.getString(keyFatTercHoje, "0.0"));
        int qtdTercHoje = sp.getInt(keyQtdTercHoje, 0);

        double abertosValor = 0.0;
        double abertosComidas = 0.0;
        double abertosTerc = 0.0;
        int abertosQtd = 0;
        int abertasCmds = 0;
        boolean houveNovos = false;

        CatalogoClassificador classificador = CatalogoClassificador.getInstance(context);

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
                    boolean ehComida = classificador.isComidaOuProducao(it.getCodProd(), it.getDescricao());

                    if (!cmd.isEntregue()) {
                        abertosValor += vlr;
                        abertosQtd += q;
                        if (ehComida) {
                            abertosComidas += vlr;
                        } else {
                            abertosTerc += vlr;
                        }
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

                        if (ehComida) {
                            fatComidasHoje += vlr;
                            qtdComidasHoje += q;
                        } else {
                            fatTercHoje += vlr;
                            qtdTercHoje += q;
                        }

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
        if (fatComidasHoje < abertosComidas) fatComidasHoje = abertosComidas;
        if (fatTercHoje < abertosTerc) fatTercHoje = abertosTerc;

        this.faturamentoAbertoAgora = abertosValor;
        this.faturamentoAbertoComidas = abertosComidas;
        this.faturamentoAbertoTerceiros = abertosTerc;
        this.qtdItensAbertoAgora = abertosQtd;
        this.comandasAbertasAgora = abertasCmds;

        if (houveNovos) {
            sp.edit()
                .putStringSet(keyItensHoje, itensHoje)
                .putString(keyFatHoje, String.valueOf(faturamentoHoje))
                .putInt(keyQtdHoje, qtdHoje)
                .putString(keyFatComHoje, String.valueOf(fatComidasHoje))
                .putInt(keyQtdComHoje, qtdComidasHoje)
                .putString(keyFatTercHoje, String.valueOf(fatTercHoje))
                .putInt(keyQtdTercHoje, qtdTercHoje)
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

        String keyFatCom = KEY_PREFIX_FAT_COMIDAS + cod + "_" + dataItem;
        String keyQtdCom = KEY_PREFIX_QTD_COMIDAS + cod + "_" + dataItem;
        String keyFatTerc = KEY_PREFIX_FAT_TERCEIROS + cod + "_" + dataItem;
        String keyQtdTerc = KEY_PREFIX_QTD_TERCEIROS + cod + "_" + dataItem;

        double faturamento = Double.parseDouble(sp.getString(keyFat, "0.0"));
        int qtd = sp.getInt(keyQtd, 0);
        double cancTotal = Double.parseDouble(sp.getString(keyCanc, "0.0"));

        double fatCom = Double.parseDouble(sp.getString(keyFatCom, "0.0"));
        int qtdCom = sp.getInt(keyQtdCom, 0);
        double fatTerc = Double.parseDouble(sp.getString(keyFatTerc, "0.0"));
        int qtdTerc = sp.getInt(keyQtdTerc, 0);

        boolean ehComida = CatalogoClassificador.getInstance(context).isComidaOuProducao(cancelado.getAutonum(), cancelado.getDescricao());

        double vlr = cancelado.getValorTotal();
        int q = cancelado.getQtde();

        faturamento = Math.max(0.0, faturamento - vlr);
        qtd = Math.max(0, qtd - q);
        cancTotal += vlr;

        if (ehComida) {
            fatCom = Math.max(0.0, fatCom - vlr);
            qtdCom = Math.max(0, qtdCom - q);
        } else {
            fatTerc = Math.max(0.0, fatTerc - vlr);
            qtdTerc = Math.max(0, qtdTerc - q);
        }

        sp.edit()
            .putString(keyFat, String.valueOf(faturamento))
            .putInt(keyQtd, qtd)
            .putString(keyCanc, String.valueOf(cancTotal))
            .putString(keyFatCom, String.valueOf(fatCom))
            .putInt(keyQtdCom, qtdCom)
            .putString(keyFatTerc, String.valueOf(fatTerc))
            .putInt(keyQtdTerc, qtdTerc)
            .apply();
    }

    public synchronized double getFaturamentoHoje(String codGarcom) {
        String cod = (codGarcom != null && !codGarcom.isEmpty()) ? GarcomManager.normalizarCodigo(codGarcom) : "223";
        String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        return Double.parseDouble(sp.getString(KEY_PREFIX_FAT_DIA + cod + "_" + dataHoje, "0.0"));
    }

    public synchronized double getFaturamentoComidasHoje(String codGarcom) {
        String cod = (codGarcom != null && !codGarcom.isEmpty()) ? GarcomManager.normalizarCodigo(codGarcom) : "223";
        String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        return Double.parseDouble(sp.getString(KEY_PREFIX_FAT_COMIDAS + cod + "_" + dataHoje, "0.0"));
    }

    public synchronized double getFaturamentoTerceirosHoje(String codGarcom) {
        String cod = (codGarcom != null && !codGarcom.isEmpty()) ? GarcomManager.normalizarCodigo(codGarcom) : "223";
        String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        return Double.parseDouble(sp.getString(KEY_PREFIX_FAT_TERCEIROS + cod + "_" + dataHoje, "0.0"));
    }

    public synchronized int getQtdItensHoje(String codGarcom) {
        String cod = (codGarcom != null && !codGarcom.isEmpty()) ? GarcomManager.normalizarCodigo(codGarcom) : "223";
        String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_VENDAS, Context.MODE_PRIVATE);
        return sp.getInt(KEY_PREFIX_QTD_DIA + cod + "_" + dataHoje, 0);
    }

    public synchronized double getFaturamentoMes(String codGarcom) {
        return somarPrefixoMes(codGarcom, KEY_PREFIX_FAT_DIA);
    }

    public synchronized double getFaturamentoComidasMes(String codGarcom) {
        return somarPrefixoMes(codGarcom, KEY_PREFIX_FAT_COMIDAS);
    }

    public synchronized double getFaturamentoTerceirosMes(String codGarcom) {
        return somarPrefixoMes(codGarcom, KEY_PREFIX_FAT_TERCEIROS);
    }

    private double somarPrefixoMes(String codGarcom, String prefixo) {
        String cod = (codGarcom != null && !codGarcom.isEmpty()) ? GarcomManager.normalizarCodigo(codGarcom) : "223";
        String mesPrefixo = prefixo + cod + "_" + new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
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
                    double fatTotal = Double.parseDouble(sp.getString(key, "0.0"));
                    double fatCom = Double.parseDouble(sp.getString(KEY_PREFIX_FAT_COMIDAS + cod + "_" + dataStr, "0.0"));
                    double fatTerc = Double.parseDouble(sp.getString(KEY_PREFIX_FAT_TERCEIROS + cod + "_" + dataStr, "0.0"));
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

                    lista.add(new DiaFaturamento(dataStr, label, fatTotal, fatCom, fatTerc, qtd, canc));
                }
            }
        }

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

    public double getFaturamentoAbertoComidas() {
        return faturamentoAbertoComidas;
    }

    public double getFaturamentoAbertoTerceiros() {
        return faturamentoAbertoTerceiros;
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
