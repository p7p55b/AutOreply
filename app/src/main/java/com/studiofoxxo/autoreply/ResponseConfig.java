package com.studiofoxxo.autoreply;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResponseConfig {
    public String title;
    public String text;
    public List<String> allowedApps;
    public List<String> triggers;
    public List<String> keywords;
    public List<String> excludedContacts;
    public boolean allApps;

    public ResponseConfig(String title, String text, List<String> allowedApps, List<String> triggers, boolean allApps) {
        this(title, text, allowedApps, triggers, allApps, new ArrayList<>(), new ArrayList<>());
    }

    public ResponseConfig(String title, String text, List<String> allowedApps, List<String> triggers,
                         boolean allApps, List<String> keywords, List<String> excludedContacts) {
        this.title = title != null ? title : "";
        this.text = text != null ? text : "";
        this.allowedApps = allowedApps != null ? new ArrayList<>(allowedApps) : new ArrayList<>();
        this.triggers = triggers != null ? new ArrayList<>(triggers) : new ArrayList<>();
        this.keywords = keywords != null ? new ArrayList<>(keywords) : new ArrayList<>();
        this.excludedContacts = excludedContacts != null ? new ArrayList<>(excludedContacts) : new ArrayList<>();
        this.allApps = allApps;
    }

    public boolean matchesApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        if (allApps) {
            return true;
        }
        return allowedApps != null && allowedApps.contains(packageName);
    }

    public boolean isKeywordTriggerSelected() {
        if (triggers == null) {
            return false;
        }
        for (String trigger : triggers) {
            if (trigger != null && (trigger.equalsIgnoreCase("mots-clés")
                    || trigger.equalsIgnoreCase("mots-cles")
                    || trigger.equalsIgnoreCase("keyword")
                    || trigger.equalsIgnoreCase("keywords"))) {
                return true;
            }
        }
        return false;
    }

    public boolean isVoiceTriggerSelected() {
        if (triggers == null) {
            return false;
        }
        for (String trigger : triggers) {
            if (trigger != null && (trigger.equalsIgnoreCase("vocaux")
                    || trigger.equalsIgnoreCase("vocal")
                    || trigger.equalsIgnoreCase("voice")
                    || trigger.equalsIgnoreCase("audio"))) {
                return true;
            }
        }
        return false;
    }

    public boolean isSocialTriggerSelected() {
        if (triggers == null) {
            return false;
        }
        for (String trigger : triggers) {
            if (trigger != null && (trigger.equalsIgnoreCase("liens/réseaux sociaux")
                    || trigger.equalsIgnoreCase("liens/reseaux sociaux")
                    || trigger.equalsIgnoreCase("liens")
                    || trigger.equalsIgnoreCase("réseaux sociaux")
                    || trigger.equalsIgnoreCase("reseaux sociaux")
                    || trigger.equalsIgnoreCase("social"))) {
                return true;
            }
        }
        return false;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("title", title != null ? title : "");
        object.put("text", text != null ? text : "");
        object.put("allApps", allApps);
        object.put("allowedApps", new JSONArray(allowedApps != null ? allowedApps : Collections.emptyList()));
        object.put("triggers", new JSONArray(triggers != null ? triggers : Collections.emptyList()));
        object.put("keywords", new JSONArray(keywords != null ? keywords : Collections.emptyList()));
        object.put("excludedContacts", new JSONArray(excludedContacts != null ? excludedContacts : Collections.emptyList()));
        return object;
    }

    public static ResponseConfig fromJson(JSONObject object) throws JSONException {
        if (object == null) {
            return new ResponseConfig("Réponse automatique", "", new ArrayList<>(), new ArrayList<>(), true);
        }
        String title = object.optString("title", "Réponse automatique");
        String text = object.optString("text", "");
        boolean allApps = object.optBoolean("allApps", true);

        List<String> allowedApps = new ArrayList<>();
        JSONArray appsArray = object.optJSONArray("allowedApps");
        if (appsArray != null) {
            for (int i = 0; i < appsArray.length(); i++) {
                String val = appsArray.optString(i, "").trim();
                if (!val.isEmpty()) {
                    allowedApps.add(val);
                }
            }
        }

        List<String> triggers = new ArrayList<>();
        JSONArray triggerArray = object.optJSONArray("triggers");
        if (triggerArray != null) {
            for (int i = 0; i < triggerArray.length(); i++) {
                String val = triggerArray.optString(i, "").trim();
                if (!val.isEmpty()) {
                    triggers.add(val);
                }
            }
        }

        List<String> keywords = new ArrayList<>();
        JSONArray keywordsArray = object.optJSONArray("keywords");
        if (keywordsArray != null) {
            for (int i = 0; i < keywordsArray.length(); i++) {
                String val = keywordsArray.optString(i, "").trim();
                if (!val.isEmpty()) {
                    keywords.add(val);
                }
            }
        }

        List<String> excludedContacts = new ArrayList<>();
        JSONArray excludedArray = object.optJSONArray("excludedContacts");
        if (excludedArray != null) {
            for (int i = 0; i < excludedArray.length(); i++) {
                String val = excludedArray.optString(i, "").trim();
                if (!val.isEmpty()) {
                    excludedContacts.add(val);
                }
            }
        }

        return new ResponseConfig(title, text, allowedApps, triggers, allApps, keywords, excludedContacts);
    }

    public static List<ResponseConfig> listFromJson(String json) {
        List<ResponseConfig> responses = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return responses;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    responses.add(fromJson(object));
                }
            }
        } catch (JSONException ignored) {
        }
        return responses;
    }

    public static String listToJson(List<ResponseConfig> responses) {
        if (responses == null) {
            return "[]";
        }
        JSONArray array = new JSONArray();
        try {
            for (ResponseConfig config : responses) {
                if (config != null) {
                    array.put(config.toJson());
                }
            }
        } catch (JSONException ignored) {
        }
        return array.toString();
    }
}
