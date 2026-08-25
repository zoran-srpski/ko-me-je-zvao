package rs.komejezvao;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;

public class MainActivity extends Activity {
    private LinearLayout root;
    private boolean historyOnly;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        historyOnly = getIntent().getBooleanExtra("history_only", false);
        buildScreen();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        historyOnly = intent.getBooleanExtra("history_only", false);
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        buildScreen();
    }

    @Override protected void onPause() {
        super.onPause();
        CallHistoryStore.markAllViewed(this);
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(32));
        scroll.addView(root);

        if (historyOnly) {
            List<CallHistoryStore.Entry> history = CallHistoryStore.load(this);
            if (history.isEmpty()) {
                TextView empty = text("Нема сачуваних пропуштених позива.", 19, false);
                root.addView(empty);
            } else {
                showHistory(history);
            }
            setContentView(scroll);
            return;
        }

        TextView title = text("Ко ме је звао?", 29, true);
        title.setTextColor(Color.rgb(13, 71, 161));
        root.addView(title);
        TextView intro = text("Аутоматски препознаје бројеве у обавештењима о пропуштеним позивима и приказује имена из ваших контаката.", 17, false);
        intro.setPadding(0, dp(10), 0, dp(24));
        root.addView(intro);

        boolean contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        addStep("1. Приступ контактима", contacts, contacts ? "Дозвољено" : "Дозволи", v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 10));

        boolean listener = isListenerEnabled();
        addStep("2. Приступ обавештењима", listener, listener ? "Укључено" : "Укључи", v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        if (Build.VERSION.SDK_INT >= 33) {
            boolean notifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            addStep("3. Приказ обавештења", notifications, notifications ? "Дозвољено" : "Дозволи", v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11));
        }

        TextView privacy = text("Приватност: поруке, бројеви и контакти обрађују се само на овом телефону и не шаљу се на интернет.", 15, false);
        privacy.setTextColor(Color.DKGRAY);
        privacy.setPadding(0, dp(24), 0, dp(18));
        root.addView(privacy);

        List<CallHistoryStore.Entry> history = CallHistoryStore.load(this);
        if (!history.isEmpty()) showHistory(history);
        setContentView(scroll);
    }

    private void addStep(String label, boolean done, String buttonLabel, View.OnClickListener action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackgroundColor(done ? Color.rgb(232, 245, 233) : Color.rgb(255, 248, 225));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(12));
        root.addView(card, cardLp);
        TextView step = text(label, 18, true);
        card.addView(step);
        Button button = new Button(this);
        button.setText((done ? "✓  " : "") + buttonLabel);
        button.setAllCaps(false);
        button.setEnabled(!done);
        if (!done) button.setOnClickListener(action);
        card.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showResult(String result) {
        TextView heading = text("Последњи препознати позиви", 20, true);
        heading.setPadding(0, dp(12), 0, dp(8));
        root.addView(heading);
        for (String row : result.split("\n")) {
            List<String> numbers = PhoneNumberParser.find(row);
            TextView line = text(row, 18, false);
            line.setPadding(dp(12), dp(11), dp(12), dp(11));
            line.setBackgroundColor(Color.rgb(232, 240, 254));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(6));
            root.addView(line, lp);
            if (!numbers.isEmpty()) {
                String number = numbers.get(0);
                Button call = new Button(this);
                call.setText("Позови " + PhoneNumberParser.pretty(number));
                call.setTextSize(18);
                call.setAllCaps(false);
                call.setOnClickListener(v -> startActivity(
                        new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))));
                LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, -2);
                buttonLp.setMargins(0, 0, 0, dp(14));
                root.addView(call, buttonLp);
            }
        }
    }

    private void showHistory(List<CallHistoryStore.Entry> history) {
        int newCount = 0;
        for (CallHistoryStore.Entry entry : history) if (!entry.viewed) newCount++;
        String countText = "Пропуштени позиви (" + history.size() + ")";
        if (newCount > 0) countText += " — нови: " + newCount;
        TextView heading = text(countText, 22, true);
        heading.setPadding(0, dp(12), 0, dp(6));
        root.addView(heading);
        Button clear = new Button(this);
        clear.setText("Обриши све");
        clear.setAllCaps(false);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Обрисати све позиве?")
                .setMessage("Овај списак ће бити трајно испражњен.")
                .setNegativeButton("Откажи", null)
                .setPositiveButton("Обриши све", (dialog, which) -> {
                    CallHistoryStore.clear(this);
                    buildScreen();
                }).show());
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-1, -2);
        clearLp.setMargins(0, 0, 0, dp(10));
        root.addView(clear, clearLp);
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy.  HH:mm", new Locale("sr", "RS"));
        for (int i = history.size() - 1; i >= 0; i--) {
            CallHistoryStore.Entry entry = history.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(10));
            card.setBackgroundColor(entry.viewed ? Color.rgb(245, 245, 245) : Color.rgb(220, 237, 255));
            TextView name = text((entry.viewed ? "" : "НОВ  •  ") + entry.name, 19, true);
            if (!entry.viewed) name.setTextColor(Color.rgb(13, 71, 161));
            card.addView(name);
            TextView details = text(PhoneNumberParser.pretty(entry.number) + "\n" + format.format(new Date(entry.time)), 16, false);
            details.setPadding(0, dp(4), 0, dp(6));
            card.addView(details);
            Button call = new Button(this);
            call.setText("Позови");
            call.setTextSize(18);
            call.setAllCaps(false);
            call.setOnClickListener(v -> startActivity(
                    new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + entry.number))));
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(0, -2, 1f);
            actions.addView(call, actionLp);
            Button delete = new Button(this);
            delete.setText("Обриши");
            delete.setTextSize(16);
            delete.setAllCaps(false);
            delete.setOnClickListener(v -> {
                CallHistoryStore.delete(this, entry);
                buildScreen();
            });
            actions.addView(delete, actionLp);
            card.addView(actions, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.setMargins(0, 0, 0, dp(10));
            root.addView(card, cardLp);
        }
    }

    private boolean isListenerEnabled() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        ComponentName component = new ComponentName(this, MissedCallNotificationService.class);
        return manager != null && manager.isNotificationListenerAccessGranted(component);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(sp); view.setTextColor(Color.rgb(32, 33, 36));
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
