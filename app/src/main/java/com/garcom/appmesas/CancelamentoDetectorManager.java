package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class CancelamentoDetectorManager {
    private static final String PREF_CANCELAMENTOS = "cancelamentos_detector_prefs";
    private static final String KEY_PENDENTES = "itens_pendentes_confirmacao";
    private static final String KEY_CONFIRMADOS = "historico_cancelamentos_confirmados";
    private static final String KEY_DESCARTADOS = "chaves_descartadas_normais";

    public interface OnCancelamentoListener {
        void onNovoItemParaConfirmar(ItemCanceladoModel item);
        void onCancelamentosAtualizados();
    }

    private static CancelamentoDetectorManager instance;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<OnCancelamentoListener> listeners = new CopyOnWriteArrayList<>();

    // Mapa dos itens ativos no último ciclo de polling (chave -> ItemCanceladoModel)
    private final Map<String, ItemCanceladoModel> itensAtivosAnteriores = new HashMap<>();
    private boolean primeiraVerificacaoFeita = false;

    // Listas persistidas
    private final List<ItemCanceladoModel> itensPendentes = new ArrayList<>();
    private final List<ItemCanceladoModel> historicoConfirmados = new ArrayList<>();
    private final Set<String> chavesDescartadas = new HashSet<>();

    private CancelamentoDetectorManager(Context context) {
        this.context = context.getApplicationContext();
        carregarDados();
    }

    public static synchronized CancelamentoDetectorManager getInstance(Context context) {
        if (instance == null) {
            instance = new CancelamentoDetectorManager(context);
        }
        return instance;
    }

    public void registrarListener(OnCancelamentoListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removerListener(OnCancelamentoListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private synchronized void carregarDados() {
        SharedPreferences sp = context.getSharedPreferences(PREF_CANCELAMENTOS, Context.MODE_PRIVATE);
        itensPendentes.clear();
        historicoConfirmados.clear();
        chavesDescartadas.clear();

        // 1. Carrega pendentes
        try {
            String jsonPend = sp.getString(KEY_PENDENTES, "[]");
            JSONArray arrPend = new JSONArray(jsonPend);
            for (int i = 0; i < arrPend.length(); i++) {
                itensPendentes.add(new ItemCanceladoModel(arrPend.getJSONObject(i)));
            }
        } catch (Exception ignored) {}

        // 2. Carrega confirmados
        try {
            String jsonConf = sp.getString(KEY_CONFIRMADOS, "[]");
            JSONArray arrConf = new JSONArray(jsonConf);
            for (int i = 0; i < arrConf.length(); i++) {
                historicoConfirmados.add(new ItemCanceladoModel(arrConf.getJSONObject(i)));
            }
        } catch (Exception ignored) {}

        // 3. Carrega descartados
        Set<String> desc = sp.getStringSet(KEY_DESCARTADOS, null);
        if (desc != null) {
            chavesDescartadas.addAll(desc);
        }
    }

    private synchronized void salvarDados() {
        SharedPreferences sp = context.getSharedPreferences(PREF_CANCELAMENTOS, Context.MODE_PRIVATE);
        JSONArray arrPend = new JSONArray();
        for (ItemCanceladoModel item : itensPendentes) {
            arrPend.put(item.toJson());
        }

        JSONArray arrConf = new JSONArray();
        for (ItemCanceladoModel item : historicoConfirmados) {
            arrConf.put(item.toJson());
        }

        sp.edit()
            .putString(KEY_PENDENTES, arrPend.toString())
            .putString(KEY_CONFIRMADOS, arrConf.toString())
            .putStringSet(KEY_DESCARTADOS, new HashSet<>(chavesDescartadas))
            .apply();
    }

    /**
     * Executado a cada ciclo de polling pelo MonitorComandasEngine.
     * Detecta itens pertencentes ao garçom que sumiram da comanda.
     */
    public synchronized void processarCicloPolling(String meuCodGarcom, List<ComandaCardModel> comandasAbertas) {
        if (meuCodGarcom == null || meuCodGarcom.trim().isEmpty()) {
            meuCodGarcom = "223"; // Padrão Wanderson
        }
        final String codAlvo = GarcomManager.normalizarCodigo(meuCodGarcom);

        // 1. Mapeia todos os itens atuais pertencentes ao garçom alvo
        Map<String, ItemCanceladoModel> itensAtuaisGarcom = new HashMap<>();

        if (comandasAbertas != null) {
            for (ComandaCardModel cmd : comandasAbertas) {
                if (cmd == null || !cmd.hasItens()) continue;
                for (ItemComandaModel it : cmd.getItens()) {
                    if (it == null) continue;
                    String codVendIt = GarcomManager.normalizarCodigo(it.getCodVend());
                    if (!codAlvo.equals(codVendIt)) continue;

                    ItemCanceladoModel model = new ItemCanceladoModel(it, cmd.getId());
                    itensAtuaisGarcom.put(model.getId(), model);
                }
            }
        }

        // Se for o primeiro ciclo logo após abrir o app, apenas inicializa a base de itens ativos
        if (!primeiraVerificacaoFeita) {
            itensAtivosAnteriores.clear();
            itensAtivosAnteriores.putAll(itensAtuaisGarcom);
            primeiraVerificacaoFeita = true;
            return;
        }

        // 2. Compara os itens anteriores com os atuais: os que sumiram são potenciais cancelamentos
        List<ItemCanceladoModel> novosSumiços = new ArrayList<>();

        for (Map.Entry<String, ItemCanceladoModel> entry : itensAtivosAnteriores.entrySet()) {
            String chave = entry.getKey();
            ItemCanceladoModel itemAntigo = entry.getValue();

            if (!itensAtuaisGarcom.containsKey(chave)) {
                // O item sumiu!
                // Verifica se já foi tratado antes
                if (!jaEstaEmPendentes(chave) && !jaEstaEmConfirmados(chave) && !chavesDescartadas.contains(chave)) {
                    itensPendentes.add(0, itemAntigo);
                    novosSumiços.add(itemAntigo);
                }
            }
        }

        // Atualiza a memória de itens ativos para o próximo ciclo
        itensAtivosAnteriores.clear();
        itensAtivosAnteriores.putAll(itensAtuaisGarcom);

        if (!novosSumiços.isEmpty()) {
            salvarDados();
            mainHandler.post(() -> {
                for (ItemCanceladoModel novo : novosSumiços) {
                    NotificationHelper.notificarItemSumiu(context, novo);
                    VibrationHelper.vibrateLongPress(context);
                    notificarItemSumiu(novo);
                }
                notificarAtualizacao();
            });
        }
    }

    private boolean jaEstaEmPendentes(String id) {
        for (ItemCanceladoModel m : itensPendentes) {
            if (m.getId().equals(id)) return true;
        }
        return false;
    }

    private boolean jaEstaEmConfirmados(String id) {
        for (ItemCanceladoModel m : historicoConfirmados) {
            if (m.getId().equals(id)) return true;
        }
        return false;
    }

    public synchronized void confirmarCancelamento(ItemCanceladoModel item) {
        if (item == null) return;
        item.setConfirmadoCancelado(true);

        // Remove dos pendentes
        Iterator<ItemCanceladoModel> it = itensPendentes.iterator();
        while (it.hasNext()) {
            if (it.next().getId().equals(item.getId())) {
                it.remove();
                break;
            }
        }

        // Adiciona ao topo dos confirmados
        historicoConfirmados.add(0, item);
        salvarDados();

        // Deduz do faturamento
        VendasFaturamentoManager.getInstance(context).deduzirCancelamento(item);

        mainHandler.post(this::notificarAtualizacao);
    }

    public synchronized void descartarCancelamento(ItemCanceladoModel item) {
        if (item == null) return;

        // Remove dos pendentes
        Iterator<ItemCanceladoModel> it = itensPendentes.iterator();
        while (it.hasNext()) {
            if (it.next().getId().equals(item.getId())) {
                it.remove();
                break;
            }
        }

        // Registra como descartado (venda normal ou transferida)
        chavesDescartadas.add(item.getId());
        salvarDados();

        mainHandler.post(this::notificarAtualizacao);
    }

    private void notificarItemSumiu(ItemCanceladoModel item) {
        for (OnCancelamentoListener l : listeners) {
            l.onNovoItemParaConfirmar(item);
        }
    }

    private void notificarAtualizacao() {
        for (OnCancelamentoListener l : listeners) {
            l.onCancelamentosAtualizados();
        }
    }

    public synchronized List<ItemCanceladoModel> getItensPendentes() {
        return new ArrayList<>(itensPendentes);
    }

    public synchronized List<ItemCanceladoModel> getHistoricoConfirmados() {
        return new ArrayList<>(historicoConfirmados);
    }

    public synchronized double getTotalCanceladoHoje() {
        String hoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        double total = 0.0;
        for (ItemCanceladoModel m : historicoConfirmados) {
            if (hoje.equals(m.getDataStr())) {
                total += m.getValorTotal();
            }
        }
        return total;
    }

    public synchronized int getQtdCanceladoHoje() {
        String hoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        int qtd = 0;
        for (ItemCanceladoModel m : historicoConfirmados) {
            if (hoje.equals(m.getDataStr())) {
                qtd += m.getQtde();
            }
        }
        return qtd;
    }
}
