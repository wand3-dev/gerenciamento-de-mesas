package com.garcom.appmesas;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private boolean proximaTelaAberta = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable proximaTelaRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tela cheia imersiva moderna
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                );
            }
        } catch (Exception ignored) {}

        setContentView(R.layout.activity_splash);

        View layoutContent = findViewById(R.id.layoutSplashContent);
        if (layoutContent != null) {
            AnimationSet animSet = new AnimationSet(true);

            AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
            fadeIn.setDuration(700);

            ScaleAnimation scale = new ScaleAnimation(
                    0.88f, 1.0f,
                    0.88f, 1.0f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            );
            scale.setDuration(700);

            animSet.addAnimation(fadeIn);
            animSet.addAnimation(scale);
            layoutContent.startAnimation(animSet);
        }

        // Aguarda 1.5 segundo para exibir a logo de abertura e avança
        proximaTelaRunnable = this::irParaProximaTela;
        handler.postDelayed(proximaTelaRunnable, 1500);
    }

    private synchronized void irParaProximaTela() {
        if (proximaTelaAberta) return;
        proximaTelaAberta = true;

        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && proximaTelaRunnable != null) {
            handler.removeCallbacks(proximaTelaRunnable);
        }
    }
}
