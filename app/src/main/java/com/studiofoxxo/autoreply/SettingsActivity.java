package com.studiofoxxo.autoreply;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settings_container, new SettingsFragment())
            .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

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
                    preference.setSummary(count > 0
                            ? count + " applications supportées détectées"
                            : "Aucune application supportée détectée");
                    return true;
                });
            }
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