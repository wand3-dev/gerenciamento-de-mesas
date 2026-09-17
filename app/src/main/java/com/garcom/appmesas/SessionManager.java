package com.garcom.appmesas;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "user_session_prefs";
    private static final String KEY_COD_FUNC = "key_cod_func";
    private static final String KEY_NOME_USUARIO = "key_nome_usuario";
    private static final String KEY_EMAIL_USUARIO = "key_email_usuario";
    private static final String KEY_ID_FIREBASE = "key_id_firebase";
    private static final String KEY_LOGGED_IN = "key_logged_in";

    public static void salvarSessao(Context context, String codFunc, String nomeUsuario) {
        salvarSessao(context, codFunc, nomeUsuario, "", "");
    }

    public static void salvarSessao(Context context, String codFunc, String nomeUsuario, String email, String uid) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_COD_FUNC, codFunc != null ? codFunc.trim() : "")
                .putString(KEY_NOME_USUARIO, nomeUsuario != null ? nomeUsuario.trim() : "")
                .putString(KEY_EMAIL_USUARIO, email != null ? email.trim() : "")
                .putString(KEY_ID_FIREBASE, uid != null ? uid.trim() : "")
                .putBoolean(KEY_LOGGED_IN, true)
                .apply();
    }

    public static boolean isLoggedIn(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_LOGGED_IN, false);
    }

    public static String getNomeUsuario(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_NOME_USUARIO, "");
    }

    public static String getEmailUsuario(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_EMAIL_USUARIO, "");
    }

    public static String getCodFunc(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_COD_FUNC, "");
    }

    public static String getIdFirebase(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_ID_FIREBASE, "");
    }

    public static void encerrarSessao(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
    }
}
