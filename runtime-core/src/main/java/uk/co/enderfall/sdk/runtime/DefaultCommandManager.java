package uk.co.enderfall.sdk.runtime;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import uk.co.enderfall.sdk.api.command.CommandManager;
import uk.co.enderfall.sdk.api.command.CommandSpec;

final class DefaultCommandManager implements CommandManager {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGate gate;
    private final Set<String> commands = new HashSet<>();

    DefaultCommandManager(String modId, String target, PlatformAdapter adapter, RegistrationGate gate) {
        this.modId = modId;
        this.target = target;
        this.adapter = adapter;
        this.gate = gate;
    }

    @Override
    public void register(CommandSpec command) {
        gate.requireOpen(modId, target);
        Objects.requireNonNull(command, "command");
        if (!commands.add(command.name())) {
            throw new IllegalStateException("[" + modId + "] Duplicate command /" + command.name() + " on " + target);
        }
        adapter.registerCommand(command);
    }
}
