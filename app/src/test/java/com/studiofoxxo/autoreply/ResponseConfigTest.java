package com.studiofoxxo.autoreply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ResponseConfigTest {

    @Test
    public void fromJson_shouldReadKeywordAndExcludedContacts() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("title", "Urgence");
        payload.put("text", "Réponse");
        payload.put("allApps", true);
        payload.put("triggers", new JSONArray().put("mots-clés"));
        payload.put("keywords", new JSONArray().put("bonjour").put("réunion"));
        payload.put("excludedContacts", new JSONArray().put("Alice").put("Bob"));

        ResponseConfig config = ResponseConfig.fromJson(payload);

        assertEquals(Arrays.asList("bonjour", "réunion"), config.keywords);
        assertEquals(Arrays.asList("Alice", "Bob"), config.excludedContacts);
        assertTrue(config.triggers.contains("mots-clés"));
    }

    @Test
    public void fromJson_shouldKeepBackwardCompatibilityWithLegacyData() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("title", "Legacy");
        payload.put("text", "Réponse");
        payload.put("allApps", false);
        payload.put("allowedApps", new JSONArray().put("com.example.app"));

        ResponseConfig config = ResponseConfig.fromJson(payload);

        assertEquals(Collections.emptyList(), config.keywords);
        assertEquals(Collections.emptyList(), config.excludedContacts);
        assertEquals(Collections.emptyList(), config.triggers);
    }
}
