package uk.co.enderfall.sdk.runtime;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ClientModContext;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandManager;
import uk.co.enderfall.sdk.api.config.ConfigManager;
import uk.co.enderfall.sdk.api.data.DataGenerationManager;
import uk.co.enderfall.sdk.api.event.EventBus;
import uk.co.enderfall.sdk.api.logging.ModLogger;
import uk.co.enderfall.sdk.api.network.NetworkManager;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockRegistrar;
import uk.co.enderfall.sdk.api.registry.CreativeTabRegistrar;
import uk.co.enderfall.sdk.api.registry.ItemRegistrar;
import uk.co.enderfall.sdk.runtime.config.DefaultConfigManager;
import uk.co.enderfall.sdk.runtime.network.DefaultNetworkManager;

/** Concrete per-mod service container used by target bootstraps. */
public final class RuntimeModContext implements ModContext, ClientModContext {
    private final String modId;
    private final PlatformAdapter adapter;
    private final RegistrationGate gate = new RegistrationGate();
    private final ModLogger logger;
    private final DefaultEventBus events;
    private final DefaultItemRegistrar items;
    private final DefaultBlockRegistrar blocks;
    private final DefaultCreativeTabRegistrar creativeTabs;
    private final DefaultCommandManager commands;
    private final DefaultConfigManager configs;
    private final DefaultNetworkManager networking;
    private final DefaultDataGenerationManager dataGeneration;

    public RuntimeModContext(String modId, PlatformAdapter adapter) {
        this.modId = validateModId(modId);
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        logger = new SystemModLogger(modId);
        events = new DefaultEventBus(logger);
        String target = adapter.platformInfo().targetId();
        items = new DefaultItemRegistrar(modId, target, adapter, gate);
        blocks = new DefaultBlockRegistrar(modId, target, adapter, gate, items);
        creativeTabs = new DefaultCreativeTabRegistrar(modId, target, adapter, gate);
        commands = new DefaultCommandManager(modId, target, adapter, gate);
        configs = new DefaultConfigManager(modId, target, adapter, gate, logger);
        networking = new DefaultNetworkManager(modId, target, adapter, gate, logger);
        dataGeneration = new DefaultDataGenerationManager(gate, modId, target);
    }

    public void freezeRegistrations() {
        gate.freeze();
    }

    public boolean registrationsFrozen() {
        return gate.frozen();
    }

    public DefaultEventBus runtimeEvents() {
        return events;
    }

    public DefaultNetworkManager runtimeNetworking() {
        return networking;
    }

    public DefaultConfigManager runtimeConfigs() {
        return configs;
    }

    public DefaultDataGenerationManager runtimeDataGeneration() {
        return dataGeneration;
    }

    @Override
    public String modId() {
        return modId;
    }

    @Override
    public ResourceId id(String path) {
        return ResourceId.of(modId, path);
    }

    @Override
    public PlatformInfo platform() {
        return adapter.platformInfo();
    }

    @Override
    public CapabilitySet capabilities() {
        return adapter.capabilities();
    }

    @Override
    public ModLogger logger() {
        return logger;
    }

    @Override
    public ItemRegistrar items() {
        return items;
    }

    @Override
    public BlockRegistrar blocks() {
        return blocks;
    }

    @Override
    public CreativeTabRegistrar creativeTabs() {
        return creativeTabs;
    }

    @Override
    public EventBus events() {
        return events;
    }

    @Override
    public CommandManager commands() {
        return commands;
    }

    @Override
    public ConfigManager configs() {
        return configs;
    }

    @Override
    public NetworkManager networking() {
        return networking;
    }

    @Override
    public DataGenerationManager dataGeneration() {
        return dataGeneration;
    }

    private static String validateModId(String value) {
        Objects.requireNonNull(value, "modId");
        if (!value.matches("[a-z][a-z0-9_]{1,63}")) {
            throw new IllegalArgumentException("Invalid mod ID: " + value);
        }
        return value;
    }
}
