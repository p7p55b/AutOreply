package com.studiofoxxo.autoreply;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MyNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "MyNotificationListener";

    // Bounded LRU cache (max 200 entries) to prevent memory leak and double-processing
    private final Set<String> handledNotificationKeys = Collections.newSetFromMap(
            new LinkedHashMap<String, Boolean>(200, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 200;
                }
            }
    );

    // Cooldown tracker per sender/package to prevent rapid duplicate auto-replies (min 3s cooldown)
    private final Map<String, Long> lastReplyTimestamps = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > 100;
                }
            }
    );

    private static final long COOLDOWN_MILLIS = 3000L;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (sbn == null) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) {
            return;
        }

        String packageName = sbn.getPackageName();
        if (packageName == null || packageName.equals(getPackageName())) {
            // Ignore own notifications
            return;
        }

        // Check global auto_reply_enabled switch
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!defaultPrefs.getBoolean("auto_reply_enabled", true)) {
            Log.d(TAG, "Auto-reply is disabled in preferences");
            return;
        }

        // Check globally excluded apps
        Set<String> excludedApps = defaultPrefs.getStringSet("excluded_apps", Collections.emptySet());
        if (excludedApps != null && excludedApps.contains(packageName)) {
            Log.d(TAG, "Package is globally excluded: " + packageName);
            return;
        }

        // Ignore ongoing events (music playback, calls, download progress)
        if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            return;
        }

        // Ignore group summaries to avoid duplicate replies when bundled
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return;
        }

        String dedupeKey = sbn.getKey() + "_" + sbn.getPostTime();
        synchronized (handledNotificationKeys) {
            if (handledNotificationKeys.contains(dedupeKey)) {
                Log.d(TAG, "Notification already handled: " + dedupeKey);
                return;
            }
        }

        // Check sender whitelist
        if (!matchesUserWhitelist(sbn)) {
            Log.d(TAG, "Notification skipped because sender is not whitelisted");
            return;
        }

        List<ResponseConfig> responses = loadResponses();
        if (responses.isEmpty()) {
            return;
        }

        String senderKey = packageName + ":" + extractSenderName(sbn);
        long now = System.currentTimeMillis();
        Long lastReply = lastReplyTimestamps.get(senderKey);
        if (lastReply != null && (now - lastReply) < COOLDOWN_MILLIS) {
            Log.d(TAG, "Skipping reply due to cooldown for: " + senderKey);
            return;
        }

        boolean sent = false;
        for (ResponseConfig response : responses) {
            if (response == null) {
                continue;
            }
            if (!response.matchesApp(packageName)) {
                continue;
            }
            if (matchesExcludedContacts(response, sbn)) {
                Log.d(TAG, "Skipping response '" + response.title + "' because sender is in excluded contacts");
                continue;
            }
            if (matchesResponseCriteria(response, sbn)) {
                Log.d(TAG, "Response criteria matched for response: " + response.title);
                if (performAutoReply(response, sbn)) {
                    sent = true;
                    lastReplyTimestamps.put(senderKey, now);
                    break;
                }
            }
        }

        if (sent) {
            synchronized (handledNotificationKeys) {
                handledNotificationKeys.add(dedupeKey);
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
    }

    private boolean matchesUserWhitelist(StatusBarNotification sbn) {
        List<ContactWhitelistStore.ContactEntry> whitelist = ContactWhitelistStore.getContacts(this);
        if (whitelist.isEmpty()) {
            return true; // No whitelist defined -> reply to all
        }

        String sender = buildSenderSource(sbn);
        Log.d(TAG, "Matching sender against whitelist: " + sender);
        return ContactWhitelistStore.matchesSource(this, sender);
    }

    private boolean matchesExcludedContacts(ResponseConfig response, StatusBarNotification sbn) {
        if (response.excludedContacts == null || response.excludedContacts.isEmpty()) {
            return false;
        }
        String sender = buildSenderSource(sbn).toLowerCase(Locale.ROOT);
        String senderNormalized = ContactWhitelistStore.normalizeForMatch(sender);

        for (String excluded : response.excludedContacts) {
            if (excluded == null) {
                continue;
            }
            String normalizedExcluded = excluded.trim().toLowerCase(Locale.ROOT);
            if (normalizedExcluded.isEmpty()) {
                continue;
            }
            if (sender.contains(normalizedExcluded)) {
                return true;
            }
            String noAccentsExcluded = ContactWhitelistStore.normalizeForMatch(normalizedExcluded);
            if (!noAccentsExcluded.isEmpty() && senderNormalized.contains(noAccentsExcluded)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesResponseCriteria(ResponseConfig response, StatusBarNotification sbn) {
        // If no criteria specified, it acts as a default/catch-all response
        if (response.triggers == null || response.triggers.isEmpty()) {
            return true;
        }

        boolean hasKeywordTrigger = response.isKeywordTriggerSelected();
        boolean hasVoiceTrigger = response.isVoiceTriggerSelected();
        boolean hasSocialTrigger = response.isSocialTriggerSelected();

        // If unknown triggers, treat as matched
        if (!hasKeywordTrigger && !hasVoiceTrigger && !hasSocialTrigger) {
            return true;
        }

        // Disjunctive (OR) evaluation for active criteria
        if (hasKeywordTrigger && matchesKeywordTrigger(response, sbn)) {
            return true;
        }
        if (hasVoiceTrigger && isSupportedVoiceNotification(sbn)) {
            return true;
        }
        if (hasSocialTrigger && matchesSocialLinkTrigger(sbn)) {
            return true;
        }

        return false;
    }

    private boolean matchesKeywordTrigger(ResponseConfig response, StatusBarNotification sbn) {
        if (response.keywords == null || response.keywords.isEmpty()) {
            return false;
        }
        String content = buildMessageContent(sbn).toLowerCase(Locale.ROOT);
        String normalizedContent = normalizeKeywordText(content);

        for (String keyword : response.keywords) {
            if (keyword == null) {
                continue;
            }
            String trimmed = keyword.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            if (content.contains(trimmed)) {
                return true;
            }
            String normalizedKeyword = normalizeKeywordText(trimmed);
            if (!normalizedKeyword.isEmpty() && normalizedContent.contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSocialLinkTrigger(StatusBarNotification sbn) {
        String content = buildMessageContent(sbn).toLowerCase(Locale.ROOT);
        return content.contains("http://") || content.contains("https://") || content.contains("www.")
                || content.contains("instagram.com") || content.contains("tiktok.com")
                || content.contains("snapchat.com") || content.contains("x.com")
                || content.contains("twitter.com") || content.contains("t.me/")
                || content.contains("discord.gg") || content.contains("discord.com")
                || content.contains("youtu.be") || content.contains("youtube.com");
    }

    private boolean isSupportedVoiceNotification(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return false;
        }

        String packageName = sbn.getPackageName();
        if (!SupportedAppStore.isSupportedPackage(packageName)) {
            return false;
        }

        String content = buildMessageContent(sbn).toLowerCase(Locale.ROOT);
        if (content.contains("message vocal") || content.contains("audio message") ||
                content.contains("message audio") || content.contains("voice message") ||
                content.contains("voice note") || content.contains("note vocale") ||
                content.contains("vocal (") || content.contains("audio (")) {
            Log.d(TAG, "Detected voice note by text content for " + packageName);
            return true;
        }

        NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (style != null) {
            for (NotificationCompat.MessagingStyle.Message message : style.getMessages()) {
                String mimeType = message.getDataMimeType();
                if (mimeType != null && mimeType.startsWith("audio/")) {
                    Log.d(TAG, "Detected voice note by mimeType for " + packageName);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Extracts only sender information (Title, conversation, remote sender)
     * excluding message body to prevent false positives in whitelist matching.
     */
    private String buildSenderSource(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return "";
        }

        Bundle extras = notification.extras;
        StringBuilder sb = new StringBuilder();

        sb.append(stringOf(extras.getCharSequence(Notification.EXTRA_TITLE))).append(" ")
          .append(stringOf(extras.getCharSequence(Notification.EXTRA_TITLE_BIG))).append(" ")
          .append(stringOf(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))).append(" ")
          .append(stringOf(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)));

        NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (style != null) {
            for (NotificationCompat.MessagingStyle.Message message : style.getMessages()) {
                if (message.getPerson() != null && message.getPerson().getName() != null) {
                    sb.append(" ").append(message.getPerson().getName());
                    if (message.getPerson().getUri() != null) {
                        sb.append(" ").append(message.getPerson().getUri());
                    }
                } else if (message.getSender() != null) {
                    sb.append(" ").append(message.getSender());
                }
            }
        }

        return sb.toString().trim();
    }

    private String extractSenderName(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) return "";
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        return title != null ? title.toString().trim() : "";
    }

    /**
     * Extracts message content (body text, lines, messaging messages)
     * used for keyword matching and voice detection.
     */
    private String buildMessageContent(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return "";
        }

        Bundle extras = notification.extras;
        StringBuilder sb = new StringBuilder();

        sb.append(stringOf(extras.getCharSequence(Notification.EXTRA_TEXT))).append(" ")
          .append(stringOf(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))).append(" ")
          .append(stringOf(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))).append(" ")
          .append(stringOf(extras.getCharSequence(Notification.EXTRA_INFO_TEXT)));

        CharSequence[] textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (textLines != null) {
            for (CharSequence line : textLines) {
                sb.append(" ").append(stringOf(line));
            }
        }

        NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (style != null) {
            for (NotificationCompat.MessagingStyle.Message message : style.getMessages()) {
                sb.append(" ").append(stringOf(message.getText()));
            }
        }

        return sb.toString().trim();
    }

    public static String normalizeKeywordText(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String noAccents = decomposed.replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private String stringOf(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    private List<ResponseConfig> loadResponses() {
        String raw = getSharedPreferences("autoreply_prefs", Context.MODE_PRIVATE)
                .getString("responses_list", "[]");
        return ResponseConfig.listFromJson(raw);
    }

    private boolean performAutoReply(ResponseConfig response, StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null || TextUtils.isEmpty(response.text)) {
            return false;
        }

        Log.d(TAG, "Sending auto-reply to " + sbn.getPackageName() + ": " + response.text);
        return tryReplyViaNotificationAction(notification, response.text);
    }

    private boolean tryReplyViaNotificationAction(Notification notification, String replyText) {
        // 1. Try platform Notification.actions
        if (notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                if (action == null) continue;
                if (isReplyAction(action.title, action.getRemoteInputs())) {
                    if (sendReply(action.actionIntent, action.getRemoteInputs(), replyText)) {
                        return true;
                    }
                }
            }
        }

        // 2. Try NotificationCompat direct actions
        int actionCount = NotificationCompat.getActionCount(notification);
        for (int i = 0; i < actionCount; i++) {
            NotificationCompat.Action compatAction = NotificationCompat.getAction(notification, i);
            if (compatAction == null) continue;
            if (compatAction.getSemanticAction() == NotificationCompat.Action.SEMANTIC_ACTION_REPLY
                    || isReplyAction(compatAction.getTitle(), compatAction.getRemoteInputs())) {
                androidx.core.app.RemoteInput[] compatInputs = compatAction.getRemoteInputs();
                if (compatAction.getActionIntent() != null && compatInputs != null && compatInputs.length > 0) {
                    Intent intent = new Intent();
                    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                    Bundle bundle = new Bundle();
                    bundle.putCharSequence(compatInputs[0].getResultKey(), replyText);
                    androidx.core.app.RemoteInput.addResultsToIntent(compatInputs, intent, bundle);
                    try {
                        compatAction.getActionIntent().send(this, 0, intent);
                        return true;
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "PendingIntent canceled in compatAction", e);
                    }
                }
            }
        }

        // 3. Try WearableExtender actions (WhatsApp and other apps frequently put reply action here)
        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender(notification);
        for (NotificationCompat.Action wearAction : wearableExtender.getActions()) {
            if (wearAction == null || wearAction.getActionIntent() == null) continue;
            if (wearAction.getSemanticAction() == NotificationCompat.Action.SEMANTIC_ACTION_REPLY
                    || isReplyAction(wearAction.getTitle(), wearAction.getRemoteInputs())) {
                androidx.core.app.RemoteInput[] inputs = wearAction.getRemoteInputs();
                if (inputs != null && inputs.length > 0) {
                    Intent intent = new Intent();
                    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                    Bundle bundle = new Bundle();
                    bundle.putCharSequence(inputs[0].getResultKey(), replyText);
                    androidx.core.app.RemoteInput.addResultsToIntent(inputs, intent, bundle);
                    try {
                        wearAction.getActionIntent().send(this, 0, intent);
                        return true;
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "PendingIntent canceled in wearable action", e);
                    }
                }
            }
        }

        return false;
    }

    private boolean isReplyAction(CharSequence title, RemoteInput[] remoteInputs) {
        if (remoteInputs == null || remoteInputs.length == 0) {
            return false;
        }
        for (RemoteInput input : remoteInputs) {
            if (input != null && input.getAllowFreeFormInput()) {
                return true;
            }
        }
        if (title != null) {
            String lower = title.toString().toLowerCase(Locale.ROOT);
            return lower.contains("reply") || lower.contains("répondre") || lower.contains("repondre")
                    || lower.contains("antworten") || lower.contains("responder")
                    || lower.contains("rispondi") || lower.contains("réponse")
                    || lower.contains("reponse") || lower.contains("message");
        }
        return false;
    }

    private boolean isReplyAction(CharSequence title, androidx.core.app.RemoteInput[] remoteInputs) {
        if (remoteInputs == null || remoteInputs.length == 0) {
            return false;
        }
        for (androidx.core.app.RemoteInput input : remoteInputs) {
            if (input != null && input.getAllowFreeFormInput()) {
                return true;
            }
        }
        if (title != null) {
            String lower = title.toString().toLowerCase(Locale.ROOT);
            return lower.contains("reply") || lower.contains("répondre") || lower.contains("repondre")
                    || lower.contains("antworten") || lower.contains("responder")
                    || lower.contains("rispondi") || lower.contains("réponse")
                    || lower.contains("reponse") || lower.contains("message");
        }
        return false;
    }

    private boolean sendReply(PendingIntent pendingIntent, RemoteInput[] remoteInputs, String replyText) {
        if (pendingIntent == null || remoteInputs == null || remoteInputs.length == 0) {
            return false;
        }
        RemoteInput targetInput = null;
        for (RemoteInput input : remoteInputs) {
            if (input != null && input.getAllowFreeFormInput()) {
                targetInput = input;
                break;
            }
        }
        if (targetInput == null) {
            targetInput = remoteInputs[0];
        }

        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        Bundle bundle = new Bundle();
        bundle.putCharSequence(targetInput.getResultKey(), replyText);
        RemoteInput.addResultsToIntent(new RemoteInput[]{targetInput}, intent, bundle);
        try {
            pendingIntent.send(this, 0, intent);
            return true;
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Failed to send reply intent", e);
            return false;
        }
    }
}
