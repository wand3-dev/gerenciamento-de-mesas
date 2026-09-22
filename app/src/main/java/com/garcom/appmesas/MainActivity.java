package com.garcom.appmesas;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import androidx.annotation.NonNull;

import com.google.android.material.card.MaterialCardView;
import android.content.res.ColorStateList;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_NOTIFICACOES = 102;
    private static final int REQ_BATERIA = 103;

    // Monitor de Comandas em Tempo Real
    private MaterialCardView cardControleServidor;
    private TextView tvStatusServidorPill, tvUltimaSincronizacao, btnConfigIpPorta;
    private MaterialButton btnLigarServidor;
    private View layoutEmptyServidor, layoutConnectingServidor;
    private MaterialButton btnEmptyLigarServidor;
    private TextView tvEmptyServidorTitulo, tvEmptyServidorDesc;
    private RecyclerView rvComandasMonitoradas;
    private ComandasMonitoradasAdapter adapterComandas;
    private final List<ComandaCardModel> listaComandasMonitoradas = new ArrayList<>();
    private final List<ComandaCardModel> listaComandasFiltradas = new ArrayList<>();
    private MonitorComandasEngine monitorEngine;
    private View tabMonitor;
    private TextView tvTabMonitorTitulo;
    private int abaAtual = 0; // 0 = Monitor, 1 = Mesas, 2 = Fila
    private Handler timer1sHandler = new Handler(Looper.getMainLooper());
    private Runnable runnableTimer1s;

    private RecyclerView rvMesas, rvFilaPedidos;
    private MesasAdapter adapter;
    private PedidosFilaAdapter filaAdapter;
    private List<PedidoFilaItem> listaFila = new ArrayList<>();

    private View tabMesas, tabFilaPedidos;
    private TextView tvTabMesasTitulo, tvTabFilaTitulo;
    private View layoutFiltrosMesas, layoutHeaderFila;
    private TextView btnLimparEntreguesFila;
    private boolean abaFilaSelecionada = false;
    private boolean ocultarEntreguesFila = true;

    private TextView tvResumoAbertas, tvResumoFechadas, tvResumoAlertas15m;
    private TextView tvServerStatusTag, btnFiltroTodas, btnFiltroMinhas, btnFiltroAbertas, btnFiltroLivres;
    private View btnAvisosRecentes;
    private TextView tvBadgeAvisos;
    private EditText etBuscarMesa;
    private TextView btnClearSearch;
    private int filtroSelecionado = 0; // 0 = Todas, 1 = Minhas, 2 = Abertas, 3 = Livres
    private String queryBusca = "";

    private View rootLayoutMain, layoutSearchContainer;

    // Chips de filtro da Fila (Bebidas vs Comidas)
    private TextView btnFilaFiltroTodas, btnFilaFiltroBebidas, btnFilaFiltroComidas;
    private int filtroSetorFila = 0; // 0 = Todos, 1 = Bebidas, 2 = Comidas

    private TextView tvNomeUsuarioLogado;
    private View btnAdicionarMesaServidor, btnConfigServidor;
    private View btnAddMinhaComandaRapida;
    private android.widget.LinearLayout layoutChipsMinhasComandas;
    private MesaManager manager;
    private ServerComandasClient serverClient;
    private UpdateChecker updateChecker;

    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private List<Mesa> listaExibicao = new ArrayList<>();

    // Mesas que já tocaram o alarme de 15 minutos para evitar repetições desnecessárias
    private Set<Integer> mesasAlertadas15Min = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manager = MesaManager.getInstance(this);
        serverClient = ServerComandasClient.getInstance(this);
        monitorEngine = MonitorComandasEngine.getInstance(this);
        updateChecker = new UpdateChecker(this);

        // Inicializa os canais de notificação do sistema e verifica permissões necessárias
        NotificationHelper.criarCanaisNotificacao(this);
        verificarEPedirTodasPermissoes();

        android.content.SharedPreferences spSettings = getSharedPreferences("app_settings", MODE_PRIVATE);
        aplicarKeepScreenOn(spSettings.getBoolean("keep_screen_on", true));

        rootLayoutMain = findViewById(R.id.rootLayoutMain);
        layoutSearchContainer = findViewById(R.id.layoutSearchContainer);

        rvMesas = findViewById(R.id.rvMesas);
        rvFilaPedidos = findViewById(R.id.rvFilaPedidos);
        tvResumoAbertas = findViewById(R.id.tvResumoAbertas);
        tvResumoFechadas = findViewById(R.id.tvResumoFechadas);
        tvResumoAlertas15m = findViewById(R.id.tvResumoAlertas15m);
        tvServerStatusTag = findViewById(R.id.tvServerStatusTag);
        tvNomeUsuarioLogado = findViewById(R.id.tvNomeUsuarioLogado);
        btnAdicionarMesaServidor = findViewById(R.id.btnAdicionarMesaServidor);
        btnConfigServidor = findViewById(R.id.btnConfigServidor);
        btnAddMinhaComandaRapida = findViewById(R.id.btnAddMinhaComandaRapida);
        layoutChipsMinhasComandas = findViewById(R.id.layoutChipsMinhasComandas);
        btnFiltroTodas = findViewById(R.id.btnFiltroTodas);
        btnFiltroMinhas = findViewById(R.id.btnFiltroMinhas);
        btnFiltroAbertas = findViewById(R.id.btnFiltroAbertas);
        btnFiltroLivres = findViewById(R.id.btnFiltroLivres);
        btnAvisosRecentes = findViewById(R.id.btnAvisosRecentes);
        tvBadgeAvisos = findViewById(R.id.tvBadgeAvisos);
        etBuscarMesa = findViewById(R.id.etBuscarMesa);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        if (btnAvisosRecentes != null) {
            btnAvisosRecentes.setOnClickListener(v -> exibirModalCentralAvisos());
        }

        if (btnAddMinhaComandaRapida != null) {
            btnAddMinhaComandaRapida.setOnClickListener(v -> exibirModalAdicionarMesaServidor());
        }
        atualizarChipsMinhasComandas();

        if (tvNomeUsuarioLogado != null) {
            tvNomeUsuarioLogado.setOnClickListener(v -> exibirModalConfigServidor());
        }
        atualizarDadosUsuario();

        tabMesas = findViewById(R.id.tabMesas);
        tabFilaPedidos = findViewById(R.id.tabFilaPedidos);
        tvTabMesasTitulo = findViewById(R.id.tvTabMesasTitulo);
        tvTabFilaTitulo = findViewById(R.id.tvTabFilaTitulo);
        layoutFiltrosMesas = findViewById(R.id.layoutFiltrosMesas);
        layoutHeaderFila = findViewById(R.id.layoutHeaderFila);
        btnLimparEntreguesFila = findViewById(R.id.btnLimparEntreguesFila);
        btnFilaFiltroTodas = findViewById(R.id.btnFilaFiltroTodas);
        btnFilaFiltroBebidas = findViewById(R.id.btnFilaFiltroBebidas);
        btnFilaFiltroComidas = findViewById(R.id.btnFilaFiltroComidas);

        // Views do Monitor de Comandas em Tempo Real
        cardControleServidor = findViewById(R.id.cardControleServidor);
        tvStatusServidorPill = findViewById(R.id.tvStatusServidorPill);
        tvUltimaSincronizacao = findViewById(R.id.tvUltimaSincronizacao);
        btnConfigIpPorta = findViewById(R.id.btnConfigIpPorta);
        btnLigarServidor = findViewById(R.id.btnLigarServidor);

        tabMonitor = findViewById(R.id.tabMonitor);
        tvTabMonitorTitulo = findViewById(R.id.tvTabMonitorTitulo);

        layoutEmptyServidor = findViewById(R.id.layoutEmptyServidor);
        layoutConnectingServidor = findViewById(R.id.layoutConnectingServidor);
        tvEmptyServidorTitulo = findViewById(R.id.tvEmptyServidorTitulo);
        tvEmptyServidorDesc = findViewById(R.id.tvEmptyServidorDesc);
        btnEmptyLigarServidor = findViewById(R.id.btnEmptyLigarServidor);
        rvComandasMonitoradas = findViewById(R.id.rvComandasMonitoradas);

        if (btnFilaFiltroTodas != null) btnFilaFiltroTodas.setOnClickListener(v -> setFiltroSetorFila(0));
        if (btnFilaFiltroBebidas != null) btnFilaFiltroBebidas.setOnClickListener(v -> setFiltroSetorFila(1));
        if (btnFilaFiltroComidas != null) btnFilaFiltroComidas.setOnClickListener(v -> setFiltroSetorFila(2));

        // Barra de busca instantânea (mesas, comandas e produtos)
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
                    atualizarListaExibicao();
                    if (adapter != null) adapter.notifyDataSetChanged();
                    atualizarFilaPedidos();
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

        aplicarTemaVisual();
        atualizarListaExibicao();
        atualizarFilaPedidos();

        rvMesas.setLayoutManager(new GridLayoutManager(this, 2));
        rvMesas.setHasFixedSize(true);
        adapter = new MesasAdapter(this, listaExibicao, mesa -> {
            Intent intent = new Intent(MainActivity.this, MesaDetailActivity.class);
            intent.putExtra("NUMERO_MESA", mesa.getNumero());
            startActivity(intent);
        });
        rvMesas.setAdapter(adapter);

        // RecyclerView da Fila de Pedidos em ordem cronológica de atendimento
        rvFilaPedidos.setLayoutManager(new LinearLayoutManager(this));
        rvFilaPedidos.setHasFixedSize(true);
        filaAdapter = new PedidosFilaAdapter(this, listaFila, new PedidosFilaAdapter.OnFilaClickListener() {
            @Override
            public void onMesaClick(Mesa mesa) {
                Intent intent = new Intent(MainActivity.this, MesaDetailActivity.class);
                intent.putExtra("NUMERO_MESA", mesa.getNumero());
                startActivity(intent);
            }

            @Override
            public void onMarcarEntregueClick(PedidoFilaItem item, int position) {
                PedidoItem p = item.getPedido();
                boolean novoEstado = !p.isEntregue();
                p.setEntregue(novoEstado);
                VibrationHelper.vibrateTick(MainActivity.this);
                manager.salvarMesas(MainActivity.this);
                sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
                atualizarFilaPedidos();
                atualizarListaExibicao();
                adapter.notifyDataSetChanged();
                atualizarResumo();
                verificarPedidosAtrasados15Minutos();

                if (novoEstado) {
                    Snackbar snackbar = Snackbar.make(rvFilaPedidos, "Mesa " + item.getMesa().getNumero() + ": " + p.getDescricao() + " entregue!", Snackbar.LENGTH_LONG);
                    snackbar.setAction("DESFAZER", v -> {
                        p.setEntregue(false);
                        VibrationHelper.vibrateTick(MainActivity.this);
                        manager.salvarMesas(MainActivity.this);
                        sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
                        atualizarFilaPedidos();
                        atualizarListaExibicao();
                        adapter.notifyDataSetChanged();
                        atualizarResumo();
                        verificarPedidosAtrasados15Minutos();
                    });
                    snackbar.setActionTextColor(Color.parseColor("#FCD34D"));
                    snackbar.show();
                }
            }
        });
        rvFilaPedidos.setAdapter(filaAdapter);

        // Configuração do RecyclerView de Comandas Monitoradas
        adapterComandas = new ComandasMonitoradasAdapter(this, listaComandasFiltradas, new ComandasMonitoradasAdapter.OnComandaActionListener() {
            @Override
            public void onComandaClick(ComandaCardModel comanda) {
                // Foco 100% nas comandas (sem tela de mesas)
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
        rvComandasMonitoradas.setHasFixedSize(false);
        rvComandasMonitoradas.setAdapter(adapterComandas);

        // Ações dos botões Ligar/Desligar Servidor
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
                            tvUltimaSincronizacao.setText("Última sync: " + ultimaSync + " (" + comandas.size() + " abertas)");
                        }
                        atualizarResumo();
                    });
                }
            });
        }

        // Listeners das Abas Principais
        if (tabMonitor != null) tabMonitor.setOnClickListener(v -> selecionarAba(0));
        if (tabMesas != null) tabMesas.setOnClickListener(v -> selecionarAba(1));
        if (tabFilaPedidos != null) tabFilaPedidos.setOnClickListener(v -> selecionarAba(2));

        btnLimparEntreguesFila.setOnClickListener(v -> {
            ocultarEntreguesFila = !ocultarEntreguesFila;
            btnLimparEntreguesFila.setText(ocultarEntreguesFila ? "Ocultar Entregues" : "Mostrar Todos");
            atualizarFilaPedidos();
        });

        // Oculta status de servidor na interface pública
        atualizarStatusServidorTag();

        btnAdicionarMesaServidor.setOnClickListener(v -> exibirModalAdicionarMesaServidor());
        if (btnConfigServidor != null) {
            btnConfigServidor.setOnClickListener(v -> exibirModalConfigServidor());
        }

        // Filtros rápidos da aba Mesas
        btnFiltroTodas.setOnClickListener(v -> setFiltro(0));
        if (btnFiltroMinhas != null) {
            btnFiltroMinhas.setOnClickListener(v -> setFiltro(1));
        }
        btnFiltroAbertas.setOnClickListener(v -> setFiltro(2));
        if (btnFiltroLivres != null) {
            btnFiltroLivres.setOnClickListener(v -> setFiltro(3));
        }

        // Polling automático a cada 10 segundos para checar novidades internas e alarme de 15m
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                atualizarListaExibicao();
                if (adapter != null) adapter.notifyDataSetChanged();
                atualizarFilaPedidos();
                atualizarResumo();
                verificarPedidosAtrasados15Minutos();
                if (monitorEngine != null && monitorEngine.isAtivo()) {
                    sincronizarComandasAbertasAutomatico();
                }
                timerHandler.postDelayed(this, 10000); // 10 segundos exatos
            }
        };

        // Inicia na aba de Monitor de Comandas por padrão
        selecionarAba(0);
    }

    private void setFiltro(int filtro) {
        filtroSelecionado = filtro;
        boolean isDark = ThemeManager.isDarkMode(this);

        btnFiltroTodas.setBackgroundResource(filtro == 0 ? R.drawable.bg_filter_chip_active : R.drawable.bg_filter_chip_inactive);
        btnFiltroTodas.setTextColor(Color.parseColor(filtro == 0 ? "#FFFFFF" : (isDark ? "#94A3B8" : "#0F172A")));

        if (btnFiltroMinhas != null) {
            btnFiltroMinhas.setBackgroundResource(filtro == 1 ? R.drawable.bg_filter_chip_active : R.drawable.bg_filter_chip_inactive);
            btnFiltroMinhas.setTextColor(Color.parseColor(filtro == 1 ? "#FFFFFF" : "#D97706"));
        }

        btnFiltroAbertas.setBackgroundResource(filtro == 2 ? R.drawable.bg_filter_chip_active : R.drawable.bg_filter_chip_inactive);
        btnFiltroAbertas.setTextColor(Color.parseColor(filtro == 2 ? "#FFFFFF" : "#065F46"));

        if (btnFiltroLivres != null) {
            btnFiltroLivres.setBackgroundResource(filtro == 3 ? R.drawable.bg_filter_chip_active : R.drawable.bg_filter_chip_inactive);
            btnFiltroLivres.setTextColor(Color.parseColor(filtro == 3 ? "#FFFFFF" : "#64748B"));
        }

        atualizarListaExibicao();
        if (adapter != null) adapter.notifyDataSetChanged();
        filtrarEAtualizarComandas();
    }

    private void selecionarAba(boolean mostrarFila) {
        selecionarAba(mostrarFila ? 2 : 1);
    }

    private void selecionarAba(int aba) {
        abaAtual = aba;
        abaFilaSelecionada = (aba == 2);
        boolean isDark = ThemeManager.isDarkMode(this);

        if (tabMonitor != null) {
            tabMonitor.setBackgroundColor(Color.parseColor(isDark ? "#131E2E" : "#1E293B"));
            if (tvTabMonitorTitulo != null) tvTabMonitorTitulo.setTextColor(Color.parseColor("#94A3B8"));
        }
        if (tabMesas != null) {
            tabMesas.setBackgroundColor(Color.parseColor(isDark ? "#131E2E" : "#1E293B"));
            if (tvTabMesasTitulo != null) tvTabMesasTitulo.setTextColor(Color.parseColor("#94A3B8"));
        }
        if (tabFilaPedidos != null) {
            tabFilaPedidos.setBackgroundColor(Color.parseColor(isDark ? "#131E2E" : "#1E293B"));
            if (tvTabFilaTitulo != null) tvTabFilaTitulo.setTextColor(Color.parseColor("#94A3B8"));
        }

        if (aba == 0) { // Monitor de Comandas
            if (tabMonitor != null) tabMonitor.setBackgroundResource(R.drawable.bg_pill_comanda);
            if (tvTabMonitorTitulo != null) tvTabMonitorTitulo.setTextColor(Color.parseColor("#92400E"));

            if (layoutFiltrosMesas != null) layoutFiltrosMesas.setVisibility(View.VISIBLE);
            if (layoutHeaderFila != null) layoutHeaderFila.setVisibility(View.GONE);
            if (rvMesas != null) rvMesas.setVisibility(View.GONE);
            if (rvFilaPedidos != null) rvFilaPedidos.setVisibility(View.GONE);

            atualizarVisibilidadeMonitor();
        } else if (aba == 1) { // Mesas
            if (tabMesas != null) tabMesas.setBackgroundResource(R.drawable.bg_pill_comanda);
            if (tvTabMesasTitulo != null) tvTabMesasTitulo.setTextColor(Color.parseColor("#92400E"));

            if (layoutEmptyServidor != null) layoutEmptyServidor.setVisibility(View.GONE);
            if (layoutConnectingServidor != null) layoutConnectingServidor.setVisibility(View.GONE);
            if (rvComandasMonitoradas != null) rvComandasMonitoradas.setVisibility(View.GONE);

            if (layoutHeaderFila != null) layoutHeaderFila.setVisibility(View.GONE);
            if (rvFilaPedidos != null) rvFilaPedidos.setVisibility(View.GONE);

            if (layoutFiltrosMesas != null) layoutFiltrosMesas.setVisibility(View.VISIBLE);
            if (rvMesas != null) rvMesas.setVisibility(View.VISIBLE);
            atualizarListaExibicao();
            if (adapter != null) adapter.notifyDataSetChanged();
        } else { // Fila
            if (tabFilaPedidos != null) tabFilaPedidos.setBackgroundResource(R.drawable.bg_pill_comanda);
            if (tvTabFilaTitulo != null) tvTabFilaTitulo.setTextColor(Color.parseColor("#92400E"));

            if (layoutEmptyServidor != null) layoutEmptyServidor.setVisibility(View.GONE);
            if (layoutConnectingServidor != null) layoutConnectingServidor.setVisibility(View.GONE);
            if (rvComandasMonitoradas != null) rvComandasMonitoradas.setVisibility(View.GONE);
            if (rvMesas != null) rvMesas.setVisibility(View.GONE);

            if (layoutFiltrosMesas != null) layoutFiltrosMesas.setVisibility(View.GONE);
            if (layoutHeaderFila != null) layoutHeaderFila.setVisibility(View.VISIBLE);
            if (rvFilaPedidos != null) rvFilaPedidos.setVisibility(View.VISIBLE);
            atualizarFilaPedidos();
        }
    }

    private void atualizarUiEstadoServidor(EstadoServidor novoEstado, String msg) {
        if (tvStatusServidorPill == null || btnLigarServidor == null) return;

        switch (novoEstado) {
            case OFFLINE:
                tvStatusServidorPill.setText("● OFFLINE");
                tvStatusServidorPill.setTextColor(Color.parseColor("#94A3B8"));
                tvStatusServidorPill.setBackgroundResource(R.drawable.bg_dashboard_card_slate);
                btnLigarServidor.setText("LIGAR SERVIDOR");
                btnLigarServidor.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
                if (tvUltimaSincronizacao != null) {
                    tvUltimaSincronizacao.setText("Servidor desligado. Nenhuma requisição ativa.");
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
        if (abaAtual != 0) return;

        EstadoServidor estado = monitorEngine != null ? monitorEngine.getEstado() : EstadoServidor.OFFLINE;

        if (estado == EstadoServidor.OFFLINE) {
            if (layoutConnectingServidor != null) layoutConnectingServidor.setVisibility(View.GONE);
            if (rvComandasMonitoradas != null) rvComandasMonitoradas.setVisibility(View.GONE);
            if (layoutEmptyServidor != null) {
                layoutEmptyServidor.setVisibility(View.VISIBLE);
                if (tvEmptyServidorTitulo != null) tvEmptyServidorTitulo.setText("Servidor Desconectado");
                if (tvEmptyServidorDesc != null) tvEmptyServidorDesc.setText("Nenhuma requisição de rede é feita até que você ligue o servidor.\nClique no botão abaixo para iniciar o monitoramento em tempo real.");
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
                // Req 11: Em caso de erro, PRESERVA os cards atuais sem apagar!
                if (layoutEmptyServidor != null) layoutEmptyServidor.setVisibility(View.GONE);
                if (rvComandasMonitoradas != null) rvComandasMonitoradas.setVisibility(View.VISIBLE);
            }
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

            if (filtroSelecionado == 1) {
                boolean ehMinha = manager.isMinhaComanda(card.getNumComanda());
                if (!ehMinha && card.getMesa() > 0) {
                    Mesa mMesa = manager.getMesa(card.getMesa());
                    if (mMesa != null && manager.isMesaMinha(mMesa)) {
                        ehMinha = true;
                    }
                }
                if (!ehMinha) {
                    continue;
                }
            }

            if (busca.isEmpty()) {
                listaComandasFiltradas.add(card);
            } else {
                boolean matchMesa = String.valueOf(card.getMesa()).contains(busca);
                boolean matchComanda = card.getNumComanda().contains(busca);
                boolean matchDoc = card.getDocumento() != null && card.getDocumento().contains(busca);
                boolean matchItem = false;
                for (ItemComandaModel it : card.getItens()) {
                    if (StringHelper.normalizar(it.getDescricao()).contains(busca)) {
                        matchItem = true;
                        break;
                    }
                }
                if (matchMesa || matchComanda || matchDoc || matchItem) {
                    listaComandasFiltradas.add(card);
                }
            }
        }

        // Garante que comandas abertas fiquem no topo (lá pra cima) e entregues no fim (lá pra baixo)
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
            tvTabMonitorTitulo.setText("📋 COMANDAS (" + listaComandasMonitoradas.size() + ")");
        }

        atualizarVisibilidadeMonitor();
    }

    private void setFiltroSetorFila(int setor) {
        filtroSetorFila = setor;
        boolean isDark = ThemeManager.isDarkMode(this);

        if (btnFilaFiltroTodas != null) {
            btnFilaFiltroTodas.setBackgroundResource(setor == 0 ? R.drawable.bg_filter_chip_active : R.drawable.bg_filter_chip_inactive);
            btnFilaFiltroTodas.setTextColor(Color.parseColor(setor == 0 ? "#FFFFFF" : (isDark ? "#94A3B8" : "#0F172A")));
        }

        if (btnFilaFiltroBebidas != null) {
            btnFilaFiltroBebidas.setBackgroundResource(setor == 1 ? R.drawable.bg_filter_chip_active : R.drawable.bg_filter_chip_inactive);
            btnFilaFiltroBebidas.setTextColor(Color.parseColor(setor == 1 ? "#FFFFFF" : "#065F46"));
        }

        if (btnFilaFiltroComidas != null) {
            btnFilaFiltroComidas.setBackgroundResource(setor == 2 ? R.drawable.bg_filter_chip_active : R.drawable.bg_filter_chip_inactive);
            btnFilaFiltroComidas.setTextColor(Color.parseColor(setor == 2 ? "#FFFFFF" : "#64748B"));
        }

        atualizarFilaPedidos();
    }

    private void atualizarFilaPedidos() {
        listaFila.clear();
        int totalPendentes = 0;
        int totalBebidasPendentes = 0;
        int totalComidasPendentes = 0;
        String buscaNorm = StringHelper.normalizar(queryBusca);

        for (Mesa m : manager.getMesas()) {
            if (m.isAberta() && m.getPedidos() != null) {
                for (PedidoItem p : m.getPedidos()) {
                    if (!p.isEntregue()) {
                        totalPendentes++;
                        if (p.isBebida()) totalBebidasPendentes++;
                        else totalComidasPendentes++;
                    }
                    if (!ocultarEntreguesFila || !p.isEntregue()) {
                        if (filtroSetorFila == 1 && !p.isBebida()) {
                            continue;
                        }
                        if (filtroSetorFila == 2 && !p.isComida()) {
                            continue;
                        }

                        if (!buscaNorm.isEmpty()) {
                            String numMesa = String.valueOf(m.getNumero());
                            boolean matchMesa = numMesa.contains(buscaNorm);
                            boolean matchComanda = p.getComanda() != null && StringHelper.normalizar(p.getComanda()).contains(buscaNorm);
                            boolean matchProduto = p.getDescricao() != null && StringHelper.normalizar(p.getDescricao()).contains(buscaNorm);
                            if (!matchMesa && !matchComanda && !matchProduto) {
                                continue;
                            }
                        }
                        listaFila.add(new PedidoFilaItem(m, p));
                    }
                }
            }
        }

        if (btnFilaFiltroTodas != null) btnFilaFiltroTodas.setText("TODOS (" + totalPendentes + ")");
        if (btnFilaFiltroBebidas != null) btnFilaFiltroBebidas.setText("☕ BEBIDAS (" + totalBebidasPendentes + ")");
        if (btnFilaFiltroComidas != null) btnFilaFiltroComidas.setText("🍳 COMIDAS (" + totalComidasPendentes + ")");

        // ORDENAÇÃO POR TEMPO: Os mais antigos (menor timestamp = pedidos primeiro) ficam no topo!
        Collections.sort(listaFila, new Comparator<PedidoFilaItem>() {
            @Override
            public int compare(PedidoFilaItem o1, PedidoFilaItem o2) {
                // Não-entregues sempre têm prioridade sobre entregues
                if (o1.getPedido().isEntregue() != o2.getPedido().isEntregue()) {
                    return o1.getPedido().isEntregue() ? 1 : -1;
                }
                // Menor timestamp = feito antes = mais tempo esperando = primeiro da fila!
                return Long.compare(o1.getTimestamp(), o2.getTimestamp());
            }
        });

        if (filaAdapter != null) {
            filaAdapter.notifyDataSetChanged();
        }

        if (tvTabFilaTitulo != null) {
            tvTabFilaTitulo.setText("⏱️ FILA DE ESPERA (" + totalPendentes + ")");
        }
    }

    private void atualizarListaExibicao() {
        listaExibicao.clear();
        String buscaRaw = queryBusca.trim();
        String buscaNorm = StringHelper.normalizar(buscaRaw);

        if (adapter != null) {
            adapter.setQueryBusca(buscaRaw);
        }

        for (Mesa m : manager.getMesas()) {
            if (filtroSelecionado == 1 && !manager.isMesaMinha(m)) continue;
            if (filtroSelecionado == 2 && !m.isAberta()) continue;
            if (filtroSelecionado == 3 && m.isAberta()) continue;

            if (!buscaNorm.isEmpty()) {
                String numMesa = String.valueOf(m.getNumero());
                boolean matchMesa = numMesa.contains(buscaNorm);
                boolean matchComanda = false;
                for (String cmd : m.getComandasUnicas()) {
                    if (StringHelper.normalizar(cmd).contains(buscaNorm)) {
                        matchComanda = true;
                        break;
                    }
                }

                boolean matchProduto = false;
                if (m.getPedidos() != null) {
                    for (PedidoItem p : m.getPedidos()) {
                        if (p.getDescricao() != null && StringHelper.normalizar(p.getDescricao()).contains(buscaNorm)) {
                            matchProduto = true;
                            break;
                        }
                    }
                }

                if (!matchMesa && !matchComanda && !matchProduto) continue;
            }

            listaExibicao.add(m);
        }
    }

    private void aplicarTemaVisual() {
        if (rootLayoutMain != null) {
            rootLayoutMain.setBackgroundColor(Color.parseColor("#F8FAFC"));
        }
        if (layoutSearchContainer != null) {
            layoutSearchContainer.setBackgroundResource(R.drawable.bg_search_bar);
        }
        if (etBuscarMesa != null) {
            etBuscarMesa.setTextColor(Color.parseColor("#0F172A"));
            etBuscarMesa.setHintTextColor(Color.parseColor("#94A3B8"));
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (filaAdapter != null) {
            filaAdapter.notifyDataSetChanged();
        }
        setFiltro(filtroSelecionado);
        selecionarAba(abaFilaSelecionada);
        setFiltroSetorFila(filtroSetorFila);
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
        TextView tvStatus = dialog.findViewById(R.id.tvStatusBuscaServidor);
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

        // Botão de checar atualizações remotas via Firebase
        MaterialButton btnCheckUpdates = dialog.findViewById(R.id.btnVerificarUpdatesDialog);
        if (btnCheckUpdates != null) {
            btnCheckUpdates.setVisibility(View.VISIBLE);
            btnCheckUpdates.setOnClickListener(v -> {
                Toast.makeText(this, "Consultando Firebase por novas versões...", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(MainActivity.this, "Firebase conectado. Nenhuma nova versão publicada ainda.", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String erro) {
                        Toast.makeText(MainActivity.this, "Erro ao consultar Firebase: " + erro, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }

        // Botão Manter Tela Ligada (Sugestão 5)
        MaterialButton btnToggleScreen = dialog.findViewById(R.id.btnToggleKeepScreenOn);
        if (btnToggleScreen != null) {
            btnToggleScreen.setVisibility(View.VISIBLE);
            android.content.SharedPreferences sp = getSharedPreferences("app_settings", MODE_PRIVATE);
            boolean keepOn = sp.getBoolean("keep_screen_on", true);
            atualizarBotaoKeepScreen(btnToggleScreen, keepOn);

            btnToggleScreen.setOnClickListener(v -> {
                boolean novoEstado = !sp.getBoolean("keep_screen_on", true);
                sp.edit().putBoolean("keep_screen_on", novoEstado).apply();
                aplicarKeepScreenOn(novoEstado);
                atualizarBotaoKeepScreen(btnToggleScreen, novoEstado);
                Toast.makeText(this, novoEstado ? "💡 Tela configurada para ficar sempre ligada!" : "Tela seguirá o tempo padrão do celular.", Toast.LENGTH_SHORT).show();
            });
        }

        btnSalvar.setText("Salvar & Conectar");
        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnSalvar.setOnClickListener(v -> {
            String novoIp = etIp.getText().toString().trim();
            String novaPorta = etPorta.getText().toString().trim();
            if (novoIp.isEmpty() || novaPorta.isEmpty()) {
                Toast.makeText(this, "Preencha IP e Porta", Toast.LENGTH_SHORT).show();
                return;
            }

            serverClient.salvarConfiguracao(novoIp, novaPorta);
            tvServerStatusTag.setText("🟢 Servidor Padaria (" + novoIp + ":" + novaPorta + ")");
            Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            sincronizarComandasAbertasAutomatico();
        });

        dialog.show();
    }

    private boolean jaVerificouUpdateNestaSessao = false;

    private void checarAtualizacaoFirebaseSilencioso() {
        if (jaVerificouUpdateNestaSessao) return;
        jaVerificouUpdateNestaSessao = true;

        updateChecker.verificarAtualizacao(new UpdateChecker.OnUpdateCheckListener() {
            @Override
            public void onUpdateAvailable(UpdateChecker.UpdateInfo info) {
                UpdateChecker.exibirDialogoAtualizacao(MainActivity.this, info);
            }

            @Override
            public void onAlreadyUpToDate() {}

            @Override
            public void onNoVersionPublished() {}

            @Override
            public void onError(String erro) {}
        });
    }

    private void exibirModalAdicionarMesaServidor() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_buscar_mesa_servidor);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etMesa = dialog.findViewById(R.id.etDialogMesa);
        EditText etComanda = dialog.findViewById(R.id.etDialogComanda);
        TextView tvStatus = dialog.findViewById(R.id.tvStatusBuscaServidor);
        Button btnCancelar = dialog.findViewById(R.id.btnCancelarBuscarServidor);
        Button btnBuscar = dialog.findViewById(R.id.btnConfirmarBuscarServidor);

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnBuscar.setOnClickListener(v -> {
            String campo1 = etMesa.getText().toString().trim();
            final String campo2 = etComanda.getText().toString().trim();

            if (campo1.isEmpty() && campo2.isEmpty()) {
                etMesa.setError("Informe a Comanda ou Mesa");
                return;
            }

            int numMesa;
            final String comandaAlvo;

            if (!campo2.isEmpty()) {
                try {
                    numMesa = Integer.parseInt(campo1);
                } catch (Exception e) {
                    etMesa.setError("Número de mesa inválido");
                    return;
                }
                comandaAlvo = campo2;
            } else {
                try {
                    numMesa = Integer.parseInt(campo1);
                } catch (Exception e) {
                    etMesa.setError("Informe um número válido");
                    return;
                }
                comandaAlvo = campo1;
            }

            // Garante criação e abertura automática do card da mesa (ex: Mesa 239)
            Mesa mesa = manager.getOuCriarMesa(numMesa);
            mesa.setAberta(true);
            manager.adicionarMinhaComanda(this, comandaAlvo);
            manager.salvarMesas(this);

            btnBuscar.setEnabled(false);
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setTextColor(Color.parseColor("#D97706"));
            tvStatus.setText("Buscando dados no servidor para a Mesa " + numMesa + "...");

            serverClient.buscarItensDaMesa(numMesa, new ServerComandasClient.OnItensMesaLoadedListener() {
                @Override
                public void onSuccess(List<JSONObject> itens) {
                    btnBuscar.setEnabled(true);
                    tvStatus.setVisibility(View.GONE);
                    importarItensParaMesa(mesa, numMesa, comandaAlvo, itens);
                    atualizarChipsMinhasComandas();
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "Mesa " + numMesa + " (CMD #" + comandaAlvo + ") aberta com sucesso!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onEmpty() {
                    // Se não achou itens na mesa informada, tenta varrer outras mesas para localizar a comanda
                    if (!comandaAlvo.isEmpty()) {
                        tvStatus.setText("Varrendo mesas no servidor para achar a Comanda #" + comandaAlvo + "...");
                        serverClient.buscarComandaVarrendoMesas(comandaAlvo, numMesa, new ServerComandasClient.OnComandaEncontradaListener() {
                            @Override
                            public void onEncontrada(int mesaOrigem, List<JSONObject> itensComanda) {
                                btnBuscar.setEnabled(true);
                                tvStatus.setVisibility(View.GONE);
                                Mesa mReal = manager.getOuCriarMesa(mesaOrigem);
                                mReal.setAberta(true);
                                importarItensParaMesa(mReal, mesaOrigem, comandaAlvo, itensComanda);
                                atualizarChipsMinhasComandas();
                                dialog.dismiss();
                                Toast.makeText(MainActivity.this, "Comanda #" + comandaAlvo + " encontrada na Mesa " + mesaOrigem + "!", Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onNotFound(String mensagem) {
                                btnBuscar.setEnabled(true);
                                tvStatus.setVisibility(View.GONE);
                                // Mesmo ainda sem itens no servidor, o card já fica ativo e monitorado!
                                atualizarListaExibicao();
                                adapter.notifyDataSetChanged();
                                atualizarChipsMinhasComandas();
                                dialog.dismiss();
                                Toast.makeText(MainActivity.this, "Mesa " + numMesa + " (CMD #" + comandaAlvo + ") criada e sendo monitorada!", Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(String erro) {
                                btnBuscar.setEnabled(true);
                                tvStatus.setVisibility(View.GONE);
                                atualizarListaExibicao();
                                adapter.notifyDataSetChanged();
                                atualizarChipsMinhasComandas();
                                dialog.dismiss();
                            }
                        });
                    } else {
                        btnBuscar.setEnabled(true);
                        tvStatus.setVisibility(View.GONE);
                        atualizarListaExibicao();
                        adapter.notifyDataSetChanged();
                        atualizarChipsMinhasComandas();
                        dialog.dismiss();
                        Toast.makeText(MainActivity.this, "Mesa " + numMesa + " criada e sendo monitorada!", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String erro) {
                    btnBuscar.setEnabled(true);
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setTextColor(Color.parseColor("#EF4444"));
                    tvStatus.setText(erro);
                    atualizarChipsMinhasComandas();
                }
            });
        });

        dialog.show();
    }

    private void importarItensParaMesa(Mesa mesa, int numMesa, String comandaFiltro, List<JSONObject> itensJson) {
        if (itensJson == null || itensJson.isEmpty()) {
            Toast.makeText(this, "Nenhum item encontrado para a Mesa " + numMesa, Toast.LENGTH_SHORT).show();
            return;
        }

        int adicionados = 0;
        for (JSONObject obj : itensJson) {
            PedidoItem novoItem = PedidoItem.fromServerJson(obj);

            if (!comandaFiltro.isEmpty() && !comandaFiltro.equals(novoItem.getComanda())) {
                continue;
            }

            boolean jaExiste = false;
            for (PedidoItem existente : mesa.getPedidos()) {
                if (existente.getId().equals(novoItem.getId())) {
                    jaExiste = true;
                    existente.setQuantidade(novoItem.getQuantidade());
                    existente.setValorTotal(novoItem.getValorTotal());
                    existente.setDescricao(novoItem.getDescricao());
                    break;
                }
            }

            if (!jaExiste) {
                mesa.adicionarPedido(novoItem);
                adicionados++;
            }
        }

        mesa.setAberta(true);
        manager.salvarMesas(this);

        atualizarListaExibicao();
        adapter.notifyDataSetChanged();
        atualizarFilaPedidos();
        atualizarResumo();

        if (adicionados > 0) {
            NotificationHelper.notificarNovoItem(this, "Mesa Importada!", "Mesa " + numMesa + ": " + adicionados + " item(ns) importado(s).");
            Toast.makeText(this, "✓ " + adicionados + " item(ns) importado(s) para a Mesa " + numMesa + "!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "✓ Itens da Mesa " + numMesa + " já estavam atualizados.", Toast.LENGTH_SHORT).show();
        }
    }

    private void atualizarChipsMinhasComandas() {
        if (layoutChipsMinhasComandas == null) return;
        layoutChipsMinhasComandas.removeAllViews();

        Set<String> comandas = manager.getMinhasComandas();
        if (comandas.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Nenhuma comanda vinculada. Toque em + MONITORAR");
            tvEmpty.setTextColor(Color.parseColor("#94A3B8"));
            tvEmpty.setTextSize(11f);
            tvEmpty.setPadding(8, 4, 8, 4);
            layoutChipsMinhasComandas.addView(tvEmpty);
            return;
        }

        for (final String cmd : comandas) {
            android.widget.LinearLayout chip = new android.widget.LinearLayout(this);
            chip.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
            chip.setBackgroundResource(R.drawable.bg_filter_chip_active);
            chip.setPadding(22, 10, 16, 10);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 16, 0);
            chip.setLayoutParams(params);

            TextView tvText = new TextView(this);
            Mesa mEncontrada = manager.buscarMesaComComanda(cmd, -1);
            String label = "CMD #" + cmd;
            int mesaAlvo = -1;
            try {
                mesaAlvo = Integer.parseInt(cmd);
            } catch (Exception ignored) {}

            if (mEncontrada != null) {
                label = "CMD #" + cmd + " (Mesa " + mEncontrada.getNumero() + ")";
                mesaAlvo = mEncontrada.getNumero();
            }
            tvText.setText(label);
            tvText.setTextColor(Color.WHITE);
            tvText.setTextSize(11f);
            tvText.setTypeface(null, android.graphics.Typeface.BOLD);
            chip.addView(tvText);

            final int fMesa = mesaAlvo;
            chip.setOnClickListener(v -> {
                if (fMesa > 0) {
                    Intent intent = new Intent(MainActivity.this, MesaDetailActivity.class);
                    intent.putExtra("NUMERO_MESA", fMesa);
                    startActivity(intent);
                }
            });

            TextView btnX = new TextView(this);
            btnX.setText(" ✕");
            btnX.setTextColor(Color.parseColor("#FDE68A"));
            btnX.setTextSize(12f);
            btnX.setTypeface(null, android.graphics.Typeface.BOLD);
            btnX.setPadding(8, 0, 4, 0);
            btnX.setOnClickListener(v -> {
                manager.removerMinhaComanda(MainActivity.this, cmd);
                atualizarChipsMinhasComandas();
                atualizarListaExibicao();
                if (adapter != null) adapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this, "Comanda #" + cmd + " removida das suas comandas.", Toast.LENGTH_SHORT).show();
            });
            chip.addView(btnX);

            layoutChipsMinhasComandas.addView(chip);
        }
    }

    private boolean sincronizandoAgora = false;

    private void sincronizarComandasAbertasAutomatico() {
        if (sincronizandoAgora) return;
        // Se o serviço em primeiro plano já está monitorando, evita duplicação de requisições e toasts
        if (MonitorMesasService.isRodando()) return;

        List<Mesa> mesasAbertas = new ArrayList<>();
        for (Mesa m : manager.getMesas()) {
            if (m.isAberta()) {
                mesasAbertas.add(m);
            }
        }

        if (mesasAbertas.isEmpty()) return;

        sincronizandoAgora = true;
        java.util.concurrent.atomic.AtomicInteger pendentes = new java.util.concurrent.atomic.AtomicInteger(mesasAbertas.size());

        for (Mesa mesa : mesasAbertas) {
            final int numMesa = mesa.getNumero();
            final List<String> comandasMonitoradas = mesa.getComandasUnicas();
            if (comandasMonitoradas.isEmpty()) {
                if (pendentes.decrementAndGet() <= 0) {
                    sincronizandoAgora = false;
                }
                continue;
            }

            serverClient.buscarItensDaMesa(numMesa, new ServerComandasClient.OnItensMesaLoadedListener() {
                @Override
                public void onSuccess(List<JSONObject> itens) {
                    boolean houveNovidade = false;
                    Set<String> idsAtivos = new HashSet<>();

                    for (JSONObject objServidor : itens) {
                        PedidoItem itemServ = PedidoItem.fromServerJson(objServidor);
                        String comandaItem = itemServ.getComanda();

                        // Apenas sincroniza/adiciona se a comanda já foi puxada pelo garçom para esta mesa
                        if (!comandasMonitoradas.contains(comandaItem)) {
                            continue;
                        }

                        idsAtivos.add(itemServ.getId());

                        boolean encontrado = false;
                        for (PedidoItem it : mesa.getPedidos()) {
                            if (it.getId().equals(itemServ.getId())) {
                                encontrado = true;
                                if (it.getQuantidade() != itemServ.getQuantidade() || !it.getValorTotal().equals(itemServ.getValorTotal())) {
                                    it.setQuantidade(itemServ.getQuantidade());
                                    it.setValorTotal(itemServ.getValorTotal());
                                    it.setDescricao(itemServ.getDescricao());
                                    houveNovidade = true;
                                }
                                break;
                            }
                        }

                        if (!encontrado) {
                            mesa.adicionarPedido(itemServ);
                            houveNovidade = true;
                            NotificationHelper.notificarNovoItem(MainActivity.this, "Item Adicionado!",
                                    "Mesa " + numMesa + ": " + itemServ.getDescricao() + " (Cmd #" + itemServ.getComanda() + ")");
                            Toast.makeText(MainActivity.this, "🔔 Mesa " + numMesa + " recebeu: " + itemServ.getDescricao(), Toast.LENGTH_LONG).show();
                        }
                    }

                    // Remove apenas itens do servidor que foram estornados / finalizados
                    // Preserva itens inseridos manualmente pelo garçom (autonum vazio)
                    for (int i = mesa.getPedidos().size() - 1; i >= 0; i--) {
                        PedidoItem it = mesa.getPedidos().get(i);
                        if (it.getAutonum() != null && !it.getAutonum().isEmpty()) {
                            if (!idsAtivos.contains(it.getId())) {
                                mesa.getPedidos().remove(i);
                                houveNovidade = true;
                            }
                        }
                    }

                    if (mesa.getPedidos().isEmpty()) {
                        mesa.setAberta(false);
                        houveNovidade = true;
                        manager.removerMesaDinamicaSeVazia(MainActivity.this, numMesa);
                        Toast.makeText(MainActivity.this, "✓ Mesa " + numMesa + " liberada no caixa da padaria.", Toast.LENGTH_SHORT).show();
                    }

                    if (houveNovidade) {
                        manager.salvarMesas(MainActivity.this);
                        atualizarListaExibicao();
                        adapter.notifyDataSetChanged();
                        atualizarFilaPedidos();
                        atualizarResumo();
                    }

                    if (pendentes.decrementAndGet() <= 0) {
                        sincronizandoAgora = false;
                    }
                }

                @Override
                public void onEmpty() {
                    // Mesa foi fechada no servidor (paga no caixa da padaria)
                    if (mesa.isAberta() && !mesa.getPedidos().isEmpty()) {
                        mesa.setAberta(false);
                        mesa.getPedidos().clear();
                        manager.salvarMesas(MainActivity.this);
                        atualizarListaExibicao();
                        adapter.notifyDataSetChanged();
                        atualizarFilaPedidos();
                        atualizarResumo();
                        Toast.makeText(MainActivity.this, "✓ Mesa " + numMesa + " liberada no caixa da padaria.", Toast.LENGTH_SHORT).show();
                    }

                    if (pendentes.decrementAndGet() <= 0) {
                        sincronizandoAgora = false;
                    }
                }

                @Override
                public void onError(String erro) {
                    if (pendentes.decrementAndGet() <= 0) {
                        sincronizandoAgora = false;
                    }
                }
            });
        }
    }

    private void verificarPedidosAtrasados15Minutos() {
        long agora = System.currentTimeMillis();
        int totalAlertas = 0;

        for (Mesa mesa : manager.getMesas()) {
            if (!mesa.isAberta()) {
                mesasAlertadas15Min.remove(mesa.getNumero());
                continue;
            }

            int atrasadosNestaMesa = 0;
            Set<String> comandasAlertadas = new HashSet<>();

            for (PedidoItem item : mesa.getPedidos()) {
                if (!item.isEntregue() && item.isAtrasado()) {
                    atrasadosNestaMesa++;
                    totalAlertas++;
                    String cmd = item.getComanda() != null ? item.getComanda().trim() : "";

                    if (!item.isAlertadoAtraso() && !comandasAlertadas.contains(cmd)) {
                        comandasAlertadas.add(cmd);
                        for (PedidoItem it : mesa.getPedidos()) {
                            if (!it.isEntregue() && cmd.equals(it.getComanda() != null ? it.getComanda().trim() : "")) {
                                it.setAlertadoAtraso(true);
                            }
                        }
                        manager.salvarMesas(this);

                        String desc = item.getQuantidade() + "x " + item.getDescricao();
                        String tempo = item.getTempoDecorridoFormatado();
                        NotificationHelper.alertarPedidoAtrasado15Min(this, mesa.getNumero(), cmd, desc, tempo, item.getId());
                        Toast.makeText(this, "🚨 Mesa " + mesa.getNumero() + ": " + desc + " esperando " + tempo + "!", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }

        tvResumoAlertas15m.setText(String.valueOf(totalAlertas));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICACOES) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✓ Notificações ativadas com sucesso!", Toast.LENGTH_SHORT).show();
                dispararNotificacaoBoasVindasTeste();
            }
            // Continua a verificação das outras permissões (bateria)
            new Handler(Looper.getMainLooper()).postDelayed(this::verificarEPedirTodasPermissoes, 300);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_NOTIFICACOES) {
            dispararNotificacaoBoasVindasTeste();
            new Handler(Looper.getMainLooper()).postDelayed(this::verificarEPedirTodasPermissoes, 300);
        } else if (requestCode == REQ_BATERIA) {
            Toast.makeText(this, "✓ Configuração de segundo plano salva!", Toast.LENGTH_SHORT).show();
        }
    }

    private android.content.BroadcastReceiver receiverAlertas = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            atualizarBadgeAvisos();
        }
    };

    private android.content.BroadcastReceiver pedidosReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            atualizarListaExibicao();
            if (adapter != null) adapter.notifyDataSetChanged();
            atualizarFilaPedidos();
            atualizarResumo();
            atualizarChipsMinhasComandas();
            atualizarBadgeAvisos();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        atualizarDadosUsuario();
        atualizarStatusServidorTag();
        aplicarTemaVisual();

        atualizarListaExibicao();
        adapter.notifyDataSetChanged();
        atualizarFilaPedidos();
        atualizarResumo();
        atualizarChipsMinhasComandas();
        verificarPedidosAtrasados15Minutos();
        timerHandler.post(timerRunnable);

        // Inicia contador individual de segundos para os cards de comandas
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pedidosReceiver, new android.content.IntentFilter(MesaManager.ACTION_PEDIDOS_ATUALIZADOS), android.content.Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(receiverAlertas, new android.content.IntentFilter(AlertaHistoricoManager.ACTION_HISTORICO_ALERTAS_ATUALIZADO), android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pedidosReceiver, new android.content.IntentFilter(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
            registerReceiver(receiverAlertas, new android.content.IntentFilter(AlertaHistoricoManager.ACTION_HISTORICO_ALERTAS_ATUALIZADO));
        }

        atualizarBadgeAvisos();
        verificarEPedirTodasPermissoes();
    }

    /**
     * Verifica e solicita todas as permissões necessárias para o funcionamento pleno do app:
     * 1. Notificações da barra de status (compatível com Android 12 e 13+)
     * 2. Execução em segundo plano sem corte de bateria
     */
    private void verificarEPedirTodasPermissoes() {
        android.content.SharedPreferences sp = getSharedPreferences("app_settings", MODE_PRIVATE);

        // 1. Notificações: verifica se estão ativas globalmente no sistema
        androidx.core.app.NotificationManagerCompat nm = androidx.core.app.NotificationManagerCompat.from(this);
        if (!nm.areNotificationsEnabled()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("🔔 Permissão de Notificações")
                    .setMessage("As notificações do AppMesas estão desativadas no seu aparelho.\n\nAtive as notificações para receber o sino de novos itens e os alertas de pedidos atrasados.")
                    .setCancelable(false)
                    .setPositiveButton("Ativar Notificações", (dialog, which) -> {
                        sp.edit().putBoolean("notif_dialog_presented_v3", true).apply();
                        abrirConfiguracoesNotificacao();
                    })
                    .setNegativeButton("Mais Tarde", (dialog, which) -> verificarPermissaoBateria())
                    .show();
            return;
        }

        // Se estiver no Android 13+ (API 33+) e precisar da permissão explícita
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICACOES);
                return;
            }
        }

        // No Android 12 ou anterior (ex: Xiaomi/MIUI), orienta o usuário a conferir sons e alertas flutuantes
        if (!sp.getBoolean("notif_dialog_presented_v3", false)) {
            sp.edit().putBoolean("notif_dialog_presented_v3", true).apply();
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("🔔 Permissão de Notificações e Sons")
                    .setMessage("O AppMesas precisa emitir notificações na barra de status com som do sino e alertas de pedidos atrasados.\n\nDeseja abrir as configurações de notificação do Android para conferir as permissões?")
                    .setCancelable(false)
                    .setPositiveButton("Conferir Notificações", (dialog, which) -> {
                        abrirConfiguracoesNotificacao();
                    })
                    .setNegativeButton("Já estão Ativas", (dialog, which) -> {
                        dispararNotificacaoBoasVindasTeste();
                        verificarPermissaoBateria();
                    })
                    .show();
            return;
        }

        verificarPermissaoBateria();
    }

    private void abrirConfiguracoesNotificacao() {
        try {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                intent.putExtra("app_package", getPackageName());
                intent.putExtra("app_uid", getApplicationInfo().uid);
            }
            startActivityForResult(intent, REQ_NOTIFICACOES);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_NOTIFICACOES);
            } catch (Exception ignored) {}
        }
    }

    private void dispararNotificacaoBoasVindasTeste() {
        NotificationHelper.notificarNovoItem(
                this,
                "🥐 Padaria Portugália",
                "Notificações ativadas com sucesso! Você receberá aqui os avisos de novos pedidos e comandas."
        );
    }

    private void verificarPermissaoBateria() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                android.content.SharedPreferences sp = getSharedPreferences("app_settings", MODE_PRIVATE);
                if (!sp.getBoolean("battery_opt_dialog_shown", false)) {
                    sp.edit().putBoolean("battery_opt_dialog_shown", true).apply();
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("🔋 Alertas em Segundo Plano")
                            .setMessage("Para garantir que os alarmes de pedidos atrasados (+15m) e as notificações nunca sejam suspensos pelo sistema com a tela desligada, permita a execução sem restrições de bateria.")
                            .setPositiveButton("Configurar", (dialog, which) -> {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:" + getPackageName()));
                                    startActivityForResult(intent, REQ_BATERIA);
                                } catch (Exception e) {
                                    try {
                                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                                    } catch (Exception ignored) {}
                                }
                            })
                            .setNegativeButton("Agora Não", null)
                            .show();
                }
            }
        }
    }

    private void atualizarDadosUsuario() {
        if (tvNomeUsuarioLogado != null) {
            tvNomeUsuarioLogado.setText("Monitor de Comandas");
        }
    }

    private void atualizarStatusServidorTag() {
        if (tvServerStatusTag != null) {
            tvServerStatusTag.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
        timer1sHandler.removeCallbacks(runnableTimer1s);
        try {
            unregisterReceiver(pedidosReceiver);
        } catch (Exception ignored) {}
        try {
            unregisterReceiver(receiverAlertas);
        } catch (Exception ignored) {}
    }

    private void atualizarResumo() {
        int abertas = 0;
        int fechadas = 0;
        for (Mesa m : manager.getMesas()) {
            if (m.isAberta()) abertas++;
            else fechadas++;
        }
        if (monitorEngine != null && monitorEngine.isAtivo() && !listaComandasMonitoradas.isEmpty()) {
            tvResumoAbertas.setText(String.valueOf(listaComandasMonitoradas.size()));
        } else {
            tvResumoAbertas.setText(String.valueOf(abertas));
        }
        tvResumoFechadas.setText(String.valueOf(fechadas));

        if (btnFiltroTodas != null) {
            btnFiltroTodas.setText("TODAS (" + manager.getMesas().size() + ")");
        }
        if (btnFiltroMinhas != null) {
            btnFiltroMinhas.setText("⭐ MINHAS (" + manager.getQtdMinhasMesas() + ")");
        }
        if (btnFiltroAbertas != null) {
            btnFiltroAbertas.setText("EM ATENDIMENTO (" + abertas + ")");
        }
        if (btnFiltroLivres != null) {
            btnFiltroLivres.setText("LIVRES (" + fechadas + ")");
        }
        if (tvTabMesasTitulo != null) {
            tvTabMesasTitulo.setText("");
        }
        atualizarBadgeAvisos();
    }

    private void atualizarBadgeAvisos() {
        if (tvBadgeAvisos == null) return;
        int naoLidos = AlertaHistoricoManager.getQtdNaoLidos(this);
        if (naoLidos > 0) {
            tvBadgeAvisos.setVisibility(View.VISIBLE);
            tvBadgeAvisos.setText(naoLidos > 99 ? "99+" : String.valueOf(naoLidos));
        } else {
            tvBadgeAvisos.setVisibility(View.GONE);
        }
    }

    private void exibirModalCentralAvisos() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_central_avisos);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        TextView btnFechar = dialog.findViewById(R.id.btnFecharCentralAvisos);
        TextView btnLimpar = dialog.findViewById(R.id.btnLimparHistoricoAvisos);
        TextView tvSubtitulo = dialog.findViewById(R.id.tvSubtituloAvisos);
        RecyclerView rvAlertas = dialog.findViewById(R.id.rvAlertasHistorico);
        View layoutVazio = dialog.findViewById(R.id.layoutAvisosVazio);

        // Marca todos como lidos ao abrir a central
        AlertaHistoricoManager.marcarTodosComoLidos(this);
        atualizarBadgeAvisos();

        List<AlertaHistorico> lista = AlertaHistoricoManager.getAlertas(this);

        rvAlertas.setLayoutManager(new LinearLayoutManager(this));
        AlertaHistoricoAdapter alertaAdapter = new AlertaHistoricoAdapter(lista, alerta -> {
            dialog.dismiss();
            if (alerta.getNumeroMesa() > 0) {
                Intent intent = new Intent(MainActivity.this, MesaDetailActivity.class);
                intent.putExtra("NUMERO_MESA", alerta.getNumeroMesa());
                startActivity(intent);
            }
        });
        rvAlertas.setAdapter(alertaAdapter);

        Runnable atualizarVisibilidade = () -> {
            if (lista.isEmpty()) {
                rvAlertas.setVisibility(View.GONE);
                layoutVazio.setVisibility(View.VISIBLE);
                if (tvSubtitulo != null) tvSubtitulo.setText("Nenhum aviso registrado");
                if (btnLimpar != null) btnLimpar.setVisibility(View.GONE);
            } else {
                rvAlertas.setVisibility(View.VISIBLE);
                layoutVazio.setVisibility(View.GONE);
                if (tvSubtitulo != null) tvSubtitulo.setText(lista.size() + " aviso(s) recente(s)");
                if (btnLimpar != null) btnLimpar.setVisibility(View.VISIBLE);
            }
        };
        atualizarVisibilidade.run();

        if (btnFechar != null) {
            btnFechar.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnLimpar != null) {
            btnLimpar.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Limpar Histórico")
                        .setMessage("Deseja apagar todos os avisos registrados?")
                        .setPositiveButton("Sim, Limpar", (d, w) -> {
                            AlertaHistoricoManager.limparTudo(this);
                            lista.clear();
                            alertaAdapter.atualizar(lista);
                            atualizarVisibilidade.run();
                            atualizarBadgeAvisos();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        }

        dialog.show();
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
            btn.setStrokeColorResource(android.R.color.holo_blue_light);
        } else {
            btn.setText("💤 MANTER TELA SEMPRE LIGADA: DESLIGADO");
            btn.setTextColor(Color.parseColor("#64748B"));
            btn.setStrokeColorResource(android.R.color.darker_gray);
        }
    }
}
