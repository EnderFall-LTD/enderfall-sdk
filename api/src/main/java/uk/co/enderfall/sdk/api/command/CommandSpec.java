package uk.co.enderfall.sdk.api.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CommandSpec {
    private final String name;
    private final String description;
    private final int permissionLevel;
    private final List<CommandArgument<?>> arguments;
    private final CommandExecutor executor;
    private final SuggestionProvider suggestions;

    private CommandSpec(Builder builder) {
        name = builder.name;
        description = builder.description;
        permissionLevel = builder.permissionLevel;
        arguments = List.copyOf(builder.arguments);
        executor = Objects.requireNonNull(builder.executor, "executor");
        suggestions = builder.suggestions;
        boolean optionalSeen = false;
        for (CommandArgument<?> argument : arguments) {
            optionalSeen |= argument.optional();
            if (optionalSeen && !argument.optional()) {
                throw new IllegalArgumentException("Required arguments cannot follow optional arguments");
            }
        }
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int permissionLevel() {
        return permissionLevel;
    }

    public List<CommandArgument<?>> arguments() {
        return arguments;
    }

    public CommandExecutor executor() {
        return executor;
    }

    public SuggestionProvider suggestions() {
        return suggestions;
    }

    public static final class Builder {
        private final String name;
        private String description = "";
        private int permissionLevel;
        private final List<CommandArgument<?>> arguments = new ArrayList<>();
        private CommandExecutor executor;
        private SuggestionProvider suggestions = (context, remaining) -> List.of();

        private Builder(String name) {
            Objects.requireNonNull(name, "name");
            if (!name.matches("[a-z][a-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid command name: " + name);
            }
            this.name = name;
        }

        public Builder description(String value) {
            description = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder permissionLevel(int value) {
            if (value < 0 || value > 4) {
                throw new IllegalArgumentException("permissionLevel must be between 0 and 4");
            }
            permissionLevel = value;
            return this;
        }

        public Builder argument(CommandArgument<?> value) {
            arguments.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder executes(CommandExecutor value) {
            executor = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder suggests(SuggestionProvider value) {
            suggestions = Objects.requireNonNull(value, "value");
            return this;
        }

        public CommandSpec build() {
            return new CommandSpec(this);
        }
    }
}
