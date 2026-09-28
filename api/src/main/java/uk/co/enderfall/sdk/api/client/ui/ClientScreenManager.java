package uk.co.enderfall.sdk.api.client.ui;

import java.util.function.Supplier;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Client initialization service for registering and opening portable screens. */
@Experimental("General portable client screens")
public interface ClientScreenManager {
    ClientScreenRef register(ResourceId id, ClientScreenSpec spec,
                             Supplier<? extends PortableClientScreen> factory);

    void open(ClientScreenRef screen);

    void close();
}
