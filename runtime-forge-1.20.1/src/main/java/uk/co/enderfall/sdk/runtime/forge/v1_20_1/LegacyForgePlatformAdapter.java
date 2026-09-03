package uk.co.enderfall.sdk.runtime.forge.v1_20_1;

import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
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

final class LegacyForgePlatformAdapter implements PlatformAdapter {
    private static final String CHANNEL_VERSION = "0";
    private final String modId;
    private final IEventBus modBus;
    private final PlatformInfo platformInfo = new LegacyForgePlatformInfo();
    private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
            Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
            Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION));
    private final DeferredRegister<Item> itemRegister;
    private final DeferredRegister<Block> blockRegister;
    private final DeferredRegister<CreativeModeTab> creativeTabRegister;
    private final Map<ResourceId, RegistryObject<? extends Item>> items = new LinkedHashMap<>();
    private final Map<ResourceId, RegistryObject<? extends Block>> blocks = new LinkedHashMap<>();
    private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
    private volatile MinecraftServer server;
    private RuntimeModContext context;

    LegacyForgePlatformAdapter(String modId, IEventBus modBus) {
        this.modId = modId;
        this.modBus = modBus;
        itemRegister = DeferredRegister.create(ForgeRegistries.ITEMS, modId);
        blockRegister = DeferredRegister.create(ForgeRegistries.BLOCKS, modId);
        creativeTabRegister = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, modId);
        itemRegister.register(modBus);
        blockRegister.register(modBus);
        creativeTabRegister.register(modBus);
    }

    void attach(RuntimeModContext runtimeContext) {
        context = runtimeContext;
        installEvents();
        if (platformInfo.environment() == Environment.CLIENT) {
            LegacyForgeClientHooks.install(modBus, context);
        }
    }

    @Override public PlatformInfo platformInfo() { return platformInfo; }
    @Override public CapabilitySet capabilities() { return capabilities; }
    @Override public Path commonConfigDirectory() { return FMLPaths.CONFIGDIR.get(); }

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
        RegistryObject<Item> item = itemRegister.register(id.path(), () -> new Item(itemProperties(spec)));
        items.put(id, item);
    }

    @Override
    public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
        RegistryObject<Block> block = blockRegister.register(id.path(), () -> new Block(blockProperties(spec)));
        blocks.put(id, block);
        if (blockItemSpec != null) {
            RegistryObject<Item> item = itemRegister.register(id.path(),
                    () -> new BlockItem(block.get(), itemProperties(blockItemSpec)));
            items.put(id, item);
        }
    }

    @Override
    public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) {
        creativeTabRegister.register(id.path(), () -> CreativeModeTab.builder()
                .title(Component.translatable(spec.titleTranslationKey()))
                .icon(() -> new ItemStack(requireItem(spec.icon().id())))
                .displayItems((parameters, output) -> spec.entries().forEach(entry ->
                        output.accept(requireItem(entry.id()))))
                .build());
    }

    @Override public void registerCommand(CommandSpec command) { LegacyForgeCommandBridge.register(command); }

    @Override
    public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                PayloadReceiver receiver) {
        ResourceLocation location = location(id);
        Predicate<String> accepted = NetworkRegistry.acceptMissingOr(CHANNEL_VERSION);
        EventNetworkChannel channel = NetworkRegistry.ChannelBuilder.named(location)
                .networkProtocolVersion(() -> CHANNEL_VERSION)
                .clientAcceptedVersions(accepted)
                .serverAcceptedVersions(accepted)
                .eventNetworkChannel();
        PayloadBinding binding = new PayloadBinding(location, direction, maximumBytes, receiver, channel);
        if (payloads.putIfAbsent(id, binding) != null) {
            throw new IllegalStateException("[" + modId + "] Duplicate payload " + id);
        }
        if (direction == PacketDirection.SERVERBOUND || direction == PacketDirection.BIDIRECTIONAL) {
            channel.addListener((NetworkEvent.ServerCustomPayloadEvent event) -> receive(binding, event,
                    PacketDirection.SERVERBOUND));
        }
        if (direction == PacketDirection.CLIENTBOUND || direction == PacketDirection.BIDIRECTIONAL) {
            channel.addListener((NetworkEvent.ClientCustomPayloadEvent event) -> receive(binding, event,
                    PacketDirection.CLIENTBOUND));
        }
    }

    @Override
    public void sendToServer(ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        if (FMLEnvironment.dist != Dist.CLIENT) {
            throw new IllegalStateException("Cannot send to a server from a dedicated server process");
        }
        LegacyForgeClientHooks.sendToServer(binding.location(), payload);
    }

    @Override
    public void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        ServerPlayer player = requireServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Unknown player " + playerId);
        }
        player.connection.send(clientbound(binding.location(), payload));
    }

    @Override
    public void sendToAll(ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        for (ServerPlayer player : requireServer().getPlayerList().getPlayers()) {
            player.connection.send(clientbound(binding.location(), payload));
        }
    }

    @Override
    public java.util.Collection<java.util.UUID> connectedPlayers() {
        MinecraftServer current = server;
        return current == null ? java.util.List.of() : current.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID).toList();
    }

    private void receive(PayloadBinding binding, NetworkEvent event, PacketDirection direction) {
        FriendlyByteBuf buffer = event.getPayload();
        int length = buffer.readableBytes();
        NetworkEvent.Context networkContext = event.getSource().get();
        if (length > binding.maximumBytes()) {
            networkContext.getNetworkManager().disconnect(Component.literal(
                    "Payload " + binding.location() + " exceeds " + binding.maximumBytes() + " bytes"));
            networkContext.setPacketHandled(true);
            return;
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        Optional<java.util.UUID> playerId = Optional.ofNullable(networkContext.getSender())
                .map(ServerPlayer::getUUID);
        networkContext.enqueueWork(() -> binding.receiver().receive(bytes, direction, playerId,
                reason -> networkContext.getNetworkManager().disconnect(Component.literal(reason))));
        networkContext.setPacketHandled(true);
    }

    private void installEvents() {
        AtomicLong tick = new AtomicLong();
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
            server = event.getServer();
            context.runtimeConfigs().loadServerConfigs();
            publishLifecycle(LifecycleEvent.Stage.SERVER_STARTING);
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                publishLifecycle(LifecycleEvent.Stage.SERVER_STARTED));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
                publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPING));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPED);
            context.runtimeConfigs().unloadServerConfigs();
            server = null;
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            uk.co.enderfall.sdk.api.event.TickEvent.Phase phase = event.phase == TickEvent.Phase.START
                    ? uk.co.enderfall.sdk.api.event.TickEvent.Phase.START
                    : uk.co.enderfall.sdk.api.event.TickEvent.Phase.END;
            long current = tick.get();
            context.runtimeEvents().publish(SdkEvents.TICK,
                    new uk.co.enderfall.sdk.api.event.TickEvent(
                            uk.co.enderfall.sdk.api.event.TickEvent.Side.SERVER, phase, current));
            if (event.phase == TickEvent.Phase.END) {
                tick.incrementAndGet();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            context.runtimeNetworking().connectionOpened(event.getEntity().getUUID());
            publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action.JOIN, event);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            context.runtimeNetworking().connectionClosed(event.getEntity().getUUID());
            publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action.LEAVE, event);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
            ResourceLocation target = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
            if (interaction(InteractionEvent.Kind.USE_ITEM, event.getEntity().getUUID(), target)) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            ResourceLocation target = ForgeRegistries.BLOCKS.getKey(
                    event.getLevel().getBlockState(event.getPos()).getBlock());
            if (interaction(InteractionEvent.Kind.USE_BLOCK, event.getEntity().getUUID(), target)) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });
    }

    private void publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action action, PlayerEvent event) {
        context.runtimeEvents().publish(SdkEvents.PLAYER,
                new uk.co.enderfall.sdk.api.event.PlayerEvent(action, event.getEntity().getUUID(),
                        event.getEntity().getGameProfile().getName()));
    }

    private boolean interaction(InteractionEvent.Kind kind, java.util.UUID playerId, ResourceLocation target) {
        if (target == null) {
            return false;
        }
        InteractionEvent event = new InteractionEvent(kind, playerId,
                ResourceId.of(target.getNamespace(), target.getPath()));
        context.runtimeEvents().publish(SdkEvents.INTERACTION, event);
        return event.cancelled();
    }

    private void publishLifecycle(LifecycleEvent.Stage stage) {
        context.runtimeEvents().publish(SdkEvents.LIFECYCLE, new LifecycleEvent(stage));
    }

    private Item requireItem(ResourceId id) {
        Supplier<? extends Item> item = items.get(id);
        if (item == null) {
            throw new IllegalStateException("[" + modId + "] Creative tab references unknown item " + id);
        }
        return item.get();
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

    private static ClientboundCustomPayloadPacket clientbound(ResourceLocation location, byte[] payload) {
        return new ClientboundCustomPayloadPacket(location,
                new FriendlyByteBuf(Unpooled.wrappedBuffer(payload)));
    }

    private static ResourceLocation location(ResourceId id) {
        ResourceLocation location = ResourceLocation.tryBuild(id.namespace(), id.path());
        if (location == null) {
            throw new IllegalArgumentException("Invalid resource ID " + id);
        }
        return location;
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

    private static BlockBehaviour.Properties blockProperties(BlockSpec spec) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .strength(spec.hardness(), spec.resistance())
                .friction(spec.friction())
                .jumpFactor(spec.jumpFactor())
                .lightLevel(state -> spec.luminance())
                .sound(sound(spec));
        if (spec.requiresTool()) {
            properties.requiresCorrectToolForDrops();
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

    private record PayloadBinding(ResourceLocation location, PacketDirection direction,
                                  int maximumBytes, PayloadReceiver receiver,
                                  EventNetworkChannel channel) {
    }
}
