package com.studiofoxxo.autoreply;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;

import com.studiofoxxo.autoreply.databinding.FragmentResponseListBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResponseListFragment extends Fragment {

    private FragmentResponseListBinding binding;
    private ResponseAdapter adapter;
    private final List<ResponseConfig> responses = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentResponseListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadResponses();
    }

    private void setupRecyclerView() {
        Context context = requireContext();
        responses.clear();
        responses.addAll(loadResponses(context));
        adapter = new ResponseAdapter(responses, this::onResponseClicked, this::onResponseDeleteRequest);
        binding.responseRecycler.setLayoutManager(new GridLayoutManager(context, 2));
        binding.responseRecycler.setAdapter(adapter);
        showEmptyState(responses.isEmpty());
    }

    private void onResponseDeleteRequest(ResponseConfig response, int position) {
        Context context = getContext();
        if (context == null) return;

        new AlertDialog.Builder(context)
                .setTitle("Supprimer la réponse")
                .setMessage("Voulez-vous vraiment supprimer \"" + response.title + "\" ?")
                .setPositiveButton("Supprimer", (dialog, which) -> deleteResponseAt(position))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void deleteResponseAt(int position) {
        Context context = getContext();
        if (context == null || position < 0 || position >= responses.size()) {
            return;
        }

        responses.remove(position);
        context.getSharedPreferences("autoreply_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("responses_list", ResponseConfig.listToJson(responses))
                .apply();

        if (adapter != null) {
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, responses.size() - position);
        }
        showEmptyState(responses.isEmpty());
    }

    private void reloadResponses() {
        Context context = getContext();
        if (context == null) return;

        responses.clear();
        responses.addAll(loadResponses(context));
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        showEmptyState(responses.isEmpty());
    }

    private void showEmptyState(boolean empty) {
        if (binding == null) return;
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.responseRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void onResponseClicked(ResponseConfig response, int position) {
        Context context = getContext();
        if (context == null) return;

        Intent intent = new Intent(context, EditResponseActivity.class);
        intent.putExtra("response_index", position);
        intent.putExtra("original_title", response.title);
        try {
            intent.putExtra("response_json", response.toJson().toString());
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            intent.putExtra("response_json", "");
        }
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static List<ResponseConfig> loadResponses(Context context) {
        String raw = context.getSharedPreferences("autoreply_prefs", Context.MODE_PRIVATE)
                .getString("responses_list", "[]");
        return ResponseConfig.listFromJson(raw);
    }
}
