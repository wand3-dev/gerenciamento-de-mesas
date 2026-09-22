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
import java.util.List;

public class ComandasMonitoradasAdapter extends RecyclerView.Adapter<ComandasMonitoradasAdapter.ComandaViewHolder> {

    public static final String PAYLOAD_TEMPO = "PAYLOAD_TEMPO";

    private final Context context;
    private final List<ComandaCardModel> listaComandas;
    private final LayoutInflater inflater;
    private OnComandaActionListener listener;

    public interface OnComandaActionListener {
        void onComandaClick(ComandaCardModel comanda);
        void onEntregueClick(ComandaCardModel comanda, int position);
    }

    public ComandasMonitoradasAdapter(Context context, List<ComandaCardModel> listaComandas, OnComandaActionListener listener) {
        this.context = context;
        this.listaComandas = listaComandas;
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public ComandaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_comanda_monitorada, parent, false);
        return new ComandaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ComandaViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_TEMPO)) {
            ComandaCardModel item = listaComandas.get(position);
            holder.tvComandaTempo.setText("⏱ " + item.getTempoFormatado());
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull ComandaViewHolder holder, int position) {
        ComandaCardModel item = listaComandas.get(position);

        // Identificação limpa e direta da comanda (sem cards)
        holder.tvComandaNumero.setText("COMANDA #" + item.getNumComanda());

        // Cronômetro de espera em tempo real
        holder.tvComandaTempo.setText("⏱ " + item.getTempoFormatado());

        // Resumo rápido de itens e valor
        holder.tvComandaQtdeItens.setText(item.getItensCountFormatado());
        holder.tvComandaTotalValor.setText(item.getTotalFormatado());

        if (item.getDocumento() != null && !item.getDocumento().trim().isEmpty()) {
            holder.tvComandaDocumento.setVisibility(View.VISIBLE);
            holder.tvComandaDocumento.setText("Doc: " + item.getDocumento().trim());
        } else {
            holder.tvComandaDocumento.setVisibility(View.GONE);
        }

        // Estado do botão de entrega e visual da linha (aberta no topo vs entregue no final)
        if (item.isEntregue()) {
            holder.btnComandaEntregue.setText("✓ ENTREGUE");
            holder.btnComandaEntregue.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#475569")));
            holder.layoutComandaItem.setAlpha(0.65f);
            holder.layoutComandaItem.setBackgroundColor(Color.parseColor("#F8FAFC"));
        } else {
            holder.btnComandaEntregue.setText("ENTREGAR");
            holder.btnComandaEntregue.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#059669")));
            holder.layoutComandaItem.setAlpha(1.0f);
            holder.layoutComandaItem.setBackgroundColor(Color.parseColor("#FFFFFF"));
        }

        holder.btnComandaEntregue.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEntregueClick(item, holder.getAdapterPosition());
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onComandaClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return listaComandas.size();
    }

    /**
     * Atualização suave do cronômetro de todas as comandas a cada segundo
     */
    public void atualizarContadoresSegundos() {
        if (!listaComandas.isEmpty()) {
            notifyItemRangeChanged(0, listaComandas.size(), PAYLOAD_TEMPO);
        }
    }

    public static class ComandaViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutComandaItem;
        TextView tvComandaNumero;
        TextView tvComandaTempo;
        TextView tvComandaQtdeItens;
        TextView tvComandaTotalValor;
        TextView tvComandaDocumento;
        MaterialButton btnComandaEntregue;

        public ComandaViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutComandaItem = itemView.findViewById(R.id.layoutComandaItem);
            tvComandaNumero = itemView.findViewById(R.id.tvComandaNumero);
            tvComandaTempo = itemView.findViewById(R.id.tvComandaTempo);
            tvComandaQtdeItens = itemView.findViewById(R.id.tvComandaQtdeItens);
            tvComandaTotalValor = itemView.findViewById(R.id.tvComandaTotalValor);
            tvComandaDocumento = itemView.findViewById(R.id.tvComandaDocumento);
            btnComandaEntregue = itemView.findViewById(R.id.btnComandaEntregue);
        }
    }
}
