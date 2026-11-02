package ir.tahmuresi.payamnegar;

public final class PhoneNumbers {
    private PhoneNumbers() { }

    /** Converts Persian and Arabic-Indic digits and returns digits only. */
    public static String normalize(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '\u06f0' && ch <= '\u06f9') ch = (char) ('0' + ch - '\u06f0');
            else if (ch >= '\u0660' && ch <= '\u0669') ch = (char) ('0' + ch - '\u0660');
            if (ch >= '0' && ch <= '9') out.append(ch);
        }
        String digits = out.toString();
        if (digits.startsWith("00")) digits = digits.substring(2);
        return digits;
    }

    /** Converts valid Iranian mobile formats to canonical +98XXXXXXXXXX. */
    public static String toIranInternational(String value) {
        if (value == null) return "";
        String compact = value.trim().replace(" ", "").replace("-", "")
                .replace("(", "").replace(")", "").replace(".", "");
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < compact.length(); i++) {
            char ch = compact.charAt(i);
            if (ch >= '\u06f0' && ch <= '\u06f9') ch = (char) ('0' + ch - '\u06f0');
            else if (ch >= '\u0660' && ch <= '\u0669') ch = (char) ('0' + ch - '\u0660');
            ascii.append(ch);
        }
        String number = ascii.toString();
        if (number.startsWith("+")) {
            number = number.substring(1);
        } else if (number.startsWith("00")) {
            number = number.substring(2);
        }
        if (!number.matches("[0-9]+")) return "";
        if (number.matches("989[0-9]{9}")) return "+" + number;
        if (number.matches("09[0-9]{9}")) return "+98" + number.substring(1);
        if (number.matches("9[0-9]{9}")) return "+98" + number;
        return "";
    }

    public static boolean isValidIranNumber(String value) {
        return value != null && value.matches("\\+989[0-9]{9}");
    }

    public static boolean looksLikeInternationalNumber(String value) {
        return isValidIranNumber(toIranInternational(value));
    }
}