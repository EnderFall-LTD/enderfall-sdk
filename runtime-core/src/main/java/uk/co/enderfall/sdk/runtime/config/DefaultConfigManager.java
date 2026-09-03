package uk.co.enderfall.sdk.runtime.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
        Path directory = scope == ConfigScope.SERVER
                ? adapter.serverConfigDirectory()
                : adapter.commonConfigDirectory();
        String suffix = switch (scope) {
            case COMMON -> "common";
            case CLIENT -> "client";
            case SERVER -> "server";
        };
        Path path = directory.resolve(modId + '-' + name + '-' + suffix + ".toml");
        try {
            return TomlConfigFile.load(path, spec, logger);
        } catch (IOException exception) {
            throw new IllegalStateException("[" + modId + "] Cannot load config " + path + " on " + target,
                    exception);
        }
    }
}
