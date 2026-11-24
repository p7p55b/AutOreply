package com.studiofoxxo.autoreply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

        assertEquals("Urgence", config.title);
        assertEquals("Réponse", config.text);
        assertTrue(config.allApps);
        assertEquals(Arrays.asList("bonjour", "réunion"), config.keywords);
        assertEquals(Arrays.asList("Alice", "Bob"), config.excludedContacts);
        assertTrue(config.triggers.contains("mots-clés"));
        assertTrue(config.isKeywordTriggerSelected());
        assertFalse(config.isVoiceTriggerSelected());
        assertFalse(config.isSocialTriggerSelected());
    }

    @Test
    public void fromJson_shouldKeepBackwardCompatibilityWithLegacyData() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("title", "Legacy");
        payload.put("text", "Réponse");
        payload.put("allApps", false);
        payload.put("allowedApps", new JSONArray().put("com.example.app"));

        ResponseConfig config = ResponseConfig.fromJson(payload);

        assertEquals("Legacy", config.title);
        assertEquals("Réponse", config.text);
        assertFalse(config.allApps);
        assertEquals(Collections.singletonList("com.example.app"), config.allowedApps);
        assertEquals(Collections.emptyList(), config.keywords);
        assertEquals(Collections.emptyList(), config.excludedContacts);
        assertEquals(Collections.emptyList(), config.triggers);
        assertFalse(config.isKeywordTriggerSelected());
    }

    @Test
    public void fromJson_nullObject_shouldReturnSafeDefaults() throws Exception {
        ResponseConfig config = ResponseConfig.fromJson(null);
        assertNotNull(config);
        assertNotNull(config.allowedApps);
        assertNotNull(config.triggers);
        assertNotNull(config.keywords);
        assertNotNull(config.excludedContacts);
        assertTrue(config.allApps);
    }

    @Test
    public void toJson_and_fromJson_symmetry() throws Exception {
        ResponseConfig original = new ResponseConfig(
                "Titre Test",
                "Mon message personnalisé",
                Arrays.asList("com.whatsapp", "com.discord"),
                Arrays.asList("mots-clés", "vocaux"),
                false,
                Arrays.asList("urgent", "sos"),
                Arrays.asList("SpamBot")
        );

        JSONObject json = original.toJson();
        ResponseConfig restored = ResponseConfig.fromJson(json);

        assertEquals(original.title, restored.title);
        assertEquals(original.text, restored.text);
        assertEquals(original.allApps, restored.allApps);
        assertEquals(original.allowedApps, restored.allowedApps);
        assertEquals(original.triggers, restored.triggers);
        assertEquals(original.keywords, restored.keywords);
        assertEquals(original.excludedContacts, restored.excludedContacts);
    }

    @Test
    public void listToJson_and_listFromJson_roundTrip() {
        ResponseConfig c1 = new ResponseConfig("R1", "T1", Collections.emptyList(), Collections.emptyList(), true);
        ResponseConfig c2 = new ResponseConfig("R2", "T2", Collections.singletonList("com.whatsapp"), Collections.singletonList("vocaux"), false);

        List<ResponseConfig> list = Arrays.asList(c1, c2);
        String json = ResponseConfig.listToJson(list);
        List<ResponseConfig> parsed = ResponseConfig.listFromJson(json);

        assertEquals(2, parsed.size());
        assertEquals("R1", parsed.get(0).title);
        assertEquals("R2", parsed.get(1).title);
    }

    @Test
    public void listFromJson_invalidString_shouldReturnEmptyList() {
        List<ResponseConfig> res1 = ResponseConfig.listFromJson(null);
        assertNotNull(res1);
        assertTrue(res1.isEmpty());

        List<ResponseConfig> res2 = ResponseConfig.listFromJson("{ not an array }");
        assertNotNull(res2);
        assertTrue(res2.isEmpty());
    }

    @Test
    public void matchesApp_shouldWorkCorrectly() {
        ResponseConfig allAppsConfig = new ResponseConfig("All", "Msg", Collections.emptyList(), Collections.emptyList(), true);
        assertTrue(allAppsConfig.matchesApp("com.whatsapp"));
        assertTrue(allAppsConfig.matchesApp("com.discord"));
        assertFalse(allAppsConfig.matchesApp(null));
        assertFalse(allAppsConfig.matchesApp(""));

        ResponseConfig specificAppConfig = new ResponseConfig("Specific", "Msg", Collections.singletonList("com.whatsapp"), Collections.emptyList(), false);
        assertTrue(specificAppConfig.matchesApp("com.whatsapp"));
        assertFalse(specificAppConfig.matchesApp("com.discord"));
    }

    @Test
    public void triggerVariants_shouldBeDetected() {
        ResponseConfig config1 = new ResponseConfig("T", "M", Collections.emptyList(), Collections.singletonList("vocaux"), true);
        assertTrue(config1.isVoiceTriggerSelected());

        ResponseConfig config2 = new ResponseConfig("T", "M", Collections.emptyList(), Collections.singletonList("voice"), true);
        assertTrue(config2.isVoiceTriggerSelected());

        ResponseConfig config3 = new ResponseConfig("T", "M", Collections.emptyList(), Collections.singletonList("liens/réseaux sociaux"), true);
        assertTrue(config3.isSocialTriggerSelected());

        ResponseConfig config4 = new ResponseConfig("T", "M", Collections.emptyList(), Collections.singletonList("mots-clés"), true);
        assertTrue(config4.isKeywordTriggerSelected());
    }

    @Test
    public void normalization_shouldRemoveAccents() {
        String normalized = ContactWhitelistStore.normalizeForMatch("Élise à l'hôpital de Jérôme");
        assertTrue(normalized.contains("elise"));
        assertTrue(normalized.contains("hopital"));
        assertTrue(normalized.contains("jerome"));

        String normalizedKeyword = MyNotificationListenerService.normalizeKeywordText("Réunion urgente !");
        assertEquals("reunion urgente", normalizedKeyword);
    }

    @Test
    public void supportedApps_shouldIncludeWhatsAppAndSignal() {
        assertTrue(SupportedAppStore.isSupportedPackage("com.whatsapp"));
        assertTrue(SupportedAppStore.isSupportedPackage("com.whatsapp.w4b"));
        assertTrue(SupportedAppStore.isSupportedPackage("org.thoughtcrime.securesms"));
        assertTrue(SupportedAppStore.isSupportedPackage("com.discord"));
        assertTrue(SupportedAppStore.isSupportedPackage("org.telegram.messenger"));

        assertEquals("WhatsApp", SupportedAppStore.getDefaultLabel("com.whatsapp"));
        assertEquals("Signal", SupportedAppStore.getDefaultLabel("org.thoughtcrime.securesms"));
    }
}
