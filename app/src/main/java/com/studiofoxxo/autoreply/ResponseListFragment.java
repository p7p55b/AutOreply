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
import androidx.recyclerview.widget.LinearLayoutManager;
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
        responses = loadResponses(requireContext());

        adapter = new ResponseAdapter(responses, this::onResponseClicked);
        binding.responseRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.responseRecycler.setAdapter(adapter);

        binding.fab.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), EditResponseActivity.class);
            startActivity(intent);
        });
    }

    private void onResponseClicked(ResponseConfig response) {
        Intent intent = new Intent(requireContext(), EditResponseActivity.class);
        intent.putExtra("response_json", response.toJson().toString());
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
