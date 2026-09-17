package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
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
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.PlayerEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.event.TickEvent;
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

final class FabricPlatformAdapter implements PlatformAdapter {
    private final String modId;
    private final PlatformInfo platformInfo = new FabricPlatformInfo();
    private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
            Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
            Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION,
            Capability.PLAYER_ACTIONS, Capability.SYNCHRONIZED_SCREENS,
            Capability.CUSTOM_RECIPES, Capability.CONTAINER_MENUS));
    private final Map<ResourceId, Item> items = new LinkedHashMap<>();
    private final Map<ResourceId, Block> blocks = new LinkedHashMap<>();
    private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
    private final Map<ResourceId, FabricRecipeBinding> recipeTypes = new LinkedHashMap<>();
    private final Map<ResourceId, FabricWorkbenchBinding> workbenches = new LinkedHashMap<>();
    private final Map<java.util.UUID, ServerPlayer> joiningPlayers = new java.util.concurrent.ConcurrentHashMap<>();
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
        ResourceLocation location = location(id);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, location);
        Item.Properties properties = itemProperties(spec).setId(key);
        Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
        items.put(id, item);
    }

    @Override
    public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
        ResourceLocation location = location(id);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, location);
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
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, location);
            Item.Properties itemProperties = itemProperties(blockItemSpec).setId(itemKey).useBlockDescriptionPrefix();
            Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProperties));
            items.put(id, item);
        }
    }

    @Override
    public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) {
        Item icon = requireItem(spec.icon().id());
        CreativeModeTab tab = FabricItemGroup.builder()
                .title(Component.translatable(spec.titleTranslationKey()))
                .icon(() -> new ItemStack(icon))
                .displayItems((parameters, output) -> spec.entries().forEach(entry -> output.accept(requireItem(entry.id()))))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, location(id), tab);
    }

    @Override
    public void registerCommand(CommandSpec command) {
        FabricCommandBridge.register(command);
    }

    @Override
    public void registerWorkbenchRecipeType(WorkbenchRecipeTypeRef recipeType) {
        ResourceId id = recipeType.id();
        if (recipeTypes.containsKey(id)) throw new IllegalStateException("[" + modId + "] Duplicate recipe type " + id);
        AtomicReference<FabricRecipeBinding> bindingReference = new AtomicReference<>();
        RecipeType<FabricWorkbenchRecipe> type = Registry.register(BuiltInRegistries.RECIPE_TYPE, location(id),
                new RecipeType<>() { @Override public String toString() { return id.toString(); } });
        RecipeSerializer<FabricWorkbenchRecipe> serializer = Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER, location(id),
                FabricWorkbenchRecipe.serializer(() -> requireRecipeBinding(bindingReference, id)));
        FabricRecipeBinding binding = new FabricRecipeBinding(type, serializer, recipeType.inputSlots());
        bindingReference.set(binding);
        recipeTypes.put(id, binding);
    }

    @Override
    public void registerWorkbench(PortableWorkbenchDefinition definition) {
        ResourceId id = definition.reference().id();
        FabricRecipeBinding recipes = recipeTypes.get(definition.spec().recipeType().id());
        if (recipes == null) throw new IllegalStateException("[" + modId + "] Workbench " + id
                + " references unregistered recipe type " + definition.spec().recipeType().id());
        if (workbenches.containsKey(id)) throw new IllegalStateException("[" + modId + "] Duplicate workbench " + id);
        AtomicReference<FabricWorkbenchBinding> bindingReference = new AtomicReference<>();
        MenuType<FabricWorkbenchMenu> menuType = Registry.register(BuiltInRegistries.MENU, location(id),
                new MenuType<>((containerId, inventory) -> new FabricWorkbenchMenu(containerId, inventory,
                        requireWorkbenchBinding(bindingReference, id)), FeatureFlags.VANILLA_SET));
        FabricWorkbenchBinding binding = new FabricWorkbenchBinding(definition, recipes, menuType);
        bindingReference.set(binding);
        workbenches.put(id, binding);
        if (platformInfo.environment() == Environment.CLIENT) FabricClientHooks.registerWorkbench(menuType);
    }

    @Override
    public void openWorkbench(java.util.UUID playerId, PortableWorkbenchDefinition definition) {
        FabricWorkbenchBinding binding = workbenches.get(definition.reference().id());
        if (binding == null) throw new IllegalStateException("[" + modId + "] Unknown workbench " + definition.reference().id());
        requireOnlinePlayer(playerId).openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new FabricWorkbenchMenu(containerId, inventory, binding),
                Component.literal(definition.spec().title())));
    }

    @Override
    public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes, PayloadReceiver receiver) {
        CustomPacketPayload.Type<FabricRawPayload> type = new CustomPacketPayload.Type<>(location(id));
        var codec = FabricRawPayload.codec(type, maximumBytes);
        if (direction == PacketDirection.SERVERBOUND || direction == PacketDirection.BIDIRECTIONAL) {
            PayloadTypeRegistry.playC2S().register(type, codec);
            if (!ServerPlayNetworking.registerGlobalReceiver(type, (payload, networkContext) -> receiver.receive(
                    payload.bytes(), PacketDirection.SERVERBOUND,
                    Optional.of(networkContext.player().getUUID()),
                    reason -> networkContext.player().connection.disconnect(Component.literal(reason))))) {
                throw new IllegalStateException("Duplicate serverbound payload " + id);
            }
        }
        if (direction == PacketDirection.CLIENTBOUND || direction == PacketDirection.BIDIRECTIONAL) {
            PayloadTypeRegistry.playS2C().register(type, codec);
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
        ServerPlayer player = joiningPlayers.get(playerId);
        if (player == null) {
            player = requireServer().getPlayerList().getPlayer(playerId);
        }
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
        FabricClientHooks.showMenu(view, actionSender, closeSender);
    }

    @Override
    public void updateMenu(long sessionId, uk.co.enderfall.sdk.api.ui.MenuState state) {
        FabricClientHooks.updateMenu(sessionId, state);
    }

    @Override
    public void closeMenu(long sessionId) {
        FabricClientHooks.closeMenu(sessionId);
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
            joiningPlayers.clear();
            server = null;
        });
        ServerTickEvents.START_SERVER_TICK.register(value -> context.runtimeEvents().publish(
                SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.START, tick.get())));
        ServerTickEvents.END_SERVER_TICK.register(value -> context.runtimeEvents().publish(
                SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.END, tick.getAndIncrement())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, value) -> {
            java.util.UUID playerId = handler.player.getUUID();
            // Fabric fires JOIN before the player is guaranteed to be visible through PlayerList.
            joiningPlayers.put(playerId, handler.player);
            try {
                context.runtimeNetworking().connectionOpened(playerId);
                context.runtimeEvents().publish(SdkEvents.PLAYER,
                        new PlayerEvent(PlayerEvent.Action.JOIN, playerId,
                                handler.player.getGameProfile().getName()));
            } finally {
                joiningPlayers.remove(playerId, handler.player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, value) -> {
            java.util.UUID playerId = handler.player.getUUID();
            joiningPlayers.remove(playerId);
            context.runtimeNetworking().connectionClosed(playerId);
            context.runtimeEvents().publish(SdkEvents.PLAYER,
                    new PlayerEvent(PlayerEvent.Action.LEAVE, playerId,
                            handler.player.getGameProfile().getName()));
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ResourceLocation target = BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem());
            return interaction(InteractionEvent.Kind.USE_ITEM,
                    level.isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                    player.getUUID(), target);
        });
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            ResourceLocation target = BuiltInRegistries.BLOCK.getKey(level.getBlockState(hitResult.getBlockPos()).getBlock());
            return interaction(InteractionEvent.Kind.USE_BLOCK,
                    level.isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                    player.getUUID(), target);
        });
        if (platformInfo.environment() == Environment.CLIENT) {
            FabricClientHooks.installLifecycle(context);
        }
    }

    private InteractionResult interaction(InteractionEvent.Kind kind, InteractionEvent.Side side,
                                          java.util.UUID playerId,
                                          ResourceLocation target) {
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

    private ServerPlayer onlinePlayer(java.util.UUID playerId) {
        ServerPlayer joining = joiningPlayers.get(playerId);
        MinecraftServer current = server;
        return joining != null ? joining
                : current == null ? null : current.getPlayerList().getPlayer(playerId);
    }

    private ServerPlayer requireOnlinePlayer(java.util.UUID playerId) {
        ServerPlayer player = onlinePlayer(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Unknown player " + playerId);
        }
        return player;
    }

    private static ResourceLocation location(ResourceId id) {
        return ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
    }

    private static FabricRecipeBinding requireRecipeBinding(
            AtomicReference<FabricRecipeBinding> reference, ResourceId id) {
        FabricRecipeBinding binding = reference.get();
        if (binding == null) throw new IllegalStateException("Recipe serializer initialized too early for " + id);
        return binding;
    }

    private static FabricWorkbenchBinding requireWorkbenchBinding(
            AtomicReference<FabricWorkbenchBinding> reference, ResourceId id) {
        FabricWorkbenchBinding binding = reference.get();
        if (binding == null) throw new IllegalStateException("Workbench menu initialized too early for " + id);
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
