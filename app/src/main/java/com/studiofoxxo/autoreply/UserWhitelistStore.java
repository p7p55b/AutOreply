package com.studiofoxxo.autoreply;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class UserWhitelistStore {
    private static final String PREFS_NAME = "autoreply_prefs";
    private static final String KEY_USER_WHITELIST = "user_whitelist";

    public static List<String> getWhitelistedUsers(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_USER_WHITELIST, "");
        List<String> users = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return users;
        }
        String[] split = raw.split(",");
        for (String value : split) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                users.add(trimmed);
            }
        }
        return users;
    }

    public static void setWhitelistedUsers(Context context, String userList) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_WHITELIST, userList == null ? "" : userList.trim()).apply();
    }

    public static String getWhitelistedUsersRaw(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_USER_WHITELIST, "");
        return raw == null ? "" : raw;
    }

    public static String getWhitelistSummary(Context context) {
        List<String> users = getWhitelistedUsers(context);
        if (users.isEmpty()) {
            return "Aucune whitelist utilisateur définie";
        }
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(users.get(i));
        }
        return "Whitelist utilisateurs : " + summary.toString();
    }
}
