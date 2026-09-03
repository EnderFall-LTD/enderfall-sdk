package uk.co.enderfall.sdk.api.event;

/** A removable event registration. Closing more than once is harmless. */
public interface Subscription extends AutoCloseable {
    boolean active();

    @Override
    void close();
}
