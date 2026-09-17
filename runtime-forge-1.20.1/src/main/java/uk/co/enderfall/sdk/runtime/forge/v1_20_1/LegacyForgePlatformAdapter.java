package uk.co.enderfall.sdk.runtime.forge.v1_20_1;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
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
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.gameplay.PlayerSnapshot;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.platform.Capability;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;
import uk.co.enderfall.sdk.runtime.ImmutableCapabilitySet;
import uk.co.enderfall.sdk.runtime.PayloadReceiver;
import uk.co.enderfall.sdk.runtime.PlatformAdapter;
import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;
import uk.co.enderfall.sdk.runtime.RuntimeModContext;

final class LegacyForgePlatformAdapter implements PlatformAdapter {
    private static final String CHANNEL_VERSION = "0";
    private final String modId;
    private final IEventBus modBus;
    private final PlatformInfo platformInfo = new LegacyForgePlatformInfo();
    private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
            Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
            Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION,
            Capability.PLAYER_ACTIONS, Capability.SYNCHRONIZED_SCREENS,
            Capability.CUSTOM_RECIPES, Capability.CONTAINER_MENUS));
    private final DeferredRegister<Item> itemRegister;
    private final DeferredRegister<Block> blockRegister;
    private final DeferredRegister<CreativeModeTab> creativeTabRegister;
    private final DeferredRegister<RecipeType<?>> recipeTypeRegister;
    private final DeferredRegister<RecipeSerializer<?>> recipeSerializerRegister;
    private final DeferredRegister<MenuType<?>> menuRegister;
    private final Map<ResourceId, RegistryObject<? extends Item>> items = new LinkedHashMap<>();
    private final Map<ResourceId, RegistryObject<? extends Block>> blocks = new LinkedHashMap<>();
    private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
    private final Map<ResourceId, LegacyForgeRecipeBinding> recipeTypes = new LinkedHashMap<>();
    private final Map<ResourceId, LegacyForgeWorkbenchBinding> workbenches = new LinkedHashMap<>();
    private volatile MinecraftServer server;
    private RuntimeModContext context;

    LegacyForgePlatformAdapter(String modId, IEventBus modBus) {
        this.modId = modId;
        this.modBus = modBus;
        itemRegister = DeferredRegister.create(ForgeRegistries.ITEMS, modId);
        blockRegister = DeferredRegister.create(ForgeRegistries.BLOCKS, modId);
        creativeTabRegister = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, modId);
        recipeTypeRegister = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, modId);
        recipeSerializerRegister = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, modId);
        menuRegister = DeferredRegister.create(ForgeRegistries.MENU_TYPES, modId);
        itemRegister.register(modBus);
        blockRegister.register(modBus);
        creativeTabRegister.register(modBus);
        recipeTypeRegister.register(modBus);
        recipeSerializerRegister.register(modBus);
        menuRegister.register(modBus);
    }

    void attach(RuntimeModContext runtimeContext) {
        context = runtimeContext;
        installEvents();
        if (platformInfo.environment() == Environment.CLIENT) {
            LegacyForgeClientHooks.install(modBus, context, workbenches.values());
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
    public void registerWorkbenchRecipeType(WorkbenchRecipeTypeRef recipeType) {
        ResourceId id = recipeType.id();
        if (recipeTypes.containsKey(id)) {
            throw new IllegalStateException("[" + modId + "] Duplicate workbench recipe type " + id);
        }
        AtomicReference<LegacyForgeRecipeBinding> bindingReference = new AtomicReference<>();
        RegistryObject<RecipeType<LegacyForgeWorkbenchRecipe>> type = recipeTypeRegister.register(id.path(), () ->
                new RecipeType<>() {
                    @Override public String toString() { return id.toString(); }
                });
        RegistryObject<RecipeSerializer<LegacyForgeWorkbenchRecipe>> serializer = recipeSerializerRegister.register(
                id.path(), () -> new LegacyForgeWorkbenchRecipe.Serializer(
                        requireRecipeBinding(bindingReference, id)));
        LegacyForgeRecipeBinding binding = new LegacyForgeRecipeBinding(
                type, serializer, recipeType.inputSlots());
        bindingReference.set(binding);
        recipeTypes.put(id, binding);
    }

    @Override
    public void registerWorkbench(PortableWorkbenchDefinition definition) {
        ResourceId id = definition.reference().id();
        LegacyForgeRecipeBinding recipes = recipeTypes.get(definition.spec().recipeType().id());
        if (recipes == null) {
            throw new IllegalStateException("[" + modId + "] Workbench " + id
                    + " references unregistered recipe type " + definition.spec().recipeType().id());
        }
        if (workbenches.containsKey(id)) {
            throw new IllegalStateException("[" + modId + "] Duplicate workbench " + id);
        }
        AtomicReference<LegacyForgeWorkbenchBinding> bindingReference = new AtomicReference<>();
        RegistryObject<MenuType<LegacyForgeWorkbenchMenu>> menuType = menuRegister.register(id.path(), () ->
                new MenuType<>((containerId, inventory) -> new LegacyForgeWorkbenchMenu(
                        containerId, inventory, requireWorkbenchBinding(bindingReference, id)),
                        FeatureFlags.VANILLA_SET));
        LegacyForgeWorkbenchBinding binding = new LegacyForgeWorkbenchBinding(definition, recipes, menuType);
        bindingReference.set(binding);
        workbenches.put(id, binding);
    }

    @Override
    public void openWorkbench(java.util.UUID playerId, PortableWorkbenchDefinition definition) {
        LegacyForgeWorkbenchBinding binding = workbenches.get(definition.reference().id());
        if (binding == null) {
            throw new IllegalStateException("[" + modId + "] Unknown workbench " + definition.reference().id());
        }
        requireOnlinePlayer(playerId).openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new LegacyForgeWorkbenchMenu(containerId, inventory, binding),
                Component.literal(definition.spec().title())));
    }

    @Override
    public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                PayloadReceiver receiver) {
        ResourceLocation location = location(id);
        Predicate<String> accepted = NetworkRegistry.acceptMissingOr(CHANNEL_VERSION);
        SimpleChannel channel = NetworkRegistry.ChannelBuilder.named(location)
                .networkProtocolVersion(() -> CHANNEL_VERSION)
                .clientAcceptedVersions(accepted)
                .serverAcceptedVersions(accepted)
                .simpleChannel();
        PayloadBinding binding = new PayloadBinding(location, direction, maximumBytes, receiver, channel);
        if (payloads.putIfAbsent(id, binding) != null) {
            throw new IllegalStateException("[" + modId + "] Duplicate payload " + id);
        }
        channel.registerMessage(0, byte[].class,
                (payload, buffer) -> buffer.writeByteArray(payload),
                buffer -> buffer.readByteArray(maximumBytes),
                (payload, source) -> receive(binding, payload, source));
    }

    @Override
    public void sendToServer(ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        if (FMLEnvironment.dist != Dist.CLIENT) {
            throw new IllegalStateException("Cannot send to a server from a dedicated server process");
        }
        LegacyForgeClientHooks.sendToServer(binding.channel(), payload);
    }

    @Override
    public void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        ServerPlayer player = requireServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Unknown player " + playerId);
        }
        player.connection.send(binding.channel().toVanillaPacket(payload, NetworkDirection.PLAY_TO_CLIENT));
    }

    @Override
    public void sendToAll(ResourceId id, byte[] payload) {
        PayloadBinding binding = requirePayload(id, payload.length);
        for (ServerPlayer player : requireServer().getPlayerList().getPlayers()) {
            player.connection.send(binding.channel().toVanillaPacket(payload, NetworkDirection.PLAY_TO_CLIENT));
        }
    }

    @Override
    public java.util.Collection<java.util.UUID> connectedPlayers() {
        MinecraftServer current = server;
        return current == null ? java.util.List.of() : current.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID).toList();
    }

    @Override
    public Optional<PlayerSnapshot> playerSnapshot(java.util.UUID playerId) {
        ServerPlayer player = onlinePlayer(playerId);
        return player == null ? Optional.empty() : Optional.of(new PlayerSnapshot(
                playerId, player.getHealth(), player.getMaxHealth()));
    }

    @Override
    public int countPlayerItem(java.util.UUID playerId, ResourceId itemId) {
        return requireOnlinePlayer(playerId).getInventory().countItem(requireItem(itemId));
    }

    @Override
    public boolean consumePlayerItems(java.util.UUID playerId, Map<ResourceId, Integer> requirements) {
        ServerPlayer player = requireOnlinePlayer(playerId);
        for (Map.Entry<ResourceId, Integer> requirement : requirements.entrySet()) {
            if (player.getInventory().countItem(requireItem(requirement.getKey())) < requirement.getValue()) {
                return false;
            }
        }
        for (Map.Entry<ResourceId, Integer> requirement : requirements.entrySet()) {
            Item item = requireItem(requirement.getKey());
            int remaining = requirement.getValue();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(item)) {
                    int removed = Math.min(remaining, stack.getCount());
                    stack.shrink(removed);
                    remaining -= removed;
                }
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    @Override
    public void givePlayerItem(java.util.UUID playerId, ResourceId itemId, int amount) {
        ServerPlayer player = requireOnlinePlayer(playerId);
        ItemStack stack = new ItemStack(requireItem(itemId), amount);
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
        player.containerMenu.broadcastChanges();
    }

    @Override
    public void sendPlayerMessage(java.util.UUID playerId, String message, boolean actionBar) {
        requireOnlinePlayer(playerId).displayClientMessage(Component.literal(message), actionBar);
    }

    @Override
    public void healPlayer(java.util.UUID playerId, double amount) {
        requireOnlinePlayer(playerId).heal((float) amount);
    }

    @Override
    public void addPlayerExperience(java.util.UUID playerId, int points) {
        requireOnlinePlayer(playerId).giveExperiencePoints(points);
    }

    @Override
    public void showMenu(uk.co.enderfall.sdk.runtime.PortableMenuView view,
                         java.util.function.Consumer<String> actionSender, Runnable closeSender) {
        LegacyForgeClientHooks.showMenu(view, actionSender, closeSender);
    }

    @Override
    public void updateMenu(long sessionId, uk.co.enderfall.sdk.api.ui.MenuState state) {
        LegacyForgeClientHooks.updateMenu(sessionId, state);
    }

    @Override
    public void closeMenu(long sessionId) {
        LegacyForgeClientHooks.closeMenu(sessionId);
    }

    private void receive(PayloadBinding binding, byte[] payload,
                         Supplier<NetworkEvent.Context> source) {
        int length = payload.length;
        NetworkEvent.Context networkContext = source.get();
        if (length > binding.maximumBytes()) {
            networkContext.getNetworkManager().disconnect(Component.literal(
                    "Payload " + binding.location() + " exceeds " + binding.maximumBytes() + " bytes"));
            networkContext.setPacketHandled(true);
            return;
        }
        Optional<java.util.UUID> playerId = Optional.ofNullable(networkContext.getSender())
                .map(ServerPlayer::getUUID);
        PacketDirection direction = playerId.isPresent()
                ? PacketDirection.SERVERBOUND : PacketDirection.CLIENTBOUND;
        networkContext.enqueueWork(() -> binding.receiver().receive(payload, direction, playerId,
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
            InteractionResult result = interaction(InteractionEvent.Kind.USE_ITEM,
                    event.getLevel().isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                    event.getEntity().getUUID(), target);
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            ResourceLocation target = ForgeRegistries.BLOCKS.getKey(
                    event.getLevel().getBlockState(event.getPos()).getBlock());
            InteractionResult result = interaction(InteractionEvent.Kind.USE_BLOCK,
                    event.getLevel().isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                    event.getEntity().getUUID(), target);
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
    }

    private void publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action action, PlayerEvent event) {
        context.runtimeEvents().publish(SdkEvents.PLAYER,
                new uk.co.enderfall.sdk.api.event.PlayerEvent(action, event.getEntity().getUUID(),
                        event.getEntity().getGameProfile().getName()));
    }

    private InteractionResult interaction(InteractionEvent.Kind kind, InteractionEvent.Side side,
                                          java.util.UUID playerId, ResourceLocation target) {
        if (target == null) {
            return InteractionResult.PASS;
        }
        InteractionEvent event = new InteractionEvent(kind, side, playerId,
                ResourceId.of(target.getNamespace(), target.getPath()));
        context.runtimeEvents().publish(SdkEvents.INTERACTION, event);
        return event.cancelled() ? InteractionResult.FAIL
                : event.handled() ? InteractionResult.SUCCESS : InteractionResult.PASS;
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

    private ServerPlayer onlinePlayer(java.util.UUID playerId) {
        MinecraftServer current = server;
        return current == null ? null : current.getPlayerList().getPlayer(playerId);
    }

    private ServerPlayer requireOnlinePlayer(java.util.UUID playerId) {
        ServerPlayer player = onlinePlayer(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Unknown player " + playerId);
        }
        return player;
    }

    private static ResourceLocation location(ResourceId id) {
        ResourceLocation location = ResourceLocation.tryBuild(id.namespace(), id.path());
        if (location == null) {
            throw new IllegalArgumentException("Invalid resource ID " + id);
        }
        return location;
    }

    private static LegacyForgeRecipeBinding requireRecipeBinding(
            AtomicReference<LegacyForgeRecipeBinding> reference, ResourceId id) {
        LegacyForgeRecipeBinding binding = reference.get();
        if (binding == null) {
            throw new IllegalStateException("Recipe serializer initialized too early for " + id);
        }
        return binding;
    }

    private static LegacyForgeWorkbenchBinding requireWorkbenchBinding(
            AtomicReference<LegacyForgeWorkbenchBinding> reference, ResourceId id) {
        LegacyForgeWorkbenchBinding binding = reference.get();
        if (binding == null) {
            throw new IllegalStateException("Workbench menu initialized too early for " + id);
        }
        return binding;
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
                                  SimpleChannel channel) {
    }
}
