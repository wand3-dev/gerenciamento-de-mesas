package com.garcom.appmesas;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.List;

public class MonitorComandasService extends Service {

    public static final int NOTIF_SERVICE_ID = 9001;
    private static boolean servicoRodando = false;
    private MonitorComandasEngine monitorEngine;
    private MonitorComandasEngine.MonitorCallback monitorCallback;
    private PowerManager.WakeLock wakeLock;

    public static boolean isRodando() {
        return servicoRodando;
    }

    public static void iniciar(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, MonitorComandasService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    ContextCompat.startForegroundService(context, intent);
                } catch (Throwable t) {
                    try {
                        context.startService(intent);
                    } catch (Throwable ignored) {}
                }
            } else {
                context.startService(intent);
            }
        } catch (Throwable ignored) {}
    }

    public static void parar(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, MonitorComandasService.class);
            context.stopService(intent);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        servicoRodando = true;

        try {
            NotificationHelper.criarCanaisNotificacao(this);

            // Notificação persistente do Foreground Service
            Notification notif = NotificationHelper.buildMonitorNotification(
                    this,
                    "Monitor de Comandas Ativo",
                    "Sincronizando comandas a cada 10s em segundo plano"
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(NOTIF_SERVICE_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } catch (Throwable t) {
                    // Fallback para startForeground padrão caso DATA_SYNC tenha restrição de sistema
                    startForeground(NOTIF_SERVICE_ID, notif);
                }
            } else {
                startForeground(NOTIF_SERVICE_ID, notif);
            }
        } catch (Throwable t) {
            android.util.Log.e("MonitorComandasService", "Erro ao executar startForeground", t);
        }

        // Adquire WakeLock parcial de segurança para manter processamento de rede contínuo
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AppMesas:MonitorComandasWakeLock");
                wakeLock.acquire(10 * 60 * 1000L); // 10 minutos com renovação
            }
        } catch (Throwable ignored) {}

        try {
            monitorEngine = MonitorComandasEngine.getInstance(this);

            // Atualiza a notificação em tempo real quando as comandas forem atualizadas
            monitorCallback = new MonitorComandasEngine.MonitorCallback() {
                @Override
                public void onEstadoAlterado(EstadoServidor novoEstado, String mensagem) {
                    try {
                        atualizarNotificacao(novoEstado, monitorEngine.getListaComandas().size(), monitorEngine.getUltimaSincronizacao());
                    } catch (Throwable ignored) {}
                }

                @Override
                public void onComandasAtualizadas(List<ComandaCardModel> comandas, String ultimaSincronizacao) {
                    try {
                        atualizarNotificacao(monitorEngine.getEstado(), comandas != null ? comandas.size() : 0, ultimaSincronizacao);
                    } catch (Throwable ignored) {}
                }
            };
            monitorEngine.registrarCallback(monitorCallback);

            if (!monitorEngine.isAtivo()) {
                monitorEngine.ligarServidor();
            }
        } catch (Throwable t) {
            android.util.Log.e("MonitorComandasService", "Erro ao inicializar MonitorEngine no Service", t);
        }
    }

    private void atualizarNotificacao(EstadoServidor estado, int totalComandas, String ultimaSync) {
        try {
            String titulo;
            if (estado == EstadoServidor.ONLINE) {
                titulo = "● Monitor: " + totalComandas + " comanda(s) aberta(s)";
            } else if (estado == EstadoServidor.CONNECTING) {
                titulo = "◌ Conectando ao servidor DataSnap...";
            } else {
                titulo = "✕ Servidor Desconectado";
            }

            String texto = (ultimaSync != null && !ultimaSync.isEmpty())
                    ? "Verificação a cada 10s • Última: " + ultimaSync
                    : "Sincronizando a cada 10s em segundo plano";

            Notification notif = NotificationHelper.buildMonitorNotification(this, titulo, texto);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIF_SERVICE_ID, notif);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        servicoRodando = true;
        if (monitorEngine != null && !monitorEngine.isAtivo()) {
            monitorEngine.ligarServidor();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        servicoRodando = false;
        if (monitorEngine != null && monitorCallback != null) {
            monitorEngine.removerCallback(monitorCallback);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
