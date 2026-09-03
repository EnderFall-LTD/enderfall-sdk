package uk.co.enderfall.sdk.api.event;

public record TickEvent(Side side, Phase phase, long tick) {
    public enum Side {
        CLIENT,
        SERVER
    }

    public enum Phase {
        START,
        END
    }
}
