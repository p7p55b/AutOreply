package com.studiofoxxo.autoreply;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ContactWhitelistStore {
    private static final String PREFS_NAME = "autoreply_prefs";
    private static final String KEY_CONTACT_WHITELIST = "contact_whitelist";

    public static class ContactEntry {
        public final String lookupKey;
        public final String displayName;

        public ContactEntry(String lookupKey, String displayName) {
            this.lookupKey = lookupKey;
            this.displayName = displayName;
        }
    }

    public static List<ContactEntry> getContacts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_CONTACT_WHITELIST, "");
        List<ContactEntry> contacts = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return contacts;
        }

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String lookupKey = object.optString("lookupKey", "");
                String displayName = object.optString("displayName", "");
                if (!lookupKey.isEmpty() && !displayName.isEmpty()) {
                    contacts.add(new ContactEntry(lookupKey, displayName));
                }
            }
        } catch (JSONException ignored) {
        }

        return contacts;
    }

    public static void saveContacts(Context context, List<ContactEntry> contacts) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray array = new JSONArray();
        for (ContactEntry contact : contacts) {
            JSONObject object = new JSONObject();
            try {
                object.put("lookupKey", contact.lookupKey);
                object.put("displayName", contact.displayName);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(KEY_CONTACT_WHITELIST, array.toString()).apply();
    }

    public static void addOrUpdateContact(Context context, ContactEntry contact) {
        List<ContactEntry> contacts = getContacts(context);
        boolean found = false;
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).lookupKey.equals(contact.lookupKey)) {
                contacts.set(i, contact);
                found = true;
                break;
            }
        }
        if (!found) {
            contacts.add(contact);
        }
        saveContacts(context, contacts);
    }

    public static void clearContacts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_CONTACT_WHITELIST).apply();
    }

    public static String getWhitelistSummary(Context context) {
        List<ContactEntry> contacts = getContacts(context);
        if (contacts.isEmpty()) {
            return "Aucune whitelist de contacts définie";
        }
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < contacts.size(); i++) {
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(contacts.get(i).displayName);
        }
        return "Whitelist contacts : " + summary.toString();
    }
}
