package com.garcom.appmesas;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class Mesa {
    private int numero;
    private boolean aberta;
    private long aberturaTimestamp;
    private List<PedidoItem> pedidos;

    public Mesa(int numero) {
        this.numero = numero;
        this.aberta = false;
        this.aberturaTimestamp = 0;
        this.pedidos = new ArrayList<>();
    }

    public Mesa(JSONObject obj) throws JSONException {
        this.numero = obj.optInt("numero", 1);
        this.aberta = obj.optBoolean("aberta", false);
        this.aberturaTimestamp = obj.optLong("aberturaTimestamp", 0);
        this.pedidos = new ArrayList<>();
        JSONArray arr = obj.optJSONArray("pedidos");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                this.pedidos.add(new PedidoItem(arr.getJSONObject(i)));
            }
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("numero", numero);
        obj.put("aberta", aberta);
        obj.put("aberturaTimestamp", aberturaTimestamp);
        JSONArray arr = new JSONArray();
        for (PedidoItem item : pedidos) {
            arr.put(item.toJSON());
        }
        obj.put("pedidos", arr);
        return obj;
    }

    public int getNumero() { return numero; }
    public boolean isAberta() { return aberta; }
    public void setAberta(boolean aberta) {
        this.aberta = aberta;
        if (aberta && aberturaTimestamp == 0) {
            aberturaTimestamp = System.currentTimeMillis();
        } else if (!aberta) {
            aberturaTimestamp = 0;
            pedidos.clear();
        }
    }

    public long getAberturaTimestamp() { return aberturaTimestamp; }
    public List<PedidoItem> getPedidos() { return pedidos; }

    public void adicionarPedido(PedidoItem item) {
        if (!aberta) {
            aberta = true;
            aberturaTimestamp = System.currentTimeMillis();
        }
        pedidos.add(0, item);
    }

    public void removerPedido(String id) {
        for (int i = 0; i < pedidos.size(); i++) {
            if (pedidos.get(i).getId().equals(id)) {
                pedidos.remove(i);
                break;
            }
        }
        if (pedidos.isEmpty()) {
            aberta = false;
            aberturaTimestamp = 0;
        }
    }

    public int contarPedidosDaComanda(String comanda) {
        int total = 0;
        for (PedidoItem pedido : pedidos) {
            if (comanda.equals(pedido.getComanda())) total++;
        }
        return total;
    }

    public void alterarComanda(String comandaAtual, String novaComanda) {
        for (PedidoItem pedido : pedidos) {
            if (comandaAtual.equals(pedido.getComanda())) pedido.setComanda(novaComanda);
        }
    }

    public void removerComanda(String comanda) {
        for (int i = pedidos.size() - 1; i >= 0; i--) {
            if (comanda.equals(pedidos.get(i).getComanda())) pedidos.remove(i);
        }
        if (pedidos.isEmpty()) {
            aberta = false;
            aberturaTimestamp = 0;
        }
    }

    public void marcarComandaEntregue(String comanda) {
        for (PedidoItem pedido : pedidos) {
            if (comanda.equals(pedido.getComanda())) pedido.setEntregue(true);
        }
    }

    public void transferirPedidosPara(Mesa destino) {
        if (destino == null || destino == this || pedidos.isEmpty()) return;

        if (!destino.aberta) {
            destino.aberta = true;
            destino.aberturaTimestamp = aberturaTimestamp > 0
                    ? aberturaTimestamp
                    : System.currentTimeMillis();
        }
        destino.pedidos.addAll(0, pedidos);
        pedidos.clear();
        aberta = false;
        aberturaTimestamp = 0;
    }

    public void transferirComandaPara(Mesa destino, String comanda) {
        if (destino == null || destino == this || pedidos.isEmpty()) return;

        List<PedidoItem> pedidosTransferidos = new ArrayList<>();
        for (int i = pedidos.size() - 1; i >= 0; i--) {
            PedidoItem pedido = pedidos.get(i);
            if (comanda.equals(pedido.getComanda())) {
                pedidosTransferidos.add(0, pedido);
                pedidos.remove(i);
            }
        }
        if (pedidosTransferidos.isEmpty()) return;

        if (!destino.aberta) {
            destino.aberta = true;
            destino.aberturaTimestamp = aberturaTimestamp > 0
                    ? aberturaTimestamp
                    : System.currentTimeMillis();
        }
        destino.pedidos.addAll(0, pedidosTransferidos);
        if (pedidos.isEmpty()) {
            aberta = false;
            aberturaTimestamp = 0;
        }
    }

    public double getTotalValor() {
        double total = 0.0;
        for (PedidoItem item : pedidos) {
            total += item.getValorTotalNumerico();
        }
        return total;
    }

    public String getTotalValorFormatado() {
        double total = getTotalValor();
        return String.format(java.util.Locale.GERMANY, "R$ %.2f", total);
    }

    public List<String> getComandasUnicas() {
        List<String> comandas = new ArrayList<>();
        for (PedidoItem item : pedidos) {
            String c = item.getComanda();
            if (c != null && !c.trim().isEmpty() && !comandas.contains(c.trim())) {
                comandas.add(c.trim());
            }
        }
        return comandas;
    }

    public String getTempoMesaFormatado() {
        if (!aberta || aberturaTimestamp == 0) return "--:--";
        long diffMs = System.currentTimeMillis() - aberturaTimestamp;
        long diffMin = diffMs / (1000 * 60);
        long diffHoras = diffMin / 60;

        if (diffMin < 1) {
            return "Aberta agora";
        } else if (diffHoras < 1) {
            return "Aberta há " + diffMin + " min";
        } else {
            long minRest = diffMin % 60;
            return "Aberta há " + diffHoras + "h " + minRest + "m";
        }
    }

    public double getValorTotal() {
        double total = 0.0;
        for (PedidoItem item : pedidos) {
            total += item.getValorTotalNumerico();
        }
        return total;
    }

    public String getValorTotalFormatado() {
        return String.format(java.util.Locale.GERMANY, "R$ %.2f", getValorTotal());
    }
}
