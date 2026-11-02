package ir.tahmuresi.payamnegar;

import android.content.Context;
import android.content.Intent;

/** Private in-process/app-package events used to refresh the open conversation UI. */
public final class AppEvents {
    public static final String ACTION_DATA_CHANGED = "ir.tahmuresi.payamnegar.DATA_CHANGED";
    private AppEvents() { }

    public static void notifyDataChanged(Context context) {
        context.sendBroadcast(new Intent(ACTION_DATA_CHANGED).setPackage(context.getPackageName()));
    }
}
