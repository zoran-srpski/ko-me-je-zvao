package rs.komejezvao;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.List;

public class MissedCallNotificationService extends NotificationListenerService {
    // A new, quiet channel prevents a second sound after the SMS app has alerted.
    static final String CHANNEL_ID = "obogacene_sms_v2";
    static final String PREFS = "last_result";

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;

        Notification posted = sbn.getNotification();
        if ((posted.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        String smsPackage = Telephony.Sms.getDefaultSmsPackage(this);
        if (smsPackage == null || !smsPackage.equals(sbn.getPackageName())) return;

        String messageBody = extractNewestSmsBody(posted);
        List<PhoneNumberParser.Match> matches = PhoneNumberParser.matches(messageBody);
        if (matches.isEmpty()) return; // Leave the original SMS notification untouched.

        Notification original = posted;
        // Never hide the original unless our replacement can be shown and can open
        // exactly the same conversation in the user's SMS application.
        if (original.contentIntent == null || !canPostNotifications()) return;

        String enriched = enrich(messageBody, matches);
        publish(enriched, matches.get(0).number, original.contentIntent, sbn.getKey());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("last_notification_text", messageBody)
                .putString("last_enriched_text", enriched)
                .putLong("last_processed_at", System.currentTimeMillis())
                .apply();
    }

    private String extractNewestSmsBody(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return "";

        // Prefer the newest actual MessagingStyle message so the sender/title and
        // older messages from a grouped notification are never parsed as the SMS.
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0) {
            for (int i = messages.length - 1; i >= 0; i--) {
                Parcelable item = messages[i];
                if (item instanceof Bundle) {
                    CharSequence text = ((Bundle) item).getCharSequence("text");
                    if (text != null && text.length() > 0) return text.toString();
                }
            }
        }

        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (bigText != null && bigText.length() > 0) return bigText.toString();

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) {
            for (int i = lines.length - 1; i >= 0; i--) {
                if (lines[i] != null && lines[i].length() > 0) return lines[i].toString();
            }
        }

        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        return text == null ? "" : text.toString();
    }

    private String enrich(String text, List<PhoneNumberParser.Match> matches) {
        boolean canReadContacts = checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        for (PhoneNumberParser.Match match : matches) {
            result.append(text, cursor, match.start);
            String name = canReadContacts ? ContactLookup.nameFor(this, match.number) : null;
            if (name == null || name.trim().isEmpty()) result.append(match.original);
            else result.append(name.trim()).append(" (").append(match.original).append(")");
            cursor = match.end;
        }
        result.append(text, cursor, text.length());
        return result.toString();
    }

    private void publish(String enriched, String firstNumber, PendingIntent openSms, String originalKey) {
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + firstNumber));
        int requestCode = 2000 + Math.abs((originalKey + firstNumber).hashCode() % 100000);
        PendingIntent callPending = PendingIntent.getActivity(this, requestCode, dial,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Број препознат у поруци")
                .setContentText(enriched)
                .setStyle(new Notification.BigTextStyle().bigText(enriched))
                .setContentIntent(openSms)
                .addAction(0, "Позови", callPending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_MESSAGE);

        // Remove only the individual SMS notification, never a grouped summary.
        cancelNotification(originalKey);
        getSystemService(NotificationManager.class)
                .notify(4101 + Math.abs(originalKey.hashCode() % 100000), builder.build());
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Обогаћене SMS поруке", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Поруке са бројевима допуњене именима из контаката");
            channel.setSound(null, null);
            channel.enableVibration(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
