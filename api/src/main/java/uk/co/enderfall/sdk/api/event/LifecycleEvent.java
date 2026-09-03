package uk.co.enderfall.sdk.api.event;

public record LifecycleEvent(Stage stage) {
    public enum Stage {
        SERVER_STARTING,
        SERVER_STARTED,
        SERVER_STOPPING,
        SERVER_STOPPED,
        CLIENT_STARTED,
        CLIENT_STOPPING
    }
}
