package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConferenciaSaidasManager {
    private static final String PREF_CONFERENCIA = "conferencia_saidas_prefs";
    private static final String KEY_COMANDAS_SAIDAS = "comandas_saidas_lista";

    public interface OnConferenciaListener {
        void onConferenciaAtualizada();
    }

    private static ConferenciaSaidasManager instance;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<OnConferenciaListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ComandaSaidaModel> comandasSaidas = new ArrayList<>();

    private ConferenciaSaidasManager(Context context) {
        this.context = context.getApplicationContext();
        carregarDados();
    }

    public static synchronized ConferenciaSaidasManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConferenciaSaidasManager(context);
        }
        return instance;
    }

    public void registrarListener(OnConferenciaListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removerListener(OnConferenciaListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private synchronized void carregarDados() {
        comandasSaidas.clear();
        SharedPreferences sp = context.getSharedPreferences(PREF_CONFERENCIA, Context.MODE_PRIVATE);
        try {
            String jsonStr = sp.getString(KEY_COMANDAS_SAIDAS, "[]");
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                comandasSaidas.add(new ComandaSaidaModel(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
    }

    private synchronized void salvarDados() {
        SharedPreferences sp = context.getSharedPreferences(PREF_CONFERENCIA, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        for (ComandaSaidaModel cmd : comandasSaidas) {
            arr.put(cmd.toJson());
        }
        sp.edit().putString(KEY_COMANDAS_SAIDAS, arr.toString()).apply();
    }

    public synchronized void registrarComandaQueSaiu(ComandaCardModel card, String codGarcom) {
        if (card == null) return;
        ComandaSaidaModel model = new ComandaSaidaModel(card, codGarcom);
        if (model.getItens().isEmpty()) {
            return; // Se não tem itens deste garçom, não precisa auditar
        }

        // Evita duplicar a mesma saída recente se já registrada
        for (ComandaSaidaModel existente : comandasSaidas) {
            if (existente.getComandaId().equals(model.getComandaId())
                    && Math.abs(existente.getTimestampSaida() - model.getTimestampSaida()) < 60000) {
                return;
            }
        }

        comandasSaidas.add(0, model);

        // Limita a 30 saídas recentes no histórico de conferência
        while (comandasSaidas.size() > 30) {
            comandasSaidas.remove(comandasSaidas.size() - 1);
        }

        salvarDados();
        mainHandler.post(this::notificar);
    }

    public synchronized void confirmarComandaTodaPaga(ComandaSaidaModel cmd) {
        if (cmd == null) return;
        cmd.setStatusConferencia("PAGA_INTEGRAL");

        // Todos os itens foram pagos: descarta cancelamento de todos eles no detector
        CancelamentoDetectorManager detector = CancelamentoDetectorManager.getInstance(context);
        for (ItemCanceladoModel it : cmd.getItens()) {
            detector.descartarCancelamento(it);
        }

        salvarDados();
        mainHandler.post(this::notificar);
    }

    public synchronized void cancelarItemIndividual(ComandaSaidaModel cmd, ItemCanceladoModel item) {
        if (cmd == null || item == null) return;

        item.setConfirmadoCancelado(true);

        CancelamentoDetectorManager detector = CancelamentoDetectorManager.getInstance(context);
        detector.confirmarCancelamento(item);

        boolean todosCancelados = true;
        for (ItemCanceladoModel it : cmd.getItens()) {
            if (!it.isConfirmadoCancelado()) {
                todosCancelados = false;
                break;
            }
        }
        cmd.setStatusConferencia(todosCancelados ? "CANCELAMENTO_TOTAL" : "CANCELAMENTO_PARCIAL");
        salvarDados();
        mainHandler.post(this::notificar);
    }

    public synchronized void cancelarComandaToda(ComandaSaidaModel cmd) {
        if (cmd == null) return;
        cmd.setStatusConferencia("CANCELAMENTO_TOTAL");

        CancelamentoDetectorManager detector = CancelamentoDetectorManager.getInstance(context);
        for (ItemCanceladoModel it : cmd.getItens()) {
            it.setConfirmadoCancelado(true);
            detector.confirmarCancelamento(it);
        }

        salvarDados();
        mainHandler.post(this::notificar);
    }

    private void notificar() {
        for (OnConferenciaListener l : listeners) {
            l.onConferenciaAtualizada();
        }
    }

    public synchronized List<ComandaSaidaModel> getComandasPendentes() {
        List<ComandaSaidaModel> pendentes = new ArrayList<>();
        for (ComandaSaidaModel c : comandasSaidas) {
            if (c.isPendente()) pendentes.add(c);
        }
        return pendentes;
    }

    public synchronized List<ComandaSaidaModel> getTodasComandasSaidas() {
        return new ArrayList<>(comandasSaidas);
    }

    public synchronized int getQtdPendentes() {
        int count = 0;
        for (ComandaSaidaModel c : comandasSaidas) {
            if (c.isPendente()) count++;
        }
        return count;
    }
}
