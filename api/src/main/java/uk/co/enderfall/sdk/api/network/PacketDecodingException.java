package uk.co.enderfall.sdk.api.network;

public final class PacketDecodingException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public PacketDecodingException(String message) {
        super(message);
    }

    public PacketDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
