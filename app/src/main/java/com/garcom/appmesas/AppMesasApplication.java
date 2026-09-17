package com.garcom.appmesas;

import android.app.Application;

public class AppMesasApplication extends Application {

    private static AppMesasApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Cria os canais de notificação do sistema
        NotificationHelper.criarCanaisNotificacao(this);
    }

    public static AppMesasApplication getInstance() {
        return instance;
    }
}
