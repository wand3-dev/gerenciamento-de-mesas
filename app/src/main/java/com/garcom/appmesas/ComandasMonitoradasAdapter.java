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
import java.util.Locale;

public class ComandasMonitoradasAdapter extends RecyclerView.Adapter<ComandasMonitoradasAdapter.ComandaViewHolder> {

    public static final String PAYLOAD_TEMPO = "PAYLOAD_TEMPO";

    private final Context context;
    private final List<ComandaCardModel> listaComandas;
    private final LayoutInflater inflater;
    private OnComandaClickListener listener;

    public interface OnComandaClickListener {
        void onComandaClick(ComandaCardModel comanda);
    }

    public ComandasMonitoradasAdapter(Context context, List<ComandaCardModel> listaComandas, OnComandaClickListener listener) {
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
            // Atualização ultra-rápida do contador de segundos sem recriar views
            ComandaCardModel item = listaComandas.get(position);
            holder.tvCardTempo.setText("Tempo: " + item.getTempoFormatado());
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull ComandaViewHolder holder, int position) {
        ComandaCardModel item = listaComandas.get(position);

        // Cabeçalho
        if (item.getMesa() > 0) {
            holder.tvCardMesaNumero.setText(String.format(Locale.getDefault(), "MESA %02d", item.getMesa()));
        } else {
            holder.tvCardMesaNumero.setText("BALCÃO");
        }

        holder.tvCardComandaBadge.setText("Comanda #" + item.getNumComanda());
        holder.tvCardTempo.setText("Tempo: " + item.getTempoFormatado());

        if (item.getDocumento() != null && !item.getDocumento().isEmpty()) {
            holder.tvCardDocumento.setVisibility(View.VISIBLE);
            holder.tvCardDocumento.setText("Doc: " + item.getDocumento());
        } else {
            holder.tvCardDocumento.setVisibility(View.GONE);
        }

        // Itens
        holder.layoutItensContainer.removeAllViews();
        List<ItemComandaModel> itens = item.getItens();
        if (itens == null || itens.isEmpty()) {
            TextView tvVazio = new TextView(context);
            tvVazio.setText("Carregando itens do servidor...");
            tvVazio.setTextColor(Color.parseColor("#94A3B8"));
            tvVazio.setTextSize(12f);
            tvVazio.setPadding(0, 4, 0, 4);
            holder.layoutItensContainer.addView(tvVazio);
        } else {
            for (ItemComandaModel prod : itens) {
                View linhaView = inflater.inflate(R.layout.item_linha_produto, holder.layoutItensContainer, false);
                TextView tvDescricaoPreco = linhaView.findViewById(R.id.tvDescricaoPreco);
                TextView tvObs = linhaView.findViewById(R.id.tvObs);

                tvDescricaoPreco.setText(prod.getTextoLinha());
                if (prod.hasObs()) {
                    tvObs.setVisibility(View.VISIBLE);
                    tvObs.setText("↳ Obs: " + prod.getObs());
                } else {
                    tvObs.setVisibility(View.GONE);
                }
                holder.layoutItensContainer.addView(linhaView);
            }
        }

        // Rodapé
        holder.tvCardQtdeItens.setText(item.getItensCountFormatado());
        holder.tvCardTotalValor.setText(item.getTotalFormatado());

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
        MaterialCardView cardComanda;
        TextView tvCardMesaNumero;
        TextView tvCardComandaBadge;
        TextView tvCardTempo;
        TextView tvCardDocumento;
        LinearLayout layoutItensContainer;
        TextView tvCardQtdeItens;
        TextView tvCardTotalValor;

        public ComandaViewHolder(@NonNull View itemView) {
            super(itemView);
            cardComanda = itemView.findViewById(R.id.cardComandaMonitorada);
            tvCardMesaNumero = itemView.findViewById(R.id.tvCardMesaNumero);
            tvCardComandaBadge = itemView.findViewById(R.id.tvCardComandaBadge);
            tvCardTempo = itemView.findViewById(R.id.tvCardTempo);
            tvCardDocumento = itemView.findViewById(R.id.tvCardDocumento);
            layoutItensContainer = itemView.findViewById(R.id.layoutItensContainer);
            tvCardQtdeItens = itemView.findViewById(R.id.tvCardQtdeItens);
            tvCardTotalValor = itemView.findViewById(R.id.tvCardTotalValor);
        }
    }
}
