package uk.co.enderfall.sdk.api.command;

public final class Arguments {
    private Arguments() {
    }

    public static CommandArgument<String> word(String name) {
        return new CommandArgument<>(name, ArgumentType.WORD, false);
    }

    public static CommandArgument<String> string(String name) {
        return new CommandArgument<>(name, ArgumentType.STRING, false);
    }

    public static CommandArgument<String> greedyString(String name) {
        return new CommandArgument<>(name, ArgumentType.GREEDY_STRING, false);
    }

    public static CommandArgument<Integer> integer(String name) {
        return new CommandArgument<>(name, ArgumentType.INTEGER, false);
    }

    public static CommandArgument<Boolean> bool(String name) {
        return new CommandArgument<>(name, ArgumentType.BOOLEAN, false);
    }

    public static CommandArgument<String> player(String name) {
        return new CommandArgument<>(name, ArgumentType.PLAYER, false);
    }

    public static <T> CommandArgument<T> optional(CommandArgument<T> argument) {
        return new CommandArgument<>(argument.name(), argument.type(), true);
    }
}
