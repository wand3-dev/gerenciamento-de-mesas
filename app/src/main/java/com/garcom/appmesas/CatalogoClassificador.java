package com.garcom.appmesas;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CatalogoClassificador {
    private static CatalogoClassificador instance;
    private final Context context;
    private final Map<String, String> categoriasPorCodigo = new HashMap<>();
    private boolean catalogoCarregado = false;

    private CatalogoClassificador(Context context) {
        this.context = context.getApplicationContext();
        carregarCatalogoAssincrono();
    }

    public static synchronized CatalogoClassificador getInstance(Context context) {
        if (instance == null) {
            instance = new CatalogoClassificador(context);
        }
        return instance;
    }

    private void carregarCatalogoAssincrono() {
        new Thread(() -> {
            try {
                InputStream is = context.getAssets().open("produtos.json");
                int size = is.available();
                byte[] buffer = new byte[size];
                int read = is.read(buffer);
                is.close();

                if (read > 0) {
                    String json = new String(buffer, StandardCharsets.UTF_8);
                    JSONArray arr = new JSONArray(json);
                    synchronized (categoriasPorCodigo) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String c = obj.optString("c", "").trim();
                            String cat = obj.optString("cat", "").trim();
                            if (!c.isEmpty()) {
                                categoriasPorCodigo.put(c, cat);
                            }
                        }
                        catalogoCarregado = true;
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    /**
     * Determina se um item pertence à meta de Comidas/Produção Própria da casa:
     * - Cozinha, PFs, Refeições, Pratos, Almoço, Sopas
     * - Pizzas, Lanches, Salgados, Sanduíches, Pasteis
     * - Padaria e Confeitaria (Pães, Bolos, Doces)
     * - Sucos Naturais feitos na hora (Laranja, Melancia, Maracujá, etc.)
     *
     * Retorna false para produtos industrializados de terceiros:
     * - Refrigerantes (Coca, Guaraná, Fanta), Cervejas em lata/garrafa, Energéticos, Cigarros, Mercearia
     */
    public boolean isComidaOuProducao(String codProd, String descricao) {
        String descNorm = normalizarTexto(descricao);

        // 1. Regra prioritária para bebidas industrializadas de terceiros (NÃO é comida/meta)
        if (ehBebidaOuTerceiroIndustrializado(descNorm)) {
            return false;
        }

        // 2. Regra explícita para Sucos Naturais e Shakes feitos na hora (SÃO produção da casa/meta)
        if (ehSucoOuBebidaFeitaNaHora(descNorm)) {
            return true;
        }

        // 3. Consulta categoria oficial pelo código do catálogo se disponível
        String cat = "";
        synchronized (categoriasPorCodigo) {
            if (codProd != null) {
                cat = categoriasPorCodigo.get(codProd.trim());
            }
        }

        if (cat != null && !cat.isEmpty()) {
            String catNorm = normalizarTexto(cat);
            if (catNorm.contains("cozinha") || catNorm.contains("refeicoes") || catNorm.contains("sopas")
                    || catNorm.contains("pizzas") || catNorm.contains("lanches") || catNorm.contains("salgados")
                    || catNorm.contains("padaria") || catNorm.contains("paes") || catNorm.contains("confeitaria")
                    || catNorm.contains("bolos") || catNorm.contains("doces") || catNorm.contains("carnes")) {
                return true;
            }
            if (catNorm.contains("bebidas") || catNorm.contains("mercearia") || catNorm.contains("cervejas") || catNorm.contains("vinhos")) {
                return false;
            }
        }

        // 4. Fallback semântico inteligente por nome do produto
        return ehTermoComidaProducao(descNorm);
    }

    private boolean ehSucoOuBebidaFeitaNaHora(String d) {
        return d.contains("suco natural") || d.contains("suco de") || d.contains("suco ")
                || d.contains("vitamina") || d.contains("shake") || d.contains("milkshake")
                || d.contains("cafe expresso") || d.contains("cappuccino") || d.contains("acai");
    }

    private boolean ehBebidaOuTerceiroIndustrializado(String d) {
        return d.contains("coca cola") || d.contains("coca-cola") || d.contains("guarana")
                || d.contains("fanta") || d.contains("sprite") || d.contains("schweppes")
                || d.contains("red bull") || d.contains("monster") || d.contains("energetico")
                || d.contains("cerveja") || d.contains("chopp") || d.contains("budweiser")
                || d.contains("heineken") || d.contains("stella") || d.contains("brahma")
                || d.contains("skol") || d.contains("amstel") || d.contains("corona")
                || d.contains("cigarro") || d.contains("agua mineral") || d.contains("naturagua")
                || d.contains("halls") || d.contains("trident") || d.contains("mentos")
                || d.contains("fini") || d.contains("doritos") || d.contains("ruffles")
                || d.contains("cheetos") || d.contains("vinho");
    }

    private boolean ehTermoComidaProducao(String d) {
        return d.contains("pf ") || d.contains("prato") || d.contains("almoco") || d.contains("jantar")
                || d.contains("carne") || d.contains("frango") || d.contains("peixe") || d.contains("camarao")
                || d.contains("picanha") || d.contains("file") || d.contains("bife") || d.contains("omelete")
                || d.contains("tapioca") || d.contains("cuscuz") || d.contains("pastel") || d.contains("coxinha")
                || d.contains("croissant") || d.contains("sanduiche") || d.contains("hamburg") || d.contains("pizza")
                || d.contains("massa") || d.contains("lasanha") || d.contains("penne") || d.contains("spaghetti")
                || d.contains("sopa") || d.contains("caldo") || d.contains("salada") || d.contains("batata")
                || d.contains("pao") || d.contains("bolo") || d.contains("torta") || d.contains("pudim")
                || d.contains("brigadeiro") || d.contains("docinho") || d.contains("sobremesa") || d.contains("porcao");
    }

    private static String normalizarTexto(String s) {
        if (s == null) return "";
        String semAcentos = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT).trim();
    }
}
