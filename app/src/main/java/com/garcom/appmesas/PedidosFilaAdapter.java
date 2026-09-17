package com.garcom.appmesas;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;
import java.util.Locale;

public class PedidosFilaAdapter extends RecyclerView.Adapter<PedidosFilaAdapter.FilaViewHolder> {

    public interface OnFilaClickListener {
        void onMesaClick(Mesa mesa);
        void onMarcarEntregueClick(PedidoFilaItem item, int position);
    }

    private Context context;
    private List<PedidoFilaItem> listaFila;
    private OnFilaClickListener listener;

    public PedidosFilaAdapter(Context context, List<PedidoFilaItem> listaFila, OnFilaClickListener listener) {
        this.context = context;
        this.listaFila = listaFila;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FilaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pedido_fila, parent, false);
        return new FilaViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FilaViewHolder holder, int position) {
        PedidoFilaItem filaItem = listaFila.get(position);
        Mesa mesa = filaItem.getMesa();
        PedidoItem pedido = filaItem.getPedido();
        boolean isDark = ThemeManager.isDarkMode(context);

        holder.tvFilaMesa.setText(String.format(Locale.getDefault(), "MESA %02d", mesa.getNumero()));
        holder.tvFilaMesa.setTextColor(Color.parseColor(isDark ? "#F8FAFC" : "#0F172A"));
        holder.tvFilaComanda.setText("CMD #" + pedido.getComanda());
        holder.tvFilaDescricao.setText(pedido.getQuantidade() + "x " + pedido.getDescricao());
        holder.tvFilaDescricao.setTextColor(Color.parseColor(isDark ? "#E2E8F0" : "#1E293B"));

        double vlr = pedido.getValorTotalNumerico();
        if (vlr > 0) {
            holder.tvFilaValor.setText(String.format(Locale.GERMANY, "R$ %.2f", vlr));
        } else {
            holder.tvFilaValor.setText("R$ " + pedido.getValorTotal());
        }

        String hora = pedido.getHoraRegistro();
        String doc = pedido.getDocumento();
        StringBuilder sbSub = new StringBuilder();
        if (hora != null && !hora.isEmpty()) {
            sbSub.append("Pedido às ").append(hora);
        }
        if (doc != null && !doc.isEmpty()) {
            if (sbSub.length() > 0) sbSub.append(" • ");
            sbSub.append("Doc: ").append(doc);
        }
        holder.tvFilaHorarioDoc.setText(sbSub.toString());

        // Cronômetro visual e faixas de alerta
        long diffMin = (System.currentTimeMillis() - pedido.getTimestamp()) / 60000;
        String tempoStr = pedido.getTempoDecorridoFormatado();

        if (pedido.isEntregue()) {
            // Entregue / Fechado (vermelho/cinza suave)
            holder.tvFilaTempoEspera.setText("✓ ENTREGUE");
            holder.tvFilaTempoEspera.setTextColor(Color.parseColor("#991B1B"));
            holder.tvFilaTempoEspera.setBackgroundColor(Color.parseColor(isDark ? "#3B1818" : "#FEE2E2"));
            holder.cardItemFila.setStrokeColor(Color.parseColor("#F87171"));
            holder.cardItemFila.setCardBackgroundColor(Color.parseColor(isDark ? "#1C1518" : "#FFF5F5"));
            holder.btnFilaAcaoEntregue.setText("DESMARCAR");
            holder.btnFilaAcaoEntregue.setBackgroundColor(Color.parseColor("#64748B"));
        } else if (diffMin >= 15) {
            // Urgente! 15m+ (Vermelho)
            holder.tvFilaTempoEspera.setText("🚨 " + tempoStr + " (+15m)");
            holder.tvFilaTempoEspera.setTextColor(Color.parseColor("#DC2626"));
            holder.tvFilaTempoEspera.setBackgroundResource(R.drawable.bg_pill_danger);
            holder.cardItemFila.setStrokeColor(Color.parseColor("#EF4444"));
            holder.cardItemFila.setStrokeWidth(4);
            holder.cardItemFila.setCardBackgroundColor(Color.parseColor(isDark ? "#261315" : "#FEF2F2"));
            holder.btnFilaAcaoEntregue.setText("✓ ENTREGAR");
            holder.btnFilaAcaoEntregue.setBackgroundResource(R.drawable.bg_button_gradient_amber);
        } else if (diffMin >= 9) {
            // Atenção! 9 a 14 min (Amarelo)
            holder.tvFilaTempoEspera.setText("⚠️ " + tempoStr);
            holder.tvFilaTempoEspera.setTextColor(Color.parseColor("#B45309"));
            holder.tvFilaTempoEspera.setBackgroundColor(Color.parseColor(isDark ? "#35220C" : "#FEF3C7"));
            holder.cardItemFila.setStrokeColor(Color.parseColor("#F59E0B"));
            holder.cardItemFila.setStrokeWidth(3);
            holder.cardItemFila.setCardBackgroundColor(Color.parseColor(isDark ? "#24180A" : "#FFFBEB"));
            holder.btnFilaAcaoEntregue.setText("✓ ENTREGAR");
            holder.btnFilaAcaoEntregue.setBackgroundResource(R.drawable.bg_button_gradient_amber);
        } else {
            // Normal (0 a 8 min) (Verde)
            holder.tvFilaTempoEspera.setText("⏱️ " + tempoStr);
            holder.tvFilaTempoEspera.setTextColor(Color.parseColor("#065F46"));
            holder.tvFilaTempoEspera.setBackgroundResource(R.drawable.bg_pill_status_aberta);
            holder.cardItemFila.setStrokeColor(Color.parseColor("#10B981"));
            holder.cardItemFila.setStrokeWidth(2);
            holder.cardItemFila.setCardBackgroundColor(Color.parseColor(isDark ? "#131E2E" : "#FFFFFF"));
            holder.btnFilaAcaoEntregue.setText("✓ ENTREGAR");
            holder.btnFilaAcaoEntregue.setBackgroundResource(R.drawable.bg_button_gradient_amber);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMesaClick(mesa);
        });

        holder.btnFilaAcaoEntregue.setOnClickListener(v -> {
            if (listener != null) listener.onMarcarEntregueClick(filaItem, position);
        });
    }

    @Override
    public int getItemCount() {
        return listaFila.size();
    }

    public static class FilaViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardItemFila;
        TextView tvFilaMesa, tvFilaComanda, tvFilaTempoEspera, tvFilaDescricao, tvFilaValor, tvFilaHorarioDoc, btnFilaAcaoEntregue;

        public FilaViewHolder(@NonNull View itemView) {
            super(itemView);
            cardItemFila = itemView.findViewById(R.id.cardItemFila);
            tvFilaMesa = itemView.findViewById(R.id.tvFilaMesa);
            tvFilaComanda = itemView.findViewById(R.id.tvFilaComanda);
            tvFilaTempoEspera = itemView.findViewById(R.id.tvFilaTempoEspera);
            tvFilaDescricao = itemView.findViewById(R.id.tvFilaDescricao);
            tvFilaValor = itemView.findViewById(R.id.tvFilaValor);
            tvFilaHorarioDoc = itemView.findViewById(R.id.tvFilaHorarioDoc);
            btnFilaAcaoEntregue = itemView.findViewById(R.id.btnFilaAcaoEntregue);
        }
    }
}
