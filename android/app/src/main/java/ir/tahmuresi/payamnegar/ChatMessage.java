package ir.tahmuresi.payamnegar;

public final class ChatMessage {
    public final String messageId;
    public final String direction;
    public final String phoneNumber;
    public final String encryptedPacket;
    public final long createdAt;
    public final String status;
    public final int totalParts;

    public ChatMessage(String messageId, String direction, String phoneNumber,
                       String encryptedPacket, long createdAt, String status, int totalParts) {
        this.messageId = messageId;
        this.direction = direction;
        this.phoneNumber = phoneNumber;
        this.encryptedPacket = encryptedPacket;
        this.createdAt = createdAt;
        this.status = status;
        this.totalParts = totalParts;
    }
}
