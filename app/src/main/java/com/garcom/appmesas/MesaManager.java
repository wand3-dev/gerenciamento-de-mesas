package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MesaManager {
    public static final String ACTION_PEDIDOS_ATUALIZADOS = "com.garcom.appmesas.ACTION_PEDIDOS_ATUALIZADOS";
    private static final String PREF_NAME = "mesas_prefs";
    private static final String KEY_MESAS = "key_mesas_data";
    private static final String KEY_HISTORICO = "key_historico_trocas";
    private static MesaManager instance;

    private List<Mesa> mesas;
    private List<Product> produtosCatalog;

    private MesaManager(Context context) {
        mesas = new ArrayList<>();
        produtosCatalog = new ArrayList<>();
        carregarMesas(context);
        carregarCatalogo(context);
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

        // Se estiver vazio, inicializa 1 a 34
        if (mesas.isEmpty() || mesas.size() != 34) {
            mesas.clear();
            for (int i = 1; i <= 34; i++) {
                mesas.add(new Mesa(i));
            }
        }
    }

    public void salvarMesas(Context context) {
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

    public List<Mesa> getMesas() {
        return mesas;
    }

    public Mesa getMesa(int numero) {
        for (Mesa m : mesas) {
            if (m.getNumero() == numero) return m;
        }
        return null;
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
