package uk.co.enderfall.sdk.api.command;

@FunctionalInterface
public interface CommandExecutor {
    int execute(CommandContext context) throws Exception;
}
