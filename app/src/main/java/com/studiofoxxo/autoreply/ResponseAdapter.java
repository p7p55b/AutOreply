package com.studiofoxxo.autoreply;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.studiofoxxo.autoreply.databinding.ItemResponseBinding;

import java.util.List;

public class ResponseAdapter extends RecyclerView.Adapter<ResponseAdapter.ViewHolder> {

    public interface OnResponseClickListener {
        void onClick(ResponseConfig response, int position);
    }

    public interface OnResponseDeleteListener {
        void onDelete(ResponseConfig response, int position);
    }

    private final List<ResponseConfig> responses;
    private final OnResponseClickListener listener;
    private final OnResponseDeleteListener deleteListener;

    public ResponseAdapter(List<ResponseConfig> responses, OnResponseClickListener listener, OnResponseDeleteListener deleteListener) {
        this.responses = responses;
        this.listener = listener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemResponseBinding binding = ItemResponseBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ResponseConfig response = responses.get(position);
        holder.binding.responseTitle.setText(response.title);
        holder.binding.responseSummary.setText(response.text);
        holder.binding.responseInfo.setText(buildInfo(response));

        holder.binding.getRoot().setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null && pos < responses.size()) {
                listener.onClick(responses.get(pos), pos);
            }
        });

        holder.binding.deleteButton.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && deleteListener != null && pos < responses.size()) {
                deleteListener.onDelete(responses.get(pos), pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return responses != null ? responses.size() : 0;
    }

    private String buildInfo(ResponseConfig response) {
        String apps;
        if (response.allApps) {
            apps = "Toutes les applications";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < response.allowedApps.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(SupportedAppStore.getDefaultLabel(response.allowedApps.get(i)));
            }
            apps = sb.length() > 0 ? sb.toString() : "Aucune app";
        }

        String triggers = (response.triggers == null || response.triggers.isEmpty())
                ? "Aucun (par défaut)"
                : String.join(", ", response.triggers);

        return "Apps : " + apps + " · Critères : " + triggers;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemResponseBinding binding;

        ViewHolder(ItemResponseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
