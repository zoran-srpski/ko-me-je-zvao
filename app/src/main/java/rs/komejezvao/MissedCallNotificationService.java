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
import java.util.ArrayList;
import java.util.List;

public class MissedCallNotificationService extends NotificationListenerService {
    static final String CHANNEL_ID = "prepoznati_pozivi";
    static final String PREFS = "last_result";

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;

        String smsPackage = Telephony.Sms.getDefaultSmsPackage(this);
        if (smsPackage == null || !smsPackage.equals(sbn.getPackageName())) return;

        String messageBody = extractNewestSmsBody(sbn.getNotification());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("last_notification_text", messageBody).apply();

        List<String> numbers = PhoneNumberParser.find(messageBody);
        if (numbers.isEmpty()) return;
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;

        List<String> rows = new ArrayList<>();
        List<CallHistoryStore.Entry> additions = new ArrayList<>();
        boolean hasKnownContact = false;
        for (String number : numbers) {
            String name = ContactLookup.nameFor(this, number);
            if (name != null && !name.trim().isEmpty()) hasKnownContact = true;
            String shownName = name == null || name.trim().isEmpty() ? "Непознат број" : name;
            rows.add(shownName + " — " + PhoneNumberParser.pretty(number));
            additions.add(new CallHistoryStore.Entry(shownName, number, sbn.getPostTime()));
        }
        if (!hasKnownContact) return;
        int unread = CallHistoryStore.add(this, additions);
        publish(rows, numbers.get(0), unread);
    }

    private String extractNewestSmsBody(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return "";

        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0) {
            Parcelable newest = messages[messages.length - 1];
            if (newest instanceof Bundle) {
                CharSequence text = ((Bundle) newest).getCharSequence("text");
                if (text != null) return text.toString();
            }
        }

        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (bigText != null) return bigText.toString();

        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        return text == null ? "" : text.toString();
    }

    private void publish(List<String> rows, String firstNumber, int unread) {
        String result = String.join("\n", rows);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("text", result).apply();
        Intent open = new Intent(this, MainActivity.class)
                .putExtra("history_only", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + firstNumber));
        PendingIntent callPending = PendingIntent.getActivity(this, 2, dial,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_app)
                .setContentTitle(unread == 1 ? "1 нов пропуштен позив" : unread + " нових пропуштених позива")
                .setContentText("Додирните за списак позивалаца")
                .setStyle(new Notification.BigTextStyle().bigText(result + "\n\nДодирните за цео списак."))
                .setContentIntent(pending)
                .addAction(0, "Позови последњег", callPending)
                .setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE);
        getSystemService(NotificationManager.class).notify(4101, builder.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Препознати пропуштени позиви", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Имена контаката пронађених у порукама о пропуштеним позивима");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
