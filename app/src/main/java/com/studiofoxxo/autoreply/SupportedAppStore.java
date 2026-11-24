package com.studiofoxxo.autoreply;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SupportedAppStore {
    private static final String PREFS_NAME = "autoreply_prefs";
    private static final String KEY_SUPPORTED_APPS = "supported_apps";

    private static final Map<String, String> KNOWN_SUPPORTED_APPS = new LinkedHashMap<>();
    static {
        KNOWN_SUPPORTED_APPS.put("com.google.android.apps.messaging", "Google Messages");
        KNOWN_SUPPORTED_APPS.put("com.whatsapp", "WhatsApp");
        KNOWN_SUPPORTED_APPS.put("com.whatsapp.w4b", "WhatsApp Business");
        KNOWN_SUPPORTED_APPS.put("org.thoughtcrime.securesms", "Signal");
        KNOWN_SUPPORTED_APPS.put("com.discord", "Discord");
        KNOWN_SUPPORTED_APPS.put("com.discord.beta", "Discord Beta");
        KNOWN_SUPPORTED_APPS.put("org.telegram.messenger", "Telegram");
        KNOWN_SUPPORTED_APPS.put("org.thunderdog.challegram", "Telegram X");
    }

    public static List<String> detectSupportedApps(Context context) {
        PackageManager pm = context.getPackageManager();
        List<String> supported = new ArrayList<>();
        for (String packageName : KNOWN_SUPPORTED_APPS.keySet()) {
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
                if (info != null) {
                    supported.add(packageName);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        persistSupportedApps(context, supported);
        return supported;
    }

    public static void persistSupportedApps(Context context, List<String> packageNames) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SUPPORTED_APPS, new org.json.JSONArray(packageNames).toString()).apply();
    }

    public static List<String> getPersistedSupportedApps(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_SUPPORTED_APPS, "[]");
        List<String> packages = new ArrayList<>();
        try {
            org.json.JSONArray array = new org.json.JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String pkg = array.optString(i, "").trim();
                if (!pkg.isEmpty()) {
                    packages.add(pkg);
                }
            }
        } catch (org.json.JSONException ignored) {
        }
        return packages;
    }

    public static String getDefaultLabel(String packageName) {
        String label = KNOWN_SUPPORTED_APPS.get(packageName);
        return label != null ? label : packageName;
    }

    public static boolean isSupportedPackage(String packageName) {
        return KNOWN_SUPPORTED_APPS.containsKey(packageName);
    }

    public static List<String> getAllKnownSupportedPackages() {
        return new ArrayList<>(KNOWN_SUPPORTED_APPS.keySet());
    }
}
