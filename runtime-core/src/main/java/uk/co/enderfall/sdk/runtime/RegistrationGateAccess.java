package uk.co.enderfall.sdk.runtime;

/** Narrow public bridge used by internal subpackages without exposing registration state through the API. */
public interface RegistrationGateAccess {
    void requireOpen(String modId, String target);
}
