package com.garcom.appmesas;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements
        MonitorComandasEngine.MonitorCallback,
        CancelamentoDetectorManager.OnCancelamentoListener,
        ConferenciaSaidasManager.OnConferenciaListener {

    // Views do Cabeçalho
    private View layoutHeaderPrincipal;
    private TextView tvStatusServidorPill;
    private View btnAtualizar;
    private View btnConfigGarcom;
    private TextView tvNomeGarcomAtivo;
    private TextView tvUltimaSincronizacao;

    // Navegação entre Abas
    private View btnTabFaturamento;
    private TextView tvLabelTabFaturamento;
    private View btnTabConferencia;
    private TextView tvLabelTabConferencia;
    private TextView tvBadgeTabConferencia;
    private ScrollView scrollAbaFaturamento;
    private LinearLayout layoutAbaConferencia;

    // Aba de Conferência de Saídas
    private TextView btnFiltroPendentes;
    private TextView btnFiltroTodas;
    private TextView tvVazioConferencia;
    private LinearLayout layoutListaComandasConferencia;

    // Meta Diária de Comidas (R$ 500)
    private View cardMetaDiaria;
    private TextView tvMetaPercentualBadge;
    private ProgressBar progressMetaDiaria;
    private TextView tvMetaProgressoValores;
    private TextView tvMetaFaltante;

    // Banner de Alerta de Cancelamento Pendente (Produto que sumiu)
    private View cardItemPendenteConfirmacao;
    private TextView tvBadgeContadorPendentes;
    private TextView tvPendenteDescricao;
    private TextView tvPendenteMesaComanda;
    private MaterialButton btnConfirmarCancelado;
    private MaterialButton btnDescartarCancelado;

    // Métricas Financeiras
    private TextView tvFaturamentoHoje;
    private TextView tvQtdItensHoje;
    private TextView tvFatComidasHoje;
    private TextView tvFatTerceirosHoje;
    private TextView tvAbertosAgora;
    private TextView tvTituloMes;
    private TextView tvFaturamentoMes;
    private TextView tvQtdItensMes;
    private TextView tvFatComidasMes;
    private TextView tvFatTerceirosMes;
    private TextView tvCancelamentosHoje;

    // Listas / Históricos
    private LinearLayout layoutHistoricoDias;
    private TextView tvVazioDias;
    private LinearLayout layoutHistoricoCancelamentos;
    private TextView tvVazioCancelamentos;

    // Camuflagem Rápida (Modo Pânico)
    private View layoutCamuflagemPanico;

    // Gerenciadores
    private MonitorComandasEngine monitorEngine;
    private VendasFaturamentoManager vendasManager;
    private CancelamentoDetectorManager cancelamentoManager;
    private ConferenciaSaidasManager conferenciaManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AlertDialog dialogCancelamentoAtivo;

    // Estados
    private boolean abaConferenciaAtiva = false;
    private boolean apenasPendentesConferencia = true;
    private long ultimoToqueHeader = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa instâncias
        monitorEngine = MonitorComandasEngine.getInstance(this);
        vendasManager = VendasFaturamentoManager.getInstance(this);
        cancelamentoManager = CancelamentoDetectorManager.getInstance(this);
        conferenciaManager = ConferenciaSaidasManager.getInstance(this);

        // Garante que o garçom padrão seja o 223 (Wanderson)
        String codAtual = ResumoGarcomManager.getCodigoGarcom(this);
        if (codAtual.isEmpty()) {
            ResumoGarcomManager.setCodigoGarcom(this, "223");
        }

        vincularViews();
        configurarBotoes();
        configurarAbas();
        configurarModoCamuflagem();

        // Solicita permissão de notificações em Android 13+
        solicitarPermissaoNotificacoes();

        // Registra listeners de cancelamento e de conferência de saídas
        cancelamentoManager.registrarListener(this);
        conferenciaManager.registrarListener(this);

        // Verifica atualizações remotas discretamente
        verificarAtualizacaoApp();

        // Atualiza a tela inicialmente com os dados salvos
        atualizarDadosDashboard();
        atualizarBadgeConferencia();
    }

    private void vincularViews() {
        layoutHeaderPrincipal = findViewById(R.id.layoutHeaderPrincipal);
        tvStatusServidorPill = findViewById(R.id.tvStatusServidorPill);
        btnAtualizar = findViewById(R.id.btnAtualizar);
        btnConfigGarcom = findViewById(R.id.btnConfigGarcom);
        tvNomeGarcomAtivo = findViewById(R.id.tvNomeGarcomAtivo);
        tvUltimaSincronizacao = findViewById(R.id.tvUltimaSincronizacao);

        btnTabFaturamento = findViewById(R.id.btnTabFaturamento);
        tvLabelTabFaturamento = findViewById(R.id.tvLabelTabFaturamento);
        btnTabConferencia = findViewById(R.id.btnTabConferencia);
        tvLabelTabConferencia = findViewById(R.id.tvLabelTabConferencia);
        tvBadgeTabConferencia = findViewById(R.id.tvBadgeTabConferencia);
        scrollAbaFaturamento = findViewById(R.id.scrollAbaFaturamento);
        layoutAbaConferencia = findViewById(R.id.layoutAbaConferencia);

        btnFiltroPendentes = findViewById(R.id.btnFiltroPendentes);
        btnFiltroTodas = findViewById(R.id.btnFiltroTodas);
        tvVazioConferencia = findViewById(R.id.tvVazioConferencia);
        layoutListaComandasConferencia = findViewById(R.id.layoutListaComandasConferencia);

        cardMetaDiaria = findViewById(R.id.cardMetaDiaria);
        tvMetaPercentualBadge = findViewById(R.id.tvMetaPercentualBadge);
        progressMetaDiaria = findViewById(R.id.progressMetaDiaria);
        tvMetaProgressoValores = findViewById(R.id.tvMetaProgressoValores);
        tvMetaFaltante = findViewById(R.id.tvMetaFaltante);

        cardItemPendenteConfirmacao = findViewById(R.id.cardItemPendenteConfirmacao);
        tvBadgeContadorPendentes = findViewById(R.id.tvBadgeContadorPendentes);
        tvPendenteDescricao = findViewById(R.id.tvPendenteDescricao);
        tvPendenteMesaComanda = findViewById(R.id.tvPendenteMesaComanda);
        btnConfirmarCancelado = findViewById(R.id.btnConfirmarCancelado);
        btnDescartarCancelado = findViewById(R.id.btnDescartarCancelado);

        tvFaturamentoHoje = findViewById(R.id.tvFaturamentoHoje);
        tvQtdItensHoje = findViewById(R.id.tvQtdItensHoje);
        tvFatComidasHoje = findViewById(R.id.tvFatComidasHoje);
        tvFatTerceirosHoje = findViewById(R.id.tvFatTerceirosHoje);
        tvAbertosAgora = findViewById(R.id.tvAbertosAgora);
        tvTituloMes = findViewById(R.id.tvTituloMes);
        tvFaturamentoMes = findViewById(R.id.tvFaturamentoMes);
        tvQtdItensMes = findViewById(R.id.tvQtdItensMes);
        tvFatComidasMes = findViewById(R.id.tvFatComidasMes);
        tvFatTerceirosMes = findViewById(R.id.tvFatTerceirosMes);
        tvCancelamentosHoje = findViewById(R.id.tvCancelamentosHoje);

        layoutHistoricoDias = findViewById(R.id.layoutHistoricoDias);
        tvVazioDias = findViewById(R.id.tvVazioDias);
        layoutHistoricoCancelamentos = findViewById(R.id.layoutHistoricoCancelamentos);
        tvVazioCancelamentos = findViewById(R.id.tvVazioCancelamentos);

        layoutCamuflagemPanico = findViewById(R.id.layoutCamuflagemPanico);
    }

    private void configurarBotoes() {
        if (btnAtualizar != null) {
            btnAtualizar.setOnClickListener(v -> {
                RotateAnimation rotate = new RotateAnimation(0, 360,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f);
                rotate.setDuration(600);
                v.startAnimation(rotate);

                if (monitorEngine != null) {
                    monitorEngine.forcarAtualizacaoImediata();
                }
                Toast.makeText(MainActivity.this, "Consultando servidor...", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnConfigGarcom != null) {
            btnConfigGarcom.setOnClickListener(v -> exibirDialogoConfigGarcom());
        }
        if (tvNomeGarcomAtivo != null) {
            tvNomeGarcomAtivo.setOnClickListener(v -> exibirDialogoConfigGarcom());
        }

        if (cardMetaDiaria != null) {
            cardMetaDiaria.setOnClickListener(v -> exibirDialogoMetaDiaria());
        }
    }

    private void configurarAbas() {
        if (btnTabFaturamento != null) {
            btnTabFaturamento.setOnClickListener(v -> alternarAba(false));
        }

        if (btnTabConferencia != null) {
            btnTabConferencia.setOnClickListener(v -> alternarAba(true));
        }

        if (btnFiltroPendentes != null) {
            btnFiltroPendentes.setOnClickListener(v -> {
                apenasPendentesConferencia = true;
                btnFiltroPendentes.setBackgroundResource(R.drawable.bg_badge_notif);
                btnFiltroPendentes.setTextColor(Color.WHITE);
                btnFiltroTodas.setBackgroundResource(R.drawable.bg_badge_categoria_terceiro);
                btnFiltroTodas.setTextColor(Color.parseColor("#475569"));
                renderizarComandasConferencia();
            });
        }

        if (btnFiltroTodas != null) {
            btnFiltroTodas.setOnClickListener(v -> {
                apenasPendentesConferencia = false;
                btnFiltroTodas.setBackgroundResource(R.drawable.bg_badge_notif);
                btnFiltroTodas.setTextColor(Color.WHITE);
                btnFiltroPendentes.setBackgroundResource(R.drawable.bg_badge_categoria_terceiro);
                btnFiltroPendentes.setTextColor(Color.parseColor("#475569"));
                renderizarComandasConferencia();
            });
        }
    }

    private void alternarAba(boolean paraConferencia) {
        abaConferenciaAtiva = paraConferencia;
        if (paraConferencia) {
            btnTabConferencia.setBackgroundResource(R.drawable.bg_tab_selected);
            tvLabelTabConferencia.setTextColor(Color.WHITE);
            btnTabFaturamento.setBackgroundResource(R.drawable.bg_tab_unselected_soft);
            tvLabelTabFaturamento.setTextColor(Color.parseColor("#475569"));

            if (scrollAbaFaturamento != null) scrollAbaFaturamento.setVisibility(View.GONE);
            if (layoutAbaConferencia != null) layoutAbaConferencia.setVisibility(View.VISIBLE);

            renderizarComandasConferencia();
        } else {
            btnTabFaturamento.setBackgroundResource(R.drawable.bg_tab_selected);
            tvLabelTabFaturamento.setTextColor(Color.WHITE);
            btnTabConferencia.setBackgroundResource(R.drawable.bg_tab_unselected_soft);
            tvLabelTabConferencia.setTextColor(Color.parseColor("#475569"));

            if (scrollAbaFaturamento != null) scrollAbaFaturamento.setVisibility(View.VISIBLE);
            if (layoutAbaConferencia != null) layoutAbaConferencia.setVisibility(View.GONE);

            atualizarDadosDashboard();
        }
    }

    private void configurarModoCamuflagem() {
        // Toque duplo no cabeçalho ativa imediatamente a camuflagem
        if (layoutHeaderPrincipal != null) {
            layoutHeaderPrincipal.setOnClickListener(v -> {
                long agora = System.currentTimeMillis();
                if (agora - ultimoToqueHeader < 400) {
                    ativarModoCamuflagem(true);
                    ultimoToqueHeader = 0;
                } else {
                    ultimoToqueHeader = agora;
                }
            });
        }

        // Toque longo (2 segundos) na tela de camuflagem restaura o app
        if (layoutCamuflagemPanico != null) {
            layoutCamuflagemPanico.setOnLongClickListener(v -> {
                VibrationHelper.vibrateLongPress(MainActivity.this);
                ativarModoCamuflagem(false);
                Toast.makeText(MainActivity.this, "Painel restaurado.", Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    private void ativarModoCamuflagem(boolean ativar) {
        if (layoutCamuflagemPanico == null) return;
        if (ativar) {
            VibrationHelper.vibrateLongPress(this);
            layoutCamuflagemPanico.setVisibility(View.VISIBLE);
        } else {
            layoutCamuflagemPanico.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (monitorEngine != null) {
            monitorEngine.registrarCallback(this);
            if (!monitorEngine.isAtivo()) {
                monitorEngine.ligarServidor();
            }
        }
        atualizarDadosDashboard();
        atualizarBadgeConferencia();
        if (abaConferenciaAtiva) {
            renderizarComandasConferencia();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (monitorEngine != null) {
            monitorEngine.removerCallback(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cancelamentoManager != null) {
            cancelamentoManager.removerListener(this);
        }
        if (conferenciaManager != null) {
            conferenciaManager.removerListener(this);
        }
    }

    /**
     * Atualiza os números, cards e históricos da interface principal
     */
    public synchronized void atualizarDadosDashboard() {
        String cod = ResumoGarcomManager.getCodigoGarcom(this);
        String nome = GarcomManager.getNomeGarcom(this, cod);

        if (nome == null || nome.isEmpty()) {
            nome = "Garçom";
        }

        if (tvNomeGarcomAtivo != null) {
            tvNomeGarcomAtivo.setText("👤 " + nome + " (#" + cod + ")");
        }

        // 1. Meta Diária (R$ 500)
        double meta = vendasManager.getMetaDiaria();
        double fatComidasHoje = vendasManager.getFaturamentoComidasHoje(cod);
        int percentual = vendasManager.getPercentualMetaHoje(cod);
        double faltante = vendasManager.getValorFaltanteMeta(cod);

        if (progressMetaDiaria != null) {
            progressMetaDiaria.setProgress(Math.min(100, Math.max(0, percentual)));
        }
        if (tvMetaPercentualBadge != null) {
            tvMetaPercentualBadge.setText(percentual + "%" + (percentual >= 100 ? " 🏆" : ""));
        }
        if (tvMetaProgressoValores != null) {
            tvMetaProgressoValores.setText(VendasFaturamentoManager.formatarMoeda(fatComidasHoje) + " de " + VendasFaturamentoManager.formatarMoeda(meta));
        }
        if (tvMetaFaltante != null) {
            if (faltante > 0) {
                tvMetaFaltante.setText("Faltam " + VendasFaturamentoManager.formatarMoeda(faltante));
                tvMetaFaltante.setTextColor(Color.parseColor("#059669"));
            } else {
                tvMetaFaltante.setText("🎉 Meta Atingida!");
                tvMetaFaltante.setTextColor(Color.parseColor("#047857"));
            }
        }

        // 2. Faturamento Hoje & Separação de Comidas vs Terceiros
        double fatHoje = vendasManager.getFaturamentoHoje(cod);
        int qtdHoje = vendasManager.getQtdItensHoje(cod);
        double fatTercHoje = vendasManager.getFaturamentoTerceirosHoje(cod);
        double abertosHoje = vendasManager.getFaturamentoAbertoAgora();
        int qtdAbertosHoje = vendasManager.getQtdItensAbertoAgora();

        if (tvFaturamentoHoje != null) {
            tvFaturamentoHoje.setText(VendasFaturamentoManager.formatarMoeda(fatHoje));
        }
        if (tvQtdItensHoje != null) {
            tvQtdItensHoje.setText(qtdHoje + (qtdHoje == 1 ? " produto vendido hoje" : " produtos vendidos hoje"));
        }
        if (tvFatComidasHoje != null) {
            tvFatComidasHoje.setText(VendasFaturamentoManager.formatarMoeda(fatComidasHoje));
        }
        if (tvFatTerceirosHoje != null) {
            tvFatTerceirosHoje.setText(VendasFaturamentoManager.formatarMoeda(fatTercHoje));
        }
        if (tvAbertosAgora != null) {
            if (abertosHoje > 0) {
                tvAbertosAgora.setText("🟢 " + VendasFaturamentoManager.formatarMoeda(abertosHoje) + " em mesas abertas agora (" + qtdAbertosHoje + " itens)");
                tvAbertosAgora.setTextColor(Color.parseColor("#FDE68A"));
            } else {
                tvAbertosAgora.setText("⚪ Nenhuma mesa aberta no momento");
                tvAbertosAgora.setTextColor(Color.parseColor("#94A3B8"));
            }
        }

        // 3. Faturamento Mês & Separação
        Calendar cal = Calendar.getInstance();
        String mesNome = new DateFormatSymbols(new Locale("pt", "BR")).getMonths()[cal.get(Calendar.MONTH)];
        if (mesNome != null && !mesNome.isEmpty()) {
            mesNome = mesNome.substring(0, 1).toUpperCase(Locale.getDefault()) + mesNome.substring(1);
        }
        int ano = cal.get(Calendar.YEAR);

        if (tvTituloMes != null) {
            tvTituloMes.setText("FATURAMENTO NO MÊS (" + mesNome.toUpperCase(Locale.getDefault()) + "/" + ano + ")");
        }

        double fatMes = vendasManager.getFaturamentoMes(cod);
        int qtdMes = vendasManager.getQtdItensMes(cod);
        double fatComMes = vendasManager.getFaturamentoComidasMes(cod);
        double fatTercMes = vendasManager.getFaturamentoTerceirosMes(cod);

        if (tvFaturamentoMes != null) {
            tvFaturamentoMes.setText(VendasFaturamentoManager.formatarMoeda(fatMes));
        }
        if (tvQtdItensMes != null) {
            tvQtdItensMes.setText(qtdMes + (qtdMes == 1 ? " venda acumulada no mês" : " vendas acumuladas no mês"));
        }
        if (tvFatComidasMes != null) {
            tvFatComidasMes.setText("🍽️ Comidas: " + VendasFaturamentoManager.formatarMoeda(fatComMes));
        }
        if (tvFatTerceirosMes != null) {
            tvFatTerceirosMes.setText("📦 Terceiros: " + VendasFaturamentoManager.formatarMoeda(fatTercMes));
        }

        // 4. Cancelamentos Hoje
        double cancHoje = cancelamentoManager.getTotalCanceladoHoje();
        int qtdCancHoje = cancelamentoManager.getQtdCanceladoHoje();

        if (tvCancelamentosHoje != null) {
            if (cancHoje > 0) {
                tvCancelamentosHoje.setText(VendasFaturamentoManager.formatarMoeda(cancHoje) + " (" + qtdCancHoje + (qtdCancHoje == 1 ? " item)" : " itens)"));
                tvCancelamentosHoje.setTextColor(Color.parseColor("#EF4444"));
            } else {
                tvCancelamentosHoje.setText("R$ 0,00 (0 itens cancelados)");
                tvCancelamentosHoje.setTextColor(Color.parseColor("#10B981"));
            }
        }

        // 5. Banner de Item Pendente de Confirmação
        atualizarBannerItemPendente();

        // 6. Histórico diário do mês
        renderizarHistoricoDias(cod);

        // 7. Histórico de cancelamentos confirmados
        renderizarHistoricoCancelamentos();
    }

    private void atualizarBadgeConferencia() {
        if (tvBadgeTabConferencia == null) return;
        int pendentes = conferenciaManager.getQtdPendentes();
        if (pendentes > 0) {
            tvBadgeTabConferencia.setText(String.valueOf(pendentes));
            tvBadgeTabConferencia.setVisibility(View.VISIBLE);
        } else {
            tvBadgeTabConferencia.setVisibility(View.GONE);
        }
    }

    /**
     * Renderiza as comandas que saíram do sistema para conferência
     */
    private synchronized void renderizarComandasConferencia() {
        if (layoutListaComandasConferencia == null) return;
        layoutListaComandasConferencia.removeAllViews();

        List<ComandaSaidaModel> comandas = apenasPendentesConferencia
                ? conferenciaManager.getComandasPendentes()
                : conferenciaManager.getTodasComandasSaidas();

        if (comandas.isEmpty()) {
            if (tvVazioConferencia != null) {
                tvVazioConferencia.setVisibility(View.VISIBLE);
                if (apenasPendentesConferencia) {
                    tvVazioConferencia.setText("Nenhuma saída pendente de conferência no momento.\nToque em 'Todas as Saídas' para ver o histórico recente.");
                } else {
                    tvVazioConferencia.setText("Nenhuma comanda saiu recentemente.\nQuando uma mesa for fechada no sistema, ela aparecerá aqui para você conferir.");
                }
            }
            return;
        }

        if (tvVazioConferencia != null) tvVazioConferencia.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        CatalogoClassificador classificador = CatalogoClassificador.getInstance(this);

        for (ComandaSaidaModel cmd : comandas) {
            View cardView = inflater.inflate(R.layout.item_comanda_conferencia, layoutListaComandasConferencia, false);

            TextView tvConfMesaCmd = cardView.findViewById(R.id.tvConfMesaCmd);
            TextView tvConfTotalValor = cardView.findViewById(R.id.tvConfTotalValor);
            TextView tvConfHorarioSaida = cardView.findViewById(R.id.tvConfHorarioSaida);
            TextView tvConfStatusBadge = cardView.findViewById(R.id.tvConfStatusBadge);
            TextView tvConfBadgeSuspeita = cardView.findViewById(R.id.tvConfBadgeSuspeita);
            LinearLayout layoutConfListaItens = cardView.findViewById(R.id.layoutConfListaItens);
            View layoutConfAcoesBotoes = cardView.findViewById(R.id.layoutConfAcoesBotoes);
            MaterialButton btnConfComandaTodaPaga = cardView.findViewById(R.id.btnConfComandaTodaPaga);
            MaterialButton btnConfCancelarTudo = cardView.findViewById(R.id.btnConfCancelarTudo);

            tvConfMesaCmd.setText(cmd.getIdentificadorComanda());
            tvConfTotalValor.setText(cmd.getTotalValorFormatado());

            String tempoTexto = "Saiu às " + cmd.getHoraSaida()
                    + (cmd.getDuracaoMinutos() > 0 ? (" • Aberta por " + cmd.getDuracaoMinutos() + " min") : "");
            tvConfHorarioSaida.setText(tempoTexto);

            // Alerta de Saída Suspeita (< 15 minutos)
            if (cmd.isEhSaidaSuspeita()) {
                tvConfBadgeSuspeita.setVisibility(View.VISIBLE);
                tvConfBadgeSuspeita.setText("⚠️ SAÍDA RÁPIDA (" + cmd.getDuracaoMinutos() + " min) — Verifique se a mesa cancelou");
                cardView.setBackgroundResource(R.drawable.bg_card_conferencia_suspeita);
            } else {
                tvConfBadgeSuspeita.setVisibility(View.GONE);
                cardView.setBackgroundResource(R.drawable.bg_card_conferencia);
            }

            // Status da conferência
            if (cmd.isPendente()) {
                tvConfStatusBadge.setText("🟡 PENDENTE");
                tvConfStatusBadge.setBackgroundResource(R.drawable.bg_dashboard_card_amber);
                tvConfStatusBadge.setTextColor(Color.parseColor("#92400E"));
                if (layoutConfAcoesBotoes != null) layoutConfAcoesBotoes.setVisibility(View.VISIBLE);
            } else if ("PAGA_INTEGRAL".equals(cmd.getStatusConferencia())) {
                tvConfStatusBadge.setText("✅ PAGA INTEGRAL");
                tvConfStatusBadge.setBackgroundResource(R.drawable.bg_badge_categoria_comida);
                tvConfStatusBadge.setTextColor(Color.parseColor("#047857"));
                if (layoutConfAcoesBotoes != null) layoutConfAcoesBotoes.setVisibility(View.GONE);
            } else if ("CANCELAMENTO_PARCIAL".equals(cmd.getStatusConferencia())) {
                tvConfStatusBadge.setText("⚠️ CANC. PARCIAL");
                tvConfStatusBadge.setBackgroundResource(R.drawable.bg_dashboard_card_amber);
                tvConfStatusBadge.setTextColor(Color.parseColor("#B45309"));
                if (layoutConfAcoesBotoes != null) layoutConfAcoesBotoes.setVisibility(View.GONE);
            } else {
                tvConfStatusBadge.setText("🚫 CANCELADA");
                tvConfStatusBadge.setBackgroundResource(R.drawable.bg_badge_notif);
                tvConfStatusBadge.setTextColor(Color.WHITE);
                if (layoutConfAcoesBotoes != null) layoutConfAcoesBotoes.setVisibility(View.GONE);
            }

            // Infla os itens desta comanda um abaixo do outro
            layoutConfListaItens.removeAllViews();
            for (ItemCanceladoModel it : cmd.getItens()) {
                View itemView = inflater.inflate(R.layout.item_linha_conferencia_item, layoutConfListaItens, false);
                TextView tvLinhaItemQtdNome = itemView.findViewById(R.id.tvLinhaItemQtdNome);
                TextView tvLinhaItemBadge = itemView.findViewById(R.id.tvLinhaItemBadge);
                TextView tvLinhaItemValor = itemView.findViewById(R.id.tvLinhaItemValor);
                TextView btnLinhaItemCancelar = itemView.findViewById(R.id.btnLinhaItemCancelar);
                TextView tvLinhaItemStatusCancelado = itemView.findViewById(R.id.tvLinhaItemStatusCancelado);

                tvLinhaItemQtdNome.setText(it.getQtde() + "x " + it.getDescricao());
                tvLinhaItemValor.setText(it.getValorFormatado());

                boolean ehComida = classificador.isComidaOuProducao(it.getCodProd(), it.getDescricao());
                if (ehComida) {
                    tvLinhaItemBadge.setText("🍽️ Comida / Produção");
                    tvLinhaItemBadge.setBackgroundResource(R.drawable.bg_badge_categoria_comida);
                    tvLinhaItemBadge.setTextColor(Color.parseColor("#047857"));
                } else {
                    tvLinhaItemBadge.setText("📦 Terceiro / Bebida");
                    tvLinhaItemBadge.setBackgroundResource(R.drawable.bg_badge_categoria_terceiro);
                    tvLinhaItemBadge.setTextColor(Color.parseColor("#475569"));
                }

                if (it.isConfirmadoCancelado()) {
                    btnLinhaItemCancelar.setVisibility(View.GONE);
                    tvLinhaItemStatusCancelado.setVisibility(View.VISIBLE);
                } else {
                    tvLinhaItemStatusCancelado.setVisibility(View.GONE);
                    btnLinhaItemCancelar.setVisibility(View.VISIBLE);

                    btnLinhaItemCancelar.setOnClickListener(v -> {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Confirmar Cancelamento do Item")
                                .setMessage("Deseja marcar como cancelado:\n\n" + it.getLinhaResumo() + "?\n\nO valor será deduzido do faturamento.")
                                .setPositiveButton("Sim, Cancelar Item", (dialog, which) -> {
                                    conferenciaManager.cancelarItemIndividual(cmd, it);
                                    Toast.makeText(MainActivity.this, "Item cancelado e deduzido!", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Voltar", null)
                                .show();
                    });
                }

                layoutConfListaItens.addView(itemView);
            }

            // Ações da Comanda Inteira
            if (btnConfComandaTodaPaga != null) {
                btnConfComandaTodaPaga.setOnClickListener(v -> {
                    conferenciaManager.confirmarComandaTodaPaga(cmd);
                    Toast.makeText(MainActivity.this, "Comanda confirmada como paga integral!", Toast.LENGTH_SHORT).show();
                });
            }

            if (btnConfCancelarTudo != null) {
                btnConfCancelarTudo.setOnClickListener(v -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Cancelar Todos os Itens da Comanda")
                            .setMessage("Tem certeza de que todos os produtos de " + cmd.getIdentificadorComanda() + " foram cancelados?")
                            .setPositiveButton("Sim, Cancelar Todos", (dialog, which) -> {
                                conferenciaManager.cancelarComandaToda(cmd);
                                Toast.makeText(MainActivity.this, "Todos os itens foram cancelados e deduzidos!", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Voltar", null)
                            .show();
                });
            }

            layoutListaComandasConferencia.addView(cardView);
        }
    }

    private void atualizarBannerItemPendente() {
        if (cardItemPendenteConfirmacao == null) return;

        List<ItemCanceladoModel> pendentes = cancelamentoManager.getItensPendentes();
        if (pendentes.isEmpty()) {
            cardItemPendenteConfirmacao.setVisibility(View.GONE);
            return;
        }

        ItemCanceladoModel itemAtual = pendentes.get(0);
        cardItemPendenteConfirmacao.setVisibility(View.VISIBLE);

        if (tvBadgeContadorPendentes != null) {
            tvBadgeContadorPendentes.setText(pendentes.size() + (pendentes.size() == 1 ? " pendente" : " pendentes"));
        }
        if (tvPendenteDescricao != null) {
            tvPendenteDescricao.setText(itemAtual.getLinhaResumo());
        }
        if (tvPendenteMesaComanda != null) {
            tvPendenteMesaComanda.setText(itemAtual.getIdentificadorComanda() + " • Detectado às " + itemAtual.getHoraFormatada());
        }

        if (btnConfirmarCancelado != null) {
            btnConfirmarCancelado.setOnClickListener(v -> {
                cancelamentoManager.confirmarCancelamento(itemAtual);
                Toast.makeText(MainActivity.this, "Cancelamento confirmado e registrado!", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnDescartarCancelado != null) {
            btnDescartarCancelado.setOnClickListener(v -> {
                cancelamentoManager.descartarCancelamento(itemAtual);
                Toast.makeText(MainActivity.this, "Item mantido como venda normal.", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void renderizarHistoricoDias(String codGarcom) {
        if (layoutHistoricoDias == null) return;
        layoutHistoricoDias.removeAllViews();

        List<VendasFaturamentoManager.DiaFaturamento> dias = vendasManager.getHistoricoDiasMes(codGarcom);
        if (dias.isEmpty()) {
            if (tvVazioDias != null) tvVazioDias.setVisibility(View.VISIBLE);
            return;
        }

        if (tvVazioDias != null) tvVazioDias.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (VendasFaturamentoManager.DiaFaturamento dia : dias) {
            View itemView = inflater.inflate(R.layout.item_historico_dia, layoutHistoricoDias, false);
            TextView tvDiaLabel = itemView.findViewById(R.id.tvDiaLabel);
            TextView tvDiaQtdItens = itemView.findViewById(R.id.tvDiaQtdItens);
            TextView tvDiaFaturamento = itemView.findViewById(R.id.tvDiaFaturamento);
            TextView tvDiaCancelado = itemView.findViewById(R.id.tvDiaCancelado);

            tvDiaLabel.setText(dia.labelExibicao);
            tvDiaQtdItens.setText(dia.qtdItens + (dia.qtdItens == 1 ? " produto vendido" : " produtos vendidos"));
            tvDiaFaturamento.setText(dia.getFaturamentoFormatado());

            if (dia.cancelado > 0) {
                tvDiaCancelado.setVisibility(View.VISIBLE);
                tvDiaCancelado.setText("- " + dia.getCanceladoFormatado() + " canc.");
            } else {
                tvDiaCancelado.setVisibility(View.GONE);
            }

            layoutHistoricoDias.addView(itemView);
        }
    }

    private void renderizarHistoricoCancelamentos() {
        if (layoutHistoricoCancelamentos == null) return;
        layoutHistoricoCancelamentos.removeAllViews();

        List<ItemCanceladoModel> confirmados = cancelamentoManager.getHistoricoConfirmados();
        if (confirmados.isEmpty()) {
            if (tvVazioCancelamentos != null) tvVazioCancelamentos.setVisibility(View.VISIBLE);
            return;
        }

        if (tvVazioCancelamentos != null) tvVazioCancelamentos.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        int maxExibir = Math.min(10, confirmados.size());
        for (int i = 0; i < maxExibir; i++) {
            ItemCanceladoModel c = confirmados.get(i);
            View itemView = inflater.inflate(R.layout.item_historico_cancelamento, layoutHistoricoCancelamentos, false);
            TextView tvCancDescricao = itemView.findViewById(R.id.tvCancDescricao);
            TextView tvCancMesaHora = itemView.findViewById(R.id.tvCancMesaHora);
            TextView tvCancValor = itemView.findViewById(R.id.tvCancValor);

            tvCancDescricao.setText(c.getQtde() + "x " + c.getDescricao());
            tvCancMesaHora.setText(c.getIdentificadorComanda() + " • " + c.getHoraFormatada() + " (" + c.getDataStr() + ")");
            tvCancValor.setText("- " + c.getValorFormatado());

            layoutHistoricoCancelamentos.addView(itemView);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DETECÇÃO DE CANCELAMENTO - PERGUNTA AO USUÁRIO
    // ═══════════════════════════════════════════════════════════════
    @Override
    public void onNovoItemParaConfirmar(ItemCanceladoModel item) {
        if (item == null) return;

        if (!isFinishing() && !isDestroyed()) {
            exibirDialogoPerguntaCancelamento(item);
        }
        atualizarDadosDashboard();
        atualizarBadgeConferencia();
    }

    @Override
    public void onCancelamentosAtualizados() {
        atualizarDadosDashboard();
        atualizarBadgeConferencia();
        if (abaConferenciaAtiva) {
            renderizarComandasConferencia();
        }
    }

    @Override
    public void onConferenciaAtualizada() {
        atualizarBadgeConferencia();
        if (abaConferenciaAtiva) {
            renderizarComandasConferencia();
        }
        atualizarDadosDashboard();
    }

    private void exibirDialogoPerguntaCancelamento(ItemCanceladoModel item) {
        if (isFinishing() || isDestroyed()) return;
        if (dialogCancelamentoAtivo != null && dialogCancelamentoAtivo.isShowing()) {
            return;
        }

        VibrationHelper.vibrateLongPress(this);

        String msg = "O produto abaixo não consta mais na comanda:\n\n"
                + "📦 " + item.getLinhaResumo() + "\n"
                + "📍 " + item.getIdentificadorComanda() + "\n\n"
                + "Este item foi realmente cancelado pelo cliente ou pelo gerente?";

        dialogCancelamentoAtivo = new AlertDialog.Builder(this)
                .setTitle("⚠️ Item Removido da Comanda")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("❌ Sim, foi Cancelado", (d, which) -> {
                    cancelamentoManager.confirmarCancelamento(item);
                    Toast.makeText(MainActivity.this, "Cancelamento registrado com sucesso!", Toast.LENGTH_SHORT).show();
                    dialogCancelamentoAtivo = null;
                })
                .setNegativeButton("✅ Não, foi Pago / Normal", (d, which) -> {
                    cancelamentoManager.descartarCancelamento(item);
                    Toast.makeText(MainActivity.this, "Item mantido como venda.", Toast.LENGTH_SHORT).show();
                    dialogCancelamentoAtivo = null;
                })
                .show();
    }

    private void exibirDialogoMetaDiaria() {
        if (isFinishing() || isDestroyed()) return;

        final EditText input = new EditText(this);
        input.setHint("Ex: 500");
        input.setText(String.format(Locale.US, "%.0f", vendasManager.getMetaDiaria()));
        input.setSelection(input.getText().length());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, pad / 2);

        TextView tvDica = new TextView(this);
        tvDica.setText("Defina a sua meta diária de vendas em Comidas, Pizzas, Lanches e Sucos Feitos na Hora:\n(Produtos de terceiros como refrigerantes em lata e cervejas não contam para esta meta).");
        tvDica.setTextSize(12.5f);
        tvDica.setTextColor(Color.parseColor("#64748B"));
        tvDica.setPadding(0, 0, 0, pad / 2);

        container.addView(tvDica);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("🎯 Meta Diária de Comidas")
                .setView(container)
                .setPositiveButton("Salvar", (d, which) -> {
                    try {
                        double novaMeta = Double.parseDouble(input.getText().toString().replace(",", "."));
                        vendasManager.setMetaDiaria(novaMeta);
                        Toast.makeText(MainActivity.this, "Meta atualizada para " + VendasFaturamentoManager.formatarMoeda(novaMeta), Toast.LENGTH_SHORT).show();
                        atualizarDadosDashboard();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Valor inválido.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void exibirDialogoConfigGarcom() {
        if (isFinishing() || isDestroyed()) return;

        final EditText input = new EditText(this);
        input.setHint("Ex: 223");
        String codAtual = ResumoGarcomManager.getCodigoGarcom(this);
        input.setText(codAtual);
        input.setSelection(input.getText().length());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, pad / 2);

        TextView tvDica = new TextView(this);
        tvDica.setText("Garçons cadastrados no sistema:\n• 223: Wanderson\n• 200: Cauã\n• 217: Miguel\n• 224: Lucas\n• 40: Kamila\n• 65: Geovana");
        tvDica.setTextSize(12.5f);
        tvDica.setTextColor(Color.parseColor("#64748B"));
        tvDica.setPadding(0, 0, 0, pad / 2);

        container.addView(tvDica);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("⚙️ Meu Código de Garçom")
                .setMessage("Digite o seu número para calcular suas vendas e monitorar cancelamentos:")
                .setView(container)
                .setPositiveButton("Salvar", (d, which) -> {
                    String novoCod = input.getText().toString().trim();
                    if (!novoCod.isEmpty()) {
                        ResumoGarcomManager.setCodigoGarcom(MainActivity.this, novoCod);
                        String nome = GarcomManager.getNomeGarcom(MainActivity.this, novoCod);
                        Toast.makeText(MainActivity.this, "Garçom definido: " + nome + " (#" + novoCod + ")", Toast.LENGTH_SHORT).show();
                        atualizarDadosDashboard();
                        if (monitorEngine != null) {
                            monitorEngine.forcarAtualizacaoImediata();
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // CALLBACKS DO MONITOR ENGINE
    // ═══════════════════════════════════════════════════════════════
    @Override
    public void onEstadoAlterado(EstadoServidor novoEstado, String mensagem) {
        runOnUiThread(() -> {
            if (tvStatusServidorPill == null) return;
            if (novoEstado == EstadoServidor.ONLINE) {
                tvStatusServidorPill.setText("● ONLINE");
                tvStatusServidorPill.setTextColor(Color.parseColor("#10B981"));
            } else if (novoEstado == EstadoServidor.CONNECTING) {
                tvStatusServidorPill.setText("● CONECTANDO");
                tvStatusServidorPill.setTextColor(Color.parseColor("#F59E0B"));
            } else if (novoEstado == EstadoServidor.ERROR) {
                tvStatusServidorPill.setText("● ERRO REDE");
                tvStatusServidorPill.setTextColor(Color.parseColor("#EF4444"));
            } else {
                tvStatusServidorPill.setText("● OFFLINE");
                tvStatusServidorPill.setTextColor(Color.parseColor("#94A3B8"));
            }
        });
    }

    @Override
    public void onComandasAtualizadas(List<ComandaCardModel> comandas, String ultimaSincronizacao) {
        runOnUiThread(() -> {
            if (tvUltimaSincronizacao != null && ultimaSincronizacao != null && !ultimaSincronizacao.isEmpty()) {
                tvUltimaSincronizacao.setText("⏱ Sincronizado às " + ultimaSincronizacao);
            }
            atualizarDadosDashboard();
            atualizarBadgeConferencia();
            if (abaConferenciaAtiva) {
                renderizarComandasConferencia();
            }
        });
    }

    private void verificarAtualizacaoApp() {
        try {
            UpdateChecker checker = new UpdateChecker(this);
            checker.verificarAtualizacao(new UpdateChecker.OnUpdateCheckListener() {
                @Override
                public void onUpdateAvailable(UpdateChecker.UpdateInfo info) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            UpdateChecker.exibirDialogoAtualizacao(MainActivity.this, info);
                        }
                    });
                }

                @Override
                public void onAlreadyUpToDate() {}

                @Override
                public void onNoVersionPublished() {}

                @Override
                public void onError(String erro) {}
            });
        } catch (Exception ignored) {}
    }

    private void solicitarPermissaoNotificacoes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }
}
