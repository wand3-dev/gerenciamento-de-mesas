package com.garcom.appmesas;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MesaManager {
    public static final String ACTION_PEDIDOS_ATUALIZADOS = "com.garcom.appmesas.ACTION_PEDIDOS_ATUALIZADOS";
    private static final String PREF_NAME = "mesas_prefs";
    private static final String KEY_MESAS = "key_mesas_data";
    private static final String KEY_HISTORICO = "key_historico_trocas";
    private static final String KEY_MINHAS_COMANDAS = "key_minhas_comandas";
    private static MesaManager instance;

    private List<Mesa> mesas;
    private List<Product> produtosCatalog;
    private Set<String> minhasComandas;

    private MesaManager(Context context) {
        mesas = new ArrayList<>();
        produtosCatalog = new ArrayList<>();
        minhasComandas = new HashSet<>();
        carregarMesas(context);
        carregarCatalogo(context);
        carregarMinhasComandas(context);
    }

    public static synchronized MesaManager getInstance(Context context) {
        if (instance == null) {
            instance = new MesaManager(context.getApplicationContext());
        }
        return instance;
    }

    private void carregarMesas(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_MESAS, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    mesas.add(new Mesa(arr.getJSONObject(i)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Se estiver vazio, inicializa pelo menos 1 a 34 como padrão
        if (mesas.isEmpty()) {
            mesas.clear();
            for (int i = 1; i <= 34; i++) {
                mesas.add(new Mesa(i));
            }
        }
    }

    public synchronized void salvarMesas(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray();
            for (Mesa m : mesas) {
                arr.put(m.toJSON());
            }
            sp.edit().putString(KEY_MESAS, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void carregarCatalogo(Context context) {
        try {
            InputStream is = context.getAssets().open("produtos.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                produtosCatalog.add(new Product(obj.optString("c"), obj.optString("d")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void carregarMinhasComandas(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> salvos = sp.getStringSet(KEY_MINHAS_COMANDAS, null);
        if (salvos != null) {
            minhasComandas.addAll(salvos);
        }
    }

    public synchronized Set<String> getMinhasComandas() {
        return new HashSet<>(minhasComandas);
    }

    public synchronized void adicionarMinhaComanda(Context context, String comanda) {
        if (comanda == null || comanda.trim().isEmpty()) return;
        String limpa = comanda.trim();
        minhasComandas.add(limpa);
        salvarMinhasComandas(context);

        // Se a comanda for número (ex: 239), garante que o card da mesa 239 existe e está aberta
        try {
            int numMesa = Integer.parseInt(limpa);
            Mesa m = getOuCriarMesa(numMesa);
            m.setAberta(true);
            salvarMesas(context);
        } catch (Exception ignored) {}
    }

    public synchronized void removerMinhaComanda(Context context, String comanda) {
        if (comanda == null) return;
        minhasComandas.remove(comanda.trim());
        salvarMinhasComandas(context);
    }

    public synchronized boolean isMinhaComanda(String comanda) {
        if (comanda == null) return false;
        return minhasComandas.contains(comanda.trim());
    }

    private void salvarMinhasComandas(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putStringSet(KEY_MINHAS_COMANDAS, new HashSet<>(minhasComandas)).apply();
    }

    public synchronized boolean isMesaMinha(Mesa mesa) {
        if (mesa == null) return false;
        String numMesaStr = String.valueOf(mesa.getNumero());
        if (minhasComandas.contains(numMesaStr)) return true;
        for (String c : mesa.getComandasUnicas()) {
            if (minhasComandas.contains(c.trim())) return true;
        }
        if (mesa.getPedidos() != null) {
            for (PedidoItem p : mesa.getPedidos()) {
                if (p.getComanda() != null && minhasComandas.contains(p.getComanda().trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized int getQtdMinhasMesas() {
        int count = 0;
        for (Mesa m : mesas) {
            if (isMesaMinha(m)) count++;
        }
        return count;
    }

    public synchronized List<Mesa> getMesas() {
        return new ArrayList<>(mesas);
    }

    public synchronized Mesa getMesa(int numero) {
        for (Mesa m : mesas) {
            if (m.getNumero() == numero) return m;
        }
        return null;
    }

    /**
     * Obtém a mesa existente ou cria dinamicamente o card se o número for superior a 34
     * ou não estiver cadastrado (ex: Mesa 239, 105, etc.)
     */
    public synchronized Mesa getOuCriarMesa(int numero) {
        for (Mesa m : mesas) {
            if (m.getNumero() == numero) return m;
        }
        Mesa nova = new Mesa(numero);
        nova.setAberta(true);
        mesas.add(nova);
        Collections.sort(mesas, (a, b) -> Integer.compare(a.getNumero(), b.getNumero()));
        return nova;
    }

    /**
     * Remove card de mesa dinâmica (> 34) quando não tiver mais pedidos e for fechada,
     * para manter a grade de mesas limpa e organizada
     */
    public synchronized void removerMesaDinamicaSeVazia(Context context, int numeroMesa) {
        if (numeroMesa <= 34) return; // Mantém fixas as mesas 1 a 34
        for (int i = 0; i < mesas.size(); i++) {
            Mesa m = mesas.get(i);
            if (m.getNumero() == numeroMesa && (!m.isAberta() || m.getPedidos().isEmpty())) {
                mesas.remove(i);
                salvarMesas(context);
                break;
            }
        }
    }

    /**
     * Transfere uma comanda e seus pedidos de uma mesa origem para a mesa destino
     */
    public synchronized void transferirComanda(Context context, String comanda, int mesaOrigem, int mesaDestino) {
        if (comanda == null || comanda.trim().isEmpty() || mesaOrigem == mesaDestino) return;
        String alvo = comanda.trim();

        Mesa mOrigem = getMesa(mesaOrigem);
        Mesa mDestino = getOuCriarMesa(mesaDestino);
        mDestino.setAberta(true);

        List<PedidoItem> transferidos = new ArrayList<>();
        if (mOrigem != null) {
            List<PedidoItem> restantes = new ArrayList<>();
            for (PedidoItem item : mOrigem.getPedidos()) {
                if (alvo.equals(item.getComanda())) {
                    transferidos.add(item);
                } else {
                    restantes.add(item);
                }
            }
            mOrigem.getPedidos().clear();
            mOrigem.getPedidos().addAll(restantes);
            if (mOrigem.getPedidos().isEmpty()) {
                mOrigem.setAberta(false);
                removerMesaDinamicaSeVazia(context, mesaOrigem);
            }
        }

        for (PedidoItem item : transferidos) {
            mDestino.adicionarPedido(item);
        }

        salvarMesas(context);
        registrarTroca(context, "Comanda #" + alvo + " transferida da Mesa " + mesaOrigem + " para Mesa " + mesaDestino);
        context.sendBroadcast(new Intent(ACTION_PEDIDOS_ATUALIZADOS));
    }

    public Mesa buscarMesaComComanda(String comanda, int numeroMesaIgnorada) {
        if (comanda == null || comanda.trim().isEmpty()) return null;
        String alvo = comanda.trim();
        for (Mesa mesa : mesas) {
            if (mesa.getNumero() == numeroMesaIgnorada || !mesa.isAberta()) continue;
            for (PedidoItem pedido : mesa.getPedidos()) {
                if (alvo.equals(pedido.getComanda())) return mesa;
            }
        }
        return null;
    }

    public void registrarTroca(Context context, String descricao) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray historico = new JSONArray(sp.getString(KEY_HISTORICO, "[]"));
            historico.put(System.currentTimeMillis() + "|" + descricao);
            while (historico.length() > 100) historico.remove(0);
            sp.edit().putString(KEY_HISTORICO, historico.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> getHistorico(Context context) {
        List<String> resultado = new ArrayList<>();
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray historico = new JSONArray(sp.getString(KEY_HISTORICO, "[]"));
            for (int i = historico.length() - 1; i >= 0; i--) {
                resultado.add(historico.optString(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultado;
    }

    /**
     * Sincroniza as mesas abertas e organiza os itens do servidor dentro de cada mesa.
     * 1. GET /func_MostrarComandasAbertas/T -> descobre quais mesas estão abertas.
     * 2. GET /func_MostrarComandasItens/T/mesa= -> popula e organiza os produtos dentro da mesa.
     */
    public synchronized int sincronizarMesasEItensDoServidor(Context context, Set<Integer> mesasAbertasServidor, java.util.Map<Integer, List<JSONObject>> itensPorMesaJson) {
        int novosItensAdicionados = 0;
        boolean houveAlteracao = false;

        // 1. Atualiza ou abre as mesas que têm comanda aberta no servidor
        for (int numMesa : mesasAbertasServidor) {
            Mesa m = getOuCriarMesa(numMesa);
            if (!m.isAberta()) {
                m.setAberta(true);
                houveAlteracao = true;
            }

            List<JSONObject> itensServidor = (itensPorMesaJson != null) ? itensPorMesaJson.get(numMesa) : null;
            if (itensServidor != null) {
                Set<String> idsItensAtivosNoServidor = new HashSet<>();

                for (JSONObject obj : itensServidor) {
                    PedidoItem novoItem = PedidoItem.fromServerJson(obj);
                    idsItensAtivosNoServidor.add(novoItem.getId());

                    // Verifica se já existe na mesa
                    PedidoItem existente = null;
                    for (PedidoItem p : m.getPedidos()) {
                        if (p.getId().equals(novoItem.getId())) {
                            existente = p;
                            break;
                        }
                    }

                    if (existente == null) {
                        m.adicionarPedido(novoItem);
                        novosItensAdicionados++;
                        houveAlteracao = true;
                    } else {
                        // Atualiza valores/quantidades, preservando se o garçom já marcou entregue
                        if (existente.getQuantidade() != novoItem.getQuantidade() ||
                            !String.valueOf(existente.getValorTotal()).equals(String.valueOf(novoItem.getValorTotal()))) {
                            existente.setQuantidade(novoItem.getQuantidade());
                            existente.setValorTotal(novoItem.getValorTotal());
                            existente.setDescricao(novoItem.getDescricao());
                            houveAlteracao = true;
                        }
                    }
                }

                // Remove da mesa itens que foram cancelados/estornados no servidor
                java.util.Iterator<PedidoItem> it = m.getPedidos().iterator();
                while (it.hasNext()) {
                    PedidoItem p = it.next();
                    if (p.getAutonum() != null && !p.getAutonum().isEmpty() && !idsItensAtivosNoServidor.contains(p.getId())) {
                        it.remove();
                        houveAlteracao = true;
                    }
                }
            }
        }

        // 2. Libera mesas que não estão mais abertas no servidor
        for (Mesa m : mesas) {
            if (m.isAberta() && !mesasAbertasServidor.contains(m.getNumero())) {
                m.setAberta(false);
                m.getPedidos().clear();
                houveAlteracao = true;
            }
        }

        if (houveAlteracao) {
            salvarMesas(context);
        }

        return novosItensAdicionados;
    }

    public List<Product> buscarProdutos(String query) {
        List<Product> result = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return result;
        String q = query.trim().toUpperCase();

        for (Product p : produtosCatalog) {
            if (p.getCodigo().toUpperCase().contains(q) || p.getDescricao().toUpperCase().contains(q)) {
                result.add(p);
                if (result.size() >= 40) break; // Limita busca rápida
            }
        }
        return result;
    }
}
