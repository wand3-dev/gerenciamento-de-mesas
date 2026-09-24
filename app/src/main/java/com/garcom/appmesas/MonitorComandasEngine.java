package com.garcom.appmesas;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonitorComandasEngine {

    public interface MonitorCallback {
        void onEstadoAlterado(EstadoServidor novoEstado, String mensagem);
        void onComandasAtualizadas(List<ComandaCardModel> comandas, String ultimaSincronizacao);
    }

    private static MonitorComandasEngine instance;
    private final Context context;
    private final ServerComandasClient serverClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EstadoServidor estado = EstadoServidor.OFFLINE;
    private String ultimaSincronizacao = "";
    private boolean ativo = false;
    private boolean pollingEmExecucao = false;

    // Mapa das comandas monitoradas ativas com chave única por comanda
    private final Map<String, ComandaCardModel> mapaComandas = new LinkedHashMap<>();

    private static final String PREF_MONITOR = "monitor_comandas_prefs";
    private static final String KEY_ENTREGUES = "key_comandas_entregues";
    private static final String KEY_ITENS_CHECADOS = "key_itens_checados";
    private final Set<String> comandasEntregues = new HashSet<>();
    private final Set<String> itensChecados = new HashSet<>();

    private final List<MonitorCallback> callbacks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private Runnable runnablePolling;
    private boolean primeiraConsulta = true;

    private MonitorComandasEngine(Context context) {
        this.context = context.getApplicationContext();
        this.serverClient = ServerComandasClient.getInstance(this.context);
        carregarComandasEntregues();
    }

    private void carregarComandasEntregues() {
        android.content.SharedPreferences sp = context.getSharedPreferences(PREF_MONITOR, Context.MODE_PRIVATE);
        Set<String> salvos = sp.getStringSet(KEY_ENTREGUES, null);
        if (salvos != null) {
            comandasEntregues.addAll(salvos);
        }
        Set<String> itensSalvos = sp.getStringSet(KEY_ITENS_CHECADOS, null);
        if (itensSalvos != null) {
            itensChecados.addAll(itensSalvos);
        }
    }

    private void salvarComandasEntregues() {
        android.content.SharedPreferences sp = context.getSharedPreferences(PREF_MONITOR, Context.MODE_PRIVATE);
        sp.edit()
            .putStringSet(KEY_ENTREGUES, new HashSet<>(comandasEntregues))
            .putStringSet(KEY_ITENS_CHECADOS, new HashSet<>(itensChecados))
            .apply();
    }

    public synchronized void alternarItemChecado(String comandaId, String itemKey) {
        if (itemKey == null || itemKey.isEmpty()) return;
        if (itensChecados.contains(itemKey)) {
            itensChecados.remove(itemKey);
        } else {
            itensChecados.add(itemKey);
        }
        salvarComandasEntregues();

        ComandaCardModel card = mapaComandas.get(comandaId);
        if (card != null) {
            for (ItemComandaModel it : card.getItens()) {
                if (itemKey.equals(it.getItemKey(card.getId()))) {
                    it.setChecado(itensChecados.contains(itemKey));
                }
            }
        }

        List<ComandaCardModel> listaFinal = getListaComandas();
        mainHandler.post(() -> {
            for (MonitorCallback cb : callbacks) {
                cb.onComandasAtualizadas(listaFinal, ultimaSincronizacao);
            }
        });
    }

    public synchronized void alternarEntregueComanda(String idComanda) {
        if (idComanda == null || idComanda.isEmpty()) return;
        if (comandasEntregues.contains(idComanda)) {
            comandasEntregues.remove(idComanda);
        } else {
            comandasEntregues.add(idComanda);
        }
        ComandaCardModel card = mapaComandas.get(idComanda);
        if (card != null) {
            card.setEntregue(comandasEntregues.contains(idComanda));
        }
        salvarComandasEntregues();

        List<ComandaCardModel> listaFinal = getListaComandas();
        mainHandler.post(() -> {
            for (MonitorCallback cb : callbacks) {
                cb.onComandasAtualizadas(listaFinal, ultimaSincronizacao);
            }
        });
    }

    public static synchronized MonitorComandasEngine getInstance(Context context) {
        if (instance == null) {
            instance = new MonitorComandasEngine(context);
        }
        return instance;
    }

    public void registrarCallback(MonitorCallback callback) {
        if (callback != null && !callbacks.contains(callback)) {
            callbacks.add(callback);
            callback.onEstadoAlterado(estado, "");
            callback.onComandasAtualizadas(getListaComandas(), ultimaSincronizacao);
        }
    }

    public void removerCallback(MonitorCallback callback) {
        callbacks.remove(callback);
    }

    public EstadoServidor getEstado() { return estado; }
    public String getUltimaSincronizacao() { return ultimaSincronizacao; }
    public boolean isAtivo() { return ativo; }

    /**
     * Retorna a lista de comandas com:
     * - Comandas abertas (não entregues) no topo (lá pra cima)
     * - Comandas entregues no fim da lista (lá pra baixo)
     */
    public synchronized List<ComandaCardModel> getListaComandas() {
        List<ComandaCardModel> lista = new ArrayList<>(mapaComandas.values());
        java.util.Collections.sort(lista, (c1, c2) -> {
            // 1. Não entregues primeiro, entregues por último
            if (c1.isEntregue() != c2.isEntregue()) {
                return c1.isEntregue() ? 1 : -1;
            }
            // 2. Se ambos têm o mesmo status, ordena pelo tempo de detecção (mais antigas primeiro)
            return Long.compare(c1.getDetectedAt(), c2.getDetectedAt());
        });
        return lista;
    }

    public synchronized void ligarServidor() {
        if (ativo && estado == EstadoServidor.ONLINE) return;
        ativo = true;
        setEstado(EstadoServidor.CONNECTING, "CONECTANDO...");
        executarCicloPolling();
    }

    public synchronized void desligarServidor() {
        ativo = false;
        if (runnablePolling != null) {
            mainHandler.removeCallbacks(runnablePolling);
        }
        setEstado(EstadoServidor.OFFLINE, "LIGAR SERVIDOR");
    }

    private void setEstado(EstadoServidor novoEstado, String msg) {
        this.estado = novoEstado;
        mainHandler.post(() -> {
            for (MonitorCallback cb : callbacks) {
                cb.onEstadoAlterado(novoEstado, msg);
            }
        });
    }

    private void executarCicloPolling() {
        if (!ativo || pollingEmExecucao) return;
        pollingEmExecucao = true;

        executor.execute(() -> {
            try {
                // 1. Consulta leve das comandas abertas via DataSnap
                List<JSONObject> comandasJson = serverClient.buscarComandasAbertasSync();

                // 2. Processar e filtrar comandas > 10 horas
                List<JSONObject> comandasValidas = new ArrayList<>();
                Set<Integer> mesasParaConsultar = new HashSet<>();
                Set<String> chavesAtivasNestaConsulta = new HashSet<>();

                for (JSONObject obj : comandasJson) {
                    String dataStr = obj.optString("DATA", "").trim();
                    String horaStr = obj.optString("HORA", "").trim();

                    // Ignora comandas com mais de 10 horas
                    if (!ComandaCardModel.isValidaMenosDe10Horas(dataStr, horaStr)) {
                        continue;
                    }
                    comandasValidas.add(obj);

                    int m = obj.optInt("MESA", 0);
                    if (m == 0) {
                        try { m = Integer.parseInt(obj.optString("MESA", "0").trim()); } catch (Exception ignored) {}
                    }
                    if (m > 0) {
                        mesasParaConsultar.add(m);
                    }
                }

                // 3. Buscar os itens das mesas para exibir diretamente em cada comanda no Home
                Map<Integer, List<ItemComandaModel>> itensPorMesa = new HashMap<>();
                for (int numMesa : mesasParaConsultar) {
                    try {
                        List<JSONObject> itensMesaJson = serverClient.buscarItensDaMesaSync(numMesa);
                        List<ItemComandaModel> listaItensMesa = new ArrayList<>();
                        for (JSONObject itObj : itensMesaJson) {
                            listaItensMesa.add(new ItemComandaModel(itObj));
                        }
                        itensPorMesa.put(numMesa, listaItensMesa);
                    } catch (Exception e) {
                        itensPorMesa.put(numMesa, new ArrayList<>());
                    }
                }

                int novasComandasDetectadas = 0;
                String descricaoPrimeiraNova = "";

                // 4. Atualizar o mapa de comandas e associar os itens de cada comanda
                synchronized (this) {
                    for (JSONObject objCmd : comandasValidas) {
                        String doc = objCmd.optString("DOCUMENTO", "").trim();
                        String numCmd = objCmd.optString("NUM_COMANDA", "").trim();
                        int m = objCmd.optInt("MESA", 0);
                        if (m == 0) {
                            try { m = Integer.parseInt(objCmd.optString("MESA", "0").trim()); } catch (Exception ignored) {}
                        }

                        // Chave única e exclusiva por comanda para não se repetirem
                        String chave;
                        if (!numCmd.isEmpty()) {
                            chave = ComandaCardModel.normalizarIdComanda(numCmd);
                        } else if (!doc.isEmpty()) {
                            chave = "DOC_" + doc;
                        } else {
                            chave = "MESA_" + m;
                        }
                        chavesAtivasNestaConsulta.add(chave);

                        ComandaCardModel card = mapaComandas.get(chave);
                        if (card == null) {
                            // Nova comanda detectada!
                            novasComandasDetectadas++;
                            if (descricaoPrimeiraNova.isEmpty()) {
                                descricaoPrimeiraNova = "CMD #" + numCmd + (m > 0 ? " (Mesa " + m + ")" : "");
                            }
                            card = new ComandaCardModel(objCmd);
                            card.setEntregue(comandasEntregues.contains(chave));
                            mapaComandas.put(chave, card);
                        } else {
                            // Comanda já existente: preserva detectedAt e atualiza valores/itens
                            card.atualizarDados(objCmd);
                            card.setEntregue(comandasEntregues.contains(chave));
                        }

                        // Associar os produtos desta comanda específica
                        List<ItemComandaModel> todosItensMesa = itensPorMesa.get(m);
                        List<ItemComandaModel> itensDestaComanda = new ArrayList<>();
                        if (todosItensMesa != null) {
                            for (ItemComandaModel item : todosItensMesa) {
                                boolean match = false;
                                if (!numCmd.isEmpty() && numCmd.equals(item.getNumComanda())) {
                                    match = true;
                                } else if (!doc.isEmpty() && doc.equals(item.getDocumento())) {
                                    match = true;
                                }
                                if (match) {
                                    itensDestaComanda.add(item);
                                }
                            }

                            // Fallback caso itens na mesa não tragam numComanda explícito
                            if (itensDestaComanda.isEmpty() && !todosItensMesa.isEmpty()) {
                                itensDestaComanda.addAll(todosItensMesa);
                            }
                        }

                        for (ItemComandaModel itProd : itensDestaComanda) {
                            itProd.setChecado(itensChecados.contains(itProd.getItemKey(card.getId())));
                        }
                        card.setItens(itensDestaComanda);
                    }

                    // Remoção automática de comandas que já foram fechadas no servidor
                    Iterator<Map.Entry<String, ComandaCardModel>> it = mapaComandas.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, ComandaCardModel> entry = it.next();
                        String chaveRemovida = entry.getKey();
                        if (!chavesAtivasNestaConsulta.contains(chaveRemovida)) {
                            comandasEntregues.remove(chaveRemovida);
                            // Purga itens checados associados à comanda fechada para liberar memória
                            String prefixo = chaveRemovida + "_";
                            Iterator<String> itItens = itensChecados.iterator();
                            while (itItens.hasNext()) {
                                if (itItens.next().startsWith(prefixo)) {
                                    itItens.remove();
                                }
                            }
                            it.remove();
                        }
                    }
                    salvarComandasEntregues();
                }

                // Toca som de sino de pedido e vibração se novas comandas foram abertas
                if (!primeiraConsulta && novasComandasDetectadas > 0) {
                    final int qtdNovas = novasComandasDetectadas;
                    final String descNova = descricaoPrimeiraNova;
                    mainHandler.post(() -> {
                        NotificationHelper.tocarSinoNovaComanda(context);
                        String msg = (qtdNovas == 1) ? ("🔔 Nova comanda: " + descNova) : ("🔔 " + qtdNovas + " novas comandas abertas!");
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
                primeiraConsulta = false;

                // Sincronização concluída com sucesso
                ultimaSincronizacao = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                setEstado(EstadoServidor.ONLINE, "● SERVIDOR ONLINE");

                List<ComandaCardModel> listaFinal = getListaComandas();
                mainHandler.post(() -> {
                    for (MonitorCallback cb : callbacks) {
                        cb.onComandasAtualizadas(listaFinal, ultimaSincronizacao);
                    }
                });

            } catch (Exception erro) {
                // Em caso de erro temporário de rede, preserva dados atuais
                String msgErro = erro.getMessage() != null ? erro.getMessage() : "Sem resposta";
                setEstado(EstadoServidor.ERROR, "ERRO DE CONEXÃO: " + msgErro);
            } finally {
                pollingEmExecucao = false;
                // Agenda o próximo ciclo de polling para daqui a 10 segundos
                if (ativo) {
                    runnablePolling = () -> executarCicloPolling();
                    mainHandler.postDelayed(runnablePolling, 10000);
                }
            }
        });
    }
}
