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
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MissedCallNotificationService extends NotificationListenerService {
    static final String CHANNEL_ID = "prepoznati_pozivi";
    static final String PREFS = "last_result";

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        Bundle extras = sbn.getNotification().extras;
        Set<String> pieces = new LinkedHashSet<>();
        add(pieces, extras.getCharSequence(Notification.EXTRA_TITLE));
        add(pieces, extras.getCharSequence(Notification.EXTRA_TEXT));
        add(pieces, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) add(pieces, line);
        String combined = String.join("\n", pieces);
        List<String> numbers = PhoneNumberParser.find(combined);
        if (numbers.isEmpty()) {
            collectNewestMessageText(extras, pieces);
            combined = String.join("\n", pieces);
            numbers = PhoneNumberParser.find(combined);
        }
        if (numbers.isEmpty()) {
            collectBundleText(extras, pieces, 0);
            combined = String.join("\n", pieces);
            numbers = PhoneNumberParser.find(combined);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("last_notification_text", combined).apply();
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

    private void add(Set<String> pieces, CharSequence value) {
        if (value != null && value.length() > 0) pieces.add(value.toString());
    }

    private void collectBundleText(Bundle bundle, Set<String> pieces, int depth) {
        if (bundle == null || depth > 2) return;
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value instanceof CharSequence) {
                add(pieces, (CharSequence) value);
            } else if (value instanceof CharSequence[]) {
                for (CharSequence item : (CharSequence[]) value) add(pieces, item);
            } else if (value instanceof Bundle) {
                collectBundleText((Bundle) value, pieces, depth + 1);
            } else if (value instanceof Parcelable[]) {
                for (Parcelable item : (Parcelable[]) value) {
                    if (item instanceof Bundle) collectBundleText((Bundle) item, pieces, depth + 1);
                }
            } else if (value instanceof ArrayList<?>) {
                for (Object item : (ArrayList<?>) value) {
                    if (item instanceof CharSequence) add(pieces, (CharSequence) item);
                    else if (item instanceof Bundle) collectBundleText((Bundle) item, pieces, depth + 1);
                }
            }
        }
    }

    private void collectNewestMessageText(Bundle extras, Set<String> pieces) {
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0) {
            Parcelable newest = messages[messages.length - 1];
            if (newest instanceof Bundle) collectBundleText((Bundle) newest, pieces, 0);
        }
    }

    private void publish(List<String> rows, String firstNumber, int unread) {
        String result = String.join("\n", rows);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("text", result).apply();
        Intent open = new Intent(this, MainActivity.class).putExtra("result", result)
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
