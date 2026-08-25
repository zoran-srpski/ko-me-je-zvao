package rs.komejezvao;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class CallHistoryStore {
    private static final String KEY = "call_history";
    private static final int MAX_ITEMS = 50;

    static final class Entry {
        final String name;
        final String number;
        final long time;
        final boolean viewed;

        Entry(String name, String number, long time) {
            this(name, number, time, false);
        }

        Entry(String name, String number, long time, boolean viewed) {
            this.name = name;
            this.number = number;
            this.time = time;
            this.viewed = viewed;
        }
    }

    private CallHistoryStore() {}

    static synchronized int add(Context context, List<Entry> additions) {
        List<Entry> current = load(context);
        for (Entry addition : additions) {
            boolean duplicate = false;
            for (int i = Math.max(0, current.size() - 5); i < current.size(); i++) {
                Entry old = current.get(i);
                if (PhoneNumberParser.localVariant(old.number).equals(PhoneNumberParser.localVariant(addition.number))
                        && Math.abs(old.time - addition.time) < 30_000L) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) current.add(addition);
        }
        while (current.size() > MAX_ITEMS) current.remove(0);
        save(context, current);
        return unreadCount(current);
    }

    static synchronized List<Entry> load(Context context) {
        List<Entry> entries = new ArrayList<>();
        String raw = prefs(context).getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                entries.add(new Entry(item.optString("name", "Непознат број"),
                        item.optString("number", ""), item.optLong("time", 0L),
                        item.has("viewed") ? item.optBoolean("viewed", false) : true));
            }
        } catch (Exception ignored) { }
        return entries;
    }

    static synchronized void markAllViewed(Context context) {
        List<Entry> entries = load(context);
        List<Entry> updated = new ArrayList<>();
        for (Entry entry : entries) updated.add(new Entry(entry.name, entry.number, entry.time, true));
        save(context, updated);
    }

    static synchronized void delete(Context context, Entry target) {
        List<Entry> entries = load(context);
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry.time == target.time && entry.number.equals(target.number)) {
                entries.remove(i);
                break;
            }
        }
        save(context, entries);
    }

    static synchronized void clear(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    private static int unreadCount(List<Entry> entries) {
        int count = 0;
        for (Entry entry : entries) if (!entry.viewed) count++;
        return count;
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries) {
                JSONObject item = new JSONObject();
                item.put("name", entry.name);
                item.put("number", entry.number);
                item.put("time", entry.time);
                item.put("viewed", entry.viewed);
                array.put(item);
            }
        } catch (Exception ignored) { }
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(MissedCallNotificationService.PREFS, Context.MODE_PRIVATE);
    }
}
