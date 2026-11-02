package ir.tahmuresi.payamnegar;

public final class PhoneNumbers {
    private PhoneNumbers() { }

    /** Keeps ASCII digits only and converts Persian/Arabic-Indic digits first. */
    public static String normalize(String value) {
        if (value == null) return "";
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '\u06f0' && ch <= '\u06f9') ch = (char) ('0' + ch - '\u06f0');
            else if (ch >= '\u0660' && ch <= '\u0669') ch = (char) ('0' + ch - '\u0660');
            if (ch >= '0' && ch <= '9') normalized.append(ch);
        }
        String digits = normalized.toString();
        if (digits.startsWith("00")) digits = digits.substring(2);
        return digits;
    }

    /** Requires an explicit +country-code; no national-prefix guessing is attempted. */
    public static boolean looksLikeInternationalNumber(String value) {
        if (value == null) return false;
        String compact = value.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .replace(".", "");
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < compact.length(); i++) {
            char ch = compact.charAt(i);
            if (ch >= '\u06f0' && ch <= '\u06f9') ch = (char) ('0' + ch - '\u06f0');
            else if (ch >= '\u0660' && ch <= '\u0669') ch = (char) ('0' + ch - '\u0660');
            ascii.append(ch);
        }
        return ascii.toString().matches("\\+[1-9][0-9]{7,14}");
    }
}
