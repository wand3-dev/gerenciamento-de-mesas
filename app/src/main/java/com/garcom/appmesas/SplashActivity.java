package com.garcom.appmesas;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private VideoView videoView;
    private TextView btnPular;
    private boolean proximaTelaAberta = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Deixa a tela cheia para a animação de abertura
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

        videoView = findViewById(R.id.videoViewAbertura);
        btnPular = findViewById(R.id.btnPularAbertura);

        if (btnPular != null) {
            btnPular.setOnClickListener(v -> irParaProximaTela());
        }

        try {
            Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.abertura_app);
            videoView.setVideoURI(videoUri);

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    try {
                        mp.setVolume(1.0f, 1.0f);
                        videoView.start();
                    } catch (Exception e) {
                        irParaProximaTela();
                    }
                }
            });

            videoView.setOnCompletionListener(mp -> irParaProximaTela());

            videoView.setOnErrorListener((mp, what, extra) -> {
                irParaProximaTela();
                return true;
            });

        } catch (Exception e) {
            irParaProximaTela();
        }
    }

    private synchronized void irParaProximaTela() {
        if (proximaTelaAberta) return;
        proximaTelaAberta = true;

        try {
            if (videoView != null && videoView.isPlaying()) {
                videoView.stopPlayback();
            }
        } catch (Exception ignored) {}

        Intent intent;
        if (SessionManager.isLoggedIn(this)) {
            intent = new Intent(SplashActivity.this, MainActivity.class);
        } else {
            intent = new Intent(SplashActivity.this, LoginActivity.class);
        }

        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (videoView != null && videoView.isPlaying()) {
                videoView.pause();
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (videoView != null && !videoView.isPlaying() && !proximaTelaAberta) {
                videoView.start();
            }
        } catch (Exception ignored) {}
    }
}
