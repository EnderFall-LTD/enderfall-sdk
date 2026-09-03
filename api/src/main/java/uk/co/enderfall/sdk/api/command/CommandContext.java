package uk.co.enderfall.sdk.api.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CommandContext {
    String sourceName();

    Optional<UUID> sourcePlayerId();

    Map<String, Object> arguments();

    default <T> T argument(CommandArgument<T> argument) {
        Object value = arguments().get(argument.name());
        if (value == null) {
            throw new IllegalArgumentException("Missing command argument: " + argument.name());
        }
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        return typed;
    }

    void reply(String message);

    void replyTranslation(String translationKey, Object... arguments);
}
