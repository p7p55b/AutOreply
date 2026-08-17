package com.studiofoxxo.autoreply;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContactWhitelistStore {
    private static final String PREFS_NAME = "autoreply_prefs";
    private static final String KEY_CONTACT_WHITELIST = "contact_whitelist";

    public static class ContactEntry {
        public final String contactId;
        public final String lookupKey;
        public final String displayName;

        public ContactEntry(String contactId, String lookupKey, String displayName) {
            this.contactId = contactId;
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
                String contactId = object.optString("contactId", "");
                String lookupKey = object.optString("lookupKey", "");
                String displayName = object.optString("displayName", "");
                if (!displayName.isEmpty()) {
                    contacts.add(new ContactEntry(contactId, lookupKey, displayName));
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
                object.put("contactId", contact.contactId);
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
            ContactEntry existing = contacts.get(i);
            boolean sameLookupKey = !existing.lookupKey.isEmpty() && existing.lookupKey.equals(contact.lookupKey);
            boolean sameDisplayName = existing.lookupKey.isEmpty() && !contact.lookupKey.isEmpty()
                    && existing.displayName.equalsIgnoreCase(contact.displayName);
            boolean sameManual = existing.lookupKey.isEmpty() && contact.lookupKey.isEmpty()
                    && existing.displayName.equalsIgnoreCase(contact.displayName);
            if (sameLookupKey || sameManual || sameDisplayName) {
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

    public static boolean matchesSource(Context context, String source) {
        if (source == null) {
            return false;
        }
        String normalizedSource = normalizeForMatch(source);
        source = source.toLowerCase();
        for (ContactEntry contact : getContacts(context)) {
            if (contact == null || contact.displayName == null) {
                continue;
            }
            String nameCandidate = contact.displayName.trim().toLowerCase();
            if (!nameCandidate.isEmpty() && source.contains(nameCandidate)) {
                return true;
            }
            String normalizedCandidate = normalizeForMatch(nameCandidate);
            if (!normalizedCandidate.isEmpty() && normalizedSource.contains(normalizedCandidate)) {
                return true;
            }
            for (String identifier : getContactIdentifiers(context, contact)) {
                if (identifier == null || identifier.isEmpty()) {
                    continue;
                }
                String identifierLower = identifier.toLowerCase();
                if (source.contains(identifierLower)) {
                    return true;
                }
                String normalizedIdentifier = normalizeForMatch(identifierLower);
                if (!normalizedIdentifier.isEmpty() && normalizedSource.contains(normalizedIdentifier)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizeForMatch(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private static Set<String> getContactIdentifiers(Context context, ContactEntry contact) {
        Set<String> identifiers = new HashSet<>();
        if (contact == null) {
            return identifiers;
        }

        if (contact.displayName != null && !contact.displayName.trim().isEmpty()) {
            identifiers.add(contact.displayName.trim().toLowerCase());
        }

        String contactId = contact.contactId;
        if (contactId == null || contactId.trim().isEmpty()) {
            return identifiers;
        }
        if (!contactId.matches("\\d+")) {
            return identifiers;
        }

        ContentResolver resolver = context.getContentResolver();
        try (Cursor phones = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                new String[]{contactId}, null)) {
            if (phones != null) {
                while (phones.moveToNext()) {
                    String phone = phones.getString(phones.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    if (phone != null) {
                        identifiers.add(phone.replaceAll("\\D+", ""));
                        identifiers.add(phone);
                    }
                }
            }
        }

        try (Cursor emails = resolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Email.ADDRESS},
                ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?",
                new String[]{contactId}, null)) {
            if (emails != null) {
                while (emails.moveToNext()) {
                    String email = emails.getString(emails.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS));
                    if (email != null) {
                        identifiers.add(email.toLowerCase());
                    }
                }
            }
        }

        return identifiers;
    }
}
