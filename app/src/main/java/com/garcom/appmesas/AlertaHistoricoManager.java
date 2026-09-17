package com.garcom.appmesas;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class AlertaHistoricoManager {

    public static final String ACTION_HISTORICO_ALERTAS_ATUALIZADO = "com.garcom.appmesas.ACTION_HISTORICO_ALERTAS_ATUALIZADO";
    private static final String PREF_NAME = "AppMesasAlertasHistoricoPrefs";
    private static final String KEY_ALERTAS = "alertas_lista_json";
    private static final int MAX_ALERTAS = 40;

    public static synchronized void registrarAlerta(Context context, AlertaHistorico alerta) {
        if (context == null || alerta == null) return;
        Context appContext = context.getApplicationContext();

        List<AlertaHistorico> lista = carregarAlertasInterno(appContext);
        // Insere o novo alerta no topo (índice 0)
        lista.add(0, alerta);

        // Mantém apenas os últimos MAX_ALERTAS para não sobrecarregar
        while (lista.size() > MAX_ALERTAS) {
            lista.remove(lista.size() - 1);
        }

        salvarAlertasInterno(appContext, lista);

        // Envia broadcast para atualizar o sino e badge na tela em tempo real
        try {
            Intent intent = new Intent(ACTION_HISTORICO_ALERTAS_ATUALIZADO);
            appContext.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    public static synchronized List<AlertaHistorico> getAlertas(Context context) {
        if (context == null) return new ArrayList<>();
        return carregarAlertasInterno(context.getApplicationContext());
    }

    public static synchronized int getQtdNaoLidos(Context context) {
        if (context == null) return 0;
        List<AlertaHistorico> lista = carregarAlertasInterno(context.getApplicationContext());
        int naoLidos = 0;
        for (AlertaHistorico a : lista) {
            if (!a.isLido()) naoLidos++;
        }
        return naoLidos;
    }

    public static synchronized void marcarTodosComoLidos(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        List<AlertaHistorico> lista = carregarAlertasInterno(appContext);
        boolean alterou = false;
        for (AlertaHistorico a : lista) {
            if (!a.isLido()) {
                a.setLido(true);
                alterou = true;
            }
        }
        if (alterou) {
            salvarAlertasInterno(appContext, lista);
            try {
                Intent intent = new Intent(ACTION_HISTORICO_ALERTAS_ATUALIZADO);
                appContext.sendBroadcast(intent);
            } catch (Exception ignored) {}
        }
    }

    public static synchronized void limparTudo(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        salvarAlertasInterno(appContext, new ArrayList<>());
        try {
            Intent intent = new Intent(ACTION_HISTORICO_ALERTAS_ATUALIZADO);
            appContext.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    private static List<AlertaHistorico> carregarAlertasInterno(Context context) {
        List<AlertaHistorico> lista = new ArrayList<>();
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String jsonStr = sp.getString(KEY_ALERTAS, null);
            if (jsonStr != null && !jsonStr.trim().isEmpty()) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    lista.add(new AlertaHistorico(obj));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    private static void salvarAlertasInterno(Context context, List<AlertaHistorico> lista) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray();
            for (AlertaHistorico a : lista) {
                arr.put(a.toJSON());
            }
            sp.edit().putString(KEY_ALERTAS, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
