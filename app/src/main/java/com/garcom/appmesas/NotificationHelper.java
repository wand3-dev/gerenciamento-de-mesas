package com.garcom.appmesas;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationHelper {

    public static final String CHANNEL_PEDIDOS = "channel_comandas_pedidos";
    public static final String CHANNEL_ATRASOS = "channel_alertas_atrasos";
    public static final String CHANNEL_MESAS   = "channel_atendimento_mesas";
    public static final String CHANNEL_MONITOR_SERVICE = "channel_monitor_service";

    private static final int ID_BASE_ATRASO = 2000;
    private static final int ID_BASE_LIBERADA = 3000;
    private static final AtomicInteger notifCounter = new AtomicInteger(100);
    private static boolean canaisCriados = false;
    private static final java.util.Map<String, Long> tempoUltimaTransferenciaNotificada = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Cria os canais de notificação para Android 8.0 (API 26) ou superior
     */
    public static synchronized void criarCanaisNotificacao(Context context) {
        if (canaisCriados || context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                // 1. Canal de Comandas e Pedidos (Alta prioridade)
                NotificationChannel canalPedidos = new NotificationChannel(
                        CHANNEL_PEDIDOS,
                        "Comandas e Novos Pedidos",
                        NotificationManager.IMPORTANCE_HIGH
                );
                canalPedidos.setDescription("Notificações de novos itens e comandas vinculadas");
                canalPedidos.enableLights(true);
                canalPedidos.setLightColor(Color.parseColor("#F97316"));
                canalPedidos.enableVibration(true);
                nm.createNotificationChannel(canalPedidos);

                // 2. Canal de Alertas de Atraso (+15m) (Prioridade máxima / Heads-up)
                NotificationChannel canalAtrasos = new NotificationChannel(
                        CHANNEL_ATRASOS,
                        "Alertas de Atraso (+15 min)",
                        NotificationManager.IMPORTANCE_HIGH
                );
                canalAtrasos.setDescription("Alertas urgentes de pedidos aguardando entrega há mais de 15 minutos");
                canalAtrasos.enableLights(true);
                canalAtrasos.setLightColor(Color.parseColor("#EF4444"));
                canalAtrasos.enableVibration(true);
                canalAtrasos.setVibrationPattern(new long[]{0, 500, 250, 500});
                nm.createNotificationChannel(canalAtrasos);

                // 3. Canal de Atendimento e Mesas
                NotificationChannel canalMesas = new NotificationChannel(
                        CHANNEL_MESAS,
                        "Atendimento e Mesas",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                canalMesas.setDescription("Atualizações de fechamento e liberação de mesas");
                nm.createNotificationChannel(canalMesas);

                // 4. Canal de Monitoramento Contínuo em Segundo Plano (Silencioso)
                NotificationChannel canalMonitor = new NotificationChannel(
                        CHANNEL_MONITOR_SERVICE,
                        "Serviço de Monitoramento de Mesas",
                        NotificationManager.IMPORTANCE_LOW
                );
                canalMonitor.setDescription("Notificação contínua para manter a sincronização de mesas ativa");
                canalMonitor.setShowBadge(false);
                canalMonitor.enableLights(false);
                canalMonitor.enableVibration(false);
                nm.createNotificationChannel(canalMonitor);

                canaisCriados = true;
            }
        }
    }

    /**
     * Verifica se o app possui permissão para emitir notificações
     */
    public static boolean podeEnviarNotificacoes(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Notifica novo item ou comanda vinculada no sistema Android (barra de status) e toca sino/vibração
     */
    public static void notificarNovoItem(Context context, String titulo, String mensagem) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();

        // Toca o som do sino de novo item e vibra 1 vez
        tocarAudio(appContext, R.raw.sino_pedido, 1);
        vibrar(appContext, new long[]{0, 350});

        // Envia notificação na barra de status
        criarCanaisNotificacao(appContext);
        if (!podeEnviarNotificacoes(appContext)) return;

        try {
            // Tenta identificar o número da mesa a partir do título ou mensagem
            int numeroMesa = extrairNumeroMesa(titulo + " " + mensagem);

            Intent intent;
            if (numeroMesa > 0) {
                intent = new Intent(appContext, MesaDetailActivity.class);
                intent.putExtra("NUMERO_MESA", numeroMesa);
            } else {
                intent = new Intent(appContext, MainActivity.class);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            int reqCode = notifCounter.incrementAndGet();
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    appContext,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_PEDIDOS)
                    .setSmallIcon(R.drawable.ic_notification_bell)
                    .setContentTitle(titulo)
                    .setContentText(mensagem)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(mensagem))
                    .setColor(Color.parseColor("#F97316"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            NotificationManagerCompat.from(appContext).notify(reqCode, builder.build());
        } catch (Exception ignored) {}
    }

    /**
     * Notificação específica de comanda vinculada a uma mesa
     */
    public static void notificarComandaVinculada(Context context, int numeroMesa, String comanda, int totalItens) {
        String titulo = String.format(Locale.getDefault(), "🥐 Mesa %02d: Comanda #%s Vinculada", numeroMesa, comanda);
        String msg = totalItens + " item(ns) importado(s) e aguardando entrega.";
        AlertaHistoricoManager.registrarAlerta(context, new AlertaHistorico("COMANDA_VINCULADA", titulo, msg, numeroMesa, comanda));
        notificarNovoItem(context, titulo, msg);
    }

    /**
     * Alerta urgente (Heads-up) de pedido atrasado há mais de 15 minutos com botão direto de entrega e adiar
     */
    public static void alertarPedidoAtrasado15Min(Context context, int numeroMesa) {
        alertarPedidoAtrasado15Min(context, numeroMesa, "", null, "há mais de 15 minutos", null);
    }

    public static void alertarPedidoAtrasado15Min(Context context, int numeroMesa, String descricaoItem, String tempoEsperado, String itemId) {
        alertarPedidoAtrasado15Min(context, numeroMesa, "", descricaoItem, tempoEsperado, itemId);
    }

    public static void alertarPedidoAtrasado15Min(Context context, int numeroMesa, String comanda, String descricaoItem, String tempoEsperado, String itemId) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();

        // Toca o aviso sonoro animado duas vezes seguidas
        tocarAudio(appContext, R.raw.alerta_atrasado, 2);
        // Vibra duas vezes: espera 0ms, vibra 500ms, pausa 250ms, vibra 500ms
        vibrar(appContext, new long[]{0, 500, 250, 500});

        criarCanaisNotificacao(appContext);
        if (!podeEnviarNotificacoes(appContext)) return;

        try {
            Intent intent = new Intent(appContext, MesaDetailActivity.class);
            intent.putExtra("NUMERO_MESA", numeroMesa);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    appContext,
                    ID_BASE_ATRASO + numeroMesa,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Ação 1: [✓ ENTREGAR] sem precisar sair de outro app
            Intent actionIntent = new Intent(appContext, PedidoActionReceiver.class);
            actionIntent.setAction(PedidoActionReceiver.ACTION_MARCAR_ENTREGUE);
            actionIntent.putExtra(PedidoActionReceiver.EXTRA_NUMERO_MESA, numeroMesa);
            if (itemId != null && !itemId.isEmpty()) {
                actionIntent.putExtra(PedidoActionReceiver.EXTRA_ITEM_ID, itemId);
            }
            PendingIntent actionPendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    ID_BASE_ATRASO + numeroMesa,
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Ação 2: [⏱️ +2 MINUTOS] (Adiar alerta por 2 min)
            Intent adiarIntent = new Intent(appContext, PedidoActionReceiver.class);
            adiarIntent.setAction(PedidoActionReceiver.ACTION_ADIAR_2_MINUTOS);
            adiarIntent.putExtra(PedidoActionReceiver.EXTRA_NUMERO_MESA, numeroMesa);
            if (comanda != null && !comanda.isEmpty()) {
                adiarIntent.putExtra(PedidoActionReceiver.EXTRA_COMANDA, comanda);
            }
            if (itemId != null && !itemId.isEmpty()) {
                adiarIntent.putExtra(PedidoActionReceiver.EXTRA_ITEM_ID, itemId);
            }
            PendingIntent adiarPendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    ID_BASE_ATRASO + 5000 + numeroMesa,
                    adiarIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            String titulo;
            if (comanda != null && !comanda.isEmpty() && descricaoItem != null && !descricaoItem.isEmpty()) {
                titulo = String.format(Locale.getDefault(), "🚨 Mesa %02d (CMD #%s): %s (+15m)", numeroMesa, comanda, descricaoItem);
            } else if (descricaoItem != null && !descricaoItem.isEmpty()) {
                titulo = String.format(Locale.getDefault(), "🚨 Mesa %02d: %s (+15m)", numeroMesa, descricaoItem);
            } else {
                titulo = String.format(Locale.getDefault(), "🚨 Mesa %02d: Pedido Atrasado (+15m)", numeroMesa);
            }

            String texto = (descricaoItem != null && !descricaoItem.isEmpty())
                    ? "Esperando entrega " + tempoEsperado + "! Toque para abrir, Entregar ou Adiar:"
                    : "Há item(ns) aguardando entrega há mais de 15 minutos! Toque para ver os detalhes.";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ATRASOS)
                    .setSmallIcon(R.drawable.ic_notification_alert)
                    .setContentTitle(titulo)
                    .setContentText(texto)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                    .setColor(Color.parseColor("#DC2626"))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setFullScreenIntent(pendingIntent, true)
                    .addAction(R.drawable.ic_notification_bell, "✓ ENTREGAR", actionPendingIntent)
                    .addAction(R.drawable.ic_notification_alert, "⏱️ +2 MIN", adiarPendingIntent);

            NotificationManagerCompat.from(appContext).notify(ID_BASE_ATRASO + numeroMesa, builder.build());
            AlertaHistoricoManager.registrarAlerta(appContext, new AlertaHistorico("ATRASO", titulo, texto, numeroMesa, comanda));

            // Tenta puxar a tela da mesa para a frente diretamente se permitido pelo sistema
            try {
                appContext.startActivity(intent);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    /**
     * Constrói a notificação do serviço contínuo em primeiro plano (Foreground Service)
     */
    public static android.app.Notification buildMonitorNotification(Context context, String titulo, String texto) {
        criarCanaisNotificacao(context);
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 9999, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, CHANNEL_MONITOR_SERVICE)
                .setSmallIcon(R.drawable.ic_notification_bell)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setColor(Color.parseColor("#059669"))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    /**
     * Dispara um alerta heads-up de demonstração/teste
     */
    public static void dispararAlertaTesteHeadsUp(Context context) {
        alertarPedidoAtrasado15Min(context, 7, "2x Café Pingado", "há 16 min", "");
    }

    /**
     * Notifica quando uma mesa foi fechada e liberada
     */
    public static void notificarMesaLiberada(Context context, int numeroMesa, String totalConta) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();

        criarCanaisNotificacao(appContext);
        if (!podeEnviarNotificacoes(appContext)) return;

        try {
            Intent intent = new Intent(appContext, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    appContext,
                    ID_BASE_LIBERADA + numeroMesa,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            String titulo = String.format(Locale.getDefault(), "✓ Mesa %02d Liberada", numeroMesa);
            String texto = "Atendimento finalizado com sucesso. Total: " + totalConta;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_MESAS)
                    .setSmallIcon(R.drawable.ic_notification_bell)
                    .setContentTitle(titulo)
                    .setContentText(texto)
                    .setColor(Color.parseColor("#10B981"))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            NotificationManagerCompat.from(appContext).notify(ID_BASE_LIBERADA + numeroMesa, builder.build());
            AlertaHistoricoManager.registrarAlerta(appContext, new AlertaHistorico("MESA_LIBERADA", titulo, texto, numeroMesa, ""));
        } catch (Exception ignored) {}
    }

    /**
     * Cancela a notificação de pedido atrasado de uma mesa (por exemplo quando o item for entregue)
     */
    public static void cancelarAlertaAtraso(Context context, int numeroMesa) {
        if (context == null) return;
        try {
            NotificationManagerCompat.from(context.getApplicationContext()).cancel(ID_BASE_ATRASO + numeroMesa);
        } catch (Exception ignored) {}
    }

    /**
     * Notifica quando um novo pedido é lançado em uma mesa/comanda monitorada
     */
    public static void notificarNovoPedido(Context context, int numeroMesa, String comanda, String descricaoItem, int quantidade) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();

        criarCanaisNotificacao(appContext);
        if (!podeEnviarNotificacoes(appContext)) return;

        try {
            Intent intent = new Intent(appContext, MesaDetailActivity.class);
            intent.putExtra("NUMERO_MESA", numeroMesa);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            int notifId = notifCounter.incrementAndGet();
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    appContext,
                    notifId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            String titulo = (comanda != null && !comanda.isEmpty())
                    ? String.format(Locale.getDefault(), "🔔 Novo Pedido - Mesa %02d (CMD #%s)", numeroMesa, comanda)
                    : String.format(Locale.getDefault(), "🔔 Novo Pedido - Mesa %02d", numeroMesa);

            String texto = quantidade + "x " + (descricaoItem != null ? descricaoItem : "Item");

            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_PEDIDOS)
                    .setSmallIcon(R.drawable.ic_notification_bell)
                    .setContentTitle(titulo)
                    .setContentText(texto)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                    .setColor(Color.parseColor("#F97316"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            NotificationManagerCompat.from(appContext).notify(notifId, builder.build());
            AlertaHistoricoManager.registrarAlerta(appContext, new AlertaHistorico("NOVO_PEDIDO", titulo, texto, numeroMesa, comanda));

            // Toca aviso sonoro e vibra
            tocarAudio(appContext, R.raw.bip_pedido, 1);
            vibrar(appContext, new long[]{0, 250, 150, 250});
        } catch (Exception ignored) {}
    }

    /**
     * Notifica quando uma comanda mudou de mesa no servidor (transferência de mesa)
     */
    public static void notificarMudancaMesa(Context context, String comanda, int mesaOrigem, int mesaDestino) {
        if (context == null || comanda == null || comanda.trim().isEmpty() || mesaOrigem == mesaDestino) return;
        final String cmd = comanda.trim();
        final Context appContext = context.getApplicationContext();

        // Evita repetições excessivas da mesma transferência nos últimos 3 minutos
        String chave = cmd + "_" + mesaOrigem + "->" + mesaDestino;
        Long ultimoTempo = tempoUltimaTransferenciaNotificada.get(chave);
        long agora = System.currentTimeMillis();
        if (ultimoTempo != null && (agora - ultimoTempo) < 180000) {
            return; // Já notificado recentemente, ignora repetição!
        }
        tempoUltimaTransferenciaNotificada.put(chave, agora);

        criarCanaisNotificacao(appContext);
        if (!podeEnviarNotificacoes(appContext)) return;

        try {
            Intent intent = new Intent(appContext, MesaDetailActivity.class);
            intent.putExtra("NUMERO_MESA", mesaDestino);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // Usa ID determinístico por comanda para atualizar a mesma notificação em vez de empilhar
            int notifId = 4000 + Math.abs(cmd.hashCode() % 1000);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    appContext,
                    notifId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            String titulo = String.format(Locale.getDefault(), "🔄 Comanda #%s Transferida!", cmd);
            String texto = String.format(Locale.getDefault(), "Atenção: transferida da Mesa %02d ➔ Mesa %02d!", mesaOrigem, mesaDestino);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ATRASOS)
                    .setSmallIcon(R.drawable.ic_notification_alert)
                    .setContentTitle(titulo)
                    .setContentText(texto)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(texto + "\nToque para abrir a Mesa " + mesaDestino))
                    .setColor(Color.parseColor("#3B82F6"))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setFullScreenIntent(pendingIntent, true);

            NotificationManagerCompat.from(appContext).notify(notifId, builder.build());
            AlertaHistoricoManager.registrarAlerta(appContext, new AlertaHistorico("TRANSFERENCIA", titulo, texto, mesaDestino, cmd));

            // Toca som de sino duplo e vibração forte de alerta
            tocarAudio(appContext, R.raw.sino_pedido, 2);
            vibrar(appContext, new long[]{0, 400, 200, 400, 200, 400});
        } catch (Exception ignored) {}
    }

    private static int extrairNumeroMesa(String texto) {
        if (texto == null) return -1;
        try {
            Pattern pattern = Pattern.compile("Mesa\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(texto);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static void tocarAudio(Context context, int rawResId, int vezes) {
        if (context == null) return;
        tocarAudioSequencial(context.getApplicationContext(), rawResId, vezes, 1);
    }

    private static void tocarAudioSequencial(Context appContext, int rawResId, int totalVezes, int vezAtual) {
        try {
            MediaPlayer mp = MediaPlayer.create(appContext, rawResId);
            if (mp != null) {
                mp.setOnCompletionListener(player -> {
                    try {
                        player.release();
                    } catch (Exception ignored) {}

                    if (vezAtual < totalVezes) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            tocarAudioSequencial(appContext, rawResId, totalVezes, vezAtual + 1);
                        }, 250); // intervalo de 250ms entre os toques
                    }
                });
                mp.start();
            }
        } catch (Exception ignored) {}
    }

    private static void vibrar(Context context, long[] pattern) {
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    v.vibrate(pattern, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void tocarSinoNovaComanda(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        android.content.SharedPreferences sp = appContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        boolean somAtivo = sp.getBoolean("som_nova_comanda", true);
        if (somAtivo) {
            tocarAudio(appContext, R.raw.sino_pedido, 1);
            vibrar(appContext, new long[]{0, 300, 150, 300});
        }
    }
}
