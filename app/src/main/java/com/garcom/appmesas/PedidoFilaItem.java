package com.garcom.appmesas;

public class PedidoFilaItem {
    private Mesa mesa;
    private PedidoItem pedido;

    public PedidoFilaItem(Mesa mesa, PedidoItem pedido) {
        this.mesa = mesa;
        this.pedido = pedido;
    }

    public Mesa getMesa() { return mesa; }
    public PedidoItem getPedido() { return pedido; }

    public long getTimestamp() {
        return pedido.getTimestamp();
    }
}
