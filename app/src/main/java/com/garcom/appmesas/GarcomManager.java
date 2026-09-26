package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public class GarcomManager {
    private static final String PREF_GARCONS = "garcons_mapeamento_prefs";
    private static final Map<String, String> garconsPadrao = new HashMap<>();

    static {
        // Mapeamentos oficiais dos garçons
        garconsPadrao.put("40", "Kamila");
        garconsPadrao.put("65", "Geovana");
        garconsPadrao.put("200", "Cauã");
        garconsPadrao.put("217", "Miguel");
        garconsPadrao.put("223", "Wanderson");
        garconsPadrao.put("224", "Lucas");
    }

    public static String getNomeGarcom(Context context, String codVend) {
        if (codVend == null || codVend.trim().isEmpty()) {
            return "";
        }
        try {
            String codigo = normalizarCodigo(codVend);
            if (codigo.isEmpty()) return "";

            // 1. Se for o usuário atualmente logado
            if (context != null) {
                try {
                    String codLogado = SessionManager.getCodFunc(context);
                    String nomeLogado = SessionManager.getNomeUsuario(context);
                    if (!codLogado.isEmpty() && normalizarCodigo(codLogado).equals(codigo) && !nomeLogado.isEmpty()) {
                        return primeiroNome(nomeLogado);
                    }
                } catch (Exception ignored) {}

                // 2. Procura nas preferências de garçons cadastrados
                try {
                    SharedPreferences sp = context.getSharedPreferences(PREF_GARCONS, Context.MODE_PRIVATE);
                    String nomeSalvo = sp.getString("garcom_" + codigo, "");
                    if (!nomeSalvo.isEmpty()) {
                        return primeiroNome(nomeSalvo);
                    }
                } catch (Exception ignored) {}
            }

            // 3. Procura no mapa padrão
            if (garconsPadrao.containsKey(codigo)) {
                return primeiroNome(garconsPadrao.get(codigo));
            }

            return "Garçom " + codigo;
        } catch (Exception e) {
            return "Garçom " + codVend;
        }
    }

    public static void registrarGarcom(Context context, String codVend, String nome) {
        if (codVend == null || nome == null) return;
        try {
            String cod = normalizarCodigo(codVend);
            String n = nome.trim();
            garconsPadrao.put(cod, n);
            if (context != null) {
                SharedPreferences sp = context.getSharedPreferences(PREF_GARCONS, Context.MODE_PRIVATE);
                sp.edit().putString("garcom_" + cod, n).apply();
            }
        } catch (Exception ignored) {}
    }

    private static String primeiroNome(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.trim().isEmpty()) return "";
        try {
            String[] partes = nomeCompleto.trim().split("\\s+");
            return partes.length > 0 ? partes[0] : nomeCompleto;
        } catch (Exception e) {
            return nomeCompleto;
        }
    }

    private static String normalizarCodigo(String cod) {
        if (cod == null) return "";
        String limpo = cod.trim();
        try {
            if (limpo.contains(".")) {
                double d = Double.parseDouble(limpo);
                return String.valueOf((long) d);
            }
            return String.valueOf(Long.parseLong(limpo));
        } catch (Exception e) {
            return limpo;
        }
    }
}
