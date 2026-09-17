package com.garcom.appmesas;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PedidosAdapter extends RecyclerView.Adapter<PedidosAdapter.PedidoViewHolder> {
    private List<PedidoItem> pedidos;
    private OnPedidoActionListener listener;

    public interface OnPedidoActionListener {
        void onEntregueClick(PedidoItem item, int position);
        void onRemoverClick(PedidoItem item, int position);
        void onAdiar2MinClick(PedidoItem item, int position);
    }

    public PedidosAdapter(List<PedidoItem> pedidos, OnPedidoActionListener listener) {
        this.pedidos = pedidos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PedidoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pedido, parent, false);
        return new PedidoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PedidoViewHolder holder, int position) {
        PedidoItem item = pedidos.get(position);
        Context context = holder.itemView.getContext();
        boolean isDark = ThemeManager.isDarkMode(context);

        // Quantidade de Itens
        holder.tvItemQtd.setText(item.getQuantidade() + "x");

        // Comanda Badge
        if (item.getComanda() != null && !item.getComanda().trim().isEmpty()) {
            holder.tvItemComanda.setVisibility(View.VISIBLE);
            holder.tvItemComanda.setText("CMD #" + item.getComanda().trim());
        } else {
            holder.tvItemComanda.setVisibility(View.GONE);
        }

        // Descrição
        holder.tvItemDescricao.setText(item.getDescricao());
        holder.tvItemDescricao.setTextColor(Color.parseColor(isDark ? "#F8FAFC" : "#0F172A"));

        // Valor R$
        if (holder.tvItemValorTotal != null) {
            double vlr = item.getValorTotalNumerico();
            if (vlr > 0) {
                holder.tvItemValorTotal.setText(String.format(Locale.GERMANY, "R$ %.2f", vlr));
            } else {
                holder.tvItemValorTotal.setText("R$ " + item.getValorTotal());
            }
        }

        // Documento do Servidor
        if (holder.tvItemDocServidor != null) {
            if (item.getDocumento() != null && !item.getDocumento().isEmpty()) {
                holder.tvItemDocServidor.setText("Doc: " + item.getDocumento());
                holder.tvItemDocServidor.setVisibility(View.VISIBLE);
            } else {
                holder.tvItemDocServidor.setVisibility(View.GONE);
            }
        }

        // Horário de Registro no servidor
        if (holder.tvItemHorario != null) {
            if (item.getHoraRegistro() != null && !item.getHoraRegistro().isEmpty()) {
                holder.tvItemHorario.setText(item.getHoraRegistro());
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                holder.tvItemHorario.setText(sdf.format(new Date(item.getTimestamp())));
            }
        }

        // Tempo decorrido com cálculo de atraso
        String tempo = item.getTempoDecorridoFormatado();
        holder.tvItemTempo.setText(tempo);

        boolean atrasado = item.isAtrasado();
        if (holder.tvAlertaAtraso15m != null) {
            holder.tvAlertaAtraso15m.setVisibility(atrasado ? View.VISIBLE : View.GONE);
        }

        if (holder.btnAdiar2Min != null) {
            holder.btnAdiar2Min.setVisibility(atrasado ? View.VISIBLE : View.GONE);
            holder.btnAdiar2Min.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (listener != null && adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onAdiar2MinClick(item, adapterPosition);
                }
            });
        }

        // ── ESTILIZAÇÃO CONFORME PEDIDO DO USUÁRIO ──
        // "se tiver mais de 1 comanda em 1 mesa, não feche a mesa completa, apenas a comanda que eu marca como fechada, ai fica vermelha"
        if (item.isEntregue()) {
            // ═══ COMANDA MARCADA COMO FECHADA / JÁ FOI PRA MESA -> FICA VERMELHA ═══
            holder.cardPedidoItem.setCardBackgroundColor(Color.parseColor(isDark ? "#281316" : "#FEF2F2")); // Fundo vermelho suave
            holder.cardPedidoItem.setStrokeColor(Color.parseColor("#DC2626")); // Borda vermelha marcante
            holder.cardPedidoItem.setStrokeWidth(3);

            if (holder.layoutStatusBar != null) {
                holder.layoutStatusBar.setBackgroundColor(Color.parseColor(isDark ? "#3A1B1E" : "#FEE2E2"));
            }

            holder.tvStatusEntregue.setText("✓ FECHADA / NA MESA");
            holder.tvStatusEntregue.setTextColor(Color.parseColor("#991B1B"));
            holder.tvStatusEntregue.setBackgroundResource(R.drawable.bg_pill_danger);

            holder.tvItemComanda.setBackgroundResource(R.drawable.bg_pill_danger);
            holder.tvItemComanda.setTextColor(Color.parseColor("#991B1B"));

            holder.tvItemTempo.setTextColor(Color.parseColor("#B91C1C"));

            holder.btnEntregue.setText("✓ Comanda Fechada (Vermelha)");
            holder.btnEntregue.setTextColor(Color.parseColor("#DC2626"));
            holder.btnEntregue.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#DC2626")));
            holder.btnEntregue.setStrokeWidth(2);
            holder.itemView.setAlpha(1.0f);

        } else {
            // ═══ COMANDA PENDENTE (FALTA IR PARA A MESA) ═══
            holder.cardPedidoItem.setCardBackgroundColor(Color.parseColor(isDark ? "#131E2E" : "#FFFFFF"));
            if (holder.layoutStatusBar != null) {
                holder.layoutStatusBar.setBackgroundColor(Color.parseColor(isDark ? "#1A273D" : "#F8FAFC"));
            }

            if (atrasado) {
                holder.cardPedidoItem.setStrokeColor(Color.parseColor("#EF4444")); // Vermelho alerta 15m
                holder.cardPedidoItem.setStrokeWidth(3);
                holder.tvItemTempo.setTextColor(Color.parseColor("#DC2626"));
            } else {
                holder.cardPedidoItem.setStrokeColor(Color.parseColor("#10B981")); // Verde ativo
                holder.cardPedidoItem.setStrokeWidth(2);
                holder.tvItemTempo.setTextColor(Color.parseColor("#64748B"));
            }

            holder.tvStatusEntregue.setText("⏳ FALTA IR");
            holder.tvStatusEntregue.setTextColor(Color.parseColor("#92400E"));
            holder.tvStatusEntregue.setBackgroundResource(R.drawable.bg_pill_comanda);

            holder.tvItemComanda.setBackgroundResource(R.drawable.bg_pill_comanda);
            holder.tvItemComanda.setTextColor(Color.parseColor("#92400E"));

            holder.btnEntregue.setText("Marcar como Fechada (Na Mesa)");
            holder.btnEntregue.setTextColor(Color.parseColor("#059669"));
            holder.btnEntregue.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#10B981")));
            holder.btnEntregue.setStrokeWidth(2);
            holder.itemView.setAlpha(1.0f);
        }

        // Observações adicionais se houver
        if (holder.tvItemAdicional != null) {
            if (item.getAdicional() != null && !item.getAdicional().trim().isEmpty()) {
                holder.tvItemAdicional.setVisibility(View.VISIBLE);
                holder.tvItemAdicional.setText(item.getAdicional());
            } else {
                holder.tvItemAdicional.setVisibility(View.GONE);
            }
        }

        holder.btnEntregue.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (listener != null && adapterPosition != RecyclerView.NO_POSITION) {
                listener.onEntregueClick(item, adapterPosition);
            }
        });

        holder.btnRemoverItem.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (listener != null && adapterPosition != RecyclerView.NO_POSITION) {
                listener.onRemoverClick(item, adapterPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pedidos.size();
    }

    public static class PedidoViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardPedidoItem;
        LinearLayout layoutStatusBar;
        TextView tvItemQtd, tvItemComanda, tvItemDescricao, tvItemTempo, tvStatusEntregue;
        TextView tvItemHorario, tvItemAdicional, tvItemDocServidor, tvItemValorTotal, tvAlertaAtraso15m;
        MaterialButton btnEntregue, btnRemoverItem, btnAdiar2Min;

        public PedidoViewHolder(@NonNull View itemView) {
            super(itemView);
            cardPedidoItem = itemView.findViewById(R.id.cardPedidoItem);
            layoutStatusBar = itemView.findViewById(R.id.layoutStatusBar);
            tvItemQtd = itemView.findViewById(R.id.tvItemQtd);
            tvItemComanda = itemView.findViewById(R.id.tvItemComanda);
            tvItemDescricao = itemView.findViewById(R.id.tvItemDescricao);
            tvItemTempo = itemView.findViewById(R.id.tvItemTempo);
            tvStatusEntregue = itemView.findViewById(R.id.tvStatusEntregue);
            tvItemHorario = itemView.findViewById(R.id.tvItemHorario);
            tvItemAdicional = itemView.findViewById(R.id.tvItemAdicional);
            tvItemDocServidor = itemView.findViewById(R.id.tvItemDocServidor);
            tvItemValorTotal = itemView.findViewById(R.id.tvItemValorTotal);
            tvAlertaAtraso15m = itemView.findViewById(R.id.tvAlertaAtraso15m);
            btnEntregue = itemView.findViewById(R.id.btnEntregue);
            btnRemoverItem = itemView.findViewById(R.id.btnRemoverItem);
            btnAdiar2Min = itemView.findViewById(R.id.btnAdiar2Min);
        }
    }
}
