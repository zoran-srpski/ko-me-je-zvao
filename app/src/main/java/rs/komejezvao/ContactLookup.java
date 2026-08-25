package rs.komejezvao;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;

final class ContactLookup {
    private ContactLookup() {}

    static String nameFor(Context context, String number) {
        String name = query(context.getContentResolver(), number);
        if (name == null) name = query(context.getContentResolver(), PhoneNumberParser.localVariant(number));
        if (name == null) name = scanAll(context.getContentResolver(), number);
        return name;
    }

    private static String query(ContentResolver resolver, String number) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String[] columns = { ContactsContract.PhoneLookup.DISPLAY_NAME };
        try (Cursor cursor = resolver.query(uri, columns, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (SecurityException ignored) { }
        return null;
    }

    private static String scanAll(ContentResolver resolver, String number) {
        String[] columns = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, columns, null, null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                String stored = cursor.getString(1);
                if (stored != null && PhoneNumberUtils.compare(stored, number)) return cursor.getString(0);
            }
        } catch (SecurityException ignored) { }
        return null;
    }
}
