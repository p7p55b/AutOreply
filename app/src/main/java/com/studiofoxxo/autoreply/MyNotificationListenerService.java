package com.studiofoxxo.autoreply;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;

public class MyNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "MyNotificationListener";
    private static final String GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return;
        }

        List<ResponseConfig> responses = loadResponses();
        for (ResponseConfig response : responses) {
            if (!matchesApp(response, sbn)) {
                continue;
            }
            if (matchesVoiceCriteria(response, sbn)) {
                Log.d(TAG, "Voice criteria matched for response: " + response.title);
                performAutoReply(response, sbn);
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        Log.d(TAG, "Notification removed: " + sbn.getPackageName());
    }

    private boolean matchesApp(ResponseConfig response, StatusBarNotification sbn) {
        if (response.allApps) {
            return true;
        }
        return response.allowedApps.contains(sbn.getPackageName());
    }

    private boolean matchesVoiceCriteria(ResponseConfig response, StatusBarNotification sbn) {
        boolean wantsVoice = response.triggers.stream().anyMatch(trigger ->
                trigger.equalsIgnoreCase("vocaux") || trigger.equalsIgnoreCase("voice") || trigger.equalsIgnoreCase("audio"));
        if (!wantsVoice) {
            return false;
        }
        return isGoogleMessagesVoiceNote(sbn);
    }

    private boolean isGoogleMessagesVoiceNote(StatusBarNotification sbn) {
        if (!GOOGLE_MESSAGES_PACKAGE.equals(sbn.getPackageName())) {
            return false;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) {
            return false;
        }

        Bundle extras = notification.extras;
        String content = (stringOf(extras.getCharSequence(Notification.EXTRA_TITLE)) + " " +
                stringOf(extras.getCharSequence(Notification.EXTRA_TEXT)) + " " +
                stringOf(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)) + " " +
                stringOf(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))).toLowerCase();

        if (content.contains("message vocal") || content.contains("audio message") ||
                content.contains("message audio") || content.contains("audio")) {
            return true;
        }

        NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (style != null) {
            for (NotificationCompat.MessagingStyle.Message message : style.getMessages()) {
                String mimeType = message.getDataMimeType();
                if (mimeType != null && mimeType.startsWith("audio/")) {
                    return true;
                }
            }
        }

        return false;
    }

    private String stringOf(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    private List<ResponseConfig> loadResponses() {
        String raw = getSharedPreferences("autoreply_prefs", Context.MODE_PRIVATE)
                .getString("responses_list", "[]");
        return ResponseConfig.listFromJson(raw);
    }

    private void performAutoReply(ResponseConfig response, StatusBarNotification sbn) {
        Log.d(TAG, "Would send auto reply for " + sbn.getPackageName() + ": " + response.text);
        // TODO: implémenter l'envoi réel du message de réponse.
    }
}
