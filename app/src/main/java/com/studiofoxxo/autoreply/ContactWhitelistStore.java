package com.studiofoxxo.autoreply;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ContactWhitelistStore {
    private static final String TAG = "ContactWhitelistStore";
    private static final String PREFS_NAME = "autoreply_prefs";
    private static final String KEY_CONTACT_WHITELIST = "contact_whitelist";

    // Cache for queried contact identifiers (phones & emails) to avoid hitting ContentResolver on every notification
    private static final Map<String, Set<String>> IDENTIFIER_CACHE = new ConcurrentHashMap<>();

    public static class ContactEntry {
        public final String contactId;
        public final String lookupKey;
        public final String displayName;

        public ContactEntry(String contactId, String lookupKey, String displayName) {
            this.contactId = contactId != null ? contactId : "";
            this.lookupKey = lookupKey != null ? lookupKey : "";
            this.displayName = displayName != null ? displayName : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContactEntry that = (ContactEntry) o;
            return Objects.equals(contactId, that.contactId) &&
                    Objects.equals(lookupKey, that.lookupKey) &&
                    Objects.equals(displayName, that.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(contactId, lookupKey, displayName);
        }
    }

    public static List<ContactEntry> getContacts(Context context) {
        if (context == null) {
            return Collections.emptyList();
        }
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
        if (context == null) {
            return;
        }
        IDENTIFIER_CACHE.clear(); // invalidate cache when contacts are modified
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray array = new JSONArray();
        if (contacts != null) {
            for (ContactEntry contact : contacts) {
                if (contact == null) continue;
                JSONObject object = new JSONObject();
                try {
                    object.put("contactId", contact.contactId);
                    object.put("lookupKey", contact.lookupKey);
                    object.put("displayName", contact.displayName);
                    array.put(object);
                } catch (JSONException ignored) {
                }
            }
        }
        prefs.edit().putString(KEY_CONTACT_WHITELIST, array.toString()).apply();
    }

    public static void addOrUpdateContact(Context context, ContactEntry contact) {
        if (context == null || contact == null || contact.displayName.trim().isEmpty()) {
            return;
        }
        List<ContactEntry> contacts = getContacts(context);
        boolean found = false;
        for (int i = 0; i < contacts.size(); i++) {
            ContactEntry existing = contacts.get(i);
            boolean sameLookupKey = !existing.lookupKey.isEmpty() && existing.lookupKey.equals(contact.lookupKey);
            boolean sameDisplayName = existing.displayName.equalsIgnoreCase(contact.displayName);
            if (sameLookupKey || sameDisplayName) {
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
        if (context == null) return;
        IDENTIFIER_CACHE.clear();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_CONTACT_WHITELIST).apply();
    }

    public static String getWhitelistSummary(Context context) {
        List<ContactEntry> contacts = getContacts(context);
        if (contacts.isEmpty()) {
            return "Aucune whitelist de contacts définie (tous autorisés)";
        }
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < contacts.size(); i++) {
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(contacts.get(i).displayName);
        }
        return "Whitelist contacts : " + summary;
    }

    public static boolean matchesSource(Context context, String senderSource) {
        if (senderSource == null || senderSource.trim().isEmpty()) {
            return false;
        }
        List<ContactEntry> contacts = getContacts(context);
        if (contacts.isEmpty()) {
            return true; // No whitelist defined -> all allowed
        }

        String normalizedSource = normalizeForMatch(senderSource);
        String senderLower = senderSource.toLowerCase(Locale.ROOT);

        for (ContactEntry contact : contacts) {
            if (contact == null || contact.displayName == null || contact.displayName.trim().isEmpty()) {
                continue;
            }

            String candidateName = contact.displayName.trim();
            String candidateLower = candidateName.toLowerCase(Locale.ROOT);
            String normalizedCandidate = normalizeForMatch(candidateName);

            // 1. Direct contains check with safety on length
            if (!candidateLower.isEmpty() && senderLower.contains(candidateLower)) {
                return true;
            }

            // 2. Word boundary match on normalized string
            if (!normalizedCandidate.isEmpty()) {
                if (matchesAsWord(normalizedSource, normalizedCandidate)) {
                    return true;
                }
            }

            // 3. Match against phone numbers / email identifiers
            for (String identifier : getContactIdentifiers(context, contact)) {
                if (identifier == null || identifier.isEmpty()) {
                    continue;
                }
                String idLower = identifier.toLowerCase(Locale.ROOT);
                if (senderLower.contains(idLower)) {
                    return true;
                }
                // Check digits only for phone numbers (min 5 digits to prevent false positives)
                String digitsOnly = identifier.replaceAll("\\D+", "");
                if (digitsOnly.length() >= 5 && senderSource.replaceAll("\\D+", "").contains(digitsOnly)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesAsWord(String text, String word) {
        if (text == null || word == null || word.isEmpty()) {
            return false;
        }
        // If word is 4+ characters, substring is safe
        if (word.length() >= 4 && text.contains(word)) {
            return true;
        }
        // For short words (e.g. "Al", "Ed", "Dan"), use word boundary matching
        String pattern = "(?i)(^|\\s+|[^a-z0-9])" + Pattern.quote(word) + "($|\\s+|[^a-z0-9])";
        return Pattern.compile(pattern).matcher(text).find();
    }

    public static String normalizeForMatch(String text) {
        if (text == null) {
            return "";
        }
        // Strip accents (é -> e, ç -> c)
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String noAccents = decomposed.replaceAll("\\p{M}", "");
        // Lowercase and replace non-alphanumeric with spaces to preserve word boundaries
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static Set<String> getContactIdentifiers(Context context, ContactEntry contact) {
        Set<String> identifiers = new HashSet<>();
        if (context == null || contact == null) {
            return identifiers;
        }

        if (contact.displayName != null && !contact.displayName.trim().isEmpty()) {
            identifiers.add(contact.displayName.trim().toLowerCase(Locale.ROOT));
        }

        String contactId = contact.contactId;
        if (contactId == null || contactId.trim().isEmpty() || !contactId.matches("\\d+")) {
            return identifiers;
        }

        // Check cache first
        Set<String> cached = IDENTIFIER_CACHE.get(contactId);
        if (cached != null) {
            identifiers.addAll(cached);
            return identifiers;
        }

        Set<String> freshIdentifiers = new HashSet<>();
        try {
            ContentResolver resolver = context.getContentResolver();
            try (Cursor phones = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                    new String[]{contactId}, null)) {
                if (phones != null) {
                    while (phones.moveToNext()) {
                        int col = phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        if (col >= 0) {
                            String phone = phones.getString(col);
                            if (phone != null && !phone.trim().isEmpty()) {
                                freshIdentifiers.add(phone.trim());
                                freshIdentifiers.add(phone.replaceAll("\\D+", ""));
                            }
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
                        int col = emails.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS);
                        if (col >= 0) {
                            String email = emails.getString(col);
                            if (email != null && !email.trim().isEmpty()) {
                                freshIdentifiers.add(email.trim().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Missing READ_CONTACTS permission when fetching identifiers: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error querying contact identifiers for id=" + contactId, e);
        }

        IDENTIFIER_CACHE.put(contactId, freshIdentifiers);
        identifiers.addAll(freshIdentifiers);
        return identifiers;
    }
}
