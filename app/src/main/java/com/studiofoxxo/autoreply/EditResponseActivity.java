package com.studiofoxxo.autoreply;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
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
        binding.responseApps.setText(response.allApps ? "Toutes les applications" : formatAppLabels(response.allowedApps));
        binding.responseTriggers.setText(response.triggers.isEmpty() ? "Aucun critère" : TextUtils.join(", ", response.triggers));
        binding.responseKeywords.setText(TextUtils.join(", ", response.keywords));
        binding.responseExcludedContacts.setText(TextUtils.join(", ", response.excludedContacts));

        binding.saveButton.setOnClickListener(v -> saveResponse());
        binding.selectAppsButton.setOnClickListener(v -> selectApps());
        binding.selectTriggersButton.setOnClickListener(v -> selectTriggers());
        binding.selectExcludedContactsButton.setOnClickListener(v -> selectExcludedContacts());
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
        response.keywords = parseList(binding.responseKeywords.getText().toString());
        response.excludedContacts = parseList(binding.responseExcludedContacts.getText().toString());
        if (isKeywordTriggerSelected(response) && response.keywords.isEmpty()) {
            Toast.makeText(this, "Définissez au moins un mot-clé ou une phrase clé pour ce déclenchement", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isVoiceTriggerSelected(response) && response.allowedApps.isEmpty()) {
            Toast.makeText(this, "Pour les vocaux, choisissez au moins une application", Toast.LENGTH_SHORT).show();
            return;
        }
        saveResponseConfig(response);
        finish();
    }

    private boolean isKeywordTriggerSelected(ResponseConfig response) {
        return response.triggers.stream().anyMatch(trigger ->
                trigger.equalsIgnoreCase("mots-clés") || trigger.equalsIgnoreCase("keyword") || trigger.equalsIgnoreCase("keywords"));
    }

    private boolean isVoiceTriggerSelected(ResponseConfig response) {
        return response.triggers.stream().anyMatch(trigger ->
                trigger.equalsIgnoreCase("vocaux") || trigger.equalsIgnoreCase("voice") || trigger.equalsIgnoreCase("audio"));
    }

    private List<String> parseList(String raw) {
        List<String> values = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return values;
        }
        String[] items = raw.split("[,;\n]+");
        for (String item : items) {
            String clean = item.trim();
            if (!clean.isEmpty()) {
                values.add(clean);
            }
        }
        return values;
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
        List<String> detected = SupportedAppStore.getPersistedSupportedApps(this);
        if (detected.isEmpty()) {
            detected = SupportedAppStore.detectSupportedApps(this);
        }

        final List<String> packageNames = detected;

        if (packageNames.isEmpty()) {
            Toast.makeText(this, "Aucune application compatible détectée. Utilisez la fonction de détection dans les réglages.", Toast.LENGTH_LONG).show();
            return;
        }

        List<String> displayNames = new ArrayList<>();
        for (String packageName : packageNames) {
            displayNames.add(SupportedAppStore.getDefaultLabel(packageName));
        }

        boolean voiceSelected = isVoiceTriggerSelected(response);
        boolean allAllowed = response.allApps && !voiceSelected;
        boolean[] selected = new boolean[packageNames.size()];
        for (int i = 0; i < packageNames.size(); i++) {
            selected[i] = allAllowed || response.allowedApps.contains(packageNames.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Applications compatibles")
                .setMultiChoiceItems(displayNames.toArray(new String[0]), selected, (dialog, which, isChecked) -> selected[which] = isChecked)
                .setPositiveButton("OK", (dialog, which) -> {
                    response.allowedApps.clear();
                    for (int i = 0; i < packageNames.size(); i++) {
                        if (selected[i]) {
                            response.allowedApps.add(packageNames.get(i));
                        }
                    }
                    if (voiceSelected && response.allowedApps.isEmpty()) {
                        Toast.makeText(this, "Pour les vocaux, choisissez au moins une application", Toast.LENGTH_SHORT).show();
                    }
                    response.allApps = !voiceSelected && response.allowedApps.isEmpty();
                    binding.responseApps.setText(response.allApps ? "Toutes les applications" : formatAppLabels(response.allowedApps));
                })
                .setNegativeButton("Annuler", null)
                .show();
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

    private void selectExcludedContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Autorise l’accès aux contacts pour sélectionner des exclusions.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> candidateNames = loadContactNames();
        if (candidateNames.isEmpty()) {
            Toast.makeText(this, "Aucun contact trouvé sur l’appareil.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> selectedValues = parseList(binding.responseExcludedContacts.getText().toString());
        boolean[] checked = new boolean[candidateNames.size()];
        for (int i = 0; i < candidateNames.size(); i++) {
            checked[i] = selectedValues.contains(candidateNames.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Contacts à exclure")
                .setMultiChoiceItems(candidateNames.toArray(new String[0]), checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton("OK", (dialog, which) -> {
                    List<String> chosen = new ArrayList<>();
                    for (int i = 0; i < candidateNames.size(); i++) {
                        if (checked[i]) {
                            chosen.add(candidateNames.get(i));
                        }
                    }
                    response.excludedContacts = chosen;
                    binding.responseExcludedContacts.setText(TextUtils.join(", ", chosen));
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private List<String> loadContactNames() {
        List<String> names = new ArrayList<>();
        String[] projection = {ContactsContract.Contacts.DISPLAY_NAME_PRIMARY};
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                ContactsContract.Contacts.HAS_PHONE_NUMBER + " > 0 OR " + ContactsContract.Contacts.IN_VISIBLE_GROUP + " = 1",
                null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC")) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    if (!TextUtils.isEmpty(name)) {
                        names.add(name.trim());
                    }
                }
            }
        }

        for (ContactWhitelistStore.ContactEntry entry : ContactWhitelistStore.getContacts(this)) {
            String name = entry.displayName != null ? entry.displayName.trim() : "";
            if (!TextUtils.isEmpty(name) && !names.contains(name)) {
                names.add(name);
            }
        }

        java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private String formatAppLabels(List<String> packageNames) {
        List<String> labels = new ArrayList<>();
        for (String packageName : packageNames) {
            labels.add(SupportedAppStore.getDefaultLabel(packageName));
        }
        return TextUtils.join(", ", labels);
    }

    private static List<ResponseConfig> loadResponses(Context context) {
        String raw = context.getSharedPreferences("autoreply_prefs", Context.MODE_PRIVATE)
                .getString("responses_list", "[]");
        return ResponseConfig.listFromJson(raw);
    }
}
