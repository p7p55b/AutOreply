package com.studiofoxxo.autoreply;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.studiofoxxo.autoreply.databinding.ItemResponseBinding;

import java.util.List;

public class ResponseAdapter extends RecyclerView.Adapter<ResponseAdapter.ViewHolder> {

    public interface OnResponseClickListener {
        void onClick(ResponseConfig response);
    }

    public interface OnResponseDeleteListener {
        void onDelete(ResponseConfig response);
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
        holder.binding.getRoot().setOnClickListener(v -> listener.onClick(response));
        holder.binding.deleteButton.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDelete(response);
        });

        // Make tile roughly square: set height equal to measured width
        holder.binding.getRoot().post(() -> {
            int w = holder.binding.getRoot().getWidth();
            if (w > 0 && holder.binding.getRoot().getHeight() != w) {
                holder.binding.getRoot().getLayoutParams().height = w;
                holder.binding.getRoot().requestLayout();
            }
        });
    }

    @Override
    public int getItemCount() {
        return responses.size();
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
            apps = sb.toString();
        }

        String triggers = response.triggers.isEmpty() ? "Aucun critère" : String.join(", ", response.triggers);
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
