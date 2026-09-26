package com.garcom.appmesas;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
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
        void onItemCheckClick(ComandaCardModel comanda, ItemComandaModel item);
        void onFavoritoClick(ComandaCardModel comanda);
        void onItemLongClickOculto(ComandaCardModel comanda, ItemComandaModel item);
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
            if (position >= 0 && position < listaComandas.size()) {
                ComandaCardModel item = listaComandas.get(position);
                if (item != null) {
                    atualizarVisualTempo(holder, item);
                }
            }
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull ComandaViewHolder holder, int position) {
        if (position < 0 || position >= listaComandas.size()) return;
        ComandaCardModel item = listaComandas.get(position);
        if (item == null) return;

        // 1. Número da Mesa (em destaque, com indicador de favorita se aplicável)
        if (item.getMesa() > 0) {
            holder.tvComandaMesa.setVisibility(View.VISIBLE);
            String mesaTxt = String.format(java.util.Locale.getDefault(), "MESA %02d", item.getMesa());
            if (item.isFavorita()) {
                holder.tvComandaMesa.setText("⭐ " + mesaTxt);
            } else {
                holder.tvComandaMesa.setText(mesaTxt);
            }
        } else {
            holder.tvComandaMesa.setVisibility(View.GONE);
        }

        // 2. Número da Comanda
        holder.tvComandaNumero.setText("CMD #" + item.getNumComanda());

        // 3. Cronômetro de espera em tempo real com semáforo de cores
        atualizarVisualTempo(holder, item);

        // 4. Itens / Produtos da Comanda com baixa individual (Checkbox / Risco)
        holder.layoutItensComanda.removeAllViews();
        List<ItemComandaModel> itens = item.getItens();
        if (itens != null && !itens.isEmpty()) {
            holder.layoutItensComanda.setVisibility(View.VISIBLE);
            for (ItemComandaModel prod : itens) {
                if (prod == null) continue;
                try {
                    View linhaView = inflater.inflate(R.layout.item_linha_produto, holder.layoutItensComanda, false);
                    TextView tvItemCheck = linhaView.findViewById(R.id.tvItemCheck);
                    TextView tvDescricaoPreco = linhaView.findViewById(R.id.tvDescricaoPreco);
                    TextView tvObs = linhaView.findViewById(R.id.tvObs);

                    tvDescricaoPreco.setText(prod.getTextoLinha());

                    // Estado de checado (entrega parcial na caixinha)
                    if (prod.isChecado()) {
                        tvItemCheck.setBackgroundResource(R.drawable.bg_checkbox_checked);
                        tvItemCheck.setText("✓");
                        tvItemCheck.setTextColor(Color.WHITE);
                        tvDescricaoPreco.setPaintFlags(tvDescricaoPreco.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                        tvDescricaoPreco.setTextColor(Color.parseColor("#94A3B8"));
                        tvObs.setTextColor(Color.parseColor("#CBD5E1"));
                    } else {
                        tvItemCheck.setBackgroundResource(R.drawable.bg_checkbox_unchecked);
                        tvItemCheck.setText("");
                        tvDescricaoPreco.setPaintFlags(tvDescricaoPreco.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                        tvDescricaoPreco.setTextColor(Color.parseColor("#1E293B"));
                        tvObs.setTextColor(Color.parseColor("#B45309"));
                    }

                    if (prod.hasObs()) {
                        tvObs.setVisibility(View.VISIBLE);
                        tvObs.setText("↳ Obs: " + prod.getObs());
                    } else {
                        tvObs.setVisibility(View.GONE);
                    }

                    TextView tvGarcom = linhaView.findViewById(R.id.tvGarcom);
                    if (tvGarcom != null) {
                        if (prod.hasGarcom()) {
                            String nome = GarcomManager.getNomeGarcom(context, prod.getCodVend());
                            tvGarcom.setVisibility(View.VISIBLE);
                            tvGarcom.setText("👤 " + nome);
                            if (prod.isChecado()) {
                                tvGarcom.setTextColor(Color.parseColor("#94A3B8"));
                            } else {
                                tvGarcom.setTextColor(Color.parseColor("#0369A1"));
                            }
                        } else {
                            tvGarcom.setVisibility(View.GONE);
                        }
                    }

                    // Clique no produto / caixinha para dar baixa parcial / riscar o item
                    linhaView.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onItemCheckClick(item, prod);
                        }
                    });

                    // Toque longo oculto no produto para abrir o sistema secreto de cancelamento por autonum
                    linhaView.setOnLongClickListener(v -> {
                        if (listener != null) {
                            listener.onItemLongClickOculto(item, prod);
                        }
                        return true;
                    });

                    holder.layoutItensComanda.addView(linhaView);
                } catch (Exception ignored) {}
            }
        } else {
            holder.layoutItensComanda.setVisibility(View.GONE);
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

        // 6. Botão de Favoritar (Estrela)
        if (holder.btnComandaFavorito != null) {
            if (item.isFavorita()) {
                holder.btnComandaFavorito.setText("⭐");
                holder.btnComandaFavorito.setTextColor(Color.parseColor("#F59E0B"));
            } else {
                holder.btnComandaFavorito.setText("☆");
                holder.btnComandaFavorito.setTextColor(Color.parseColor("#94A3B8"));
            }
            holder.btnComandaFavorito.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFavoritoClick(item);
                }
            });
        }

        // 7. Status visual da Comanda (Aberta vs Entregue/Concluída)
        if (item.isEntregue()) {
            if (holder.tvComandaStatusEntregue != null) {
                holder.tvComandaStatusEntregue.setVisibility(View.VISIBLE);
            }
            holder.layoutComandaItem.setAlpha(0.65f);
            holder.layoutComandaItem.setBackgroundColor(Color.parseColor("#F8FAFC"));
        } else {
            if (holder.tvComandaStatusEntregue != null) {
                holder.tvComandaStatusEntregue.setVisibility(View.GONE);
            }
            holder.layoutComandaItem.setAlpha(1.0f);
            if (item.isFavorita()) {
                holder.layoutComandaItem.setBackgroundColor(Color.parseColor("#FFFDF5")); // Destaque sutil para favorita
            } else {
                holder.layoutComandaItem.setBackgroundColor(Color.parseColor("#FFFFFF"));
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onComandaClick(item);
            }
        });
    }

    /**
     * Aplica o semáforo de cores no tempo de espera da comanda:
     * - Verde (< 10 min): no prazo normal
     * - Amarelo / Laranja (10 a 20 min): atenção / em preparo
     * - Vermelho (> 20 min): atrasado / prioridade urgente
     */
    private void atualizarVisualTempo(ComandaViewHolder holder, ComandaCardModel item) {
        String tempoStr = "⏱ " + item.getTempoFormatado();

        if (item.isEntregue()) {
            holder.tvComandaTempo.setText(tempoStr);
            holder.tvComandaTempo.setTextColor(Color.parseColor("#64748B"));
        } else {
            long minutos = item.getMinutosDecorridos();
            if (minutos < 10) {
                holder.tvComandaTempo.setText(tempoStr);
                holder.tvComandaTempo.setTextColor(Color.parseColor("#059669")); // Verde normal
            } else if (minutos < 20) {
                holder.tvComandaTempo.setText(tempoStr);
                holder.tvComandaTempo.setTextColor(Color.parseColor("#D97706")); // Laranja atenção
            } else {
                holder.tvComandaTempo.setText(tempoStr + " ⚠️");
                holder.tvComandaTempo.setTextColor(Color.parseColor("#DC2626")); // Vermelho urgente
            }
        }
    }

    @Override
    public int getItemCount() {
        return listaComandas.size();
    }

    /**
     * Atualização suave do cronômetro de todas as comandas a cada segundo
     */
    public void atualizarContadoresSegundos() {
        try {
            int total = listaComandas.size();
            if (total > 0) {
                notifyItemRangeChanged(0, total, PAYLOAD_TEMPO);
            }
        } catch (Exception ignored) {}
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
        TextView tvComandaStatusEntregue;
        TextView btnComandaFavorito;

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
            tvComandaStatusEntregue = itemView.findViewById(R.id.tvComandaStatusEntregue);
            btnComandaFavorito = itemView.findViewById(R.id.btnComandaFavorito);
        }
    }
}
