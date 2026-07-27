package com.studiofoxxo.autoreply;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.studiofoxxo.autoreply.databinding.FragmentResponseListBinding;

import java.util.ArrayList;
import java.util.List;

public class ResponseListFragment extends Fragment {

    private FragmentResponseListBinding binding;
    private ResponseAdapter adapter;
    private List<ResponseConfig> responses;

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

        binding.fab.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), EditResponseActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadResponses();
    }

    private void setupRecyclerView() {
        responses = loadResponses(requireContext());
        adapter = new ResponseAdapter(responses, this::onResponseClicked, this::onResponseDelete);
        binding.responseRecycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.responseRecycler.setAdapter(adapter);
        showEmptyState(responses.isEmpty());
    }

    private void onResponseDelete(ResponseConfig response) {
        // remove from list and persist
        for (int i = 0; i < responses.size(); i++) {
            if (responses.get(i).title.equals(response.title)) {
                responses.remove(i);
                break;
            }
        }
        getContext().getSharedPreferences("autoreply_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("responses_list", ResponseConfig.listToJson(responses))
                .apply();
        adapter.notifyDataSetChanged();
        showEmptyState(responses.isEmpty());
    }

    private void reloadResponses() {
        responses.clear();
        responses.addAll(loadResponses(requireContext()));
        adapter.notifyDataSetChanged();
        showEmptyState(responses.isEmpty());
    }

    private void showEmptyState(boolean empty) {
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.responseRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void onResponseClicked(ResponseConfig response) {
        Intent intent = new Intent(requireContext(), EditResponseActivity.class);
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
