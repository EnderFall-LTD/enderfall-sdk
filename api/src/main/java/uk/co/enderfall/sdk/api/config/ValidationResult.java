package uk.co.enderfall.sdk.api.config;

import java.util.Objects;

public record ValidationResult(boolean valid, String message) {
    public ValidationResult {
        Objects.requireNonNull(message, "message");
        if (valid && !message.isEmpty()) {
            throw new IllegalArgumentException("A valid result cannot contain an error message");
        }
        if (!valid && message.isBlank()) {
            throw new IllegalArgumentException("An invalid result requires an error message");
        }
    }

    public static ValidationResult success() {
        return new ValidationResult(true, "");
    }

    public static ValidationResult failure(String message) {
        return new ValidationResult(false, message);
    }
}
