package uk.co.enderfall.sdk.runtime.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import uk.co.enderfall.sdk.api.config.ConfigHandle;
import uk.co.enderfall.sdk.api.config.ConfigManager;
import uk.co.enderfall.sdk.api.config.ConfigScope;
import uk.co.enderfall.sdk.api.config.ConfigSpec;
import uk.co.enderfall.sdk.api.logging.ModLogger;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.runtime.PlatformAdapter;
import uk.co.enderfall.sdk.runtime.RegistrationGateAccess;

public final class DefaultConfigManager implements ConfigManager {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    private final ModLogger logger;
    private final Set<String> registeredNames = new HashSet<>();
    private final List<PendingServerConfig> serverConfigs = new ArrayList<>();

    public DefaultConfigManager(String modId, String target, PlatformAdapter adapter,
                                RegistrationGateAccess gate, ModLogger logger) {
        this.modId = modId;
        this.target = target;
        this.adapter = adapter;
        this.gate = gate;
        this.logger = logger;
    }

    @Override
    public ConfigHandle register(String name, ConfigScope scope, ConfigSpec spec) {
        gate.requireOpen(modId, target);
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(spec, "spec");
        if (!name.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid config name: " + name);
        }
        String identity = scope.name() + ':' + name;
        if (!registeredNames.add(identity)) {
            throw new IllegalStateException("[" + modId + "] Duplicate " + scope + " config " + name);
        }
        if (scope == ConfigScope.CLIENT && adapter.platformInfo().environment() == Environment.DEDICATED_SERVER) {
            throw new IllegalStateException("[" + modId + "] Client config " + name
                    + " cannot be loaded on a dedicated server");
        }
        String suffix = switch (scope) {
            case COMMON -> "common";
            case CLIENT -> "client";
            case SERVER -> "server";
        };
        String fileName = modId + '-' + name + '-' + suffix + ".toml";
        if (scope == ConfigScope.SERVER) {
            DefaultConfigHandle handle = DefaultConfigHandle.pending(
                    "[" + modId + "] SERVER config " + name,
                    TomlConfigFile.defaults(spec));
            serverConfigs.add(new PendingServerConfig(fileName, spec, handle));
            return handle;
        }
        Path path = adapter.commonConfigDirectory().resolve(fileName);
        return load(path, spec);
    }

    /** Loads every registered world-specific config once a server world path exists. */
    public void loadServerConfigs() {
        Path directory = adapter.serverConfigDirectory();
        for (PendingServerConfig pending : serverConfigs) {
            DefaultConfigHandle loaded = load(directory.resolve(pending.fileName()), pending.spec());
            pending.handle().replace(loaded);
        }
    }

    /** Drops values from the previous world and restores declared defaults. */
    public void unloadServerConfigs() {
        for (PendingServerConfig pending : serverConfigs) {
            pending.handle().reset(TomlConfigFile.defaults(pending.spec()));
        }
    }

    private DefaultConfigHandle load(Path path, ConfigSpec spec) {
        try {
            return TomlConfigFile.load(path, spec, logger);
        } catch (IOException exception) {
            throw new IllegalStateException("[" + modId + "] Cannot load config " + path + " on " + target,
                    exception);
        }
    }

    private record PendingServerConfig(String fileName, ConfigSpec spec, DefaultConfigHandle handle) {
    }
}
