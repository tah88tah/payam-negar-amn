package ir.tahmuresi.payamnegar;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Application-level SMS framing. Each frame is deliberately kept as one ordinary SMS. */
public final class SmsPacketProtocol {
    public static final int MAX_PARTS = 20;
    public static final int PAYLOAD_CHARS_PER_SMS = 110;
    private static final Pattern FRAME = Pattern.compile(
            "^PN1:([0-9a-fA-F]{16}):(\\d{1,2})/(\\d{1,2}):([A-Za-z0-9_-]+)$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private SmsPacketProtocol() { }

    public static String newMessageId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        StringBuilder result = new StringBuilder(16);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    public static List<String> split(String encryptedPacket, String messageId) {
        if (encryptedPacket == null || encryptedPacket.isEmpty()) {
            throw new IllegalArgumentException("بستهٔ رمز‌شده خالی است.");
        }
        if (messageId == null || !messageId.matches("[0-9a-fA-F]{16}")) {
            throw new IllegalArgumentException("شناسهٔ پیام معتبر نیست.");
        }
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(encryptedPacket.getBytes(StandardCharsets.UTF_8));
        int count = (encoded.length() + PAYLOAD_CHARS_PER_SMS - 1) / PAYLOAD_CHARS_PER_SMS;
        if (count < 1) count = 1;
        if (count > MAX_PARTS) {
            throw new IllegalArgumentException("این پیام برای ارسال پیامکی بیش از حد طولانی است. لطفاً کوتاه‌تر بنویسید.");
        }
        List<String> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int start = index * PAYLOAD_CHARS_PER_SMS;
            int end = Math.min(encoded.length(), start + PAYLOAD_CHARS_PER_SMS);
            String frame = "PN1:" + messageId.toLowerCase(Locale.ROOT) + ":"
                    + (index + 1) + "/" + count + ":" + encoded.substring(start, end);
            if (frame.length() > 140 || !frame.matches("[A-Za-z0-9_:/-]+")) {
                throw new IllegalStateException("قاب‌بندی پیام از محدودهٔ امن یک SMS خارج شده است.");
            }
            result.add(frame);
        }
        return result;
    }

    public static ParsedFrame parse(String smsBody) {
        if (smsBody == null) return null;
        Matcher matcher = FRAME.matcher(smsBody.trim());
        if (!matcher.matches()) return null;
        try {
            String id = matcher.group(1).toLowerCase(Locale.ROOT);
            int part = Integer.parseInt(matcher.group(2));
            int total = Integer.parseInt(matcher.group(3));
            String data = matcher.group(4);
            if (total < 1 || total > MAX_PARTS || part < 1 || part > total || data.length() > PAYLOAD_CHARS_PER_SMS) {
                return null;
            }
            return new ParsedFrame(id, part, total, data);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String decodePayload(String encodedPayload) {
        byte[] bytes = Base64.getUrlDecoder().decode(encodedPayload);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static final class ParsedFrame {
        public final String messageId;
        public final int partNumber;
        public final int totalParts;
        public final String payload;

        ParsedFrame(String messageId, int partNumber, int totalParts, String payload) {
            this.messageId = messageId;
            this.partNumber = partNumber;
            this.totalParts = totalParts;
            this.payload = payload;
        }
    }
}
