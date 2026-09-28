package uk.co.enderfall.sdk.runtime;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import uk.co.enderfall.sdk.api.client.command.ClientCommandManager;
import uk.co.enderfall.sdk.api.command.ArgumentType;
import uk.co.enderfall.sdk.api.command.CommandSpec;

/** Registration-time validation for commands owned by a portable client entrypoint. */
final class DefaultClientCommandManager implements ClientCommandManager {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGate gate;
    private final Set<String> names = new HashSet<>();

    DefaultClientCommandManager(String modId, String target, PlatformAdapter adapter, RegistrationGate gate) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.target = Objects.requireNonNull(target, "target");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public void register(CommandSpec command) {
        Objects.requireNonNull(command, "command");
        gate.requireOpen(modId, target);
        if (command.permissionLevel() != 0) {
            throw failure(command, "client commands cannot require server permission levels");
        }
        if (command.arguments().stream().anyMatch(argument -> argument.type() == ArgumentType.PLAYER)) {
            throw failure(command, "the player argument is server-only");
        }
        if (!names.add(command.name())) throw failure(command, "duplicate client command name");
        try {
            adapter.registerClientCommand(command);
        } catch (RuntimeException exception) {
            names.remove(command.name());
            throw exception;
        }
    }

    private IllegalStateException failure(CommandSpec command, String reason) {
        return new IllegalStateException("[" + modId + "] Client command '" + command.name()
                + "' is invalid on " + target + ": " + reason);
    }
}
