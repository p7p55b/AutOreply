package com.studiofoxxo.autoreply;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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
        this.title = title;
        this.text = text;
        this.allowedApps = allowedApps != null ? allowedApps : new ArrayList<>();
        this.triggers = triggers != null ? triggers : new ArrayList<>();
        this.keywords = keywords != null ? keywords : new ArrayList<>();
        this.excludedContacts = excludedContacts != null ? excludedContacts : new ArrayList<>();
        this.allApps = allApps;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("title", title);
        object.put("text", text);
        object.put("allApps", allApps);
        object.put("allowedApps", new JSONArray(allowedApps));
        object.put("triggers", new JSONArray(triggers));
        object.put("keywords", new JSONArray(keywords));
        object.put("excludedContacts", new JSONArray(excludedContacts));
        return object;
    }

    public static ResponseConfig fromJson(JSONObject object) throws JSONException {
        String title = object.optString("title", "Réponse automatique");
        String text = object.optString("text", "");
        boolean allApps = object.optBoolean("allApps", true);
        List<String> allowedApps = new ArrayList<>();
        JSONArray appsArray = object.optJSONArray("allowedApps");
        if (appsArray != null) {
            for (int i = 0; i < appsArray.length(); i++) {
                allowedApps.add(appsArray.optString(i));
            }
        }
        List<String> triggers = new ArrayList<>();
        JSONArray triggerArray = object.optJSONArray("triggers");
        if (triggerArray != null) {
            for (int i = 0; i < triggerArray.length(); i++) {
                triggers.add(triggerArray.optString(i));
            }
        }
        List<String> keywords = new ArrayList<>();
        JSONArray keywordsArray = object.optJSONArray("keywords");
        if (keywordsArray != null) {
            for (int i = 0; i < keywordsArray.length(); i++) {
                keywords.add(keywordsArray.optString(i));
            }
        }
        List<String> excludedContacts = new ArrayList<>();
        JSONArray excludedArray = object.optJSONArray("excludedContacts");
        if (excludedArray != null) {
            for (int i = 0; i < excludedArray.length(); i++) {
                excludedContacts.add(excludedArray.optString(i));
            }
        }
        return new ResponseConfig(title, text, allowedApps, triggers, allApps, keywords, excludedContacts);
    }

    public static List<ResponseConfig> listFromJson(String json) {
        List<ResponseConfig> responses = new ArrayList<>();
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
        JSONArray array = new JSONArray();
        try {
            for (ResponseConfig config : responses) {
                array.put(config.toJson());
            }
        } catch (JSONException ignored) {
        }
        return array.toString();
    }
}
