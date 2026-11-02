package ir.tahmuresi.payamnegar;
import java.nio.file.Files;
import java.nio.file.Path;
public final class DecryptPacket {
  public static void main(String[] args) throws Exception {
    String packet = Files.readString(Path.of(args[0]));
    String plain = CryptoEngine.decrypt(packet, "یک عبارت محرمانهٔ قوی برای دو نفر");
    if (!"پیام ساخته‌شده با WebCrypto".equals(plain)) throw new AssertionError(plain);
    System.out.println("PASS: Java decrypted WebCrypto envelope: " + plain);
  }
}
