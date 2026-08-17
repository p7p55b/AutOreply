package com.studiofoxxo.autoreply;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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

import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

public class SettingsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settings_container, new SettingsFragment())
            .commit();
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
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            registerLaunchers();

            Preference verifyPref = findPreference("verify_notification_permission");
            if (verifyPref != null) {
                verifyPref.setSummary(isNotificationAccessGranted()
                    ? "Autorisation accordée"
                    : "Autorisation manquante");
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
                            count > 0 ? count + " applications supportées détectées" : "Aucune application supportée détectée",
                            Toast.LENGTH_SHORT).show();
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
                    ContactWhitelistStore.clearContacts(requireContext());
                    Toast.makeText(requireContext(), "Whitelist de contacts vidée", Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        }

        private ActivityResultLauncher<String> requestContactsPermissionLauncher;
        private ActivityResultLauncher<Intent> pickContactLauncher;

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
                    String contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                    String lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY));
                    String displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                    if (displayName == null) {
                        displayName = "Contact inconnu";
                    }
                    ContactWhitelistStore.addOrUpdateContact(requireContext(),
                            new ContactWhitelistStore.ContactEntry(contactId, lookupKey, displayName));
                    Log.d("SettingsActivity", "Added whitelist contact: " + displayName + " id=" + contactId + " lookup=" + lookupKey);
                    Toast.makeText(requireContext(), "Contact ajouté à la whitelist", Toast.LENGTH_SHORT).show();
                }
            }
        }

        private void showAddManualWhitelistDialog() {
            EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

            new AlertDialog.Builder(requireContext())
                    .setTitle("Ajouter un contact Discord / manuel")
                    .setMessage("Entrez le nom ou pseudo d'un expéditeur autorisé")
                    .setView(input)
                    .setPositiveButton("Ajouter", (dialog, which) -> {
                        String value = input.getText().toString().trim();
                        if (!value.isEmpty()) {
                            ContactWhitelistStore.addOrUpdateContact(requireContext(),
                                    new ContactWhitelistStore.ContactEntry("", "", value));
                            Log.d("SettingsActivity", "Added manual whitelist entry: " + value);
                            Toast.makeText(requireContext(), "Entrée Discord ajoutée à la whitelist", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
        }

        private boolean isNotificationAccessGranted() {
            Context context = requireContext();
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.getPackageName());
        }

        private void updateDetectAppsSummary(Preference preference) {
            List<String> supported = SupportedAppStore.getPersistedSupportedApps(requireContext());
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
                preference.setSummary("Détecté : " + labels.toString());
            }
        }
    }
}