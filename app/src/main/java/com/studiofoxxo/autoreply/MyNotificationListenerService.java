package com.studiofoxxo.autoreply;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MyNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "MyNotificationListener";
    private final Set<String> handledNotificationKeys = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return;
        }

        Log.d(TAG, "Notification posted: package=" + sbn.getPackageName());
        Log.d(TAG, "  title=" + stringOf(notification.extras.getCharSequence(Notification.EXTRA_TITLE)));
        Log.d(TAG, "  text=" + stringOf(notification.extras.getCharSequence(Notification.EXTRA_TEXT)));
        Log.d(TAG, "  summary=" + stringOf(notification.extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)));

        String key = sbn.getKey();
        if (key != null && handledNotificationKeys.contains(key)) {
            Log.d(TAG, "Notification already handled: " + key);
            return;
        }

        if (!matchesUserWhitelist(sbn)) {
            Log.d(TAG, "Notification skipped because sender is not whitelisted");
            return;
        }

        List<ResponseConfig> responses = loadResponses();
        boolean sent = false;
        for (ResponseConfig response : responses) {
            if (!matchesApp(response, sbn)) {
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
                    break;
                }
            }
        }

        if (sent && key != null) {
            handledNotificationKeys.add(key);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        Log.d(TAG, "Notification removed: " + sbn.getPackageName());
    }

    private boolean matchesApp(ResponseConfig response, StatusBarNotification sbn) {
        if (response.allApps) {
            if (isVoiceTriggerSelected(response) && response.allowedApps.isEmpty()) {
                return false;
            }
            return true;
        }
        return response.allowedApps.contains(sbn.getPackageName());
    }

    private boolean matchesUserWhitelist(StatusBarNotification sbn) {
        List<ContactWhitelistStore.ContactEntry> whitelist = ContactWhitelistStore.getContacts(this);
        Log.d(TAG, "Whitelist loaded: " + ContactWhitelistStore.getWhitelistSummary(this));
        if (whitelist.isEmpty()) {
            return true;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) {
            return false;
        }

        Bundle extras = notification.extras;
        String title = stringOf(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = stringOf(extras.getCharSequence(Notification.EXTRA_TEXT));
        String summary = stringOf(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        String subText = stringOf(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        String titleBig = stringOf(extras.getCharSequence(Notification.EXTRA_TITLE_BIG));
        String conversationTitle = stringOf(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        StringBuilder sourceBuilder = new StringBuilder(title)
                .append(" ")
                .append(titleBig)
                .append(" ")
                .append(conversationTitle)
                .append(" ")
                .append(text)
                .append(" ")
                .append(summary)
                .append(" ")
                .append(subText);

        CharSequence[] textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (textLines != null) {
            for (CharSequence line : textLines) {
                sourceBuilder.append(" ").append(stringOf(line));
            }
        }

        if (extras.containsKey(Notification.EXTRA_INFO_TEXT)) {
            sourceBuilder.append(" ").append(stringOf(extras.getCharSequence(Notification.EXTRA_INFO_TEXT)));
        }

        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            if (value instanceof CharSequence) {
                sourceBuilder.append(" ").append(stringOf((CharSequence) value));
            } else if (value instanceof CharSequence[]) {
                for (CharSequence item : (CharSequence[]) value) {
                    sourceBuilder.append(" ").append(stringOf(item));
                }
            }
        }

        NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (style != null) {
            if (style.getUser() != null && style.getUser().getName() != null) {
                sourceBuilder.append(" ").append(style.getUser().getName());
            }
            for (NotificationCompat.MessagingStyle.Message message : style.getMessages()) {
                if (message.getPerson() != null && message.getPerson().getName() != null) {
                    sourceBuilder.append(" ").append(message.getPerson().getName());
                } else if (message.getSender() != null) {
                    sourceBuilder.append(" ").append(message.getSender());
                }
                sourceBuilder.append(" ").append(stringOf(message.getText()));
            }
        }

        String source = sourceBuilder.toString().toLowerCase();
        Log.d(TAG, "Notification source text for whitelist matching: " + source);
        boolean match = ContactWhitelistStore.matchesSource(this, source);
        Log.d(TAG, "Whitelist match result: " + match);
        return match;
    }

    private boolean matchesResponseCriteria(ResponseConfig response, StatusBarNotification sbn) {
        boolean keywordMatch = true;
        if (isKeywordTriggerSelected(response)) {
            keywordMatch = matchesKeywordTrigger(response, sbn);
        }
        boolean voiceMatch = true;
        if (isVoiceTriggerSelected(response)) {
            voiceMatch = isSupportedVoiceNotification(sbn);
        }
        boolean socialMatch = true;
        if (isSocialTriggerSelected(response)) {
            socialMatch = matchesSocialLinkTrigger(sbn);
        }
        return keywordMatch && voiceMatch && socialMatch;
    }

    private boolean matchesExcludedContacts(ResponseConfig response, StatusBarNotification sbn) {
        if (response.excludedContacts == null || response.excludedContacts.isEmpty()) {
            return false;
        }
        String source = buildNotificationSource(sbn).toLowerCase(Locale.ROOT);
        for (String excluded : response.excludedContacts) {
            if (excluded == null) {
                continue;
            }
            String normalized = excluded.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (source.contains(lower) || source.contains(normalizeKeywordText(lower))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesKeywordTrigger(ResponseConfig response, StatusBarNotification sbn) {
        if (response.keywords == null || response.keywords.isEmpty()) {
            return false;
        }
        String source = buildNotificationSource(sbn).toLowerCase(Locale.ROOT);
        for (String keyword : response.keywords) {
            if (keyword == null) {
                continue;
            }
            String normalized = keyword.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (source.contains(lower) || source.contains(normalizeKeywordText(lower))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSocialLinkTrigger(StatusBarNotification sbn) {
        String source = buildNotificationSource(sbn).toLowerCase(Locale.ROOT);
        return source.contains("http") || source.contains("www.") || source.contains("instagram")
                || source.contains("tiktok") || source.contains("snapchat") || source.contains("x.com")
                || source.contains("twitter") || source.contains("telegram") || source.contains("discord");
    }

    private boolean isKeywordTriggerSelected(ResponseConfig response) {
        return response.triggers.stream().anyMatch(trigger ->
                trigger.equalsIgnoreCase("mots-clés") || trigger.equalsIgnoreCase("keyword") || trigger.equalsIgnoreCase("keywords"));
    }

    private boolean isSocialTriggerSelected(ResponseConfig response) {
        return response.triggers.stream().anyMatch(trigger ->
                trigger.equalsIgnoreCase("liens/réseaux sociaux") || trigger.equalsIgnoreCase("liens")
                        || trigger.equalsIgnoreCase("réseaux sociaux") || trigger.equalsIgnoreCase("social"));
    }

    private boolean isVoiceTriggerSelected(ResponseConfig response) {
        return response.triggers.stream().anyMatch(trigger ->
                trigger.equalsIgnoreCase("vocaux") || trigger.equalsIgnoreCase("voice") || trigger.equalsIgnoreCase("audio"));
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

        Bundle extras = notification.extras;
        String content = (stringOf(extras.getCharSequence(Notification.EXTRA_TITLE)) + " " +
                stringOf(extras.getCharSequence(Notification.EXTRA_TEXT)) + " " +
                stringOf(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)) + " " +
                stringOf(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))).toLowerCase();

        if (content.contains("message vocal") || content.contains("audio message") ||
                content.contains("message audio") || content.contains("audio") ||
                content.contains("voice message") || content.contains("voice note")) {
            Log.d(TAG, "Detected voice note by content string for " + packageName);
            return true;
        }

        NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (style != null) {
            for (NotificationCompat.MessagingStyle.Message message : style.getMessages()) {
                String mimeType = message.getDataMimeType();
                Log.d(TAG, "Message mimeType=" + mimeType + " text=" + message.getText());
                if (mimeType != null && mimeType.startsWith("audio/")) {
                    Log.d(TAG, "Detected voice note by mimeType for " + packageName);
                    return true;
                }
            }
        }

        return false;
    }

    private String buildNotificationSource(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return "";
        }

        Bundle extras = notification.extras;
        StringBuilder sourceBuilder = new StringBuilder();
        sourceBuilder.append(stringOf(extras.getCharSequence(Notification.EXTRA_TITLE))).append(" ")
                .append(stringOf(extras.getCharSequence(Notification.EXTRA_TEXT))).append(" ")
                .append(stringOf(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))).append(" ")
                .append(stringOf(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))).append(" ")
                .append(stringOf(extras.getCharSequence(Notification.EXTRA_TITLE_BIG))).append(" ")
                .append(stringOf(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)));

        CharSequence[] textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (textLines != null) {
            for (CharSequence line : textLines) {
                sourceBuilder.append(" ").append(stringOf(line));
            }
        }

        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            if (value instanceof CharSequence) {
                sourceBuilder.append(" ").append(stringOf((CharSequence) value));
            } else if (value instanceof CharSequence[]) {
                for (CharSequence item : (CharSequence[]) value) {
                    sourceBuilder.append(" ").append(stringOf(item));
                }
            }
        }

        NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (style != null) {
            if (style.getUser() != null && style.getUser().getName() != null) {
                sourceBuilder.append(" ").append(style.getUser().getName());
            }
            for (NotificationCompat.MessagingStyle.Message message : style.getMessages()) {
                if (message.getPerson() != null && message.getPerson().getName() != null) {
                    sourceBuilder.append(" ").append(message.getPerson().getName());
                } else if (message.getSender() != null) {
                    sourceBuilder.append(" ").append(message.getSender());
                }
                sourceBuilder.append(" ").append(stringOf(message.getText()));
            }
        }

        return sourceBuilder.toString();
    }

    private String normalizeKeywordText(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
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
        Log.d(TAG, "Would send auto reply for " + sbn.getPackageName() + ": " + response.text);

        Notification notification = sbn.getNotification();
        if (notification == null) {
            return false;
        }

        boolean sent = tryReplyViaNotificationAction(notification, response.text);
        if (sent) {
            Log.d(TAG, "Auto-reply sent via notification action");
        } else {
            Log.d(TAG, "No reply action available for this notification");
        }
        return sent;
    }

    private boolean tryReplyViaNotificationAction(Notification notification, String replyText) {
        if (notification.actions == null) {
            return false;
        }

        for (Notification.Action action : notification.actions) {
            if (action == null || action.title == null) {
                continue;
            }
            String title = action.title.toString().toLowerCase();
            if (title.contains("reply") || title.contains("répondre") || title.contains("reply all") || title.contains("réponse")) {
                PendingIntent pendingIntent = action.actionIntent;
                RemoteInput remoteInput = null;
                if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                    remoteInput = action.getRemoteInputs()[0];
                }
                if (pendingIntent != null && remoteInput != null) {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putCharSequence(remoteInput.getResultKey(), replyText);
                    RemoteInput.addResultsToIntent(new RemoteInput[]{remoteInput}, intent, bundle);
                    try {
                        pendingIntent.send(this, 0, intent);
                        return true;
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "Failed to send reply intent", e);
                    }
                }
            }
        }

        return false;
    }
}
