package uk.co.enderfall.sdk.runtime;

import java.nio.file.Path;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

/** Internal boundary implemented once by each Minecraft/loader target. */
public interface PlatformAdapter {
    PlatformInfo platformInfo();

    CapabilitySet capabilities();

    Path commonConfigDirectory();

    Path serverConfigDirectory();

    void registerItem(ResourceId id, ItemSpec spec);

    void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec);

    void registerCreativeTab(ResourceId id, CreativeTabSpec spec);

    void registerCommand(CommandSpec command);

    void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes, PayloadReceiver receiver);

    void sendToServer(ResourceId id, byte[] payload);

    void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload);

    void sendToAll(ResourceId id, byte[] payload);
}
