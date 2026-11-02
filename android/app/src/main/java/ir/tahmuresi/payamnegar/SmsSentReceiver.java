package ir.tahmuresi.payamnegar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SmsSentReceiver extends BroadcastReceiver {
    static final String EXTRA_MESSAGE_ID = "message_id";
    static final String EXTRA_PART_NUMBER = "part_number";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String messageId = intent.getStringExtra(EXTRA_MESSAGE_ID);
        int partNumber = intent.getIntExtra(EXTRA_PART_NUMBER, -1);
        if (messageId == null || partNumber < 1) return;
        int resultCode = getResultCode();
        MessageStore store = new MessageStore(context);
        try {
            store.updateOutgoingPart(messageId, partNumber, resultCode == Activity.RESULT_OK, resultCode);
        } finally {
            store.close();
        }
        AppEvents.notifyDataChanged(context);
    }
}
