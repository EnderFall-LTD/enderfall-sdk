package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
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

final class NeoForgePlatformAdapter implements PlatformAdapter {
    private final String modId;
    private final IEventBus modBus;
    private final PlatformInfo platformInfo = new NeoForgePlatformInfo();
    private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
            Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
            Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION));
    private final DeferredRegister.Items itemRegister;
    private final DeferredRegister.Blocks blockRegister;
    private final DeferredRegister<CreativeModeTab> creativeTabRegister;
    private final Map<ResourceId, Supplier<? extends Item>> items = new LinkedHashMap<>();
    private final Map<ResourceId, Supplier<? extends Block>> blocks = new LinkedHashMap<>();
    private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
    private volatile MinecraftServer server;
    private RuntimeModContext context;

    NeoForgePlatformAdapter(String modId, IEventBus modBus) {
        this.modId = modId;
        this.modBus = modBus;
        itemRegister = DeferredRegister.createItems(modId);
        blockRegister = DeferredRegister.createBlocks(modId);
        creativeTabRegister = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, modId);
        itemRegister.register(modBus);
        blockRegister.register(modBus);
        creativeTabRegister.register(modBus);
    }

    void attach(RuntimeModContext runtimeContext) {
        context = runtimeContext;
        modBus.addListener(this::registerPayloadHandlers);
        installEvents();
        if (platformInfo.environment() == Environment.CLIENT) {
            NeoForgeClientHooks.install(modBus, context);
        }
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
        return FMLPaths.CONFIGDIR.get();
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
        var item = itemRegister.registerItem(id.path(), Item::new, () -> itemProperties(spec));
        items.put(id, item);
    }

    @Override
    public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
        var block = blockRegister.registerBlock(id.path(), Block::new, () -> blockProperties(spec));
        blocks.put(id, block);
        if (blockItemSpec != null) {
            var blockItem = itemRegister.registerSimpleBlockItem(
                    id.path(), block, () -> itemProperties(blockItemSpec));
            items.put(id, blockItem);
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

    @Override
    public void registerCommand(CommandSpec command) {
        NeoForgeCommandBridge.register(command);
    }

    @Override
    public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                PayloadReceiver receiver) {
        CustomPacketPayload.Type<NeoForgeRawPayload> type = new CustomPacketPayload.Type<>(identifier(id));
        if (payloads.putIfAbsent(id, new PayloadBinding(type, direction, maximumBytes, receiver)) != null) {
            throw new IllegalStateException("[" + modId + "] Duplicate payload " + id);
        }
    }

    @Override
    public void sendToServer(ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            throw new IllegalStateException("Cannot send to a server from a dedicated server process");
        }
        NeoForgeClientHooks.sendToServer(new NeoForgeRawPayload(binding.type(), payload));
    }

    @Override
    public void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        ServerPlayer player = requireServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Unknown player " + playerId);
        }
        PacketDistributor.sendToPlayer(player, new NeoForgeRawPayload(binding.type(), payload));
    }

    @Override
    public void sendToAll(ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        requireServer();
        PacketDistributor.sendToAllPlayers(new NeoForgeRawPayload(binding.type(), payload));
    }

    @Override
    public java.util.Collection<java.util.UUID> connectedPlayers() {
        MinecraftServer current = server;
        return current == null ? java.util.List.of() : current.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID).toList();
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("0.1").optional();
        for (PayloadBinding binding : payloads.values()) {
            var codec = NeoForgeRawPayload.codec(binding.type(), binding.maximumBytes());
            switch (binding.direction()) {
                case SERVERBOUND -> registrar.playToServer(binding.type(), codec,
                        (payload, networkContext) -> receive(binding, payload, networkContext));
                case CLIENTBOUND -> registrar.playToClient(binding.type(), codec,
                        (payload, networkContext) -> receive(binding, payload, networkContext));
                case BIDIRECTIONAL -> registrar.playBidirectional(binding.type(), codec,
                        (payload, networkContext) -> receive(binding, payload, networkContext));
            }
        }
    }

    private static void receive(PayloadBinding binding, NeoForgeRawPayload payload,
                                IPayloadContext networkContext) {
        PacketDirection direction = networkContext.flow() == PacketFlow.SERVERBOUND
                ? PacketDirection.SERVERBOUND : PacketDirection.CLIENTBOUND;
        Optional<java.util.UUID> playerId = networkContext.player() instanceof ServerPlayer player
                ? Optional.of(player.getUUID()) : Optional.empty();
        binding.receiver().receive(payload.bytes(), direction, playerId,
                reason -> networkContext.disconnect(Component.literal(reason)));
    }

    private void installEvents() {
        AtomicLong tick = new AtomicLong();
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
            server = event.getServer();
            context.runtimeConfigs().loadServerConfigs();
            publishLifecycle(LifecycleEvent.Stage.SERVER_STARTING);
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                publishLifecycle(LifecycleEvent.Stage.SERVER_STARTED));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
                publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPING));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPED);
            context.runtimeConfigs().unloadServerConfigs();
            server = null;
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> context.runtimeEvents().publish(
                SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.START, tick.get())));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> context.runtimeEvents().publish(
                SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.END, tick.getAndIncrement())));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            context.runtimeNetworking().connectionOpened(event.getEntity().getUUID());
            publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action.JOIN, event);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            context.runtimeNetworking().connectionClosed(event.getEntity().getUUID());
            publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action.LEAVE, event);
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
            Identifier target = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
            if (interaction(InteractionEvent.Kind.USE_ITEM, event.getEntity().getUUID(), target)) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            Identifier target = BuiltInRegistries.BLOCK.getKey(
                    event.getLevel().getBlockState(event.getPos()).getBlock());
            if (interaction(InteractionEvent.Kind.USE_BLOCK, event.getEntity().getUUID(), target)) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });
    }

    private void publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action action,
                               PlayerEvent event) {
        context.runtimeEvents().publish(SdkEvents.PLAYER,
                new uk.co.enderfall.sdk.api.event.PlayerEvent(action, event.getEntity().getUUID(),
                        event.getEntity().getGameProfile().name()));
    }

    private boolean interaction(InteractionEvent.Kind kind, java.util.UUID playerId,
                                Identifier target) {
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

    private record PayloadBinding(CustomPacketPayload.Type<NeoForgeRawPayload> type,
                                  PacketDirection direction, int maximumBytes,
                                  PayloadReceiver receiver) {
    }
}
