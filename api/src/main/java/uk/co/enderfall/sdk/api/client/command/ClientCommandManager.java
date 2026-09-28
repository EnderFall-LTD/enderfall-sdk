package uk.co.enderfall.sdk.api.client.command;

import uk.co.enderfall.sdk.api.command.CommandSpec;

/** Registers commands that execute entirely on the physical client. */
public interface ClientCommandManager {
    /**
     * Registers a portable client command.
     *
     * <p>Client commands must use permission level zero and cannot use the server-only
     * player argument. All other portable command arguments and suggestions are supported.</p>
     */
    void register(CommandSpec command);
}
