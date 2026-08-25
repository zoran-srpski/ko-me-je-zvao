package rs.komejezvao;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PhoneNumberParser {
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:(?:\\+|00)381|0)(?:[\\s()./-]*\\d){8,10}(?!\\d)");

    private PhoneNumberParser() {}

    static List<String> find(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null) return new ArrayList<>();
        Matcher matcher = PHONE.matcher(text);
        while (matcher.find()) {
            String value = matcher.group().trim();
            String digits = value.replaceAll("\\D", "");
            if (digits.startsWith("00381")) digits = digits.substring(2);
            if (digits.startsWith("381")) value = "+" + digits;
            else value = digits;
            found.add(value);
        }
        return new ArrayList<>(found);
    }

    static String localVariant(String number) {
        String digits = number.replaceAll("\\D", "");
        if (digits.startsWith("381")) return "0" + digits.substring(3);
        return digits;
    }

    static String pretty(String number) {
        String local = localVariant(number);
        if (local.length() >= 9 && local.startsWith("0")) {
            int prefix = local.startsWith("011") || local.startsWith("021") || local.startsWith("018") ? 3 : 3;
            String rest = local.substring(prefix);
            if (rest.length() == 7) return local.substring(0, prefix) + " " + rest.substring(0, 3) + " " + rest.substring(3);
            if (rest.length() == 6) return local.substring(0, prefix) + " " + rest.substring(0, 3) + " " + rest.substring(3);
        }
        return number;
    }
}
