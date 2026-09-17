package com.garcom.appmesas;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MonitorMesasService extends Service {

    public static final int NOTIF_SERVICE_ID = 9001;
    private static final long INTERVALO_SYNC_MS = 10000; // 10 segundos

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnableSync;
    private MesaManager manager;
    private ServerComandasClient serverClient;
    private boolean sincronizando = false;
    private static boolean servicoRodando = false;

    public static boolean isRodando() {
        return servicoRodando;
    }

    public static void iniciar(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, MonitorMesasService.class);
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        servicoRodando = true;
        manager = MesaManager.getInstance(this);
        serverClient = ServerComandasClient.getInstance(this);

        // Inicia em primeiro plano imediatamente com notificação discreta
        Notification notif = NotificationHelper.buildMonitorNotification(
                this,
                "AppMesas Ativo (Monitorando)",
                "Monitorando mesas e atrasos em segundo plano"
        );
        startForeground(NOTIF_SERVICE_ID, notif);

        iniciarLoopSincronizacao();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        servicoRodando = true;
        atualizarStatusNotificacao();
        return START_STICKY; // Reinicia automaticamente se o sistema matar
    }

    private void iniciarLoopSincronizacao() {
        runnableSync = new Runnable() {
            @Override
            public void run() {
                sincronizarMesasServidor();
                verificarAlertasAtraso();
                atualizarStatusNotificacao();
                handler.postDelayed(this, INTERVALO_SYNC_MS);
            }
        };
        handler.post(runnableSync);
    }

    private void sincronizarMesasServidor() {
        if (sincronizando || manager == null || serverClient == null) return;

        List<Mesa> mesasAbertas = new ArrayList<>();
        for (Mesa m : manager.getMesas()) {
            if (m.isAberta()) mesasAbertas.add(m);
        }
        if (mesasAbertas.isEmpty()) return;

        sincronizando = true;
        java.util.concurrent.atomic.AtomicInteger pendentes = new java.util.concurrent.atomic.AtomicInteger(mesasAbertas.size());

        for (Mesa mesa : mesasAbertas) {
            final int numMesa = mesa.getNumero();
            final List<String> comandasMonitoradas = mesa.getComandasUnicas();
            if (comandasMonitoradas.isEmpty()) {
                if (pendentes.decrementAndGet() <= 0) sincronizando = false;
                continue;
            }

            serverClient.buscarItensDaMesa(numMesa, new ServerComandasClient.OnItensMesaLoadedListener() {
                @Override
                public void onSuccess(List<JSONObject> itens) {
                    boolean houveNovidade = false;
                    for (JSONObject objServidor : itens) {
                        PedidoItem itemServ = PedidoItem.fromServerJson(objServidor);
                        String comandaItem = itemServ.getComanda();
                        if (!comandasMonitoradas.contains(comandaItem)) continue;

                        boolean encontrado = false;
                        for (PedidoItem local : mesa.getPedidos()) {
                            if (local.getId().equals(itemServ.getId())) {
                                encontrado = true;
                                break;
                            }
                        }
                        if (!encontrado) {
                            mesa.adicionarPedido(itemServ);
                            houveNovidade = true;
                        }
                    }

                    if (houveNovidade) {
                        manager.salvarMesas(MonitorMesasService.this);
                        sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
                        NotificationHelper.notificarNovoItem(
                                MonitorMesasService.this,
                                "Mesa " + numMesa + ": Novo Pedido!",
                                "Novos itens foram vinculados à mesa " + numMesa
                        );
                    }

                    if (pendentes.decrementAndGet() <= 0) sincronizando = false;
                }

                @Override
                public void onEmpty() {
                    if (pendentes.decrementAndGet() <= 0) sincronizando = false;
                }

                @Override
                public void onError(String error) {
                    if (pendentes.decrementAndGet() <= 0) sincronizando = false;
                }
            });
        }
    }

    private void verificarAlertasAtraso() {
        if (manager == null) return;
        boolean houveAlteracao = false;

        for (Mesa mesa : manager.getMesas()) {
            if (!mesa.isAberta() || mesa.getPedidos() == null) continue;

            // Agrupa por comanda para alertar "uma vez por comanda apenas"
            Set<String> comandasAlertadasNestaMesa = new HashSet<>();

            for (PedidoItem item : mesa.getPedidos()) {
                if (!item.isEntregue() && item.isAtrasado()) {
                    String cmd = item.getComanda() != null ? item.getComanda().trim() : "";
                    // Só alerta se essa comanda ainda não foi alertada
                    if (!item.isAlertadoAtraso() && !comandasAlertadasNestaMesa.contains(cmd)) {
                        comandasAlertadasNestaMesa.add(cmd);

                        // Marca todos os itens pendentes desta mesma comanda como alertados
                        for (PedidoItem it : mesa.getPedidos()) {
                            if (!it.isEntregue() && cmd.equals(it.getComanda() != null ? it.getComanda().trim() : "")) {
                                it.setAlertadoAtraso(true);
                            }
                        }
                        houveAlteracao = true;

                        String desc = item.getQuantidade() + "x " + item.getDescricao();
                        String tempo = item.getTempoDecorridoFormatado();
                        NotificationHelper.alertarPedidoAtrasado15Min(
                                this,
                                mesa.getNumero(),
                                cmd,
                                desc,
                                tempo,
                                item.getId()
                        );
                    }
                }
            }
        }

        if (houveAlteracao) {
            manager.salvarMesas(this);
            sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
        }
    }

    private void atualizarStatusNotificacao() {
        if (manager == null) return;
        int abertas = 0;
        int pendentesBebidas = 0;
        int pendentesComidas = 0;
        int totalAtrasados = 0;

        for (Mesa m : manager.getMesas()) {
            if (m.isAberta() && m.getPedidos() != null) {
                abertas++;
                for (PedidoItem p : m.getPedidos()) {
                    if (!p.isEntregue()) {
                        if (p.isBebida()) pendentesBebidas++;
                        else pendentesComidas++;
                        if (p.isAtrasado()) totalAtrasados++;
                    }
                }
            }
        }

        String texto;
        if (totalAtrasados > 0) {
            texto = "🚨 " + totalAtrasados + " atraso(s)! | ☕ " + pendentesBebidas + " beb. | 🍳 " + pendentesComidas + " com.";
        } else if (abertas > 0) {
            texto = abertas + " mesas abertas | ☕ " + pendentesBebidas + " bebidas | 🍳 " + pendentesComidas + " comidas pendentes";
        } else {
            texto = "Nenhuma mesa aberta no momento";
        }

        Notification notif = NotificationHelper.buildMonitorNotification(
                this,
                "AppMesas Ativo (Monitorando)",
                texto
        );
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_SERVICE_ID, notif);
        }
    }

    @Override
    public void onDestroy() {
        servicoRodando = false;
        if (handler != null && runnableSync != null) {
            handler.removeCallbacks(runnableSync);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
