package com.garcom.appmesas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class PedidoActionReceiver extends BroadcastReceiver {
    public static final String ACTION_MARCAR_ENTREGUE = "com.garcom.appmesas.ACTION_MARCAR_ENTREGUE";
    public static final String ACTION_ADIAR_2_MINUTOS = "com.garcom.appmesas.ACTION_ADIAR_2_MINUTOS";
    public static final String ACTION_TESTE_ALERTA = "com.garcom.appmesas.ACTION_TESTE_ALERTA";
    public static final String EXTRA_NUMERO_MESA = "extra_numero_mesa";
    public static final String EXTRA_ITEM_ID = "extra_item_id";
    public static final String EXTRA_COMANDA = "extra_comanda";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) return;
        String action = intent.getAction();

        if (ACTION_TESTE_ALERTA.equals(action)) {
            NotificationHelper.dispararAlertaTesteHeadsUp(context);
            return;
        }

        if (ACTION_ADIAR_2_MINUTOS.equals(action)) {
            int numeroMesa = intent.getIntExtra(EXTRA_NUMERO_MESA, -1);
            String comanda = intent.getStringExtra(EXTRA_COMANDA);
            String itemId = intent.getStringExtra(EXTRA_ITEM_ID);

            MesaManager manager = MesaManager.getInstance(context);
            Mesa mesa = manager.getMesa(numeroMesa);

            if (mesa != null && mesa.getPedidos() != null) {
                int adiados = 0;
                for (PedidoItem p : mesa.getPedidos()) {
                    if (!p.isEntregue()) {
                        boolean match = false;
                        if (comanda != null && !comanda.isEmpty() && comanda.equals(p.getComanda())) {
                            match = true;
                        } else if (itemId != null && !itemId.isEmpty() && itemId.equals(p.getId())) {
                            match = true;
                        } else if ((comanda == null || comanda.isEmpty()) && (itemId == null || itemId.isEmpty())) {
                            match = true;
                        }
                        if (match) {
                            p.adiarMaisDoisMinutos();
                            adiados++;
                        }
                    }
                }

                manager.salvarMesas(context);
                NotificationHelper.cancelarAlertaAtraso(context, numeroMesa);
                VibrationHelper.vibrateTick(context);
                context.sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));

                String msg = (comanda != null && !comanda.isEmpty())
                        ? "⏱️ +2 minutos adicionados para a Comanda #" + comanda + " (Mesa " + numeroMesa + ")!"
                        : "⏱️ +2 minutos adicionados para a Mesa " + numeroMesa + "!";
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (ACTION_MARCAR_ENTREGUE.equals(action)) {
            int numeroMesa = intent.getIntExtra(EXTRA_NUMERO_MESA, -1);
            String itemId = intent.getStringExtra(EXTRA_ITEM_ID);

            MesaManager manager = MesaManager.getInstance(context);
            Mesa mesa = manager.getMesa(numeroMesa);

            if (mesa != null && mesa.getPedidos() != null) {
                boolean marcou = false;
                if (itemId != null && !itemId.isEmpty()) {
                    for (PedidoItem p : mesa.getPedidos()) {
                        if (itemId.equals(p.getId())) {
                            p.setEntregue(true);
                            marcou = true;
                            break;
                        }
                    }
                }

                if (!marcou) {
                    // Marca todos os itens pendentes da mesa como entregues
                    for (PedidoItem p : mesa.getPedidos()) {
                        p.setEntregue(true);
                    }
                }

                manager.salvarMesas(context);
                NotificationHelper.cancelarAlertaAtraso(context, numeroMesa);
                VibrationHelper.vibrateSuccess(context);
                context.sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));

                Toast.makeText(context, "✓ Mesa " + numeroMesa + ": Marcado como entregue!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
