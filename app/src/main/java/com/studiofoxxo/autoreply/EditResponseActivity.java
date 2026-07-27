package com.studiofoxxo.autoreply;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.studiofoxxo.autoreply.databinding.ActivityEditResponseBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditResponseActivity extends AppCompatActivity {

    private ActivityEditResponseBinding binding;
    private ResponseConfig response;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditResponseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        response = loadResponseFromIntent();
        setupUi();
    }

    private ResponseConfig loadResponseFromIntent() {
        String json = getIntent().getStringExtra("response_json");
        if (!TextUtils.isEmpty(json)) {
            try {
                return ResponseConfig.fromJson(new org.json.JSONObject(json));
            } catch (org.json.JSONException e) {
                e.printStackTrace();
            }
        }
        return new ResponseConfig("Nouvelle réponse", "", new ArrayList<>(), new ArrayList<>(), true);
    }

    private void setupUi() {
        binding.responseTitle.setText(response.title);
        binding.responseText.setText(response.text);
        binding.responseApps.setText(response.allApps ? "Toutes les applications" : TextUtils.join(", ", response.allowedApps));
        binding.responseTriggers.setText(response.triggers.isEmpty() ? "Aucun critère" : TextUtils.join(", ", response.triggers));

        binding.saveButton.setOnClickListener(v -> saveResponse());
        binding.selectAppsButton.setOnClickListener(v -> selectApps());
        binding.selectTriggersButton.setOnClickListener(v -> selectTriggers());
    }

    private void saveResponse() {
        String title = binding.responseTitle.getText().toString().trim();
        String text = binding.responseText.getText().toString().trim();
        if (title.isEmpty() || text.isEmpty()) {
            Toast.makeText(this, "Le titre et le message sont requis", Toast.LENGTH_SHORT).show();
            return;
        }
        response.title = title;
        response.text = text;
        saveResponseConfig(response);
        finish();
    }

    private void saveResponseConfig(ResponseConfig response) {
        List<ResponseConfig> list = loadResponses(this);
        boolean updated = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).title.equals(response.title)) {
                list.set(i, response);
                updated = true;
                break;
            }
        }
        if (!updated) {
            list.add(response);
        }
        getSharedPreferences("autoreply_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("responses_list", ResponseConfig.listToJson(list))
                .apply();
    }

    private void selectApps() {
        // TODO: afficher un dialogue de sélection des applications
    }

    private void selectTriggers() {
        String[] options = new String[] {
                "mots-clés",
                "liens/réseaux sociaux",
                "vocaux"
        };
        boolean[] selected = new boolean[options.length];
        for (int i = 0; i < options.length; i++) {
            String option = options[i];
            selected[i] = response.triggers.stream()
                    .anyMatch(trigger -> trigger.equalsIgnoreCase(option));
        }

        new AlertDialog.Builder(this)
                .setTitle("Choisir les critères de déclenchement")
                .setMultiChoiceItems(options, selected, (dialog, which, isChecked) -> {
                    selected[which] = isChecked;
                })
                .setPositiveButton("OK", (dialog, which) -> {
                    response.triggers.clear();
                    for (int i = 0; i < options.length; i++) {
                        if (selected[i]) {
                            response.triggers.add(options[i]);
                        }
                    }
                    binding.responseTriggers.setText(response.triggers.isEmpty()
                            ? "Aucun critère"
                            : TextUtils.join(", ", response.triggers));
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private static List<ResponseConfig> loadResponses(Context context) {
        String raw = context.getSharedPreferences("autoreply_prefs", Context.MODE_PRIVATE)
                .getString("responses_list", "[]");
        return ResponseConfig.listFromJson(raw);
    }
}
