package com.garcom.appmesas;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_NOTIFICACOES = 102;

    // Monitor de Comandas em Tempo Real
    private TextView tvStatusServidorPill, tvUltimaSincronizacao, btnConfigIpPorta;
    private MaterialButton btnLigarServidor;
    private View layoutEmptyServidor, layoutConnectingServidor;
    private MaterialButton btnEmptyLigarServidor;
    private TextView tvEmptyServidorTitulo, tvEmptyServidorDesc;
    private TextView tvTabMonitorTitulo;
    private RecyclerView rvComandasMonitoradas;
    private ComandasMonitoradasAdapter adapterComandas;
    private final List<ComandaCardModel> listaComandasMonitoradas = new ArrayList<>();
    private final List<ComandaCardModel> listaComandasFiltradas = new ArrayList<>();
    private MonitorComandasEngine monitorEngine;

    // Timer de 1s para o cronômetro individual de cada comanda
    private final Handler timer1sHandler = new Handler(Looper.getMainLooper());
    private Runnable runnableTimer1s;

    // Busca rápida
    private EditText etBuscarMesa;
    private TextView btnClearSearch;
    private String queryBusca = "";

    private TextView tvNomeUsuarioLogado;
    private ServerComandasClient serverClient;
    private UpdateChecker updateChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serverClient = ServerComandasClient.getInstance(this);
        monitorEngine = MonitorComandasEngine.getInstance(this);
        updateChecker = new UpdateChecker(this);

        NotificationHelper.criarCanaisNotificacao(this);
        verificarEPedirTodasPermissoes();

        android.content.SharedPreferences spSettings = getSharedPreferences("app_settings", MODE_PRIVATE);
        aplicarKeepScreenOn(spSettings.getBoolean("keep_screen_on", true));

        // Inicialização de Views
        tvStatusServidorPill = findViewById(R.id.tvStatusServidorPill);
        tvUltimaSincronizacao = findViewById(R.id.tvUltimaSincronizacao);
        btnConfigIpPorta = findViewById(R.id.btnConfigIpPorta);
        btnLigarServidor = findViewById(R.id.btnLigarServidor);
        tvNomeUsuarioLogado = findViewById(R.id.tvNomeUsuarioLogado);
        tvTabMonitorTitulo = findViewById(R.id.tvTabMonitorTitulo);

        layoutEmptyServidor = findViewById(R.id.layoutEmptyServidor);
        layoutConnectingServidor = findViewById(R.id.layoutConnectingServidor);
        tvEmptyServidorTitulo = findViewById(R.id.tvEmptyServidorTitulo);
        tvEmptyServidorDesc = findViewById(R.id.tvEmptyServidorDesc);
        btnEmptyLigarServidor = findViewById(R.id.btnEmptyLigarServidor);
        rvComandasMonitoradas = findViewById(R.id.rvComandasMonitoradas);

        etBuscarMesa = findViewById(R.id.etBuscarMesa);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        // Barra de busca instantânea de comandas
        if (etBuscarMesa != null) {
            etBuscarMesa.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    queryBusca = s.toString();
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(queryBusca.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    filtrarEAtualizarComandas();
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (etBuscarMesa != null) etBuscarMesa.setText("");
            });
        }

        // Configuração do RecyclerView de Comandas
        adapterComandas = new ComandasMonitoradasAdapter(this, listaComandasFiltradas, new ComandasMonitoradasAdapter.OnComandaActionListener() {
            @Override
            public void onComandaClick(ComandaCardModel comanda) {
                // Apenas comandas - sem abrir tela de mesa
            }

            @Override
            public void onEntregueClick(ComandaCardModel comanda, int position) {
                VibrationHelper.vibrateSuccess(MainActivity.this);
                if (monitorEngine != null) {
                    monitorEngine.alternarEntregueComanda(comanda.getId());
                }
            }
        });
        rvComandasMonitoradas.setLayoutManager(new LinearLayoutManager(this));
        rvComandasMonitoradas.setHasFixedSize(true);
        rvComandasMonitoradas.setAdapter(adapterComandas);

        // Ações Ligar / Desligar
        View.OnClickListener listenerToggleServidor = v -> {
            if (monitorEngine != null && monitorEngine.isAtivo()) {
                monitorEngine.desligarServidor();
                Toast.makeText(MainActivity.this, "Servidor desligado.", Toast.LENGTH_SHORT).show();
            } else if (monitorEngine != null) {
                monitorEngine.ligarServidor();
                Toast.makeText(MainActivity.this, "Conectando ao servidor...", Toast.LENGTH_SHORT).show();
            }
        };

        if (btnLigarServidor != null) btnLigarServidor.setOnClickListener(listenerToggleServidor);
        if (btnEmptyLigarServidor != null) btnEmptyLigarServidor.setOnClickListener(listenerToggleServidor);
        if (btnConfigIpPorta != null) btnConfigIpPorta.setOnClickListener(v -> exibirModalConfigServidor());
        if (tvNomeUsuarioLogado != null) tvNomeUsuarioLogado.setOnClickListener(v -> exibirModalConfigServidor());

        // Registrar Callback do Motor de Monitoramento
        if (monitorEngine != null) {
            monitorEngine.registrarCallback(new MonitorComandasEngine.MonitorCallback() {
                @Override
                public void onEstadoAlterado(EstadoServidor novoEstado, String mensagem) {
                    runOnUiThread(() -> atualizarUiEstadoServidor(novoEstado, mensagem));
                }

                @Override
                public void onComandasAtualizadas(List<ComandaCardModel> comandas, String ultimaSync) {
                    runOnUiThread(() -> {
                        listaComandasMonitoradas.clear();
                        listaComandasMonitoradas.addAll(comandas);
                        filtrarEAtualizarComandas();
                        if (tvUltimaSincronizacao != null && !ultimaSync.isEmpty()) {
                            tvUltimaSincronizacao.setText("Última sync: " + ultimaSync + " (" + comandas.size() + " comanda(s) aberta(s))");
                        }
                    });
                }
            });
        }

        // Inicia automaticamente o monitoramento de comandas ao abrir o app
        if (monitorEngine != null && !monitorEngine.isAtivo()) {
            monitorEngine.ligarServidor();
        }
    }

    private void filtrarEAtualizarComandas() {
        listaComandasFiltradas.clear();
        String busca = StringHelper.normalizar(queryBusca.trim());

        Set<String> chavesInseridas = new HashSet<>();
        for (ComandaCardModel card : listaComandasMonitoradas) {
            String chave = card.getId();
            if (chavesInseridas.contains(chave)) {
                continue; // Não deixa comandas repetirem
            }
            chavesInseridas.add(chave);

            if (busca.isEmpty()) {
                listaComandasFiltradas.add(card);
            } else {
                boolean matchComanda = card.getNumComanda().contains(busca);
                boolean matchMesa = card.getMesa() > 0 && String.valueOf(card.getMesa()).contains(busca);
                boolean matchDoc = card.getDocumento() != null && card.getDocumento().contains(busca);
                boolean matchItem = false;
                for (ItemComandaModel it : card.getItens()) {
                    if (StringHelper.normalizar(it.getDescricao()).contains(busca)) {
                        matchItem = true;
                        break;
                    }
                }
                if (matchComanda || matchMesa || matchDoc || matchItem) {
                    listaComandasFiltradas.add(card);
                }
            }
        }

        // Ordenação: comandas abertas primeiro (lá pra cima), entregues no final (lá pra baixo)
        Collections.sort(listaComandasFiltradas, (c1, c2) -> {
            if (c1.isEntregue() != c2.isEntregue()) {
                return c1.isEntregue() ? 1 : -1;
            }
            return Long.compare(c1.getDetectedAt(), c2.getDetectedAt());
        });

        if (adapterComandas != null) {
            adapterComandas.notifyDataSetChanged();
        }

        if (tvTabMonitorTitulo != null) {
            tvTabMonitorTitulo.setText("📋 COMANDAS ABERTAS (" + listaComandasMonitoradas.size() + ")");
        }

        atualizarVisibilidadeMonitor();
    }

    private void atualizarUiEstadoServidor(EstadoServidor novoEstado, String msg) {
        if (tvStatusServidorPill == null || btnLigarServidor == null) return;

        switch (novoEstado) {
            case OFFLINE:
                tvStatusServidorPill.setText("● OFFLINE");
                tvStatusServidorPill.setTextColor(Color.parseColor("#94A3B8"));
                tvStatusServidorPill.setBackgroundResource(R.drawable.bg_dashboard_card_slate);
                btnLigarServidor.setText("LIGAR");
                btnLigarServidor.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
                if (tvUltimaSincronizacao != null) {
                    tvUltimaSincronizacao.setText("Servidor desligado. Toque em LIGAR para iniciar.");
                }
                break;
            case CONNECTING:
                tvStatusServidorPill.setText("◌ CONECTANDO...");
                tvStatusServidorPill.setTextColor(Color.parseColor("#38BDF8"));
                tvStatusServidorPill.setBackgroundResource(R.drawable.bg_dashboard_card_slate);
                btnLigarServidor.setText("CONECTANDO...");
                btnLigarServidor.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0284C7")));
                if (tvUltimaSincronizacao != null) {
                    tvUltimaSincronizacao.setText("Consultando DataSnap Padaria...");
                }
                break;
            case ONLINE:
                tvStatusServidorPill.setText("● ONLINE");
                tvStatusServidorPill.setTextColor(Color.parseColor("#86EFAC"));
                tvStatusServidorPill.setBackgroundResource(R.drawable.bg_dashboard_card_emerald);
                btnLigarServidor.setText("DESLIGAR");
                btnLigarServidor.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
                break;
            case ERROR:
                tvStatusServidorPill.setText("✕ ERRO");
                tvStatusServidorPill.setTextColor(Color.parseColor("#FCA5A5"));
                tvStatusServidorPill.setBackgroundResource(R.drawable.bg_dashboard_card_amber);
                btnLigarServidor.setText("RECONECTAR");
                btnLigarServidor.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F59E0B")));
                if (tvUltimaSincronizacao != null && msg != null && !msg.isEmpty()) {
                    tvUltimaSincronizacao.setText(msg);
                }
                break;
        }

        atualizarVisibilidadeMonitor();
    }

    private void atualizarVisibilidadeMonitor() {
        EstadoServidor estado = monitorEngine != null ? monitorEngine.getEstado() : EstadoServidor.OFFLINE;

        if (estado == EstadoServidor.OFFLINE) {
            if (layoutConnectingServidor != null) layoutConnectingServidor.setVisibility(View.GONE);
            if (rvComandasMonitoradas != null) rvComandasMonitoradas.setVisibility(View.GONE);
            if (layoutEmptyServidor != null) {
                layoutEmptyServidor.setVisibility(View.VISIBLE);
                if (tvEmptyServidorTitulo != null) tvEmptyServidorTitulo.setText("Servidor Desconectado");
                if (tvEmptyServidorDesc != null) tvEmptyServidorDesc.setText("Toque no botão abaixo para iniciar o monitoramento das comandas em tempo real.");
                if (btnEmptyLigarServidor != null) {
                    btnEmptyLigarServidor.setVisibility(View.VISIBLE);
                    btnEmptyLigarServidor.setText("▶ LIGAR SERVIDOR");
                    btnEmptyLigarServidor.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#059669")));
                }
            }
        } else if (estado == EstadoServidor.CONNECTING) {
            if (layoutEmptyServidor != null) layoutEmptyServidor.setVisibility(View.GONE);
            if (rvComandasMonitoradas != null) rvComandasMonitoradas.setVisibility(View.GONE);
            if (layoutConnectingServidor != null) layoutConnectingServidor.setVisibility(View.VISIBLE);
        } else if (estado == EstadoServidor.ONLINE) {
            if (layoutConnectingServidor != null) layoutConnectingServidor.setVisibility(View.GONE);
            if (listaComandasFiltradas.isEmpty()) {
                if (layoutEmptyServidor != null) {
                    layoutEmptyServidor.setVisibility(View.VISIBLE);
                    if (tvEmptyServidorTitulo != null) tvEmptyServidorTitulo.setText("Nenhuma Comanda Aberta");
                    if (tvEmptyServidorDesc != null) tvEmptyServidorDesc.setText("Não há comandas abertas nas últimas 10 horas registradas no servidor.");
                    if (btnEmptyLigarServidor != null) btnEmptyLigarServidor.setVisibility(View.GONE);
                }
                if (rvComandasMonitoradas != null) rvComandasMonitoradas.setVisibility(View.GONE);
            } else {
                if (layoutEmptyServidor != null) layoutEmptyServidor.setVisibility(View.GONE);
                if (rvComandasMonitoradas != null) rvComandasMonitoradas.setVisibility(View.VISIBLE);
            }
        } else if (estado == EstadoServidor.ERROR) {
            if (layoutConnectingServidor != null) layoutConnectingServidor.setVisibility(View.GONE);
            if (listaComandasFiltradas.isEmpty()) {
                if (layoutEmptyServidor != null) {
                    layoutEmptyServidor.setVisibility(View.VISIBLE);
                    if (tvEmptyServidorTitulo != null) tvEmptyServidorTitulo.setText("Falha na Conexão");
                    if (tvEmptyServidorDesc != null) tvEmptyServidorDesc.setText("Não foi possível comunicar com o servidor DataSnap. Verifique o IP e Porta.");
                    if (btnEmptyLigarServidor != null) {
                        btnEmptyLigarServidor.setVisibility(View.VISIBLE);
                        btnEmptyLigarServidor.setText("🔄 TENTAR NOVAMENTE");
                        btnEmptyLigarServidor.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#DC2626")));
                    }
                }
                if (rvComandasMonitoradas != null) rvComandasMonitoradas.setVisibility(View.GONE);
            } else {
                if (layoutEmptyServidor != null) layoutEmptyServidor.setVisibility(View.GONE);
                if (rvComandasMonitoradas != null) rvComandasMonitoradas.setVisibility(View.VISIBLE);
            }
        }
    }

    private void exibirModalConfigServidor() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_buscar_mesa_servidor);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitulo = dialog.findViewById(R.id.tvTituloDialogServidor);
        TextView tvSubtitulo = dialog.findViewById(R.id.tvSubtituloDialogServidor);
        TextView tvLabel1 = dialog.findViewById(R.id.tvLabelCampo1);
        TextView tvLabel2 = dialog.findViewById(R.id.tvLabelCampo2);
        EditText etIp = dialog.findViewById(R.id.etDialogMesa);
        EditText etPorta = dialog.findViewById(R.id.etDialogComanda);
        Button btnCancelar = dialog.findViewById(R.id.btnCancelarBuscarServidor);
        Button btnSalvar = dialog.findViewById(R.id.btnConfirmarBuscarServidor);

        if (tvTitulo != null) tvTitulo.setText("⚙️ CONFIGURAÇÃO DO SERVIDOR");
        if (tvSubtitulo != null) tvSubtitulo.setText("Ajuste o IP e Porta do servidor DataSnap da Padaria.");
        if (tvLabel1 != null) tvLabel1.setText("ENDEREÇO IP DO SERVIDOR");
        if (tvLabel2 != null) tvLabel2.setText("PORTA DO SERVIDOR");
        etIp.setHint("IP do Servidor (ex: 192.168.0.246)");
        etIp.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        etIp.setText(serverClient.getServerIp());

        etPorta.setHint("Porta (ex: 8075)");
        etPorta.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPorta.setText(serverClient.getServerPort());

        MaterialButton btnCheckUpdates = dialog.findViewById(R.id.btnVerificarUpdatesDialog);
        if (btnCheckUpdates != null) {
            btnCheckUpdates.setVisibility(View.VISIBLE);
            btnCheckUpdates.setOnClickListener(v -> {
                Toast.makeText(this, "Consultando atualizações...", Toast.LENGTH_SHORT).show();
                updateChecker.verificarAtualizacao(new UpdateChecker.OnUpdateCheckListener() {
                    @Override
                    public void onUpdateAvailable(UpdateChecker.UpdateInfo info) {
                        dialog.dismiss();
                        UpdateChecker.exibirDialogoAtualizacao(MainActivity.this, info);
                    }

                    @Override
                    public void onAlreadyUpToDate() {
                        Toast.makeText(MainActivity.this, "✓ O aplicativo já está na versão mais recente!", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onNoVersionPublished() {
                        Toast.makeText(MainActivity.this, "Nenhuma nova versão publicada ainda.", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String erro) {
                        Toast.makeText(MainActivity.this, "Erro: " + erro, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }

        MaterialButton btnToggleKeepScreenOn = dialog.findViewById(R.id.btnToggleKeepScreenOn);
        if (btnToggleKeepScreenOn != null) {
            btnToggleKeepScreenOn.setVisibility(View.VISIBLE);
            android.content.SharedPreferences spSettings = getSharedPreferences("app_settings", MODE_PRIVATE);
            boolean keepOn = spSettings.getBoolean("keep_screen_on", true);
            atualizarBotaoKeepScreen(btnToggleKeepScreenOn, keepOn);

            btnToggleKeepScreenOn.setOnClickListener(v -> {
                boolean novoValor = !spSettings.getBoolean("keep_screen_on", true);
                spSettings.edit().putBoolean("keep_screen_on", novoValor).apply();
                aplicarKeepScreenOn(novoValor);
                atualizarBotaoKeepScreen(btnToggleKeepScreenOn, novoValor);
                Toast.makeText(this, novoValor ? "Tela configurada para NUNCA apagar" : "Tela apagará conforme padrão do Android", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnCancelar != null) btnCancelar.setOnClickListener(v -> dialog.dismiss());
        if (btnSalvar != null) {
            btnSalvar.setText("SALVAR CONEXÃO");
            btnSalvar.setOnClickListener(v -> {
                String novoIp = etIp.getText().toString().trim();
                String novaPorta = etPorta.getText().toString().trim();

                if (novoIp.isEmpty()) {
                    etIp.setError("Digite o IP");
                    return;
                }
                if (novaPorta.isEmpty()) {
                    etPorta.setError("Digite a Porta");
                    return;
                }

                serverClient.salvarConfiguracao(novoIp, novaPorta);
                dialog.dismiss();
                Toast.makeText(this, "Configurações salvas! Reconectando...", Toast.LENGTH_SHORT).show();

                if (monitorEngine != null) {
                    monitorEngine.desligarServidor();
                    monitorEngine.ligarServidor();
                }
            });
        }

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Inicia contador suave de segundos a cada 1s para o tempo de espera das comandas
        if (runnableTimer1s == null) {
            runnableTimer1s = new Runnable() {
                @Override
                public void run() {
                    if (adapterComandas != null && monitorEngine != null && monitorEngine.isAtivo()) {
                        adapterComandas.atualizarContadoresSegundos();
                    }
                    timer1sHandler.postDelayed(this, 1000);
                }
            };
        }
        timer1sHandler.removeCallbacks(runnableTimer1s);
        timer1sHandler.post(runnableTimer1s);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (runnableTimer1s != null) {
            timer1sHandler.removeCallbacks(runnableTimer1s);
        }
    }

    private void verificarEPedirTodasPermissoes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICACOES);
            }
        }
    }

    private void aplicarKeepScreenOn(boolean keepOn) {
        if (keepOn) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void atualizarBotaoKeepScreen(MaterialButton btn, boolean keepOn) {
        if (keepOn) {
            btn.setText("💡 MANTER TELA SEMPRE LIGADA: LIGADO");
            btn.setTextColor(Color.parseColor("#0284C7"));
        } else {
            btn.setText("💤 MANTER TELA SEMPRE LIGADA: DESLIGADO");
            btn.setTextColor(Color.parseColor("#64748B"));
        }
    }
}
