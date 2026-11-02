package ir.tahmuresi.payamnegar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;
import android.provider.Telephony;

/** Receives only our PN1-framed messages from the configured peer number. */
public final class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        String expectedSender = AppPrefs.peerPhoneNormalized(context);
        if (expectedSender.isEmpty()) return;

        SmsMessage[] messages;
        try {
            messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (RuntimeException ex) {
            return;
        }
        if (messages == null || messages.length == 0) return;

        MessageStore store = new MessageStore(context);
        boolean completedAny = false;
        try {
            store.pruneOldPendingParts(System.currentTimeMillis() - 72L * 60L * 60L * 1000L);
            for (SmsMessage sms : messages) {
                if (sms == null) continue;
                String sender = PhoneNumbers.normalize(sms.getOriginatingAddress());
                if (sender.isEmpty() || !expectedSender.equals(sender)) continue;

                SmsPacketProtocol.ParsedFrame frame = SmsPacketProtocol.parse(sms.getMessageBody());
                if (frame == null) continue;

                int result = store.acceptIncomingPart(sender, frame,
                        sms.getTimestampMillis() > 0 ? sms.getTimestampMillis() : System.currentTimeMillis());
                if (result == MessageStore.INCOMING_COMPLETE) completedAny = true;
            }
        } catch (RuntimeException ex) {
            android.util.Log.e("PayamNegar", "Unable to process incoming SMS frame", ex);
        } finally {
            store.close();
        }
        if (completedAny) {
            AppEvents.notifyDataChanged(context);
            NotificationHelper.notifyIncoming(context, AppPrefs.peerName(context));
        }
    }
}
