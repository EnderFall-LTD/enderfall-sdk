package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.LevelResource;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.PlayerEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.event.TickEvent;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.platform.Capability;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.runtime.ImmutableCapabilitySet;
import uk.co.enderfall.sdk.runtime.PayloadReceiver;
import uk.co.enderfall.sdk.runtime.PlatformAdapter;
import uk.co.enderfall.sdk.runtime.RuntimeModContext;

final class FabricPlatformAdapter implements PlatformAdapter {
    private final String modId;
    private final PlatformInfo platformInfo = new FabricPlatformInfo();
    private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
            Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
            Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION));
    private final Map<ResourceId, Item> items = new LinkedHashMap<>();
    private final Map<ResourceId, Block> blocks = new LinkedHashMap<>();
    private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
    private volatile MinecraftServer server;
    private RuntimeModContext context;

    FabricPlatformAdapter(String modId) {
        this.modId = modId;
    }

    void attach(RuntimeModContext runtimeContext) {
        context = runtimeContext;
        installEvents();
    }

    @Override
    public PlatformInfo platformInfo() {
        return platformInfo;
    }

    @Override
    public CapabilitySet capabilities() {
        return capabilities;
    }

    @Override
    public Path commonConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path serverConfigDirectory() {
        MinecraftServer current = server;
        if (current == null) {
            throw new IllegalStateException("[" + modId + "] SERVER config is unavailable before a world starts");
        }
        return current.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
    }

    @Override
    public void registerItem(ResourceId id, ItemSpec spec) {
        Identifier identifier = identifier(id);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, identifier);
        Item.Properties properties = itemProperties(spec).setId(key);
        Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
        items.put(id, item);
    }

    @Override
    public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
        Identifier identifier = identifier(id);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, identifier);
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .setId(blockKey)
                .strength(spec.hardness(), spec.resistance())
                .friction(spec.friction())
                .jumpFactor(spec.jumpFactor())
                .lightLevel(state -> spec.luminance())
                .sound(sound(spec));
        if (spec.requiresTool()) {
            properties.requiresCorrectToolForDrops();
        }
        Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new Block(properties));
        blocks.put(id, block);
        if (blockItemSpec != null) {
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, identifier);
            Item.Properties itemProperties = itemProperties(blockItemSpec).setId(itemKey)
                    .useBlockDescriptionPrefix();
            Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProperties));
            items.put(id, item);
        }
    }

    @Override
    public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) {
        Item icon = requireItem(spec.icon().id());
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(Component.translatable(spec.titleTranslationKey()))
                .icon(() -> new ItemStack(icon))
                .displayItems((parameters, output) -> spec.entries().forEach(entry ->
                        output.accept(requireItem(entry.id()))))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, identifier(id), tab);
    }

    @Override
    public void registerCommand(CommandSpec command) {
        FabricCommandBridge.register(command);
    }

    @Override
    public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                PayloadReceiver receiver) {
        CustomPacketPayload.Type<FabricRawPayload> type = new CustomPacketPayload.Type<>(identifier(id));
        var codec = FabricRawPayload.codec(type, maximumBytes);
        if (direction == PacketDirection.SERVERBOUND || direction == PacketDirection.BIDIRECTIONAL) {
            PayloadTypeRegistry.serverboundPlay().register(type, codec);
            if (!ServerPlayNetworking.registerGlobalReceiver(type, (payload, networkContext) -> receiver.receive(
                    payload.bytes(), PacketDirection.SERVERBOUND,
                    Optional.of(networkContext.player().getUUID()),
                    reason -> networkContext.player().connection.disconnect(Component.literal(reason))))) {
                throw new IllegalStateException("Duplicate serverbound payload " + id);
            }
        }
        if (direction == PacketDirection.CLIENTBOUND || direction == PacketDirection.BIDIRECTIONAL) {
            PayloadTypeRegistry.clientboundPlay().register(type, codec);
            if (platformInfo.environment() == Environment.CLIENT) {
                FabricClientHooks.registerReceiver(type, receiver);
            }
        }
        payloads.put(id, new PayloadBinding(type, maximumBytes));
    }

    @Override
    public void sendToServer(ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            throw new IllegalStateException("Cannot send to a server from a dedicated server process");
        }
        FabricClientHooks.sendToServer(new FabricRawPayload(binding.type(), payload));
    }

    @Override
    public void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        ServerPlayer player = requireServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Unknown player " + playerId);
        }
        ServerPlayNetworking.send(player, new FabricRawPayload(binding.type(), payload));
    }

    @Override
    public void sendToAll(ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        for (ServerPlayer player : requireServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new FabricRawPayload(binding.type(), payload));
        }
    }

    @Override
    public java.util.Collection<java.util.UUID> connectedPlayers() {
        MinecraftServer current = server;
        return current == null ? java.util.List.of() : current.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID).toList();
    }

    private void installEvents() {
        AtomicLong tick = new AtomicLong();
        ServerLifecycleEvents.SERVER_STARTING.register(value -> {
            server = value;
            context.runtimeConfigs().loadServerConfigs();
            publishLifecycle(LifecycleEvent.Stage.SERVER_STARTING);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(value -> publishLifecycle(LifecycleEvent.Stage.SERVER_STARTED));
        ServerLifecycleEvents.SERVER_STOPPING.register(value -> publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPING));
        ServerLifecycleEvents.SERVER_STOPPED.register(value -> {
            publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPED);
            context.runtimeConfigs().unloadServerConfigs();
            server = null;
        });
        ServerTickEvents.START_SERVER_TICK.register(value -> context.runtimeEvents().publish(
                SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.START, tick.get())));
        ServerTickEvents.END_SERVER_TICK.register(value -> context.runtimeEvents().publish(
                SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.END, tick.getAndIncrement())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, value) -> {
            context.runtimeNetworking().connectionOpened(handler.player.getUUID());
            context.runtimeEvents().publish(SdkEvents.PLAYER,
                    new PlayerEvent(PlayerEvent.Action.JOIN, handler.player.getUUID(),
                            handler.player.getGameProfile().name()));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, value) -> {
            context.runtimeNetworking().connectionClosed(handler.player.getUUID());
            context.runtimeEvents().publish(SdkEvents.PLAYER,
                    new PlayerEvent(PlayerEvent.Action.LEAVE, handler.player.getUUID(),
                            handler.player.getGameProfile().name()));
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            Identifier target = BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem());
            return interaction(InteractionEvent.Kind.USE_ITEM, player.getUUID(), target);
        });
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            Identifier target = BuiltInRegistries.BLOCK.getKey(
                    level.getBlockState(hitResult.getBlockPos()).getBlock());
            return interaction(InteractionEvent.Kind.USE_BLOCK, player.getUUID(), target);
        });
        if (platformInfo.environment() == Environment.CLIENT) {
            FabricClientHooks.installLifecycle(context);
        }
    }

    private InteractionResult interaction(InteractionEvent.Kind kind, java.util.UUID playerId,
                                          Identifier target) {
        InteractionEvent event = new InteractionEvent(kind, playerId,
                ResourceId.of(target.getNamespace(), target.getPath()));
        context.runtimeEvents().publish(SdkEvents.INTERACTION, event);
        return event.cancelled() ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    private void publishLifecycle(LifecycleEvent.Stage stage) {
        context.runtimeEvents().publish(SdkEvents.LIFECYCLE, new LifecycleEvent(stage));
    }

    private Item requireItem(ResourceId id) {
        Item item = items.get(id);
        if (item == null) {
            throw new IllegalStateException("[" + modId + "] Creative tab references unknown item " + id);
        }
        return item;
    }

    private PayloadBinding requirePayload(ResourceId id, int length) {
        PayloadBinding binding = payloads.get(id);
        if (binding == null) {
            throw new IllegalStateException("[" + modId + "] Unknown payload " + id);
        }
        if (length > binding.maximumBytes()) {
            throw new IllegalArgumentException("Payload " + id + " exceeds " + binding.maximumBytes() + " bytes");
        }
        return binding;
    }

    private MinecraftServer requireServer() {
        MinecraftServer current = server;
        if (current == null) {
            throw new IllegalStateException("No Minecraft server is running");
        }
        return current;
    }

    private static Identifier identifier(ResourceId id) {
        return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
    }

    private static Item.Properties itemProperties(ItemSpec spec) {
        Item.Properties properties = new Item.Properties().stacksTo(spec.maxStackSize())
                .rarity(switch (spec.rarity()) {
                    case COMMON -> net.minecraft.world.item.Rarity.COMMON;
                    case UNCOMMON -> net.minecraft.world.item.Rarity.UNCOMMON;
                    case RARE -> net.minecraft.world.item.Rarity.RARE;
                    case EPIC -> net.minecraft.world.item.Rarity.EPIC;
                });
        if (spec.durability() > 0) {
            properties.durability(spec.durability());
        }
        if (spec.fireResistant()) {
            properties.fireResistant();
        }
        return properties;
    }

    private static SoundType sound(BlockSpec spec) {
        return switch (spec.sound()) {
            case STONE -> SoundType.STONE;
            case WOOD -> SoundType.WOOD;
            case METAL -> SoundType.METAL;
            case GLASS -> SoundType.GLASS;
            case WOOL -> SoundType.WOOL;
            case GRAVEL -> SoundType.GRAVEL;
            case SAND -> SoundType.SAND;
        };
    }

    private record PayloadBinding(CustomPacketPayload.Type<FabricRawPayload> type, int maximumBytes) {
    }
}
