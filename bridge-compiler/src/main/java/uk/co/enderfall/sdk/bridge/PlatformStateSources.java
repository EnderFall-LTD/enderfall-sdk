package uk.co.enderfall.sdk.bridge;

/**
 * Shared state operations with explicit native ABI facets.
 * These are authored compiler templates, not contextual edits of canonical Java.
 * Layout intentionally retains reference source and bytecode parity.
 */
final class PlatformStateSources {
    private PlatformStateSources() { }

    static String emit(PlatformOperation operation, NativePlatformPolicy policy) throws BridgeGenerationException {
        return switch (operation) {
            case HEADER -> switch (policy) {
                case FABRIC_LEGACY -> """
                    package ${PACKAGE};
                    
                    import java.nio.file.Path;
                    import java.util.EnumSet;
                    import java.util.LinkedHashMap;
                    import java.util.Map;
                    import java.util.Optional;
                    import java.util.concurrent.atomic.AtomicLong;
                    import net.fabricmc.api.EnvType;
                    import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
                    import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
                    import net.fabricmc.fabric.api.event.player.UseBlockCallback;
                    import net.fabricmc.fabric.api.event.player.UseItemCallback;
                    import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
                    import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
                    import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
                    import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
                    import net.fabricmc.loader.api.FabricLoader;
                    import net.minecraft.core.Registry;
                    import net.minecraft.core.registries.BuiltInRegistries;
                    import net.minecraft.network.FriendlyByteBuf;
                    import net.minecraft.network.chat.Component;
                    import net.minecraft.resources.ResourceLocation;
                    import net.minecraft.server.MinecraftServer;
                    import net.minecraft.server.level.ServerPlayer;
                    import net.minecraft.world.InteractionResult;
                    import net.minecraft.world.InteractionResultHolder;
                    import net.minecraft.world.item.BlockItem;
                    import net.minecraft.world.item.CreativeModeTab;
                    import net.minecraft.world.item.Item;
                    import net.minecraft.world.item.ItemStack;
                    import net.minecraft.world.flag.FeatureFlags;
                    import net.minecraft.world.inventory.MenuType;
                    import net.minecraft.world.SimpleMenuProvider;
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
                    import uk.co.enderfall.sdk.runtime.RuntimeModContext;
                    import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;
                    
                    final class ${PlatformAdapter} implements PlatformAdapter {
                        private final String modId;
                        private final PlatformInfo platformInfo = new ${PlatformInfo}();
                        private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
                                Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
                                Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION,
                                Capability.PLAYER_ACTIONS, Capability.SYNCHRONIZED_SCREENS,
                                Capability.CUSTOM_RECIPES, Capability.CONTAINER_MENUS));
                        private final Map<ResourceId, Item> items = new LinkedHashMap<>();
                        private final Map<ResourceId, Block> blocks = new LinkedHashMap<>();
                        private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
                        private final Map<ResourceId, ${RecipeBinding}> recipeTypes = new LinkedHashMap<>();
                        private final Map<ResourceId, ${WorkbenchBinding}> workbenches = new LinkedHashMap<>();
                        private final Map<java.util.UUID, ServerPlayer> joiningPlayers = new java.util.concurrent.ConcurrentHashMap<>();
                        private volatile MinecraftServer server;
                        private RuntimeModContext context;
                    
                    """;
                case FABRIC_UNKEYED -> """
                    package ${PACKAGE};
                    
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
                    import net.minecraft.network.chat.Component;
                    import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
                    import net.minecraft.resources.ResourceLocation;
                    import net.minecraft.server.MinecraftServer;
                    import net.minecraft.server.level.ServerPlayer;
                    import net.minecraft.world.InteractionResult;
                    import net.minecraft.world.InteractionResultHolder;
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
                    
                    final class ${PlatformAdapter} implements PlatformAdapter {
                        private final String modId;
                        private final PlatformInfo platformInfo = new ${PlatformInfo}();
                        private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
                                Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
                                Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION,
                                Capability.PLAYER_ACTIONS, Capability.SYNCHRONIZED_SCREENS,
                                Capability.CUSTOM_RECIPES, Capability.CONTAINER_MENUS));
                        private final Map<ResourceId, Item> items = new LinkedHashMap<>();
                        private final Map<ResourceId, Block> blocks = new LinkedHashMap<>();
                        private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
                        private final Map<ResourceId, ${RecipeBinding}> recipeTypes = new LinkedHashMap<>();
                        private final Map<ResourceId, ${WorkbenchBinding}> workbenches = new LinkedHashMap<>();
                        private final Map<java.util.UUID, ServerPlayer> joiningPlayers = new java.util.concurrent.ConcurrentHashMap<>();
                        private volatile MinecraftServer server;
                        private RuntimeModContext context;
                    
                    """;
                case FABRIC_KEYED -> """
                    package ${PACKAGE};
                    
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
                    
                    final class ${PlatformAdapter} implements PlatformAdapter {
                        private final String modId;
                        private final PlatformInfo platformInfo = new ${PlatformInfo}();
                        private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
                                Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
                                Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION,
                                Capability.PLAYER_ACTIONS, Capability.SYNCHRONIZED_SCREENS,
                                Capability.CUSTOM_RECIPES, Capability.CONTAINER_MENUS));
                        private final Map<ResourceId, Item> items = new LinkedHashMap<>();
                        private final Map<ResourceId, Block> blocks = new LinkedHashMap<>();
                        private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
                        private final Map<ResourceId, ${RecipeBinding}> recipeTypes = new LinkedHashMap<>();
                        private final Map<ResourceId, ${WorkbenchBinding}> workbenches = new LinkedHashMap<>();
                        private final Map<java.util.UUID, ServerPlayer> joiningPlayers = new java.util.concurrent.ConcurrentHashMap<>();
                        private volatile MinecraftServer server;
                        private RuntimeModContext context;
                    
                    """;
                case FABRIC_IDENTIFIER -> """
                    package ${PACKAGE};
                    
                    import java.nio.file.Path;
                    import java.util.EnumSet;
                    import java.util.LinkedHashMap;
                    import java.util.Map;
                    import java.util.Optional;
                    import java.util.concurrent.atomic.AtomicLong;
                    import java.util.concurrent.atomic.AtomicReference;
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
                    
                    final class ${PlatformAdapter} implements PlatformAdapter {
                        private final String modId;
                        private final PlatformInfo platformInfo = new ${PlatformInfo}();
                        private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
                                Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
                                Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION,
                                Capability.PLAYER_ACTIONS, Capability.SYNCHRONIZED_SCREENS,
                                Capability.CUSTOM_RECIPES, Capability.CONTAINER_MENUS));
                        private final Map<ResourceId, Item> items = new LinkedHashMap<>();
                        private final Map<ResourceId, Block> blocks = new LinkedHashMap<>();
                        private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
                        private final Map<ResourceId, ${RecipeBinding}> recipeTypes = new LinkedHashMap<>();
                        private final Map<ResourceId, ${WorkbenchBinding}> workbenches = new LinkedHashMap<>();
                        private final Map<java.util.UUID, ServerPlayer> joiningPlayers = new java.util.concurrent.ConcurrentHashMap<>();
                        private volatile MinecraftServer server;
                        private RuntimeModContext context;
                    
                    """;
                case LEGACY_FML -> """
                    package ${PACKAGE};
                    
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
                    
                    final class ${PlatformAdapter} implements PlatformAdapter {
                        private static final String CHANNEL_VERSION = "0";
                        private final String modId;
                        private final IEventBus modBus;
                        private final PlatformInfo platformInfo = new ${PlatformInfo}();
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
                        private final Map<ResourceId, ${RecipeBinding}> recipeTypes = new LinkedHashMap<>();
                        private final Map<ResourceId, ${WorkbenchBinding}> workbenches = new LinkedHashMap<>();
                        private volatile MinecraftServer server;
                        private RuntimeModContext context;
                    
                    """;
                case NEOFORGE -> """
                    package ${PACKAGE};
                    
                    import java.nio.file.Path;
                    import java.util.EnumSet;
                    import java.util.LinkedHashMap;
                    import java.util.Map;
                    import java.util.Optional;
                    import java.util.concurrent.atomic.AtomicLong;
                    import java.util.concurrent.atomic.AtomicReference;
                    import java.util.function.Supplier;
                    import net.minecraft.core.registries.BuiltInRegistries;
                    import net.minecraft.core.registries.Registries;
                    import net.minecraft.network.chat.Component;
                    import net.minecraft.network.protocol.PacketFlow;
                    import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
                    import net.minecraft.resources.ResourceLocation;
                    import net.minecraft.server.MinecraftServer;
                    import net.minecraft.server.level.ServerPlayer;
                    import net.minecraft.world.InteractionResult;
                    import net.minecraft.world.SimpleMenuProvider;
                    import net.minecraft.world.flag.FeatureFlags;
                    import net.minecraft.world.inventory.MenuType;
                    import net.minecraft.world.item.CreativeModeTab;
                    import net.minecraft.world.item.Item;
                    import net.minecraft.world.item.ItemStack;
                    import net.minecraft.world.item.crafting.RecipeSerializer;
                    import net.minecraft.world.item.crafting.RecipeType;
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
                    import net.neoforged.neoforge.registries.DeferredHolder;
                    import net.neoforged.neoforge.registries.DeferredRegister;
                    import uk.co.enderfall.sdk.api.ResourceId;
                    import uk.co.enderfall.sdk.api.command.CommandSpec;
                    import uk.co.enderfall.sdk.api.event.InteractionEvent;
                    import uk.co.enderfall.sdk.api.event.LifecycleEvent;
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
                    
                    final class ${PlatformAdapter} implements PlatformAdapter {
                        private final String modId;
                        private final IEventBus modBus;
                        private final PlatformInfo platformInfo = new ${PlatformInfo}();
                        private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
                                Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
                                Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION,
                                Capability.PLAYER_ACTIONS, Capability.SYNCHRONIZED_SCREENS,
                                Capability.CUSTOM_RECIPES, Capability.CONTAINER_MENUS));
                        private final DeferredRegister.Items itemRegister;
                        private final DeferredRegister.Blocks blockRegister;
                        private final DeferredRegister<CreativeModeTab> creativeTabRegister;
                        private final DeferredRegister<RecipeType<?>> recipeTypeRegister;
                        private final DeferredRegister<RecipeSerializer<?>> recipeSerializerRegister;
                        private final DeferredRegister<MenuType<?>> menuRegister;
                        private final Map<ResourceId, Supplier<? extends Item>> items = new LinkedHashMap<>();
                        private final Map<ResourceId, Supplier<? extends Block>> blocks = new LinkedHashMap<>();
                        private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
                        private final Map<ResourceId, ${RecipeBinding}> recipeTypes = new LinkedHashMap<>();
                        private final Map<ResourceId, ${WorkbenchBinding}> workbenches = new LinkedHashMap<>();
                        private volatile MinecraftServer server;
                        private RuntimeModContext context;
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                    package ${PACKAGE};
                    
                    import java.nio.file.Path;
                    import java.util.EnumSet;
                    import java.util.LinkedHashMap;
                    import java.util.Map;
                    import java.util.Optional;
                    import java.util.concurrent.atomic.AtomicLong;
                    import java.util.concurrent.atomic.AtomicReference;
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
                    import net.minecraft.world.SimpleMenuProvider;
                    import net.minecraft.world.flag.FeatureFlags;
                    import net.minecraft.world.inventory.MenuType;
                    import net.minecraft.world.item.CreativeModeTab;
                    import net.minecraft.world.item.Item;
                    import net.minecraft.world.item.ItemStack;
                    import net.minecraft.world.item.crafting.RecipeSerializer;
                    import net.minecraft.world.item.crafting.RecipeType;
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
                    
                    final class ${PlatformAdapter} implements PlatformAdapter {
                        private final String modId;
                        private final IEventBus modBus;
                        private final PlatformInfo platformInfo = new ${PlatformInfo}();
                        private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.of(
                                Capability.REGISTRIES, Capability.EVENTS, Capability.COMMANDS,
                                Capability.CONFIGURATION, Capability.NETWORKING, Capability.DATA_GENERATION,
                                Capability.PLAYER_ACTIONS, Capability.SYNCHRONIZED_SCREENS,
                                Capability.CUSTOM_RECIPES, Capability.CONTAINER_MENUS));
                        private final DeferredRegister.Items itemRegister;
                        private final DeferredRegister.Blocks blockRegister;
                        private final DeferredRegister<CreativeModeTab> creativeTabRegister;
                        private final DeferredRegister<RecipeType<?>> recipeTypeRegister;
                        private final DeferredRegister<RecipeSerializer<?>> recipeSerializerRegister;
                        private final DeferredRegister<MenuType<?>> menuRegister;
                        private final Map<ResourceId, Supplier<? extends Item>> items = new LinkedHashMap<>();
                        private final Map<ResourceId, Supplier<? extends Block>> blocks = new LinkedHashMap<>();
                        private final Map<ResourceId, PayloadBinding> payloads = new LinkedHashMap<>();
                        private final Map<ResourceId, ${RecipeBinding}> recipeTypes = new LinkedHashMap<>();
                        private final Map<ResourceId, ${WorkbenchBinding}> workbenches = new LinkedHashMap<>();
                        private volatile MinecraftServer server;
                        private RuntimeModContext context;
                    
                    """;
            };
            case CONSTRUCTOR -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        ${PlatformAdapter}(String modId) {
                            this.modId = modId;
                        }
                    
                    """;
                case LEGACY_FML -> """
                        ${PlatformAdapter}(String modId, IEventBus modBus) {
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
                    
                    """;
                case NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        ${PlatformAdapter}(String modId, IEventBus modBus) {
                            this.modId = modId;
                            this.modBus = modBus;
                            itemRegister = DeferredRegister.createItems(modId);
                            blockRegister = DeferredRegister.createBlocks(modId);
                            creativeTabRegister = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, modId);
                            recipeTypeRegister = DeferredRegister.create(Registries.RECIPE_TYPE, modId);
                            recipeSerializerRegister = DeferredRegister.create(Registries.RECIPE_SERIALIZER, modId);
                            menuRegister = DeferredRegister.create(Registries.MENU, modId);
                            itemRegister.register(modBus);
                            blockRegister.register(modBus);
                            creativeTabRegister.register(modBus);
                            recipeTypeRegister.register(modBus);
                            recipeSerializerRegister.register(modBus);
                            menuRegister.register(modBus);
                        }
                    
                    """;
            };
            case ATTACH -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        void attach(RuntimeModContext runtimeContext) {
                            context = runtimeContext;
                            installEvents();
                        }
                    
                    """;
                case LEGACY_FML -> """
                        void attach(RuntimeModContext runtimeContext) {
                            context = runtimeContext;
                            installEvents();
                            if (platformInfo.environment() == Environment.CLIENT) {
                                ${ClientHooks}.install(modBus, context, workbenches.values());
                            }
                        }
                    
                    """;
                case NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        void attach(RuntimeModContext runtimeContext) {
                            context = runtimeContext;
                            modBus.addListener(this::registerPayloadHandlers);
                            installEvents();
                            if (platformInfo.environment() == Environment.CLIENT) {
                                ${ClientHooks}.install(modBus, context, workbenches.values());
                            }
                        }
                    
                    """;
            };
            case PLATFORM_INFO -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED, LEGACY_FML -> """
                        @Override public PlatformInfo platformInfo() { return platformInfo; }
                    """;
                case FABRIC_KEYED, FABRIC_IDENTIFIER, NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        @Override
                        public PlatformInfo platformInfo() {
                            return platformInfo;
                        }
                    
                    """;
            };
            case CAPABILITIES -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED, LEGACY_FML -> """
                        @Override public CapabilitySet capabilities() { return capabilities; }
                    """;
                case FABRIC_KEYED, FABRIC_IDENTIFIER, NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        @Override
                        public CapabilitySet capabilities() {
                            return capabilities;
                        }
                    
                    """;
            };
            case COMMON_CONFIG_DIRECTORY -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED -> """
                        @Override public Path commonConfigDirectory() { return FabricLoader.getInstance().getConfigDir(); }
                    
                    """;
                case FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        @Override
                        public Path commonConfigDirectory() {
                            return FabricLoader.getInstance().getConfigDir();
                        }
                    
                    """;
                case LEGACY_FML -> """
                        @Override public Path commonConfigDirectory() { return FMLPaths.CONFIGDIR.get(); }
                    
                    """;
                case NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        @Override
                        public Path commonConfigDirectory() {
                            return FMLPaths.CONFIGDIR.get();
                        }
                    
                    """;
            };
            case SERVER_CONFIG_DIRECTORY -> """
                        @Override
                        public Path serverConfigDirectory() {
                            MinecraftServer current = server;
                            if (current == null) {
                                throw new IllegalStateException("[" + modId + "] SERVER config is unavailable before a world starts");
                            }
                            return current.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
                        }
                    
                    """;
            default -> throw new BridgeGenerationException("Operation " + operation + " does not belong to PlatformStateSources");
        };
    }
}
