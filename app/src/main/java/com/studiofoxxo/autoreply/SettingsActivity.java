package com.studiofoxxo.autoreply;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen preferenceScreen) {
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey());
        fragment.setArguments(args);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, fragment)
                .addToBackStack(preferenceScreen.getKey())
                .commit();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private ActivityResultLauncher<String> requestContactsPermissionLauncher;
        private ActivityResultLauncher<Intent> pickContactLauncher;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            registerLaunchers();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            setupPreferences();
        }

        @Override
        public void onResume() {
            super.onResume();
            updateNotificationPermissionSummary();
            updateWhitelistSummary();
            updateExcludedAppsSummary();
        }

        private void setupPreferences() {
            Preference verifyPref = findPreference("verify_notification_permission");
            if (verifyPref != null) {
                verifyPref.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivity(intent);
                    return true;
                });
            }

            Preference detectAppsPref = findPreference("detect_supported_apps");
            if (detectAppsPref != null) {
                updateDetectAppsSummary(detectAppsPref);
                detectAppsPref.setOnPreferenceClickListener(preference -> {
                    int count = SupportedAppStore.detectSupportedApps(requireContext()).size();
                    updateDetectAppsSummary(detectAppsPref);
                    Toast.makeText(requireContext(),
                            count > 0 ? count + " application(s) supportée(s) détectée(s)" : "Aucune application supportée détectée",
                            Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            Preference excludeAppsPref = findPreference("exclude_apps");
            if (excludeAppsPref != null) {
                updateExcludedAppsSummary();
                excludeAppsPref.setOnPreferenceClickListener(preference -> {
                    showExcludeAppsDialog();
                    return true;
                });
            }

            Preference whitelistUsersPref = findPreference("exclude_contacts");
            if (whitelistUsersPref != null) {
                whitelistUsersPref.setSummaryProvider(preference -> ContactWhitelistStore.getWhitelistSummary(requireContext()));
                whitelistUsersPref.setOnPreferenceClickListener(preference -> {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
                    } else {
                        launchContactPicker();
                    }
                    return true;
                });
            }

            Preference manualWhitelistPref = findPreference("add_manual_whitelist");
            if (manualWhitelistPref != null) {
                manualWhitelistPref.setOnPreferenceClickListener(preference -> {
                    showAddManualWhitelistDialog();
                    return true;
                });
            }

            Preference clearContactWhitelistPref = findPreference("clear_contact_whitelist");
            if (clearContactWhitelistPref != null) {
                clearContactWhitelistPref.setOnPreferenceClickListener(preference -> {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Effacer la whitelist")
                            .setMessage("Voulez-vous supprimer tous les contacts de la whitelist ? Si la whitelist est vide, l'application répondra à tous les expéditeurs autorisés par les règles.")
                            .setPositiveButton("Effacer", (dialog, which) -> {
                                ContactWhitelistStore.clearContacts(requireContext());
                                updateWhitelistSummary();
                                Toast.makeText(requireContext(), "Whitelist de contacts vidée", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Annuler", null)
                            .show();
                    return true;
                });
            }

            // Criteria explanation helpers
            Preference criteriaKeywords = findPreference("criteria_keywords");
            if (criteriaKeywords != null) {
                criteriaKeywords.setOnPreferenceClickListener(pref -> {
                    showInfoDialog("Mots-clés", "Les mots-clés et expressions sont configurés directement dans chaque règle de réponse (depuis l'écran principal). Le déclenchement s'active dès que le message contient l'un des mots-clés définis.");
                    return true;
                });
            }

            Preference criteriaLinks = findPreference("criteria_links");
            if (criteriaLinks != null) {
                criteriaLinks.setOnPreferenceClickListener(pref -> {
                    showInfoDialog("Liens / Réseaux sociaux", "Ce critère détecte automatiquement les liens URL (http, https, www) et les partages réseaux sociaux (Instagram, TikTok, Snapchat, Twitter/X, Telegram, Discord, YouTube).");
                    return true;
                });
            }

            Preference criteriaVoice = findPreference("criteria_voice");
            if (criteriaVoice != null) {
                criteriaVoice.setOnPreferenceClickListener(pref -> {
                    showInfoDialog("Messages vocaux", "Ce critère détecte les messages vocaux et notes audio reçus sur WhatsApp, Telegram, Discord et Google Messages.");
                    return true;
                });
            }
        }

        private void showInfoDialog(String title, String message) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Compris", null)
                    .show();
        }

        private void showExcludeAppsDialog() {
            List<String> packages = SupportedAppStore.getAllKnownSupportedPackages();
            List<String> labels = new ArrayList<>();
            for (String pkg : packages) {
                labels.add(SupportedAppStore.getDefaultLabel(pkg));
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            Set<String> currentlyExcluded = prefs.getStringSet("excluded_apps", Collections.emptySet());
            boolean[] checked = new boolean[packages.size()];
            for (int i = 0; i < packages.size(); i++) {
                checked[i] = currentlyExcluded != null && currentlyExcluded.contains(packages.get(i));
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle("Applications exclues")
                    .setMultiChoiceItems(labels.toArray(new String[0]), checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                    .setPositiveButton("Enregistrer", (dialog, which) -> {
                        Set<String> newExcluded = new HashSet<>();
                        for (int i = 0; i < packages.size(); i++) {
                            if (checked[i]) {
                                newExcluded.add(packages.get(i));
                            }
                        }
                        prefs.edit().putStringSet("excluded_apps", newExcluded).apply();
                        updateExcludedAppsSummary();
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
        }

        private void updateExcludedAppsSummary() {
            Preference excludeAppsPref = findPreference("exclude_apps");
            if (excludeAppsPref == null) return;
            Context context = getContext();
            if (context == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> excluded = prefs.getStringSet("excluded_apps", Collections.emptySet());
            if (excluded == null || excluded.isEmpty()) {
                excludeAppsPref.setSummary("Aucune application exclue");
            } else {
                List<String> names = new ArrayList<>();
                for (String pkg : excluded) {
                    names.add(SupportedAppStore.getDefaultLabel(pkg));
                }
                excludeAppsPref.setSummary("Exclues : " + String.join(", ", names));
            }
        }

        private void registerLaunchers() {
            requestContactsPermissionLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), granted -> {
                        if (granted) {
                            launchContactPicker();
                        } else {
                            Toast.makeText(requireContext(), "Permission contacts refusée", Toast.LENGTH_SHORT).show();
                        }
                    });

            pickContactLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(), result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            handleContactPicked(result.getData().getData());
                        }
                    });
        }

        private void launchContactPicker() {
            Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
            pickContactLauncher.launch(intent);
        }

        private void handleContactPicked(Uri contactUri) {
            if (contactUri == null) {
                return;
            }
            String[] projection = {
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            };
            try (Cursor cursor = requireContext().getContentResolver().query(contactUri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idCol = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                    int lookupCol = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
                    int nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);

                    String contactId = idCol >= 0 ? cursor.getString(idCol) : "";
                    String lookupKey = lookupCol >= 0 ? cursor.getString(lookupCol) : "";
                    String displayName = nameCol >= 0 ? cursor.getString(nameCol) : "Contact inconnu";
                    if (displayName == null) {
                        displayName = "Contact inconnu";
                    }

                    ContactWhitelistStore.addOrUpdateContact(requireContext(),
                            new ContactWhitelistStore.ContactEntry(contactId, lookupKey, displayName));
                    updateWhitelistSummary();
                    Toast.makeText(requireContext(), "Contact ajouté à la whitelist : " + displayName, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("SettingsActivity", "Error handling picked contact", e);
                Toast.makeText(requireContext(), "Impossible de charger le contact", Toast.LENGTH_SHORT).show();
            }
        }

        private void showAddManualWhitelistDialog() {
            EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            input.setHint("Ex: John, PseudoDiscord, etc.");

            new AlertDialog.Builder(requireContext())
                    .setTitle("Ajouter un contact manuel / Discord")
                    .setMessage("Entrez le nom ou pseudo de l'expéditeur autorisé")
                    .setView(input)
                    .setPositiveButton("Ajouter", (dialog, which) -> {
                        String value = input.getText().toString().trim();
                        if (!value.isEmpty()) {
                            ContactWhitelistStore.addOrUpdateContact(requireContext(),
                                    new ContactWhitelistStore.ContactEntry("", "", value));
                            updateWhitelistSummary();
                            Toast.makeText(requireContext(), "Entrée ajoutée à la whitelist : " + value, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
        }

        private void updateWhitelistSummary() {
            Preference whitelistUsersPref = findPreference("exclude_contacts");
            if (whitelistUsersPref != null && getContext() != null) {
                whitelistUsersPref.setSummary(ContactWhitelistStore.getWhitelistSummary(requireContext()));
            }
        }

        private void updateNotificationPermissionSummary() {
            Preference verifyPref = findPreference("verify_notification_permission");
            if (verifyPref != null && getContext() != null) {
                boolean granted = isNotificationAccessGranted();
                verifyPref.setSummary(granted
                        ? "Autorisation accordée (le service d'auto-réponse est actif)"
                        : "Autorisation manquante (cliquez pour accorder l'accès aux notifications)");
            }
        }

        private boolean isNotificationAccessGranted() {
            Context context = getContext();
            if (context == null) return false;
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.getPackageName());
        }

        private void updateDetectAppsSummary(Preference preference) {
            Context context = getContext();
            if (context == null) return;
            List<String> supported = SupportedAppStore.getPersistedSupportedApps(context);
            if (supported.isEmpty()) {
                preference.setSummary("Aucune application supportée détectée");
            } else {
                StringBuilder labels = new StringBuilder();
                for (int i = 0; i < supported.size(); i++) {
                    if (i > 0) {
                        labels.append(", ");
                    }
                    labels.append(SupportedAppStore.getDefaultLabel(supported.get(i)));
                }
                preference.setSummary("Détecté : " + labels);
            }
        }
    }
}
