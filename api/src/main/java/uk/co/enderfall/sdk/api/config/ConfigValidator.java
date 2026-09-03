package uk.co.enderfall.sdk.api.config;

import java.util.Objects;
import java.util.function.Predicate;

@FunctionalInterface
public interface ConfigValidator<T> {
    ValidationResult validate(T value);

    static <T> ConfigValidator<T> acceptingAll() {
        return value -> ValidationResult.success();
    }

    static <T> ConfigValidator<T> matching(Predicate<T> predicate, String failureMessage) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(failureMessage, "failureMessage");
        return value -> predicate.test(value) ? ValidationResult.success() : ValidationResult.failure(failureMessage);
    }
}
