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
import java.util.Set;

public class MyNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "MyNotificationListener";
    private static final String GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging";
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

        List<ResponseConfig> responses = loadResponses();
        boolean sent = false;
        for (ResponseConfig response : responses) {
            if (!matchesApp(response, sbn)) {
                continue;
            }
            if (matchesVoiceCriteria(response, sbn)) {
                Log.d(TAG, "Voice criteria matched for response: " + response.title);
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

    private boolean matchesVoiceCriteria(ResponseConfig response, StatusBarNotification sbn) {
        if (!isVoiceTriggerSelected(response)) {
            return false;
        }
        return isGoogleMessagesVoiceNote(sbn);
    }

    private boolean isVoiceTriggerSelected(ResponseConfig response) {
        return response.triggers.stream().anyMatch(trigger ->
                trigger.equalsIgnoreCase("vocaux") || trigger.equalsIgnoreCase("voice") || trigger.equalsIgnoreCase("audio"));
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
            Log.d(TAG, "Detected voice note by content string");
            return true;
        }

        NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (style != null) {
            for (NotificationCompat.MessagingStyle.Message message : style.getMessages()) {
                String mimeType = message.getDataMimeType();
                Log.d(TAG, "Message mimeType=" + mimeType + " text=" + message.getText());
                if (mimeType != null && mimeType.startsWith("audio/")) {
                    Log.d(TAG, "Detected voice note by mimeType");
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
