package ir.tahmuresi.payamnegar;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Local-only encryption compatible with the browser prototype's packet format:
 * PBKDF2-HMAC-SHA-256 (310,000 rounds) + AES-256-GCM, 128-bit tag.
 * Password conversion is explicit UTF-8 to match TextEncoder/WebCrypto.
 */
public final class CryptoEngine {
    public static final int ITERATIONS = 310_000;
    public static final String ALGORITHM = "PBKDF2-SHA256+A256GCM";
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final int MAX_PLAINTEXT_BYTES = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern STRING_FIELD = Pattern.compile("\\\"([a-z]+)\\\"\\s*:\\s*\\\"([A-Za-z0-9_+\\-]+)\\\"");
    private static final Pattern INT_FIELD = Pattern.compile("\\\"([a-z]+)\\\"\\s*:\\s*(\\d+)");

    private CryptoEngine() { }

    public static String encrypt(String plaintext, String passphrase) throws GeneralSecurityException {
        if (plaintext == null || plaintext.trim().isEmpty()) {
            throw new IllegalArgumentException("متن پیام خالی است.");
        }
        validatePassphrase(passphrase);
        byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        if (plainBytes.length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("متن پیام بیش از حد طولانی است.");
        }

        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(IV_BYTES);
        byte[] passwordBytes = passphrase.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = null;
        try {
            keyBytes = pbkdf2HmacSha256(passwordBytes, salt, ITERATIONS, KEY_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertextAndTag = cipher.doFinal(plainBytes);

            return "{\"v\":1,\"alg\":\"" + ALGORITHM + "\",\"iter\":" + ITERATIONS
                    + ",\"salt\":\"" + b64Url(salt)
                    + "\",\"iv\":\"" + b64Url(iv)
                    + "\",\"ct\":\"" + b64Url(ciphertextAndTag) + "\"}";
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
            Arrays.fill(plainBytes, (byte) 0);
            if (keyBytes != null) Arrays.fill(keyBytes, (byte) 0);
        }
    }

    public static String decrypt(String packet, String passphrase) throws GeneralSecurityException {
        validatePassphrase(passphrase);
        Envelope envelope = parseEnvelope(packet);
        byte[] passwordBytes = passphrase.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = null;
        try {
            keyBytes = pbkdf2HmacSha256(passwordBytes, envelope.salt, ITERATIONS, KEY_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_BITS, envelope.iv));
            byte[] plaintext = cipher.doFinal(envelope.ciphertextAndTag);
            try {
                return new String(plaintext, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException ex) {
            throw new GeneralSecurityException("عبارت محرمانه اشتباه است یا بستهٔ پیام دست‌کاری شده است.", ex);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
            if (keyBytes != null) Arrays.fill(keyBytes, (byte) 0);
        }
    }

    public static boolean isValidEnvelope(String packet) {
        try {
            parseEnvelope(packet);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static void validatePassphrase(String passphrase) {
        if (passphrase == null || passphrase.length() < 12) {
            throw new IllegalArgumentException("عبارت محرمانه باید دست‌کم ۱۲ نویسه داشته باشد.");
        }
    }

    private static Envelope parseEnvelope(String packet) {
        if (packet == null || packet.length() > 12_000) {
            throw new IllegalArgumentException("بستهٔ پیام معتبر نیست.");
        }
        String trimmed = packet.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("قالب بسته معتبر نیست.");
        }
        String algorithm = stringField(trimmed, "alg");
        String saltText = stringField(trimmed, "salt");
        String ivText = stringField(trimmed, "iv");
        String ctText = stringField(trimmed, "ct");
        int version = intField(trimmed, "v");
        int iterations = intField(trimmed, "iter");
        if (version != 1 || !ALGORITHM.equals(algorithm) || iterations != ITERATIONS) {
            throw new IllegalArgumentException("نسخه یا الگوریتم بسته پشتیبانی نمی‌شود.");
        }
        byte[] salt = decodeB64Url(saltText);
        byte[] iv = decodeB64Url(ivText);
        byte[] ciphertext = decodeB64Url(ctText);
        if (salt.length != SALT_BYTES || iv.length != IV_BYTES
                || ciphertext.length < 16 || ciphertext.length > MAX_PLAINTEXT_BYTES + 16) {
            throw new IllegalArgumentException("طول داده‌های رمزنگاری معتبر نیست.");
        }
        return new Envelope(salt, iv, ciphertext);
    }

    private static String stringField(String json, String name) {
        Matcher matcher = STRING_FIELD.matcher(json);
        String found = null;
        while (matcher.find()) {
            if (name.equals(matcher.group(1))) {
                if (found != null) throw new IllegalArgumentException("فیلد تکراری در بسته وجود دارد.");
                found = matcher.group(2);
            }
        }
        if (found == null) throw new IllegalArgumentException("بسته ناقص است.");
        return found;
    }

    private static int intField(String json, String name) {
        Matcher matcher = INT_FIELD.matcher(json);
        Integer found = null;
        while (matcher.find()) {
            if (name.equals(matcher.group(1))) {
                if (found != null) throw new IllegalArgumentException("فیلد تکراری در بسته وجود دارد.");
                try {
                    found = Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("عدد بسته معتبر نیست.", ex);
                }
            }
        }
        if (found == null) throw new IllegalArgumentException("بسته ناقص است.");
        return found;
    }

    private static byte[] pbkdf2HmacSha256(byte[] password, byte[] salt, int rounds, int outputLength)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(password, "HmacSHA256"));
        byte[] firstBlock = Arrays.copyOf(salt, salt.length + 4);
        firstBlock[firstBlock.length - 1] = 1; // INT32_BE(1), one SHA-256 block is sufficient for 32 bytes.
        byte[] u = mac.doFinal(firstBlock);
        byte[] accumulator = u.clone();
        for (int round = 1; round < rounds; round++) {
            u = mac.doFinal(u);
            for (int i = 0; i < accumulator.length; i++) accumulator[i] ^= u[i];
        }
        byte[] result = Arrays.copyOf(accumulator, outputLength);
        Arrays.fill(firstBlock, (byte) 0);
        Arrays.fill(u, (byte) 0);
        Arrays.fill(accumulator, (byte) 0);
        return result;
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String b64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decodeB64Url(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("دادهٔ Base64 بسته معتبر نیست.");
        }
        return Base64.getUrlDecoder().decode(value);
    }

    private static final class Envelope {
        final byte[] salt;
        final byte[] iv;
        final byte[] ciphertextAndTag;

        Envelope(byte[] salt, byte[] iv, byte[] ciphertextAndTag) {
            this.salt = salt;
            this.iv = iv;
            this.ciphertextAndTag = ciphertextAndTag;
        }
    }
}
