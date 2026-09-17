package com.garcom.appmesas;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class MesasAdapter extends RecyclerView.Adapter<MesasAdapter.MesaViewHolder> {
    private List<Mesa> listaMesas;
    private OnMesaClickListener listener;
    private Context context;

    public interface OnMesaClickListener {
        void onMesaClick(Mesa mesa);
    }

    public MesasAdapter(Context context, List<Mesa> listaMesas, OnMesaClickListener listener) {
        this.context = context;
        this.listaMesas = listaMesas;
        this.listener = listener;
    }

    private String queryBusca = "";

    public void setQueryBusca(String queryBusca) {
        this.queryBusca = queryBusca != null ? queryBusca : "";
    }

    @NonNull
    @Override
    public MesaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mesa, parent, false);
        return new MesaViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MesaViewHolder holder, int position) {
        Mesa mesa = listaMesas.get(position);
        boolean isDark = ThemeManager.isDarkMode(context);

        holder.tvMesaNumero.setText(String.format("MESA %02d", mesa.getNumero()));

        if (mesa.isAberta() && !mesa.getPedidos().isEmpty()) {
            // ── Mesa ABERTA ──────────────────────────────────────
            holder.cardMesa.setCardBackgroundColor(Color.parseColor(isDark ? "#131E2E" : "#FFFFFF"));
            holder.cardMesa.setStrokeColor(Color.parseColor("#10B981")); // Emerald border
            holder.cardMesa.setStrokeWidth(3);

            holder.tvMesaStatus.setText("ABERTA");
            holder.tvMesaStatus.setBackgroundResource(R.drawable.bg_pill_status_aberta);
            holder.tvMesaStatus.setTextColor(Color.parseColor("#065F46"));

            holder.tvMesaNumero.setTextColor(Color.parseColor(isDark ? "#F8FAFC" : "#0F172A"));

            // Subtitle livre off, comandas layout on
            if (holder.layoutMesaLivreInfo != null) holder.layoutMesaLivreInfo.setVisibility(View.GONE);
            holder.tvMesaLivreSub.setVisibility(View.GONE);
            holder.layoutComandasMesa.setVisibility(View.VISIBLE);
            holder.layoutMesaFooter.setVisibility(View.VISIBLE);

            // Comandas list
            List<String> comandas = mesa.getComandasUnicas();
            if (!comandas.isEmpty()) {
                if (comandas.size() == 1) {
                    holder.tvMesaComandas.setText("Cmd #" + comandas.get(0));
                } else {
                    holder.tvMesaComandas.setText(comandas.size() + " Comandas");
                }
            } else {
                holder.tvMesaComandas.setText("Comanda Ativa");
            }

            // Valor Total
            double totalValor = mesa.getTotalValor();
            if (totalValor > 0) {
                holder.tvMesaValorTotal.setVisibility(View.VISIBLE);
                holder.tvMesaValorTotal.setText(mesa.getTotalValorFormatado());
            } else {
                holder.tvMesaValorTotal.setVisibility(View.GONE);
            }

            // Contagem de itens, pendentes e cronômetro visual colorido
            int totalItens = 0;
            int pendentes = 0;
            boolean temAtrasado15Min = false;
            long agora = System.currentTimeMillis();
            long tempoMaisAntigo = 0;

            for (PedidoItem it : mesa.getPedidos()) {
                totalItens += it.getQuantidade();
                if (!it.isEntregue()) {
                    pendentes++;
                    long esperaItem = agora - it.getTimestamp();
                    if (esperaItem > tempoMaisAntigo) {
                        tempoMaisAntigo = esperaItem;
                    }
                    if (esperaItem >= 15 * 60 * 1000) {
                        temAtrasado15Min = true;
                    }
                }
            }

            long minEspera = tempoMaisAntigo / 60000;
            if (pendentes > 0) {
                if (minEspera >= 15) {
                    holder.tvMesaTempo.setText("⏱️ " + minEspera + " min (URGENTE)");
                    holder.tvMesaTempo.setTextColor(Color.parseColor("#DC2626"));
                } else if (minEspera >= 9) {
                    holder.tvMesaTempo.setText("⏱️ " + minEspera + " min (ATENÇÃO)");
                    holder.tvMesaTempo.setTextColor(Color.parseColor("#D97706"));
                } else {
                    holder.tvMesaTempo.setText("⏱️ " + (minEspera == 0 ? "Agora" : minEspera + " min"));
                    holder.tvMesaTempo.setTextColor(Color.parseColor("#059669"));
                }
                holder.tvMesaItensCount.setText(pendentes + " falta ir");
                holder.tvMesaItensCount.setTextColor(Color.parseColor("#D97706"));
            } else {
                holder.tvMesaTempo.setText("✓ " + mesa.getTempoMesaFormatado());
                holder.tvMesaTempo.setTextColor(Color.parseColor(isDark ? "#94A3B8" : "#64748B"));
                holder.tvMesaItensCount.setText("✓ Entregues (" + totalItens + ")");
                holder.tvMesaItensCount.setTextColor(Color.parseColor("#10B981"));
            }

            // Alerta 15m
            holder.tvMesaAlerta15m.setVisibility(temAtrasado15Min ? View.VISIBLE : View.GONE);
            if (temAtrasado15Min) {
                holder.cardMesa.setStrokeColor(Color.parseColor("#EF4444")); // Red border if delayed!
                holder.cardMesa.setStrokeWidth(4);
            } else if (pendentes > 0 && minEspera >= 9) {
                holder.cardMesa.setStrokeColor(Color.parseColor("#F59E0B")); // Amarelo atenção
                holder.cardMesa.setStrokeWidth(3);
            }

        } else {
            // ── Mesa LIVRE ──────────────────────────────────────
            holder.cardMesa.setCardBackgroundColor(Color.parseColor(isDark ? "#0F172A" : "#FFFFFF"));
            holder.cardMesa.setStrokeColor(Color.parseColor(isDark ? "#1E293B" : "#E2E8F0"));
            holder.cardMesa.setStrokeWidth(2);

            holder.tvMesaStatus.setText("LIVRE");
            holder.tvMesaStatus.setBackgroundResource(R.drawable.bg_pill_status_livre);
            holder.tvMesaStatus.setTextColor(Color.parseColor("#64748B"));

            holder.tvMesaNumero.setTextColor(Color.parseColor("#64748B"));

            if (holder.layoutMesaLivreInfo != null) holder.layoutMesaLivreInfo.setVisibility(View.VISIBLE);
            holder.tvMesaLivreSub.setVisibility(View.VISIBLE);
            holder.layoutComandasMesa.setVisibility(View.GONE);
            holder.layoutMesaFooter.setVisibility(View.GONE);
            holder.tvMesaAlerta15m.setVisibility(View.GONE);
        }

        // ── Busca Reversa por Produto ("De quem é esse prato?") ──
        if (holder.tvMesaProdutoEncontrado != null) {
            String buscaNorm = StringHelper.normalizar(queryBusca);
            boolean achou = false;
            if (!buscaNorm.isEmpty() && mesa.getPedidos() != null && !mesa.getPedidos().isEmpty()) {
                for (PedidoItem p : mesa.getPedidos()) {
                    String descNorm = StringHelper.normalizar(p.getDescricao());
                    if (descNorm.contains(buscaNorm)) {
                        String statusStr = p.isEntregue() ? "(✓ Entregue)" : "(⏳ Falta Ir)";
                        holder.tvMesaProdutoEncontrado.setText("🔍 " + p.getQuantidade() + "x " + p.getDescricao() + " " + statusStr);
                        if (isDark) {
                            holder.tvMesaProdutoEncontrado.setBackgroundResource(R.drawable.bg_pill_comanda);
                            holder.tvMesaProdutoEncontrado.setTextColor(Color.parseColor("#92400E"));
                        } else {
                            holder.tvMesaProdutoEncontrado.setBackgroundResource(R.drawable.bg_pill_found_product);
                            holder.tvMesaProdutoEncontrado.setTextColor(Color.parseColor("#B45309"));
                        }
                        holder.tvMesaProdutoEncontrado.setVisibility(View.VISIBLE);
                        achou = true;
                        break;
                    }
                }
            }
            if (!achou) {
                holder.tvMesaProdutoEncontrado.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMesaClick(mesa);
        });
    }

    @Override
    public int getItemCount() {
        return listaMesas.size();
    }

    public static class MesaViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardMesa;
        TextView tvMesaNumero, tvMesaStatus, tvMesaComandas, tvMesaValorTotal;
        TextView tvMesaLivreSub, tvMesaTempo, tvMesaItensCount, tvMesaAlerta15m;
        TextView tvMesaProdutoEncontrado;
        LinearLayout layoutComandasMesa, layoutMesaFooter, layoutMesaLivreInfo;

        public MesaViewHolder(@NonNull View itemView) {
            super(itemView);
            cardMesa = itemView.findViewById(R.id.cardMesa);
            tvMesaNumero = itemView.findViewById(R.id.tvMesaNumero);
            tvMesaStatus = itemView.findViewById(R.id.tvMesaStatus);
            tvMesaComandas = itemView.findViewById(R.id.tvMesaComandas);
            tvMesaValorTotal = itemView.findViewById(R.id.tvMesaValorTotal);
            tvMesaLivreSub = itemView.findViewById(R.id.tvMesaLivreSub);
            tvMesaTempo = itemView.findViewById(R.id.tvMesaTempo);
            tvMesaItensCount = itemView.findViewById(R.id.tvMesaItensCount);
            tvMesaAlerta15m = itemView.findViewById(R.id.tvMesaAlerta15m);
            tvMesaProdutoEncontrado = itemView.findViewById(R.id.tvMesaProdutoEncontrado);
            layoutComandasMesa = itemView.findViewById(R.id.layoutComandasMesa);
            layoutMesaFooter = itemView.findViewById(R.id.layoutMesaFooter);
            layoutMesaLivreInfo = itemView.findViewById(R.id.layoutMesaLivreInfo);
        }
    }
}
