package com.garcom.appmesas;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MesaDetailActivity extends AppCompatActivity {
    private int numeroMesa;
    private Mesa mesa;
    private MesaManager manager;
    private ServerComandasClient serverClient;

    private TextView tvTituloMesa, tvMesaTempoTotal;
    private View tvEmptyState;
    private TextView tvPendentesCount, tvEntreguesCount, tvComandasCount, tvHeaderTotalValor;
    private RecyclerView rvPedidos;
    private PedidosAdapter adapter;
    private MaterialButton btnAdicionarPedido, btnTrocarMesa, btnComandas, btnFecharMesa, btnSincronizarMesa;
    private ImageButton btnVoltar;

    // Abas de Bebidas Pendentes vs Comidas Pendentes
    private View layoutAbasSetor, tabBebidasPendentes, tabComidasPendentes, tabTodosItens;
    private TextView tvTabBebidasTitulo, tvTabComidasTitulo, tvTabTodosTitulo;
    private View layoutEmptyAba;
    private TextView tvIconeEmptyAba, tvMensagemEmptyAba;
    private View layoutAcaoLoteSetor;
    private MaterialButton btnEntregarTodosSetor;
    private int filtroSetor = 0; // 0 = Bebidas Pendentes, 1 = Comidas Pendentes, 2 = Todos
    private List<PedidoItem> listaExibicaoPedidos = new ArrayList<>();
    private boolean filtroInicialDefinido = false;

    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private boolean alertado15MinNestaMesa = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mesa_detail);

        numeroMesa = getIntent().getIntExtra("NUMERO_MESA", 1);
        manager = MesaManager.getInstance(this);
        serverClient = ServerComandasClient.getInstance(this);
        mesa = manager.getMesa(numeroMesa);
        if (mesa == null) {
            mesa = manager.getOuCriarMesa(numeroMesa);
        }

        tvTituloMesa = findViewById(R.id.tvTituloMesa);
        tvMesaTempoTotal = findViewById(R.id.tvMesaTempoTotal);
        tvPendentesCount = findViewById(R.id.tvPendentesCount);
        tvEntreguesCount = findViewById(R.id.tvEntreguesCount);
        tvComandasCount = findViewById(R.id.tvComandasCount);
        tvHeaderTotalValor = findViewById(R.id.tvHeaderTotalValor);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        rvPedidos = findViewById(R.id.rvPedidos);

        layoutAbasSetor = findViewById(R.id.layoutAbasSetor);
        tabBebidasPendentes = findViewById(R.id.tabBebidasPendentes);
        tabComidasPendentes = findViewById(R.id.tabComidasPendentes);
        tabTodosItens = findViewById(R.id.tabTodosItens);
        tvTabBebidasTitulo = findViewById(R.id.tvTabBebidasTitulo);
        tvTabComidasTitulo = findViewById(R.id.tvTabComidasTitulo);
        tvTabTodosTitulo = findViewById(R.id.tvTabTodosTitulo);
        layoutEmptyAba = findViewById(R.id.layoutEmptyAba);
        tvIconeEmptyAba = findViewById(R.id.tvIconeEmptyAba);
        tvMensagemEmptyAba = findViewById(R.id.tvMensagemEmptyAba);

        if (tabBebidasPendentes != null) tabBebidasPendentes.setOnClickListener(v -> setFiltroSetor(0));
        if (tabComidasPendentes != null) tabComidasPendentes.setOnClickListener(v -> setFiltroSetor(1));
        if (tabTodosItens != null) tabTodosItens.setOnClickListener(v -> setFiltroSetor(2));

        layoutAcaoLoteSetor = findViewById(R.id.layoutAcaoLoteSetor);
        btnEntregarTodosSetor = findViewById(R.id.btnEntregarTodosSetor);
        if (btnEntregarTodosSetor != null) {
            btnEntregarTodosSetor.setOnClickListener(v -> entregarTodosDoSetorAtual());
        }

        View root = findViewById(R.id.rootLayoutDetail);
        boolean isDark = ThemeManager.isDarkMode(this);
        if (root != null) {
            root.setBackgroundColor(Color.parseColor(isDark ? "#090D16" : "#F1F5F9"));
        }

        btnAdicionarPedido = findViewById(R.id.btnAdicionarPedido);
        MaterialButton btnPuxarComandaEmpty = findViewById(R.id.btnPuxarComandaEmpty);
        btnSincronizarMesa = findViewById(R.id.btnSincronizarMesa);
        btnTrocarMesa = findViewById(R.id.btnTrocarMesa);
        btnComandas = findViewById(R.id.btnComandas);
        btnFecharMesa = findViewById(R.id.btnFecharMesa);
        btnVoltar = findViewById(R.id.btnVoltar);

        tvTituloMesa.setText(String.format("MESA %02d", numeroMesa));

        rvPedidos.setLayoutManager(new LinearLayoutManager(this));
        rvPedidos.setHasFixedSize(true);
        adapter = new PedidosAdapter(listaExibicaoPedidos, new PedidosAdapter.OnPedidoActionListener() {
            @Override
            public void onEntregueClick(PedidoItem item, int position) {
                boolean novoEstado = !item.isEntregue();
                item.setEntregue(novoEstado);
                VibrationHelper.vibrateTick(MesaDetailActivity.this);
                manager.salvarMesas(MesaDetailActivity.this);

                // Se todos os itens da mesa foram entregues, cancela a notificação de atraso
                if (novoEstado) {
                    boolean todosEntregues = true;
                    for (PedidoItem p : mesa.getPedidos()) {
                        if (!p.isEntregue()) {
                            todosEntregues = false;
                            break;
                        }
                    }
                    if (todosEntregues) {
                        NotificationHelper.cancelarAlertaAtraso(MesaDetailActivity.this, numeroMesa);
                    }

                    Snackbar snackbar = Snackbar.make(rvPedidos, item.getDescricao() + " entregue!", Snackbar.LENGTH_LONG);
                    snackbar.setAction("DESFAZER", v -> {
                        item.setEntregue(false);
                        VibrationHelper.vibrateTick(MesaDetailActivity.this);
                        manager.salvarMesas(MesaDetailActivity.this);
                        sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
                        atualizarUI();
                    });
                    snackbar.setActionTextColor(Color.parseColor("#FCD34D"));
                    snackbar.show();
                }

                sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
                atualizarUI();
            }

            @Override
            public void onRemoverClick(PedidoItem item, int position) {
                new AlertDialog.Builder(MesaDetailActivity.this)
                        .setTitle("Desvincular Comanda")
                        .setMessage("Deseja desvincular a comanda '" + item.getComanda() + "' da Mesa " + numeroMesa + "?")
                        .setPositiveButton("Desvincular", (d, w) -> {
                            mesa.removerPedido(item.getId());
                            manager.salvarMesas(MesaDetailActivity.this);
                            sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
                            atualizarUI();
                            Toast.makeText(MesaDetailActivity.this, "Comanda desvinculada.", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }

            @Override
            public void onAdiar2MinClick(PedidoItem item, int position) {
                String comanda = item.getComanda();
                for (PedidoItem p : mesa.getPedidos()) {
                    if (!p.isEntregue()) {
                        if (comanda != null && !comanda.isEmpty() && comanda.equals(p.getComanda())) {
                            p.adiarMaisDoisMinutos();
                        } else if (p.getId().equals(item.getId())) {
                            p.adiarMaisDoisMinutos();
                        }
                    }
                }
                manager.salvarMesas(MesaDetailActivity.this);
                NotificationHelper.cancelarAlertaAtraso(MesaDetailActivity.this, numeroMesa);
                VibrationHelper.vibrateTick(MesaDetailActivity.this);
                sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
                atualizarUI();
                Toast.makeText(MesaDetailActivity.this, "⏱️ +2 minutos adicionados!", Toast.LENGTH_SHORT).show();
            }
        });
        rvPedidos.setAdapter(adapter);

        btnVoltar.setOnClickListener(v -> finish());
        btnAdicionarPedido.setOnClickListener(v -> exibirModalVincularComandaServidor());
        if (btnPuxarComandaEmpty != null) {
            btnPuxarComandaEmpty.setOnClickListener(v -> exibirModalVincularComandaServidor());
        }
        btnSincronizarMesa.setOnClickListener(v -> sincronizarComandasDaMesa());
        btnTrocarMesa.setOnClickListener(v -> exibirSelecaoComandasParaTransferir());
        btnComandas.setOnClickListener(v -> exibirGerenciadorComandas());

        btnFecharMesa.setOnClickListener(v -> {
            if (!mesa.isAberta()) {
                Toast.makeText(this, "Esta mesa já está livre!", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Fechar Mesa " + numeroMesa)
                    .setMessage("Deseja fechar esta mesa e liberar o atendimento?")
                    .setPositiveButton("Sim, Fechar", (d, w) -> {
                        String totalStr = mesa.getValorTotalFormatado();
                        mesa.setAberta(false);
                        mesa.getPedidos().clear();
                        manager.salvarMesas(MesaDetailActivity.this);
                        manager.removerMesaDinamicaSeVazia(MesaDetailActivity.this, numeroMesa);
                        NotificationHelper.cancelarAlertaAtraso(MesaDetailActivity.this, numeroMesa);
                        NotificationHelper.notificarMesaLiberada(MesaDetailActivity.this, numeroMesa, totalStr);
                        sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
                        Toast.makeText(this, "Mesa " + numeroMesa + " fechada e liberada!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                atualizarTempos();
                sincronizarComandasDaMesaSilencioso();
                timerHandler.postDelayed(this, 10000); // 10 segundos
            }
        };
    }

    private android.content.BroadcastReceiver pedidosReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            mesa = manager.getMesa(numeroMesa);
            atualizarUI();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        mesa = manager.getMesa(numeroMesa);
        atualizarUI();
        timerHandler.post(timerRunnable);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pedidosReceiver, new android.content.IntentFilter(MesaManager.ACTION_PEDIDOS_ATUALIZADOS), android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pedidosReceiver, new android.content.IntentFilter(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
        try {
            unregisterReceiver(pedidosReceiver);
        } catch (Exception ignored) {}
    }

    private void setFiltroSetor(int filtro) {
        filtroSetor = filtro;
        boolean isDark = ThemeManager.isDarkMode(this);

        if (tabBebidasPendentes != null) {
            tabBebidasPendentes.setBackgroundResource(filtro == 0 ? R.drawable.bg_pill_comanda : 0);
            if (filtro != 0) tabBebidasPendentes.setBackgroundColor(Color.parseColor(isDark ? "#131E2E" : "#1E293B"));
            if (tvTabBebidasTitulo != null) tvTabBebidasTitulo.setTextColor(Color.parseColor(filtro == 0 ? "#92400E" : "#94A3B8"));
        }

        if (tabComidasPendentes != null) {
            tabComidasPendentes.setBackgroundResource(filtro == 1 ? R.drawable.bg_pill_comanda : 0);
            if (filtro != 1) tabComidasPendentes.setBackgroundColor(Color.parseColor(isDark ? "#131E2E" : "#1E293B"));
            if (tvTabComidasTitulo != null) tvTabComidasTitulo.setTextColor(Color.parseColor(filtro == 1 ? "#92400E" : "#94A3B8"));
        }

        if (tabTodosItens != null) {
            tabTodosItens.setBackgroundResource(filtro == 2 ? R.drawable.bg_pill_comanda : 0);
            if (filtro != 2) tabTodosItens.setBackgroundColor(Color.parseColor(isDark ? "#131E2E" : "#1E293B"));
            if (tvTabTodosTitulo != null) tvTabTodosTitulo.setTextColor(Color.parseColor(filtro == 2 ? "#92400E" : "#94A3B8"));
        }

        filtrarEAtualizarLista();
    }

    private void filtrarEAtualizarLista() {
        listaExibicaoPedidos.clear();

        if (filtroSetor == 0) {
            // Bebidas Pendentes
            for (PedidoItem p : mesa.getPedidos()) {
                if (!p.isEntregue() && p.isBebida()) {
                    listaExibicaoPedidos.add(p);
                }
            }
            if (listaExibicaoPedidos.isEmpty()) {
                if (layoutEmptyAba != null) {
                    layoutEmptyAba.setVisibility(View.VISIBLE);
                    if (tvIconeEmptyAba != null) tvIconeEmptyAba.setText("☕");
                    if (tvMensagemEmptyAba != null) tvMensagemEmptyAba.setText("✓ Nenhuma bebida pendente nesta mesa!");
                }
                rvPedidos.setVisibility(View.GONE);
            } else {
                if (layoutEmptyAba != null) layoutEmptyAba.setVisibility(View.GONE);
                rvPedidos.setVisibility(View.VISIBLE);
            }
        } else if (filtroSetor == 1) {
            // Comidas Pendentes
            for (PedidoItem p : mesa.getPedidos()) {
                if (!p.isEntregue() && p.isComida()) {
                    listaExibicaoPedidos.add(p);
                }
            }
            if (listaExibicaoPedidos.isEmpty()) {
                if (layoutEmptyAba != null) {
                    layoutEmptyAba.setVisibility(View.VISIBLE);
                    if (tvIconeEmptyAba != null) tvIconeEmptyAba.setText("🍳");
                    if (tvMensagemEmptyAba != null) tvMensagemEmptyAba.setText("✓ Nenhuma comida pendente nesta mesa!");
                }
                rvPedidos.setVisibility(View.GONE);
            } else {
                if (layoutEmptyAba != null) layoutEmptyAba.setVisibility(View.GONE);
                rvPedidos.setVisibility(View.VISIBLE);
            }
        } else {
            // Todos os itens
            listaExibicaoPedidos.addAll(mesa.getPedidos());
            if (layoutEmptyAba != null) layoutEmptyAba.setVisibility(View.GONE);
            rvPedidos.setVisibility(View.VISIBLE);
        }

        // Atualiza botão de entrega rápida em lote
        if (layoutAcaoLoteSetor != null && btnEntregarTodosSetor != null) {
            int pendentesNestaAba = 0;
            for (PedidoItem p : listaExibicaoPedidos) {
                if (!p.isEntregue()) pendentesNestaAba++;
            }

            if (pendentesNestaAba > 0) {
                layoutAcaoLoteSetor.setVisibility(View.VISIBLE);
                if (filtroSetor == 0) {
                    btnEntregarTodosSetor.setText("✓ ENTREGAR TODAS AS BEBIDAS (" + pendentesNestaAba + ")");
                } else if (filtroSetor == 1) {
                    btnEntregarTodosSetor.setText("✓ ENTREGAR TODAS AS COMIDAS (" + pendentesNestaAba + ")");
                } else {
                    btnEntregarTodosSetor.setText("✓ ENTREGAR TODOS OS PENDENTES (" + pendentesNestaAba + ")");
                }
            } else {
                layoutAcaoLoteSetor.setVisibility(View.GONE);
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void entregarTodosDoSetorAtual() {
        if (mesa == null || mesa.getPedidos() == null) return;

        List<PedidoItem> itensParaEntregar = new ArrayList<>();
        for (PedidoItem p : mesa.getPedidos()) {
            if (!p.isEntregue()) {
                if (filtroSetor == 0 && p.isBebida()) {
                    itensParaEntregar.add(p);
                } else if (filtroSetor == 1 && p.isComida()) {
                    itensParaEntregar.add(p);
                } else if (filtroSetor == 2) {
                    itensParaEntregar.add(p);
                }
            }
        }

        if (itensParaEntregar.isEmpty()) return;

        for (PedidoItem p : itensParaEntregar) {
            p.setEntregue(true);
        }

        VibrationHelper.vibrateSuccess(this);
        manager.salvarMesas(this);

        // Se todos os itens da mesa foram entregues, cancela a notificação de atraso
        boolean todosEntregues = true;
        for (PedidoItem p : mesa.getPedidos()) {
            if (!p.isEntregue()) {
                todosEntregues = false;
                break;
            }
        }
        if (todosEntregues) {
            NotificationHelper.cancelarAlertaAtraso(this, numeroMesa);
        }

        sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));

        int qtdEntregue = itensParaEntregar.size();
        String tipoStr = (filtroSetor == 0) ? "bebidas" : (filtroSetor == 1) ? "comidas" : "itens";
        Snackbar snackbar = Snackbar.make(rvPedidos, qtdEntregue + " " + tipoStr + " marcadas como entregues!", Snackbar.LENGTH_LONG);
        snackbar.setAction("DESFAZER", v -> {
            for (PedidoItem p : itensParaEntregar) {
                p.setEntregue(false);
            }
            VibrationHelper.vibrateTick(MesaDetailActivity.this);
            manager.salvarMesas(MesaDetailActivity.this);
            sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
            atualizarUI();
        });
        snackbar.setActionTextColor(Color.parseColor("#FCD34D"));
        snackbar.show();

        // Se estava na aba de bebidas e ainda há comidas pendentes, salta automaticamente para comidas!
        if (filtroSetor == 0) {
            int comidasRestantes = 0;
            for (PedidoItem p : mesa.getPedidos()) {
                if (!p.isEntregue() && p.isComida()) comidasRestantes++;
            }
            if (comidasRestantes > 0) {
                setFiltroSetor(1);
            } else {
                atualizarUI();
            }
        } else {
            atualizarUI();
        }
    }

    private void atualizarUI() {
        if (mesa.getPedidos().isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            if (layoutAbasSetor != null) layoutAbasSetor.setVisibility(View.GONE);
            if (layoutAcaoLoteSetor != null) layoutAcaoLoteSetor.setVisibility(View.GONE);
            if (layoutEmptyAba != null) layoutEmptyAba.setVisibility(View.GONE);
            rvPedidos.setVisibility(View.GONE);
            tvMesaTempoTotal.setText("--:--");
            tvHeaderTotalValor.setText("R$ 0,00");
            tvComandasCount.setText("0");
        } else {
            tvEmptyState.setVisibility(View.GONE);
            if (layoutAbasSetor != null) layoutAbasSetor.setVisibility(View.VISIBLE);
            tvMesaTempoTotal.setText(mesa.getTempoMesaFormatado());
            tvHeaderTotalValor.setText(mesa.getTotalValorFormatado());
            tvComandasCount.setText(String.valueOf(mesa.getComandasUnicas().size()));
        }

        int totalBebidasPendentes = 0;
        int totalComidasPendentes = 0;
        for (PedidoItem it : mesa.getPedidos()) {
            if (!it.isEntregue()) {
                if (it.isBebida()) totalBebidasPendentes++;
                else totalComidasPendentes++;
            }
        }

        if (tvTabBebidasTitulo != null) tvTabBebidasTitulo.setText("☕ BEBIDAS (" + totalBebidasPendentes + ")");
        if (tvTabComidasTitulo != null) tvTabComidasTitulo.setText("🍳 COMIDAS (" + totalComidasPendentes + ")");
        if (tvTabTodosTitulo != null) tvTabTodosTitulo.setText("TODOS (" + mesa.getPedidos().size() + ")");

        if (!filtroInicialDefinido && !mesa.getPedidos().isEmpty()) {
            filtroInicialDefinido = true;
            if (totalBebidasPendentes > 0) {
                filtroSetor = 0;
            } else if (totalComidasPendentes > 0) {
                filtroSetor = 1;
            } else {
                filtroSetor = 2;
            }
            setFiltroSetor(filtroSetor);
        } else {
            setFiltroSetor(filtroSetor);
        }

        atualizarContadores();
    }

    private void atualizarContadores() {
        int pendentes = 0;
        int entregues = 0;
        for (PedidoItem it : mesa.getPedidos()) {
            if (it.isEntregue()) entregues++;
            else pendentes++;
        }
        tvPendentesCount.setText(String.valueOf(pendentes));
        tvEntreguesCount.setText(String.valueOf(entregues));
    }

    private void atualizarTempos() {
        if (mesa.isAberta()) {
            tvMesaTempoTotal.setText(mesa.getTempoMesaFormatado());

            // Verifica se algum item pendente ultrapassou 15 minutos
            long agora = System.currentTimeMillis();
            boolean temAtrasado15Min = false;
            for (PedidoItem item : mesa.getPedidos()) {
                if (!item.isEntregue() && (agora - item.getTimestamp() >= 15 * 60 * 1000)) {
                    temAtrasado15Min = true;
                    break;
                }
            }

            if (temAtrasado15Min) {
                if (!alertado15MinNestaMesa) {
                    alertado15MinNestaMesa = true;
                    NotificationHelper.alertarPedidoAtrasado15Min(this, numeroMesa);
                    Toast.makeText(this, "⚠️ Comanda esperando há mais de 15 minutos na Mesa " + numeroMesa + "!", Toast.LENGTH_LONG).show();
                }
            } else {
                alertado15MinNestaMesa = false;
            }
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Modal para buscar e vincular itens do servidor nesta mesa específica
     */
    private void exibirModalVincularComandaServidor() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_buscar_mesa_servidor);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitulo = dialog.findViewById(R.id.tvTituloDialogServidor);
        TextView tvSubtitulo = dialog.findViewById(R.id.tvSubtituloDialogServidor);
        EditText etMesa = dialog.findViewById(R.id.etDialogMesa);
        EditText etComanda = dialog.findViewById(R.id.etDialogComanda);
        TextView tvStatus = dialog.findViewById(R.id.tvStatusBuscaServidor);
        Button btnCancelar = dialog.findViewById(R.id.btnCancelarBuscarServidor);
        Button btnBuscar = dialog.findViewById(R.id.btnConfirmarBuscarServidor);

        if (tvTitulo != null) tvTitulo.setText("📥 PUXAR COMANDA - MESA " + numeroMesa);
        if (tvSubtitulo != null) tvSubtitulo.setText("Digite a comanda desejada ou deixe vazio para puxar todas desta mesa.");
        if (btnBuscar != null) btnBuscar.setText("Puxar Comanda");

        // Trava o número da mesa com a mesa atual desta tela
        etMesa.setText(String.valueOf(numeroMesa));
        etMesa.setEnabled(false);

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnBuscar.setOnClickListener(v -> {
            final String comandaFiltro = etComanda.getText().toString().trim();

            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setTextColor(Color.parseColor("#D97706"));
            if (!comandaFiltro.isEmpty()) {
                tvStatus.setText("Procurando Comanda #" + comandaFiltro + " nas mesas...");
                serverClient.buscarComandaVarrendoMesas(comandaFiltro, numeroMesa, new ServerComandasClient.OnComandaEncontradaListener() {
                    @Override
                    public void onEncontrada(int mesaOrigem, List<JSONObject> itensComanda) {
                        btnBuscar.setEnabled(true);
                        tvStatus.setVisibility(View.GONE);
                        importarItensDoServidor(itensComanda, comandaFiltro);
                        dialog.dismiss();
                    }

                    @Override
                    public void onNotFound(String mensagem) {
                        btnBuscar.setEnabled(true);
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setTextColor(Color.parseColor("#EF4444"));
                        tvStatus.setText(mensagem);
                    }

                    @Override
                    public void onError(String erro) {
                        btnBuscar.setEnabled(true);
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setTextColor(Color.parseColor("#EF4444"));
                        tvStatus.setText(erro);
                    }
                });
            } else {
                tvStatus.setText("Consultando itens da Mesa " + numeroMesa + "...");
                serverClient.buscarItensDaMesa(numeroMesa, new ServerComandasClient.OnItensMesaLoadedListener() {
                    @Override
                    public void onSuccess(List<JSONObject> itens) {
                        btnBuscar.setEnabled(true);
                        tvStatus.setVisibility(View.GONE);
                        importarItensDoServidor(itens, "");
                        dialog.dismiss();
                    }

                    @Override
                    public void onEmpty() {
                        btnBuscar.setEnabled(true);
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setTextColor(Color.parseColor("#EF4444"));
                        tvStatus.setText("Nenhum item aberto encontrado para a Mesa " + numeroMesa + ".");
                    }

                    @Override
                    public void onError(String erro) {
                        btnBuscar.setEnabled(true);
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setTextColor(Color.parseColor("#EF4444"));
                        tvStatus.setText(erro);
                    }
                });
            }
        });

        dialog.show();
        etComanda.requestFocus();
    }

    private void importarItensDoServidor(List<JSONObject> itensJson, String comandaFiltro) {
        if (itensJson == null || itensJson.isEmpty()) {
            Toast.makeText(this, "Nenhum item retornado do servidor.", Toast.LENGTH_SHORT).show();
            return;
        }

        int adicionados = 0;
        for (JSONObject obj : itensJson) {
            PedidoItem novoItem = PedidoItem.fromServerJson(obj);

            // Se o usuário informou um filtro de comanda, aplica o filtro
            if (!comandaFiltro.isEmpty() && !comandaFiltro.equals(novoItem.getComanda())) {
                continue;
            }

            // Verifica se este item específico já existe na mesa (por AUTONUM / ID)
            boolean jaExiste = false;
            for (PedidoItem existente : mesa.getPedidos()) {
                if (existente.getId().equals(novoItem.getId())) {
                    jaExiste = true;
                    // Mantém status de entregue se já foi marcado pelo garçom
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

        manager.salvarMesas(this);
        sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
        atualizarUI();

        if (adicionados > 0) {
            NotificationHelper.notificarNovoItem(this, "Itens Importados!", "Mesa " + numeroMesa + ": " + adicionados + " novo(s) item(ns) importado(s).");
            Toast.makeText(this, "✓ " + adicionados + " novo(s) item(ns) importado(s) na Mesa " + numeroMesa + "!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "✓ Itens da Mesa " + numeroMesa + " já estão sincronizados.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Sincronização manual acionada pelo botão 🔄 SINCRONIZAR
     */
    private void sincronizarComandasDaMesa() {
        btnSincronizarMesa.setEnabled(false);
        btnSincronizarMesa.setText("🔄 CONSULTANDO...");

        serverClient.buscarItensDaMesa(numeroMesa, new ServerComandasClient.OnItensMesaLoadedListener() {
            @Override
            public void onSuccess(List<JSONObject> itens) {
                btnSincronizarMesa.setEnabled(true);
                btnSincronizarMesa.setText("🔄 SINCRONIZAR");

                boolean atualizou = processarAtualizacaoItensServidor(itens);
                if (atualizou) {
                    Toast.makeText(MesaDetailActivity.this, "✓ Dados atualizados do servidor!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MesaDetailActivity.this, "✓ Mesa já sincronizada com o servidor.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onEmpty() {
                btnSincronizarMesa.setEnabled(true);
                btnSincronizarMesa.setText("🔄 SINCRONIZAR");

                if (mesa.isAberta() && !mesa.getPedidos().isEmpty()) {
                    mesa.setAberta(false);
                    mesa.getPedidos().clear();
                    manager.salvarMesas(MesaDetailActivity.this);
                    atualizarUI();
                    Toast.makeText(MesaDetailActivity.this, "✓ Mesa " + numeroMesa + " finalizada no caixa da padaria.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MesaDetailActivity.this, "Nenhum item aberto para a Mesa " + numeroMesa + " no servidor.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String erro) {
                btnSincronizarMesa.setEnabled(true);
                btnSincronizarMesa.setText("🔄 SINCRONIZAR");
                Toast.makeText(MesaDetailActivity.this, "Erro ao sincronizar: " + erro, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sincronizarComandasDaMesaSilencioso() {
        serverClient.buscarItensDaMesa(numeroMesa, new ServerComandasClient.OnItensMesaLoadedListener() {
            @Override
            public void onSuccess(List<JSONObject> itens) {
                processarAtualizacaoItensServidor(itens);
            }

            @Override
            public void onEmpty() {
                if (mesa.isAberta() && !mesa.getPedidos().isEmpty()) {
                    mesa.setAberta(false);
                    mesa.getPedidos().clear();
                    manager.salvarMesas(MesaDetailActivity.this);
                    atualizarUI();
                    Toast.makeText(MesaDetailActivity.this, "✓ Mesa " + numeroMesa + " finalizada no caixa da padaria.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String erro) {}
        });
    }

    private boolean processarAtualizacaoItensServidor(List<JSONObject> itensServidor) {
        if (itensServidor == null) return false;
        boolean houveNovidade = false;
        java.util.Set<String> idsAtivosServidor = new java.util.HashSet<>();
        List<String> comandasMonitoradas = mesa.getComandasUnicas();
        boolean filtrarPorComandas = !comandasMonitoradas.isEmpty();

        for (JSONObject objServidor : itensServidor) {
            PedidoItem itemServ = PedidoItem.fromServerJson(objServidor);
            String comandaItem = itemServ.getComanda();

            // Se a mesa já possui comandas vinculadas, respeita o filtro
            // Se estava vazia, aceita todos os itens que o servidor retornou para esta mesa
            if (filtrarPorComandas && !comandasMonitoradas.contains(comandaItem) && !comandaItem.equals(String.valueOf(numeroMesa))) {
                continue;
            }

            idsAtivosServidor.add(itemServ.getId());

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
                // Item novinho lançado na mesa/comanda!
                mesa.adicionarPedido(itemServ);
                houveNovidade = true;
                NotificationHelper.notificarNovoItem(this, "Novo Item!",
                        "Mesa " + numeroMesa + ": " + itemServ.getDescricao() + " (Cmd #" + itemServ.getComanda() + ")");
                Toast.makeText(this, "🔔 Mesa " + numeroMesa + " recebeu: " + itemServ.getDescricao(), Toast.LENGTH_SHORT).show();
            }
        }

        // Remove itens do servidor que não existem mais (estornados/pagos)
        // IMPORTANTE: apenas remove itens que vieram do servidor (autonum preenchido).
        // Itens manuais inseridos pelo garçom (autonum vazio) são preservados!
        for (int i = mesa.getPedidos().size() - 1; i >= 0; i--) {
            PedidoItem it = mesa.getPedidos().get(i);
            if (it.getAutonum() != null && !it.getAutonum().isEmpty()) {
                if (!idsAtivosServidor.contains(it.getId())) {
                    mesa.getPedidos().remove(i);
                    houveNovidade = true;
                }
            }
        }

        // Se todos os itens sumiram, fecha a mesa
        if (mesa.getPedidos().isEmpty() && mesa.isAberta()) {
            mesa.setAberta(false);
            manager.removerMesaDinamicaSeVazia(this, numeroMesa);
            houveNovidade = true;
            Toast.makeText(this, "✓ Mesa " + numeroMesa + " liberada automaticamente!", Toast.LENGTH_SHORT).show();
        }

        if (houveNovidade) {
            manager.salvarMesas(this);
            sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
            atualizarUI();
        }
        return houveNovidade;
    }

    // ── GERENCIAMENTO E TRANSFERÊNCIA DE COMANDAS ──────────────────────

    private void exibirGerenciadorComandas() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), 0);

        ScrollView scroll = new ScrollView(this);
        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(lista);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(280));
        scrollParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(scroll, scrollParams);

        AlertDialog painel = new AlertDialog.Builder(this)
                .setTitle("Comandas na Mesa " + numeroMesa)
                .setView(root)
                .setNegativeButton("Fechar", null)
                .create();

        renderizarComandas(lista, painel);
        painel.show();
    }

    private void renderizarComandas(LinearLayout lista, AlertDialog painel) {
        lista.removeAllViews();
        List<String> comandas = mesa.getComandasUnicas();
        if (comandas.isEmpty()) {
            TextView vazio = new TextView(this);
            vazio.setText("Nenhuma comanda vinculada nesta mesa.");
            vazio.setTextColor(Color.LTGRAY);
            vazio.setPadding(0, dp(16), 0, dp(16));
            lista.addView(vazio);
            return;
        }

        for (String comanda : comandas) {
            PedidoItem refItem = null;
            for (PedidoItem p : mesa.getPedidos()) {
                if (comanda.equals(p.getComanda())) {
                    refItem = p;
                    break;
                }
            }

            MaterialButton item = new MaterialButton(this);
            item.setAllCaps(false);
            String info = "Comanda #" + comanda;
            if (refItem != null) {
                info += " • " + refItem.getQuantidade() + " itens • R$ " + refItem.getValorTotal();
            }
            item.setText(info);
            item.setOnClickListener(v -> exibirAcoesComanda(comanda, painel));
            lista.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        }
    }

    private void exibirAcoesComanda(String comanda, AlertDialog painel) {
        String titulo = "Comanda #" + comanda;
        String[] acoes = {"Marcar Entregue", "Transferir para outra mesa", "Desvincular comanda"};
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setItems(acoes, (dialog, which) -> {
                    if (which == 0) {
                        mesa.marcarComandaEntregue(comanda);
                        manager.salvarMesas(this);
                        atualizarUI();
                        painel.dismiss();
                    } else if (which == 1) {
                        painel.dismiss();
                        exibirSelecaoMesaDestino(comanda);
                    } else if (which == 2) {
                        mesa.removerComanda(comanda);
                        manager.salvarMesas(this);
                        atualizarUI();
                        painel.dismiss();
                        Toast.makeText(this, "Comanda #" + comanda + " removida da mesa.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void exibirSelecaoComandasParaTransferir() {
        List<String> comandas = mesa.getComandasUnicas();
        if (comandas.isEmpty()) {
            Toast.makeText(this, "Não há comandas para transferir.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] opcoes = new String[comandas.size()];
        boolean[] marcadas = new boolean[comandas.size()];
        for (int i = 0; i < comandas.size(); i++) opcoes[i] = "COMANDA #" + comandas.get(i);

        new AlertDialog.Builder(this)
                .setTitle("Selecione as comandas")
                .setMultiChoiceItems(opcoes, marcadas, (dialog, which, checked) -> marcadas[which] = checked)
                .setPositiveButton("Escolher mesa destino", (dialog, which) -> {
                    List<String> selecionadas = new ArrayList<>();
                    for (int i = 0; i < comandas.size(); i++) if (marcadas[i]) selecionadas.add(comandas.get(i));
                    if (selecionadas.isEmpty()) Toast.makeText(this, "Selecione ao menos uma comanda.", Toast.LENGTH_SHORT).show();
                    else exibirSelecaoMesaDestino(selecionadas);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void exibirSelecaoMesaDestino(String comanda) {
        List<String> comandas = new ArrayList<>();
        comandas.add(comanda);
        exibirSelecaoMesaDestino(comandas);
    }

    private void exibirSelecaoMesaDestino(List<String> comandas) {
        List<Mesa> destinos = new ArrayList<>();
        List<String> opcoes = new ArrayList<>();
        for (Mesa candidata : manager.getMesas()) {
            if (candidata.getNumero() != numeroMesa) {
                destinos.add(candidata);
                opcoes.add(String.format("MESA %02d (%d comandas)", candidata.getNumero(), candidata.getComandasUnicas().size()));
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Transferir para qual mesa?")
                .setItems(opcoes.toArray(new String[0]), (dialog, which) -> {
                    Mesa destino = destinos.get(which);
                    for (String comanda : comandas) {
                        Mesa outraMesa = manager.buscarMesaComComanda(comanda, numeroMesa);
                        if (outraMesa != null && outraMesa.getNumero() != destino.getNumero()) {
                            Toast.makeText(this, "Comanda #" + comanda + " já está na Mesa " + outraMesa.getNumero() + ".", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Confirmar transferência")
                            .setMessage("Transferir " + comandas.size() + " comanda(s) para a Mesa " + destino.getNumero() + "?")
                            .setPositiveButton("Transferir", (d, w) -> {
                                for (String comanda : comandas) mesa.transferirComandaPara(destino, comanda);
                                if (mesa.getPedidos().isEmpty()) {
                                    mesa.setAberta(false);
                                    manager.removerMesaDinamicaSeVazia(this, numeroMesa);
                                }
                                manager.salvarMesas(this);
                                manager.registrarTroca(this, "Mesa " + numeroMesa + " para Mesa " + destino.getNumero() + ": " + comandas);
                                sendBroadcast(new Intent(MesaManager.ACTION_PEDIDOS_ATUALIZADOS));
                                atualizarUI();
                                Toast.makeText(this, "Comanda(s) transferida(s) para a Mesa " + destino.getNumero() + "!", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private int dp(int valor) {
        return (int) (valor * getResources().getDisplayMetrics().density + 0.5f);
    }
}
