package com.garcom.appmesas;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AlertaHistoricoAdapter extends RecyclerView.Adapter<AlertaHistoricoAdapter.ViewHolder> {

    public interface OnAlertaClickListener {
        void onAlertaClick(AlertaHistorico alerta);
    }

    private List<AlertaHistorico> lista = new ArrayList<>();
    private OnAlertaClickListener listener;

    public AlertaHistoricoAdapter(List<AlertaHistorico> itens, OnAlertaClickListener listener) {
        if (itens != null) {
            this.lista.addAll(itens);
        }
        this.listener = listener;
    }

    public void atualizar(List<AlertaHistorico> novosItens) {
        lista.clear();
        if (novosItens != null) {
            lista.addAll(novosItens);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alerta_historico, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AlertaHistorico alerta = lista.get(position);

        holder.tvIconeAlerta.setText(alerta.getIconeTexto());
        holder.tvTituloAlerta.setText(alerta.getTitulo());
        holder.tvHoraAlerta.setText(alerta.getHoraFormatada());
        holder.tvMensagemAlerta.setText(alerta.getMensagem());

        // Cor de fundo do ícone arredondado
        try {
            GradientDrawable bgIcone = new GradientDrawable();
            bgIcone.setShape(GradientDrawable.OVAL);
            bgIcone.setColor(Color.parseColor(alerta.getCorTag()));
            holder.tvIconeAlerta.setBackground(bgIcone);
            holder.tvIconeAlerta.setTextColor(Color.WHITE);
        } catch (Exception ignored) {}

        if (alerta.getNumeroMesa() > 0) {
            holder.tvIrParaMesa.setVisibility(View.VISIBLE);
            holder.tvIrParaMesa.setText("➔ Abrir Mesa " + String.format("%02d", alerta.getNumeroMesa()));
        } else {
            holder.tvIrParaMesa.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAlertaClick(alerta);
            }
        });
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIconeAlerta, tvTituloAlerta, tvHoraAlerta, tvMensagemAlerta, tvIrParaMesa;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIconeAlerta = itemView.findViewById(R.id.tvIconeAlerta);
            tvTituloAlerta = itemView.findViewById(R.id.tvTituloAlerta);
            tvHoraAlerta = itemView.findViewById(R.id.tvHoraAlerta);
            tvMensagemAlerta = itemView.findViewById(R.id.tvMensagemAlerta);
            tvIrParaMesa = itemView.findViewById(R.id.tvIrParaMesa);
        }
    }
}
