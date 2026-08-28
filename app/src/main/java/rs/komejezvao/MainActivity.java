package rs.komejezvao;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private LinearLayout root;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(32));
        scroll.addView(root);

        TextView title = text("Ко ме је звао?", 29, true);
        title.setTextColor(Color.rgb(13, 71, 161));
        root.addView(title);
        TextView intro = text("Допуњује SMS обавештења именима из ваших контаката, без промене подразумеване SMS апликације.", 17, false);
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

        TextView behavior = text("Како ради: SMS без броја остаје непромењен. Када порука садржи број, оригинално обавештење замењује се истим текстом допуњеним именом контакта. Додир на обавештење и даље отвара вашу SMS апликацију, а дугме „Позови“ отвара бројчаник.", 16, false);
        behavior.setPadding(0, dp(8), 0, dp(18));
        root.addView(behavior);

        String last = getSharedPreferences(MissedCallNotificationService.PREFS, MODE_PRIVATE)
                .getString("last_enriched_text", "");
        if (!last.isEmpty()) {
            TextView heading = text("Последња обрађена порука", 19, true);
            heading.setPadding(0, dp(8), 0, dp(6));
            root.addView(heading);
            TextView preview = text(last, 17, false);
            preview.setPadding(dp(12), dp(12), dp(12), dp(12));
            preview.setBackgroundColor(Color.rgb(232, 240, 254));
            root.addView(preview);
        }
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
