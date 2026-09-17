package com.garcom.appmesas;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class LoginActivity extends AppCompatActivity {

    private TextView tabEntrar, tabCadastrar;
    private TextView tvTituloSecao, tvSubtituloSecao;
    private View layoutCampoNome, layoutConfirmarSenha;
    private EditText etLoginNome, etLoginEmail, etLoginSenha, etLoginConfirmarSenha;
    private TextView tvLoginStatus, btnAlternarModoTexto;
    private MaterialButton btnFazerLogin;

    private boolean modoCadastro = false;
    private FirebaseAuthClient authClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Se já houver sessão ativa, vai direto para a tela principal
        if (SessionManager.isLoggedIn(this)) {
            abrirTelaPrincipal();
            return;
        }

        setContentView(R.layout.activity_login);

        authClient = FirebaseAuthClient.getInstance();

        tabEntrar = findViewById(R.id.tabEntrar);
        tabCadastrar = findViewById(R.id.tabCadastrar);
        tvTituloSecao = findViewById(R.id.tvTituloSecao);
        tvSubtituloSecao = findViewById(R.id.tvSubtituloSecao);
        layoutCampoNome = findViewById(R.id.layoutCampoNome);
        layoutConfirmarSenha = findViewById(R.id.layoutConfirmarSenha);
        etLoginNome = findViewById(R.id.etLoginNome);
        etLoginEmail = findViewById(R.id.etLoginEmail);
        etLoginSenha = findViewById(R.id.etLoginSenha);
        etLoginConfirmarSenha = findViewById(R.id.etLoginConfirmarSenha);
        tvLoginStatus = findViewById(R.id.tvLoginStatus);
        btnFazerLogin = findViewById(R.id.btnFazerLogin);
        btnAlternarModoTexto = findViewById(R.id.btnAlternarModoTexto);

        tabEntrar.setOnClickListener(v -> alternarModo(false));
        tabCadastrar.setOnClickListener(v -> alternarModo(true));
        btnAlternarModoTexto.setOnClickListener(v -> alternarModo(!modoCadastro));

        btnFazerLogin.setOnClickListener(v -> {
            if (modoCadastro) {
                executarCadastroFirebase();
            } else {
                executarLoginFirebase();
            }
        });

        // Inicializa no modo Login
        alternarModo(false);
    }

    private void alternarModo(boolean cadastro) {
        this.modoCadastro = cadastro;
        tvLoginStatus.setVisibility(View.GONE);

        if (cadastro) {
            tabCadastrar.setBackgroundResource(R.drawable.bg_button_gradient_amber);
            tabCadastrar.setTextColor(Color.WHITE);

            tabEntrar.setBackgroundColor(Color.TRANSPARENT);
            tabEntrar.setTextColor(Color.parseColor("#94A3B8"));

            tvTituloSecao.setText("Criar Nova Conta");
            tvSubtituloSecao.setText("Cadastre-se para acessar o sistema no Firebase");
            layoutCampoNome.setVisibility(View.VISIBLE);
            layoutConfirmarSenha.setVisibility(View.VISIBLE);
            btnFazerLogin.setText("CADASTRAR NO FIREBASE");
            btnAlternarModoTexto.setText("Já possui cadastro? Clique para entrar");
        } else {
            tabEntrar.setBackgroundResource(R.drawable.bg_button_gradient_amber);
            tabEntrar.setTextColor(Color.WHITE);

            tabCadastrar.setBackgroundColor(Color.TRANSPARENT);
            tabCadastrar.setTextColor(Color.parseColor("#94A3B8"));

            tvTituloSecao.setText("Acesse sua conta");
            tvSubtituloSecao.setText("Entre com seu e-mail e senha do Firebase");
            layoutCampoNome.setVisibility(View.GONE);
            layoutConfirmarSenha.setVisibility(View.GONE);
            btnFazerLogin.setText("ENTRAR COM FIREBASE");
            btnAlternarModoTexto.setText("Não tem uma conta? Cadastre-se aqui");
        }
    }

    private void executarLoginFirebase() {
        String email = etLoginEmail.getText().toString().trim();
        String senha = etLoginSenha.getText().toString().trim();

        if (email.isEmpty()) {
            etLoginEmail.setError("Informe seu e-mail");
            etLoginEmail.requestFocus();
            return;
        }
        if (!email.contains("@") || !email.contains(".")) {
            etLoginEmail.setError("Informe um e-mail válido");
            etLoginEmail.requestFocus();
            return;
        }
        if (senha.isEmpty()) {
            etLoginSenha.setError("Informe sua senha");
            etLoginSenha.requestFocus();
            return;
        }

        mostrarCarregamento("Autenticando com o Firebase...");

        authClient.fazerLogin(email, senha, new FirebaseAuthClient.AuthCallback() {
            @Override
            public void onSuccess(String emailRetornado, String nomeUsuario, String localId, String idToken) {
                esconderCarregamento();
                SessionManager.salvarSessao(LoginActivity.this, "", nomeUsuario, emailRetornado, localId);
                Toast.makeText(LoginActivity.this, "✓ Bem-vindo, " + nomeUsuario + "!", Toast.LENGTH_SHORT).show();
                abrirTelaPrincipal();
            }

            @Override
            public void onError(String mensagemErro) {
                esconderCarregamento();
                mostrarErro(mensagemErro);
            }
        });
    }

    private void executarCadastroFirebase() {
        String nome = etLoginNome.getText().toString().trim();
        String email = etLoginEmail.getText().toString().trim();
        String senha = etLoginSenha.getText().toString().trim();
        String confirmarSenha = etLoginConfirmarSenha.getText().toString().trim();

        if (nome.isEmpty()) {
            etLoginNome.setError("Informe seu nome");
            etLoginNome.requestFocus();
            return;
        }
        if (email.isEmpty() || !email.contains("@") || !email.contains(".")) {
            etLoginEmail.setError("Informe um e-mail válido");
            etLoginEmail.requestFocus();
            return;
        }
        if (senha.length() < 6) {
            etLoginSenha.setError("A senha deve ter no mínimo 6 dígitos");
            etLoginSenha.requestFocus();
            return;
        }
        if (!senha.equals(confirmarSenha)) {
            etLoginConfirmarSenha.setError("As senhas não coincidem");
            etLoginConfirmarSenha.requestFocus();
            return;
        }

        mostrarCarregamento("Criando sua conta no Firebase...");

        authClient.criarConta(nome, email, senha, new FirebaseAuthClient.AuthCallback() {
            @Override
            public void onSuccess(String emailRetornado, String nomeUsuario, String localId, String idToken) {
                esconderCarregamento();
                SessionManager.salvarSessao(LoginActivity.this, "", nomeUsuario, emailRetornado, localId);
                Toast.makeText(LoginActivity.this, "✓ Conta criada com sucesso, " + nomeUsuario + "!", Toast.LENGTH_SHORT).show();
                abrirTelaPrincipal();
            }

            @Override
            public void onError(String mensagemErro) {
                esconderCarregamento();
                mostrarErro(mensagemErro);
            }
        });
    }

    private void mostrarCarregamento(String texto) {
        btnFazerLogin.setEnabled(false);
        tvLoginStatus.setVisibility(View.VISIBLE);
        tvLoginStatus.setTextColor(Color.parseColor("#F59E0B"));
        tvLoginStatus.setText("⏳ " + texto);
    }

    private void esconderCarregamento() {
        btnFazerLogin.setEnabled(true);
        tvLoginStatus.setVisibility(View.GONE);
    }

    private void mostrarErro(String msg) {
        tvLoginStatus.setVisibility(View.VISIBLE);
        tvLoginStatus.setTextColor(Color.parseColor("#EF4444"));
        tvLoginStatus.setText("⚠️ " + msg);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void abrirTelaPrincipal() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
