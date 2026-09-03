package uk.co.enderfall.sdk.runtime;

@FunctionalInterface
public interface DisconnectHandler {
    void disconnect(String reason);
}
