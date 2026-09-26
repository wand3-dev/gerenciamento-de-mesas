package com.garcom.appmesas;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class VibrationHelper {

    /**
     * Vibração tátil sutil (tick háptico) de 35ms para cliques e confirmações rápidas.
     */
    public static void vibrateTick(Context context) {
        if (context == null) return;
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(35);
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Vibração dupla de sucesso (para ações em lote como entregar todas as bebidas/comidas).
     */
    public static void vibrateSuccess(Context context) {
        if (context == null) return;
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(new long[]{0, 40, 50, 60}, -1));
                } else {
                    v.vibrate(new long[]{0, 40, 50, 60}, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void vibrateLongPress(Context context) {
        if (context == null) return;
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(70);
                }
            }
        } catch (Exception ignored) {}
    }
}
