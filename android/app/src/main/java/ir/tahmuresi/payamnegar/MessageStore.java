package ir.tahmuresi.payamnegar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Stores encrypted packets and routing metadata only. Plaintext and the shared phrase are never persisted. */
public final class MessageStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "payam_negar_local.db";
    private static final int DB_VERSION = 1;

    public static final int INCOMING_WAITING = 0;
    public static final int INCOMING_COMPLETE = 1;
    public static final int INCOMING_INVALID = -1;
    public static final int INCOMING_DUPLICATE = 2;

    public MessageStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "message_id TEXT NOT NULL,"
                + "direction TEXT NOT NULL,"
                + "phone_number TEXT NOT NULL,"
                + "packet TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL,"
                + "status TEXT NOT NULL,"
                + "total_parts INTEGER NOT NULL,"
                + "UNIQUE(direction, message_id))");

        db.execSQL("CREATE TABLE pending_parts ("
                + "sender TEXT NOT NULL,"
                + "message_id TEXT NOT NULL,"
                + "part_number INTEGER NOT NULL,"
                + "total_parts INTEGER NOT NULL,"
                + "payload TEXT NOT NULL,"
                + "received_at INTEGER NOT NULL,"
                + "PRIMARY KEY(sender, message_id, part_number))");

        db.execSQL("CREATE TABLE outgoing_parts ("
                + "message_id TEXT NOT NULL,"
                + "part_number INTEGER NOT NULL,"
                + "state TEXT NOT NULL,"
                + "error_code INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(message_id, part_number))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Versioned migrations will be added here. Do not drop chat history during an update.
    }

    public int acceptIncomingPart(String sender, SmsPacketProtocol.ParsedFrame frame, long receivedAt) {
        SQLiteDatabase db = getWritableDatabase();
        int result = INCOMING_WAITING;
        db.beginTransaction();
        try {
            if (messageExists(db, "IN", frame.messageId)) {
                result = INCOMING_DUPLICATE;
                db.setTransactionSuccessful();
                return result;
            }

            Integer previousTotal = getExistingTotal(db, sender, frame.messageId);
            if (previousTotal != null && previousTotal != frame.totalParts) {
                deletePending(db, sender, frame.messageId);
                result = INCOMING_INVALID;
                db.setTransactionSuccessful();
                return result;
            }

            String oldPayload = getExistingPayload(db, sender, frame.messageId, frame.partNumber);
            if (oldPayload != null && !oldPayload.equals(frame.payload)) {
                deletePending(db, sender, frame.messageId);
                result = INCOMING_INVALID;
                db.setTransactionSuccessful();
                return result;
            }

            ContentValues part = new ContentValues();
            part.put("sender", sender);
            part.put("message_id", frame.messageId);
            part.put("part_number", frame.partNumber);
            part.put("total_parts", frame.totalParts);
            part.put("payload", frame.payload);
            part.put("received_at", receivedAt);
            db.insertWithOnConflict("pending_parts", null, part, SQLiteDatabase.CONFLICT_IGNORE);

            int count = countParts(db, sender, frame.messageId);
            if (count == frame.totalParts) {
                StringBuilder encoded = new StringBuilder();
                long firstReceived = receivedAt;
                try (Cursor cursor = db.rawQuery(
                        "SELECT part_number, payload, received_at FROM pending_parts "
                                + "WHERE sender=? AND message_id=? ORDER BY part_number ASC",
                        new String[]{sender, frame.messageId})) {
                    int expected = 1;
                    while (cursor.moveToNext()) {
                        if (cursor.getInt(0) != expected++) {
                            deletePending(db, sender, frame.messageId);
                            result = INCOMING_INVALID;
                            db.setTransactionSuccessful();
                            return result;
                        }
                        encoded.append(cursor.getString(1));
                        firstReceived = Math.min(firstReceived, cursor.getLong(2));
                    }
                }
                try {
                    String packet = SmsPacketProtocol.decodePayload(encoded.toString());
                    if (!CryptoEngine.isValidEnvelope(packet)) {
                        deletePending(db, sender, frame.messageId);
                        result = INCOMING_INVALID;
                    } else {
                        ContentValues message = new ContentValues();
                        message.put("message_id", frame.messageId);
                        message.put("direction", "IN");
                        message.put("phone_number", sender);
                        message.put("packet", packet);
                        message.put("created_at", firstReceived);
                        message.put("status", "RECEIVED");
                        message.put("total_parts", frame.totalParts);
                        db.insertWithOnConflict("messages", null, message, SQLiteDatabase.CONFLICT_IGNORE);
                        deletePending(db, sender, frame.messageId);
                        result = INCOMING_COMPLETE;
                    }
                } catch (IllegalArgumentException ex) {
                    deletePending(db, sender, frame.messageId);
                    result = INCOMING_INVALID;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return result;
    }

    public void addOutgoing(String messageId, String phone, String packet, int totalParts, long createdAt) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues message = new ContentValues();
            message.put("message_id", messageId);
            message.put("direction", "OUT");
            message.put("phone_number", phone);
            message.put("packet", packet);
            message.put("created_at", createdAt);
            message.put("status", "SENDING");
            message.put("total_parts", totalParts);
            db.insertOrThrow("messages", null, message);
            for (int i = 1; i <= totalParts; i++) {
                ContentValues part = new ContentValues();
                part.put("message_id", messageId);
                part.put("part_number", i);
                part.put("state", "QUEUED");
                part.put("error_code", 0);
                db.insertOrThrow("outgoing_parts", null, part);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void updateOutgoingPart(String messageId, int partNumber, boolean sent, int errorCode) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues part = new ContentValues();
            part.put("state", sent ? "SENT" : "FAILED");
            part.put("error_code", errorCode);
            db.update("outgoing_parts", part, "message_id=? AND part_number=?",
                    new String[]{messageId, String.valueOf(partNumber)});

            int total = scalarCount(db, "SELECT COUNT(*) FROM outgoing_parts WHERE message_id=?",
                    new String[]{messageId});
            int terminal = scalarCount(db, "SELECT COUNT(*) FROM outgoing_parts WHERE message_id=? AND state IN ('SENT','FAILED')",
                    new String[]{messageId});
            int succeeded = scalarCount(db, "SELECT COUNT(*) FROM outgoing_parts WHERE message_id=? AND state='SENT'",
                    new String[]{messageId});
            int failed = scalarCount(db, "SELECT COUNT(*) FROM outgoing_parts WHERE message_id=? AND state='FAILED'",
                    new String[]{messageId});

            String status = "SENDING";
            if (total > 0 && terminal == total) {
                if (failed == 0) status = "SENT";
                else if (succeeded == 0) status = "FAILED";
                else status = "PARTIAL";
            }
            ContentValues message = new ContentValues();
            message.put("status", status);
            db.update("messages", message, "message_id=? AND direction='OUT'", new String[]{messageId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Marks only not-yet-submitted SMS parts failed; sent callbacks are never overwritten. */
    public void failQueuedParts(String messageId, int errorCode) {
        List<Integer> queued = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT part_number FROM outgoing_parts WHERE message_id=? AND state='QUEUED'",
                new String[]{messageId})) {
            while (cursor.moveToNext()) queued.add(cursor.getInt(0));
        }
        for (int partNumber : queued) updateOutgoingPart(messageId, partNumber, false, errorCode);
    }

    public List<ChatMessage> getMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT message_id,direction,phone_number,packet,created_at,status,total_parts "
                        + "FROM messages ORDER BY created_at ASC, id ASC", null)) {
            while (cursor.moveToNext()) {
                messages.add(new ChatMessage(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getLong(4), cursor.getString(5), cursor.getInt(6)));
            }
        }
        return messages;
    }

    public void pruneOldPendingParts(long cutoffMillis) {
        getWritableDatabase().delete("pending_parts", "received_at < ?",
                new String[]{String.valueOf(cutoffMillis)});
    }

    private boolean messageExists(SQLiteDatabase db, String direction, String messageId) {
        return scalarCount(db, "SELECT COUNT(*) FROM messages WHERE direction=? AND message_id=?",
                new String[]{direction, messageId}) > 0;
    }

    private Integer getExistingTotal(SQLiteDatabase db, String sender, String messageId) {
        try (Cursor cursor = db.rawQuery("SELECT total_parts FROM pending_parts WHERE sender=? AND message_id=? LIMIT 1",
                new String[]{sender, messageId})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : null;
        }
    }

    private String getExistingPayload(SQLiteDatabase db, String sender, String messageId, int partNumber) {
        try (Cursor cursor = db.rawQuery("SELECT payload FROM pending_parts WHERE sender=? AND message_id=? AND part_number=?",
                new String[]{sender, messageId, String.valueOf(partNumber)})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private int countParts(SQLiteDatabase db, String sender, String messageId) {
        return scalarCount(db, "SELECT COUNT(*) FROM pending_parts WHERE sender=? AND message_id=?",
                new String[]{sender, messageId});
    }

    private int scalarCount(SQLiteDatabase db, String sql, String[] args) {
        try (Cursor cursor = db.rawQuery(sql, args)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private void deletePending(SQLiteDatabase db, String sender, String messageId) {
        db.delete("pending_parts", "sender=? AND message_id=?", new String[]{sender, messageId});
    }
}
