package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;

public class ThemeManager {
    private static final String PREF_NAME = "app_theme_prefs";
    private static final String KEY_DARK_MODE = "dark_mode_active";

    public static boolean isDarkMode(Context context) {
        if (context == null) return false;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_DARK_MODE, false);
    }

    public static void setDarkMode(Context context, boolean darkMode) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_DARK_MODE, darkMode).apply();
    }

    public static boolean toggleDarkMode(Context context) {
        boolean novoModo = !isDarkMode(context);
        setDarkMode(context, novoModo);
        return novoModo;
    }
}
