package uk.co.enderfall.sdk.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

final class RegistrationGate implements RegistrationGateAccess {
    private final AtomicBoolean frozen = new AtomicBoolean();

    @Override
    public void requireOpen(String modId, String target) {
        if (frozen.get()) {
            throw new IllegalStateException("[" + modId + "] Registration is closed on " + target);
        }
    }

    void freeze() {
        frozen.set(true);
    }

    boolean frozen() {
        return frozen.get();
    }
}
