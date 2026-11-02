package ir.tahmuresi.payamnegar;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class NotificationHelper {
    private static final String CHANNEL_ID = "encrypted_sms_messages";

    private NotificationHelper() { }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        "پیام‌های رمزنگاری‌شده", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("اعلان دریافت پیام رمزنگاری‌شده در پیام‌نگار امن");
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void notifyIncoming(Context context, String peerName) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null || !notificationManager.areNotificationsEnabled()) return;
        createChannel(context);
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 7301, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("پیام رمز‌شدهٔ جدید")
                .setContentText("از طرف " + peerName + " دریافت شد؛ برای خواندن، گفتگو را باز کنید.")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "یک بستهٔ پیام رمزنگاری‌شده دریافت شد. متن پیام در اعلان نمایش داده نمی‌شود."))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .build();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), notification);
    }
}
