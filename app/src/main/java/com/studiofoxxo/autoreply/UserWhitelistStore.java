package com.studiofoxxo.autoreply;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy whitelist store kept for compatibility, delegating to ContactWhitelistStore.
 */
public class UserWhitelistStore {

    public static List<String> getWhitelistedUsers(Context context) {
        List<String> list = new ArrayList<>();
        for (ContactWhitelistStore.ContactEntry entry : ContactWhitelistStore.getContacts(context)) {
            if (entry != null && !entry.displayName.isEmpty()) {
                list.add(entry.displayName);
            }
        }
        return list;
    }

    public static void setWhitelistedUsers(Context context, String userList) {
        if (context == null || userList == null) return;
        String[] split = userList.split("[,;\n]+");
        for (String user : split) {
            String trimmed = user.trim();
            if (!trimmed.isEmpty()) {
                ContactWhitelistStore.addOrUpdateContact(context,
                        new ContactWhitelistStore.ContactEntry("", "", trimmed));
            }
        }
    }

    public static String getWhitelistedUsersRaw(Context context) {
        return ContactWhitelistStore.getWhitelistSummary(context);
    }

    public static String getWhitelistSummary(Context context) {
        return ContactWhitelistStore.getWhitelistSummary(context);
    }
}
