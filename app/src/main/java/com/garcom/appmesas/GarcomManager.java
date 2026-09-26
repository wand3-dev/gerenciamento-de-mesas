package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public class GarcomManager {
    private static final String PREF_GARCONS = "garcons_mapeamento_prefs";
    private static final Map<String, String> garconsPadrao = new HashMap<>();

    static {
        // Mapeamentos conhecidos
        garconsPadrao.put("200", "Cauã");
        garconsPadrao.put("223", "Wanderson");
    }

    public static String getNomeGarcom(Context context, String codVend) {
        if (codVend == null || codVend.trim().isEmpty()) {
            return "";
        }
        String codigo = normalizarCodigo(codVend);

        // 1. Se for o usuário atualmente logado
        if (context != null) {
            String codLogado = SessionManager.getCodFunc(context);
            String nomeLogado = SessionManager.getNomeUsuario(context);
            if (!codLogado.isEmpty() && codLogado.equals(codigo) && !nomeLogado.isEmpty()) {
                return primeiroNome(nomeLogado);
            }

            // 2. Procura nas preferências de garçons cadastrados
            SharedPreferences sp = context.getSharedPreferences(PREF_GARCONS, Context.MODE_PRIVATE);
            String nomeSalvo = sp.getString("garcom_" + codigo, "");
            if (!nomeSalvo.isEmpty()) {
                return primeiroNome(nomeSalvo);
            }
        }

        // 3. Procura no mapa padrão
        if (garconsPadrao.containsKey(codigo)) {
            return primeiroNome(garconsPadrao.get(codigo));
        }

        return "Garçom " + codigo;
    }

    public static void registrarGarcom(Context context, String codVend, String nome) {
        if (codVend == null || nome == null) return;
        String cod = codVend.trim();
        String n = nome.trim();
        garconsPadrao.put(cod, n);
        if (context != null) {
            SharedPreferences sp = context.getSharedPreferences(PREF_GARCONS, Context.MODE_PRIVATE);
            sp.edit().putString("garcom_" + cod, n).apply();
        }
    }

    private static String primeiroNome(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.trim().isEmpty()) return "";
        String[] partes = nomeCompleto.trim().split("\\s+");
        return partes.length > 0 ? partes[0] : nomeCompleto;
    }

    private static String normalizarCodigo(String cod) {
        if (cod == null) return "";
        String limpo = cod.trim();
        try {
            return String.valueOf(Integer.parseInt(limpo));
        } catch (Exception e) {
            return limpo;
        }
    }
}
