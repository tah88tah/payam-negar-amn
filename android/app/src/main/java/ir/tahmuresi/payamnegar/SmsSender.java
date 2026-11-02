package ir.tahmuresi.payamnegar;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import java.util.List;

public final class SmsSender {
    private SmsSender() { }

    public static void send(Context context, String destination, String messageId, List<String> frames,
                            MessageStore store) {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("مجوز ارسال پیامک فعال نیست.");
        }
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)) {
            throw new IllegalStateException("این دستگاه قابلیت ارسال و دریافت SMS ندارد.");
        }
        if (!PhoneNumbers.looksLikeInternationalNumber(destination)) {
            throw new IllegalArgumentException("شمارهٔ طرف مقابل باید با قالب بین‌المللی و علامت + ثبت شده باشد.");
        }
        if (frames == null || frames.isEmpty() || frames.size() > SmsPacketProtocol.MAX_PARTS) {
            throw new IllegalArgumentException("تعداد بخش‌های پیام معتبر نیست.");
        }

        SmsManager manager = context.getSystemService(SmsManager.class);
        if (manager == null) manager = SmsManager.getDefault();
        for (int i = 0; i < frames.size(); i++) {
            String frame = frames.get(i);
            int[] length = SmsMessage.calculateLength(frame, false);
            if (length == null || length.length == 0 || length[0] != 1) {
                throw new IllegalStateException("یکی از بخش‌های پیام در یک SMS جا نمی‌شود؛ ارسال متوقف شد.");
            }
            Intent statusIntent = new Intent(context, SmsSentReceiver.class);
            statusIntent.setAction(context.getPackageName() + ".SMS_SENT." + messageId + "." + (i + 1));
            statusIntent.putExtra(SmsSentReceiver.EXTRA_MESSAGE_ID, messageId);
            statusIntent.putExtra(SmsSentReceiver.EXTRA_PART_NUMBER, i + 1);
            int requestCode = 0x20000000 | ((messageId.hashCode() & 0x00ffffff) << 5) | (i + 1);
            PendingIntent sentIntent = PendingIntent.getBroadcast(context, requestCode, statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            try {
                manager.sendTextMessage(destination, null, frame, sentIntent, null);
            } catch (RuntimeException ex) {
                for (int remaining = i + 1; remaining <= frames.size(); remaining++) {
                    store.updateOutgoingPart(messageId, remaining, false, -101);
                }
                throw new IllegalStateException("ارسال یکی از پیامک‌ها شروع نشد؛ بخش‌های ارسال‌نشده ناموفق علامت‌گذاری شدند.", ex);
            }
        }
    }
}
