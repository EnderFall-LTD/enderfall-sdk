package uk.co.enderfall.sdk.api.client.ui;

/** Operations safe to request from a portable screen callback. */
public interface ClientScreenContext {
    int width();

    int height();

    void close();

    void open(ClientScreenRef screen);
}
