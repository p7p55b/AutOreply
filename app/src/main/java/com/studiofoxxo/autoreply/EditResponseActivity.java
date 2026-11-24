package com.studiofoxxo.autoreply;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.studiofoxxo.autoreply.databinding.ActivityEditResponseBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EditResponseActivity extends AppCompatActivity {
    private static final String TAG = "EditResponseActivity";

    private ActivityEditResponseBinding binding;
    private ResponseConfig response;
    private int responseIndex = -1;
    private String originalTitle = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditResponseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        responseIndex = getIntent().getIntExtra("response_index", -1);
        originalTitle = getIntent().getStringExtra("original_title");
        response = loadResponseFromIntent();
        if (originalTitle == null && response != null) {
            originalTitle = response.title;
        }

        setupUi();
    }

    private ResponseConfig loadResponseFromIntent() {
        String json = getIntent().getStringExtra("response_json");
        if (!TextUtils.isEmpty(json)) {
            try {
                return ResponseConfig.fromJson(new org.json.JSONObject(json));
            } catch (org.json.JSONException e) {
                Log.e(TAG, "Error parsing response_json from Intent", e);
            }
        }
        return new ResponseConfig("Nouvelle réponse", "", new ArrayList<>(), new ArrayList<>(), true);
    }

    private void setupUi() {
        binding.responseTitle.setText(response.title);
        binding.responseText.setText(response.text);
        binding.responseApps.setText(response.allApps ? "Toutes les applications" : formatAppLabels(response.allowedApps));
        binding.responseTriggers.setText(response.triggers.isEmpty() ? "Aucun critère (par défaut)" : TextUtils.join(", ", response.triggers));
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

        if (response.isKeywordTriggerSelected() && response.keywords.isEmpty()) {
            Toast.makeText(this, "Définissez au moins un mot-clé ou une phrase clé pour ce déclenchement", Toast.LENGTH_SHORT).show();
            return;
        }

        saveResponseConfig(response);
        finish();
    }

    private List<String> parseList(String raw) {
        List<String> values = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return values;
        }
        String[] items = raw.split("[,;\n]+");
        for (String item : items) {
            String clean = item.trim();
            if (!clean.isEmpty() && !values.contains(clean)) {
                values.add(clean);
            }
        }
        return values;
    }

    private void saveResponseConfig(ResponseConfig response) {
        List<ResponseConfig> list = loadResponses(this);
        boolean updated = false;

        // 1. Try to update by index if valid
        if (responseIndex >= 0 && responseIndex < list.size()) {
            list.set(responseIndex, response);
            updated = true;
        } else if (!TextUtils.isEmpty(originalTitle)) {
            // 2. Try to update by original title
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).title.equals(originalTitle)) {
                    list.set(i, response);
                    updated = true;
                    break;
                }
            }
        }

        // 3. Fallback: match by current title
        if (!updated) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).title.equals(response.title)) {
                    list.set(i, response);
                    updated = true;
                    break;
                }
            }
        }

        // 4. If new, append
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
        if (detected.isEmpty()) {
            // Fallback to all known supported packages so user is never blocked
            detected = SupportedAppStore.getAllKnownSupportedPackages();
        }

        final List<String> packageNames = detected;
        List<String> displayNames = new ArrayList<>();
        for (String packageName : packageNames) {
            displayNames.add(SupportedAppStore.getDefaultLabel(packageName));
        }

        boolean[] selected = new boolean[packageNames.size()];
        for (int i = 0; i < packageNames.size(); i++) {
            selected[i] = response.allApps || response.allowedApps.contains(packageNames.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Applications autorisées")
                .setMultiChoiceItems(displayNames.toArray(new String[0]), selected, (dialog, which, isChecked) -> selected[which] = isChecked)
                .setPositiveButton("OK", (dialog, which) -> {
                    response.allowedApps.clear();
                    int count = 0;
                    for (int i = 0; i < packageNames.size(); i++) {
                        if (selected[i]) {
                            response.allowedApps.add(packageNames.get(i));
                            count++;
                        }
                    }
                    // If all selected or none selected, treat as all apps
                    response.allApps = (count == 0 || count == packageNames.size());
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
                .setTitle("Critères de déclenchement")
                .setMultiChoiceItems(options, selected, (dialog, which, isChecked) -> selected[which] = isChecked)
                .setPositiveButton("OK", (dialog, which) -> {
                    response.triggers.clear();
                    for (int i = 0; i < options.length; i++) {
                        if (selected[i]) {
                            response.triggers.add(options[i]);
                        }
                    }
                    binding.responseTriggers.setText(response.triggers.isEmpty()
                            ? "Aucun critère (par défaut)"
                            : TextUtils.join(", ", response.triggers));
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void selectExcludedContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Autorisez l'accès aux contacts pour sélectionner des exclusions.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> candidateNames = loadContactNames();
        if (candidateNames.isEmpty()) {
            Toast.makeText(this, "Aucun contact trouvé sur l'appareil.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> selectedValues = parseList(binding.responseExcludedContacts.getText().toString());
        boolean[] checked = new boolean[candidateNames.size()];
        for (int i = 0; i < candidateNames.size(); i++) {
            checked[i] = selectedValues.contains(candidateNames.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Contacts à exclure")
                .setMultiChoiceItems(candidateNames.toArray(new String[0]), checked, (dialog, which, isChecked) -> checked[which] = isChecked)
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
                int nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
                if (nameIndex >= 0) {
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(nameIndex);
                        if (!TextUtils.isEmpty(name)) {
                            names.add(name.trim());
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Missing READ_CONTACTS permission in loadContactNames", e);
        } catch (Exception e) {
            Log.e(TAG, "Error querying contacts in loadContactNames", e);
        }

        for (ContactWhitelistStore.ContactEntry entry : ContactWhitelistStore.getContacts(this)) {
            String name = entry.displayName != null ? entry.displayName.trim() : "";
            if (!TextUtils.isEmpty(name) && !names.contains(name)) {
                names.add(name);
            }
        }

        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private String formatAppLabels(List<String> packageNames) {
        if (packageNames == null || packageNames.isEmpty()) {
            return "Toutes les applications";
        }
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
