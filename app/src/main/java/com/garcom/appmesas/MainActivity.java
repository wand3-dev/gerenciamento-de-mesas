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
        CancelamentoDetectorManager.OnCancelamentoListener {

    // Views do Cabeçalho
    private TextView tvStatusServidorPill;
    private View btnAtualizar;
    private View btnConfigGarcom;
    private TextView tvNomeGarcomAtivo;
    private TextView tvUltimaSincronizacao;

    // Card de Alerta de Cancelamento Pendente (Aparece se um produto sumir da comanda)
    private View cardItemPendenteConfirmacao;
    private TextView tvBadgeContadorPendentes;
    private TextView tvPendenteDescricao;
    private TextView tvPendenteMesaComanda;
    private MaterialButton btnConfirmarCancelado;
    private MaterialButton btnDescartarCancelado;

    // Métricas de Vendas
    private TextView tvFaturamentoHoje;
    private TextView tvQtdItensHoje;
    private TextView tvAbertosAgora;
    private TextView tvTituloMes;
    private TextView tvFaturamentoMes;
    private TextView tvQtdItensMes;
    private TextView tvCancelamentosHoje;

    // Listas / Históricos
    private LinearLayout layoutHistoricoDias;
    private TextView tvVazioDias;
    private LinearLayout layoutHistoricoCancelamentos;
    private TextView tvVazioCancelamentos;

    // Motores e Gerenciadores
    private MonitorComandasEngine monitorEngine;
    private VendasFaturamentoManager vendasManager;
    private CancelamentoDetectorManager cancelamentoManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AlertDialog dialogCancelamentoAtivo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa instâncias
        monitorEngine = MonitorComandasEngine.getInstance(this);
        vendasManager = VendasFaturamentoManager.getInstance(this);
        cancelamentoManager = CancelamentoDetectorManager.getInstance(this);

        // Garante que o garçom padrão seja o 223 (Wanderson)
        String codAtual = ResumoGarcomManager.getCodigoGarcom(this);
        if (codAtual.isEmpty()) {
            ResumoGarcomManager.setCodigoGarcom(this, "223");
        }

        vincularViews();
        configurarBotoes();

        // Solicita permissão de notificações em Android 13+
        solicitarPermissaoNotificacoes();

        // Registra listeners de cancelamento e monitoramento
        cancelamentoManager.registrarListener(this);

        // Verifica atualizações remotas discretamente
        verificarAtualizacaoApp();

        // Atualiza a tela inicialmente com os dados salvos
        atualizarDadosDashboard();
    }

    private void vincularViews() {
        tvStatusServidorPill = findViewById(R.id.tvStatusServidorPill);
        btnAtualizar = findViewById(R.id.btnAtualizar);
        btnConfigGarcom = findViewById(R.id.btnConfigGarcom);
        tvNomeGarcomAtivo = findViewById(R.id.tvNomeGarcomAtivo);
        tvUltimaSincronizacao = findViewById(R.id.tvUltimaSincronizacao);

        cardItemPendenteConfirmacao = findViewById(R.id.cardItemPendenteConfirmacao);
        tvBadgeContadorPendentes = findViewById(R.id.tvBadgeContadorPendentes);
        tvPendenteDescricao = findViewById(R.id.tvPendenteDescricao);
        tvPendenteMesaComanda = findViewById(R.id.tvPendenteMesaComanda);
        btnConfirmarCancelado = findViewById(R.id.btnConfirmarCancelado);
        btnDescartarCancelado = findViewById(R.id.btnDescartarCancelado);

        tvFaturamentoHoje = findViewById(R.id.tvFaturamentoHoje);
        tvQtdItensHoje = findViewById(R.id.tvQtdItensHoje);
        tvAbertosAgora = findViewById(R.id.tvAbertosAgora);
        tvTituloMes = findViewById(R.id.tvTituloMes);
        tvFaturamentoMes = findViewById(R.id.tvFaturamentoMes);
        tvQtdItensMes = findViewById(R.id.tvQtdItensMes);
        tvCancelamentosHoje = findViewById(R.id.tvCancelamentosHoje);

        layoutHistoricoDias = findViewById(R.id.layoutHistoricoDias);
        tvVazioDias = findViewById(R.id.tvVazioDias);
        layoutHistoricoCancelamentos = findViewById(R.id.layoutHistoricoCancelamentos);
        tvVazioCancelamentos = findViewById(R.id.tvVazioCancelamentos);
    }

    private void configurarBotoes() {
        // Botão de atualização manual com animação suave de giro
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

        // Botão para ajustar o código do garçom
        if (btnConfigGarcom != null) {
            btnConfigGarcom.setOnClickListener(v -> exibirDialogoConfigGarcom());
        }
        if (tvNomeGarcomAtivo != null) {
            tvNomeGarcomAtivo.setOnClickListener(v -> exibirDialogoConfigGarcom());
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
    }

    /**
     * Atualiza os números, cards e históricos da interface
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

        // 1. Faturamento Hoje
        double fatHoje = vendasManager.getFaturamentoHoje(cod);
        int qtdHoje = vendasManager.getQtdItensHoje(cod);
        double abertosHoje = vendasManager.getFaturamentoAbertoAgora();
        int qtdAbertosHoje = vendasManager.getQtdItensAbertoAgora();

        if (tvFaturamentoHoje != null) {
            tvFaturamentoHoje.setText(VendasFaturamentoManager.formatarMoeda(fatHoje));
        }
        if (tvQtdItensHoje != null) {
            tvQtdItensHoje.setText(qtdHoje + (qtdHoje == 1 ? " produto vendido hoje" : " produtos vendidos hoje"));
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

        // 2. Faturamento Mês
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

        if (tvFaturamentoMes != null) {
            tvFaturamentoMes.setText(VendasFaturamentoManager.formatarMoeda(fatMes));
        }
        if (tvQtdItensMes != null) {
            tvQtdItensMes.setText(qtdMes + (qtdMes == 1 ? " venda acumulada no mês" : " vendas acumuladas no mês"));
        }

        // 3. Cancelamentos Hoje
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

        // 4. Banner de Item Pendente de Confirmação
        atualizarBannerItemPendente();

        // 5. Histórico diário do mês
        renderizarHistoricoDias(cod);

        // 6. Histórico de cancelamentos confirmados
        renderizarHistoricoCancelamentos();
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

        // Se a atividade estiver visível, abre o diálogo de pergunta imediatamente
        if (!isFinishing() && !isDestroyed()) {
            exibirDialogoPerguntaCancelamento(item);
        }
        atualizarDadosDashboard();
    }

    @Override
    public void onCancelamentosAtualizados() {
        atualizarDadosDashboard();
    }

    private void exibirDialogoPerguntaCancelamento(ItemCanceladoModel item) {
        if (isFinishing() || isDestroyed()) return;
        if (dialogCancelamentoAtivo != null && dialogCancelamentoAtivo.isShowing()) {
            // Já existe um diálogo aberto, o banner na tela tratará o próximo
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

    // ═══════════════════════════════════════════════════════════════
    // DIÁLOGO PARA CONFIGURAR CÓDIGO DO GARÇOM
    // ═══════════════════════════════════════════════════════════════
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
