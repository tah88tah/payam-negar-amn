package ir.tahmuresi.payamnegar;

import java.util.ArrayList;
import java.util.List;

/** Runs without Android SDK to verify crypto and SMS framing invariants. */
public final class PureJavaSelfTest {
    public static void main(String[] args) throws Exception {
        String phrase = "یک عبارت محرمانهٔ قوی برای دو نفر";
        String message = "سلام خواهرم؛ این یک پیام آزمایشی فارسی است.\nپیام باید فقط بعد از رمزگشایی قابل خواندن باشد.";
        String packet = CryptoEngine.encrypt(message, phrase);
        require(CryptoEngine.isValidEnvelope(packet), "Generated envelope should validate");
        require(message.equals(CryptoEngine.decrypt(packet, phrase)), "AES-GCM round-trip failed");
        require(!packet.contains(message), "Plaintext must not appear in the encrypted JSON packet");
        String secondPacket = CryptoEngine.encrypt(message, phrase);
        require(!packet.equals(secondPacket), "Each encryption must use fresh salt/IV and produce a different packet");

        String tampered = packet.substring(0, packet.length() - 3) + "AAA";
        boolean tamperRejected = false;
        try { CryptoEngine.decrypt(tampered, phrase); }
        catch (Exception expected) { tamperRejected = true; }
        require(tamperRejected, "Tampered ciphertext must be rejected by GCM authentication");

        boolean wrongKeyRejected = false;
        try { CryptoEngine.decrypt(packet, "عبارت کاملاً اشتباه است"); }
        catch (Exception expected) { wrongKeyRejected = true; }
        require(wrongKeyRejected, "Wrong passphrase must be rejected");

        String id = SmsPacketProtocol.newMessageId();
        require(id.matches("[0-9a-f]{16}"), "Message ID must be 64-bit hex");
        List<String> frames = SmsPacketProtocol.split(packet, id);
        require(frames.size() > 1, "Test message should exercise multipart SMS reassembly");
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < frames.size(); i++) {
            String frameText = frames.get(i);
            require(frameText.length() <= 140, "SMS frame exceeds conservative 140-char limit");
            require(frameText.matches("[A-Za-z0-9_:/-]+"), "Frame must be ASCII GSM-safe subset");
            SmsPacketProtocol.ParsedFrame parsed = SmsPacketProtocol.parse(frameText);
            require(parsed != null, "Generated frame did not parse");
            require(parsed.partNumber == i + 1, "Frame order mismatch");
            require(parsed.totalParts == frames.size(), "Frame total mismatch");
            require(parsed.messageId.equals(id), "Frame message ID mismatch");
            payload.append(parsed.payload);
        }
        require(packet.equals(SmsPacketProtocol.decodePayload(payload.toString())), "Frame reassembly failed");
        require(SmsPacketProtocol.parse("ordinary human SMS") == null, "Ordinary SMS must be ignored");
        require(PhoneNumbers.normalize("+۹۸ ۹۱۲-۳۴۵-۶۷۸۹").equals("989123456789"), "Persian digits normalization failed");
        require(PhoneNumbers.looksLikeInternationalNumber("+989123456789"), "Valid international number rejected");
        require(PhoneNumbers.looksLikeInternationalNumber("+۹۸۹۱۲۳۴۵۶۷۸۹"), "Persian international digits should be accepted");
        require(!PhoneNumbers.looksLikeInternationalNumber("09123456789"), "National-only number should require country code");
        require(!PhoneNumbers.looksLikeInternationalNumber("++989123456789"), "Malformed number must be rejected");
        require(!PhoneNumbers.looksLikeInternationalNumber("+0989123456789"), "Country code cannot begin with zero");

        // Emit a packet so tools/crosscheck-webcrypto.mjs can verify WebCrypto interoperability.
        System.out.println(packet);
        System.err.println("PASS: crypto round-trip, wrong-key rejection, SMS frames, reassembly, and phone formatting");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
