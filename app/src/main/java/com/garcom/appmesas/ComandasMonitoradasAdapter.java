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

        // 1. Número da Mesa (em destaque)
        if (item.getMesa() > 0) {
            holder.tvComandaMesa.setVisibility(View.VISIBLE);
            holder.tvComandaMesa.setText(String.format(java.util.Locale.getDefault(), "MESA %02d", item.getMesa()));
        } else {
            holder.tvComandaMesa.setVisibility(View.GONE);
        }

        // 2. Número da Comanda
        holder.tvComandaNumero.setText("CMD #" + item.getNumComanda());

        // 3. Cronômetro de espera em tempo real
        holder.tvComandaTempo.setText("⏱ " + item.getTempoFormatado());

        // 4. Itens / Produtos da Comanda (exibidos diretamente no Home)
        holder.layoutItensComanda.removeAllViews();
        List<ItemComandaModel> itens = item.getItens();
        if (itens != null && !itens.isEmpty()) {
            holder.layoutItensComanda.setVisibility(View.VISIBLE);
            for (ItemComandaModel prod : itens) {
                View linhaView = inflater.inflate(R.layout.item_linha_produto, holder.layoutItensComanda, false);
                TextView tvDescricaoPreco = linhaView.findViewById(R.id.tvDescricaoPreco);
                TextView tvObs = linhaView.findViewById(R.id.tvObs);

                tvDescricaoPreco.setText(prod.getTextoLinha());
                if (prod.hasObs()) {
                    tvObs.setVisibility(View.VISIBLE);
                    tvObs.setText("↳ Obs: " + prod.getObs());
                } else {
                    tvObs.setVisibility(View.GONE);
                }
                holder.layoutItensComanda.addView(linhaView);
            }
        } else {
            // Se ainda não carregou ou não possui lista detalhada de itens
            if (item.getQtdeItens() > 0) {
                holder.layoutItensComanda.setVisibility(View.VISIBLE);
                TextView tvSimples = new TextView(context);
                tvSimples.setText(item.getQtdeItens() + " item(ns) lançado(s)");
                tvSimples.setTextColor(Color.parseColor("#64748B"));
                tvSimples.setTextSize(12f);
                tvSimples.setPadding(0, 2, 0, 2);
                holder.layoutItensComanda.addView(tvSimples);
            } else {
                holder.layoutItensComanda.setVisibility(View.GONE);
            }
        }

        // 5. Resumo de Quantidade e Valor Total
        holder.tvComandaQtdeItens.setText(item.getItensCountFormatado());
        holder.tvComandaTotalValor.setText(item.getTotalFormatado());

        if (item.getDocumento() != null && !item.getDocumento().trim().isEmpty()) {
            holder.tvComandaDocumento.setVisibility(View.VISIBLE);
            holder.tvComandaDocumento.setText("Doc: " + item.getDocumento().trim());
        } else {
            holder.tvComandaDocumento.setVisibility(View.GONE);
        }

        // 6. Botão de Entrega e estilo visual (Aberta vs Entregue)
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
        TextView tvComandaMesa;
        TextView tvComandaNumero;
        TextView tvComandaTempo;
        LinearLayout layoutItensComanda;
        TextView tvComandaQtdeItens;
        TextView tvComandaTotalValor;
        TextView tvComandaDocumento;
        MaterialButton btnComandaEntregue;

        public ComandaViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutComandaItem = itemView.findViewById(R.id.layoutComandaItem);
            tvComandaMesa = itemView.findViewById(R.id.tvComandaMesa);
            tvComandaNumero = itemView.findViewById(R.id.tvComandaNumero);
            tvComandaTempo = itemView.findViewById(R.id.tvComandaTempo);
            layoutItensComanda = itemView.findViewById(R.id.layoutItensComanda);
            tvComandaQtdeItens = itemView.findViewById(R.id.tvComandaQtdeItens);
            tvComandaTotalValor = itemView.findViewById(R.id.tvComandaTotalValor);
            tvComandaDocumento = itemView.findViewById(R.id.tvComandaDocumento);
            btnComandaEntregue = itemView.findViewById(R.id.btnComandaEntregue);
        }
    }
}
