package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ResumoGarcomManager {
    private static final String PREF_RESUMO = "resumo_garcom_prefs";
    private static final String KEY_COD_GARCOM = "codigo_garcom_ativo";
    private static final String KEY_ULTIMA_DATA = "data_ultimo_registro";

    public static class ResumoGarcomDados {
        public final String codigoGarcom;
        public final String nomeGarcom;
        public final int totalPedidosHoje;
        public final double totalFaturamentoHoje;
        public final int pedidosAbertosAgora;
        public final double faturamentoAbertoAgora;
        public final int comandasAtivasAgora;

        public ResumoGarcomDados(String codigoGarcom, String nomeGarcom, int totalPedidosHoje,
                                 double totalFaturamentoHoje, int pedidosAbertosAgora,
                                 double faturamentoAbertoAgora, int comandasAtivasAgora) {
            this.codigoGarcom = codigoGarcom;
            this.nomeGarcom = nomeGarcom;
            this.totalPedidosHoje = totalPedidosHoje;
            this.totalFaturamentoHoje = totalFaturamentoHoje;
            this.pedidosAbertosAgora = pedidosAbertosAgora;
            this.faturamentoAbertoAgora = faturamentoAbertoAgora;
            this.comandasAtivasAgora = comandasAtivasAgora;
        }

        public boolean temCodigoConfigurado() {
            return codigoGarcom != null && !codigoGarcom.isEmpty();
        }

        public String getFaturamentoHojeFormatado() {
            return formatarMoeda(totalFaturamentoHoje);
        }

        public String getFaturamentoAbertoFormatado() {
            return formatarMoeda(faturamentoAbertoAgora);
        }

        public String getTituloGarcom() {
            if (!temCodigoConfigurado()) {
                return "👤 Toque para definir garçom";
            }
            if (nomeGarcom != null && !nomeGarcom.isEmpty()) {
                return "👤 " + nomeGarcom + " (#" + codigoGarcom + ")";
            }
            return "👤 Garçom #" + codigoGarcom;
        }

        public String getTextoResumoHoje() {
            if (!temCodigoConfigurado()) {
                return "Configurar código na ⚙️ para ver pedidos e faturamento";
            }
            String pedidosTxt = totalPedidosHoje == 1 ? "1 pedido" : (totalPedidosHoje + " pedidos");
            String fatTxt = getFaturamentoHojeFormatado();
            if (pedidosAbertosAgora > 0) {
                return pedidosTxt + " hoje • " + fatTxt + " (" + pedidosAbertosAgora + " ativos)";
            }
            return pedidosTxt + " hoje • " + fatTxt;
        }
    }

    public static String getCodigoGarcom(Context context) {
        if (context == null) return "";
        SharedPreferences sp = context.getSharedPreferences(PREF_RESUMO, Context.MODE_PRIVATE);
        String salvo = sp.getString(KEY_COD_GARCOM, "");
        if (salvo != null && !salvo.trim().isEmpty()) {
            return GarcomManager.normalizarCodigo(salvo);
        }
        // Fallback para sessão se houver
        try {
            String codSessao = SessionManager.getCodFunc(context);
            if (codSessao != null && !codSessao.trim().isEmpty()) {
                return GarcomManager.normalizarCodigo(codSessao);
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static void setCodigoGarcom(Context context, String codGarcom) {
        if (context == null) return;
        String norm = (codGarcom != null) ? GarcomManager.normalizarCodigo(codGarcom) : "";
        SharedPreferences sp = context.getSharedPreferences(PREF_RESUMO, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_COD_GARCOM, norm).apply();
    }

    public static String getNomeGarcomAtivo(Context context) {
        String cod = getCodigoGarcom(context);
        if (cod.isEmpty()) return "";
        return GarcomManager.getNomeGarcom(context, cod);
    }

    public static synchronized ResumoGarcomDados calcularEAtualizar(Context context, List<ComandaCardModel> comandas) {
        if (context == null) {
            return new ResumoGarcomDados("", "", 0, 0, 0, 0, 0);
        }

        String meuCodigo = getCodigoGarcom(context);
        String nomeGarcom = getNomeGarcomAtivo(context);

        if (meuCodigo.isEmpty()) {
            return new ResumoGarcomDados("", "", 0, 0, 0, 0, 0);
        }

        String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_RESUMO, Context.MODE_PRIVATE);

        // Se virou o dia, limpa o histórico de itens acumulados
        String ultimaData = sp.getString(KEY_ULTIMA_DATA, "");
        if (!dataHoje.equals(ultimaData)) {
            sp.edit()
                .putString(KEY_ULTIMA_DATA, dataHoje)
                .remove("itens_hoje_" + meuCodigo)
                .remove("faturamento_hoje_" + meuCodigo)
                .remove("qtd_pedidos_hoje_" + meuCodigo)
                .apply();
        }

        String keyItensHoje = "itens_hoje_" + meuCodigo + "_" + dataHoje;
        String keyFatHoje = "faturamento_hoje_" + meuCodigo + "_" + dataHoje;
        String keyQtdHoje = "qtd_pedidos_hoje_" + meuCodigo + "_" + dataHoje;

        Set<String> itensRegistradosHoje = new HashSet<>(sp.getStringSet(keyItensHoje, new HashSet<>()));
        double faturamentoHoje = Double.parseDouble(sp.getString(keyFatHoje, "0.0"));
        int totalPedidosHoje = sp.getInt(keyQtdHoje, 0);

        int pedidosAbertosAgora = 0;
        double faturamentoAbertoAgora = 0.0;
        int comandasAtivasAgora = 0;

        boolean houveNovosRegistros = false;

        if (comandas != null) {
            for (ComandaCardModel comanda : comandas) {
                if (comanda == null || !comanda.hasItens()) continue;
                List<ItemComandaModel> itens = comanda.getItens();
                boolean temItemDesteGarcomNaComanda = false;

                for (ItemComandaModel it : itens) {
                    if (it == null) continue;
                    String codVendIt = GarcomManager.normalizarCodigo(it.getCodVend());
                    if (!meuCodigo.equals(codVendIt)) continue;

                    temItemDesteGarcomNaComanda = true;
                    int qtd = Math.max(1, it.getVlrQtde());
                    double valor = it.getValorTotalNumerico();

                    // Se a comanda ainda está aberta (não entregue)
                    if (!comanda.isEntregue()) {
                        pedidosAbertosAgora += qtd;
                        faturamentoAbertoAgora += valor;
                    }

                    // Chave única deste item para não duplicar no acumulado do dia
                    String chaveItem;
                    if (it.getAutonum() != null && !it.getAutonum().isEmpty()) {
                        chaveItem = "AUT_" + it.getAutonum();
                    } else {
                        chaveItem = comanda.getId() + "_" + it.getItemKey(comanda.getId());
                    }

                    if (!itensRegistradosHoje.contains(chaveItem)) {
                        itensRegistradosHoje.add(chaveItem);
                        totalPedidosHoje += qtd;
                        faturamentoHoje += valor;
                        houveNovosRegistros = true;
                    }
                }

                if (temItemDesteGarcomNaComanda && !comanda.isEntregue()) {
                    comandasAtivasAgora++;
                }
            }
        }

        // Salva se houve novos itens detectados hoje
        if (houveNovosRegistros) {
            sp.edit()
                .putStringSet(keyItensHoje, itensRegistradosHoje)
                .putString(keyFatHoje, String.valueOf(faturamentoHoje))
                .putInt(keyQtdHoje, totalPedidosHoje)
                .putString(KEY_ULTIMA_DATA, dataHoje)
                .apply();
        }

        // Garantia de consistência: total nunca pode ser menor que o aberto agora
        if (totalPedidosHoje < pedidosAbertosAgora) totalPedidosHoje = pedidosAbertosAgora;
        if (faturamentoHoje < faturamentoAbertoAgora) faturamentoHoje = faturamentoAbertoAgora;

        return new ResumoGarcomDados(
            meuCodigo,
            nomeGarcom,
            totalPedidosHoje,
            faturamentoHoje,
            pedidosAbertosAgora,
            faturamentoAbertoAgora,
            comandasAtivasAgora
        );
    }

    public static synchronized void zerarResumoHoje(Context context, String codGarcom) {
        if (context == null) return;
        String cod = (codGarcom != null) ? GarcomManager.normalizarCodigo(codGarcom) : getCodigoGarcom(context);
        if (cod.isEmpty()) return;
        String dataHoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences sp = context.getSharedPreferences(PREF_RESUMO, Context.MODE_PRIVATE);
        sp.edit()
            .remove("itens_hoje_" + cod + "_" + dataHoje)
            .remove("faturamento_hoje_" + cod + "_" + dataHoje)
            .remove("qtd_pedidos_hoje_" + cod + "_" + dataHoje)
            .apply();
    }

    public static String formatarMoeda(double valor) {
        try {
            return String.format(Locale.GERMANY, "R$ %.2f", valor);
        } catch (Exception e) {
            return "R$ " + valor;
        }
    }
}
