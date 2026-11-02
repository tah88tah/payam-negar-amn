package ir.tahmuresi.payamnegar;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String FILE = "payam_negar_settings";
    private static final String KEY_PEER_NAME = "peer_name";
    private static final String KEY_PEER_PHONE = "peer_phone";

    private AppPrefs() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String peerName(Context context) {
        return prefs(context).getString(KEY_PEER_NAME, "طرف گفتگو");
    }

    public static String peerPhone(Context context) {
        return prefs(context).getString(KEY_PEER_PHONE, "");
    }

    public static String peerPhoneNormalized(Context context) {
        return PhoneNumbers.normalize(peerPhone(context));
    }

    public static void savePeer(Context context, String name, String phone) {
        prefs(context).edit()
                .putString(KEY_PEER_NAME, name == null || name.trim().isEmpty() ? "طرف گفتگو" : name.trim())
                .putString(KEY_PEER_PHONE, phone == null ? "" : phone.trim())
                .apply();
    }
}
