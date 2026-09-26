package com.garcom.appmesas;

import android.app.Application;
import android.util.Log;

public class AppMesasApplication extends Application {

    private static final String TAG = "AppMesasApp";
    private static AppMesasApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Instala tratamento global para proteger o app e registrar qualquer erro inesperado
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                Log.e(TAG, "Exceção não tratada capturada na thread: " + thread.getName(), throwable);
                getSharedPreferences("app_crash_logs", MODE_PRIVATE)
                    .edit()
                    .putString("ultimo_crash_msg", throwable != null ? throwable.getMessage() : "")
                    .putString("ultimo_crash_trace", throwable != null ? Log.getStackTraceString(throwable) : "")
                    .putLong("ultimo_crash_time", System.currentTimeMillis())
                    .apply();
            } catch (Throwable ignored) {}

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });

        // Cria os canais de notificação do sistema com segurança
        try {
            NotificationHelper.criarCanaisNotificacao(this);
        } catch (Throwable ignored) {}
    }

    public static AppMesasApplication getInstance() {
        return instance;
    }
}
