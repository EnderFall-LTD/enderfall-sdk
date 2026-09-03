package uk.co.enderfall.sdk.api.command;

import java.util.Objects;

/** Description of a portable command argument. */
public record CommandArgument<T>(String name, ArgumentType type, boolean optional) {
    public CommandArgument {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (!name.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid command argument name: " + name);
        }
    }
}
