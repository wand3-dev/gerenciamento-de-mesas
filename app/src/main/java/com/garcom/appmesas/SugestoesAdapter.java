package com.garcom.appmesas;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SugestoesAdapter extends RecyclerView.Adapter<SugestoesAdapter.SugestaoViewHolder> {
    private List<Product> produtos;
    private OnSugestaoClickListener listener;

    public interface OnSugestaoClickListener {
        void onSugestaoClick(Product product);
    }

    public SugestoesAdapter(List<Product> produtos, OnSugestaoClickListener listener) {
        this.produtos = produtos;
        this.listener = listener;
    }

    public void atualizarLista(List<Product> novaLista) {
        this.produtos = novaLista;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SugestaoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sugestao, parent, false);
        return new SugestaoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SugestaoViewHolder holder, int position) {
        Product p = produtos.get(position);
        holder.tvSugestaoCod.setText("CÓD: " + p.getCodigo());
        holder.tvSugestaoDesc.setText(p.getDescricao());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSugestaoClick(p);
        });
    }

    @Override
    public int getItemCount() {
        return produtos.size();
    }

    public static class SugestaoViewHolder extends RecyclerView.ViewHolder {
        TextView tvSugestaoCod, tvSugestaoDesc;

        public SugestaoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSugestaoCod = itemView.findViewById(R.id.tvSugestaoCod);
            tvSugestaoDesc = itemView.findViewById(R.id.tvSugestaoDesc);
        }
    }
}
