package uk.co.enderfall.sdk.api.registry;

import java.util.*;
import java.util.function.Consumer;
import uk.co.enderfall.sdk.api.*;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;
import uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec;
import uk.co.enderfall.sdk.api.ui.*;

/**
 * Static, namespaced declarations attached once during mod initialization.
 * Configuration callbacks are evaluated on attachment, before native registration.
 */
@Experimental("Declarative registration groups")
public final class Registration {
    private Registration() { }
    public static Blocks blocks(String namespace) { return new Blocks(namespace); }
    public static Items items(String namespace) { return new Items(namespace); }
    public static Menus menus(String namespace) { return new Menus(namespace); }
    public static Recipes recipes(String namespace) { return new Recipes(namespace); }
    public static Tabs tabs(String namespace) { return new Tabs(namespace); }

    public static BlockEntitySpec storage(BlockRef block, Consumer<BlockEntitySpec.Builder> configure) {
        BlockEntitySpec.Builder builder = BlockEntitySpec.builder(block);
        configure.accept(builder);
        return builder.build();
    }

    public abstract static class Group {
        private final String namespace;
        private final List<Entry> entries = new ArrayList<>();
        private boolean attached;
        private Group(String namespace) { this.namespace = ResourceId.of(namespace, "validation").namespace(); }
        final ResourceId id(String path) {
            if (attached) throw new IllegalStateException("[" + namespace + "] Registration group is frozen");
            return ResourceId.of(namespace, path);
        }
        final void add(Entry entry) {
            if (entries.stream().anyMatch(e -> e.key.equals(entry.key)))
                throw new IllegalArgumentException("Duplicate declaration " + entry.key);
            entries.add(entry);
        }
    }

    private record Key(String kind, ResourceId id) { }
    private abstract static class Entry {
        final Key key;
        final int phase;
        Entry(String kind, ResourceId id, int phase) { this.key = new Key(kind, id); this.phase = phase; }
        abstract Runnable prepare(ModContext context, Resolution resolution);
    }

    /** Resolves all block copy dependencies before any native registration. */
    private static final class Resolution {
        final Map<ResourceId, BlockEntry> blocks = new LinkedHashMap<>();
        final Map<ResourceId, BlockOptions> resolved = new LinkedHashMap<>();
        final Set<ResourceId> visiting = new LinkedHashSet<>();
        BlockOptions block(ResourceId id) {
            if (resolved.containsKey(id)) return resolved.get(id);
            BlockEntry entry = blocks.get(id);
            if (entry == null) throw new IllegalArgumentException("Missing block copy declaration " + id + "; attach its group too");
            if (!visiting.add(id)) throw new IllegalArgumentException("Block property copy cycle " + visiting + " -> " + id);
            BlockOptions options = new BlockOptions(this, new BlockRef(id));
            entry.configure.accept(options);
            if (options.behavior != null) options.builder.behavior(options.behavior);
            options.spec = options.builder.build();
            resolved.put(id, options);
            visiting.remove(id);
            return options;
        }
    }

    /** Group order is immaterial: blocks/items, recipe types, menus, then creative tabs. */
    public static void register(ModContext context, Group... groups) {
        Objects.requireNonNull(context, "context");
        List<Entry> entries = new ArrayList<>();
        Set<Group> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Key> keys = new HashSet<>();
        Resolution resolution = new Resolution();
        for (Group group : groups) {
            Objects.requireNonNull(group, "group");
            if (!seen.add(group) || group.attached) throw new IllegalStateException("Group already attached: " + group.namespace);
            if (!context.modId().equals(group.namespace)) throw new IllegalArgumentException("Cannot attach " + group.namespace + " to " + context.modId());
            for (Entry entry : group.entries) {
                if (!keys.add(entry.key)) throw new IllegalArgumentException("Duplicate declaration " + entry.key);
                entries.add(entry);
                if (entry instanceof BlockEntry block) resolution.blocks.put(entry.key.id, block);
            }
        }
        // Freeze before running consumer callbacks too: reentrant declaration/attachment is an error.
        for (Group group : groups) group.attached = true;
        // Configuration is resolved first: errors cannot leave half-registered native objects.
        for (ResourceId id : resolution.blocks.keySet()) resolution.block(id);
        for (var entry : resolution.resolved.entrySet()) {
            if (entry.getValue().item != null && !keys.add(new Key("item", entry.getKey())))
                throw new IllegalArgumentException("Block item conflicts with item " + entry.getKey());
        }
        entries.sort(Comparator.comparingInt(e -> e.phase));
        List<Runnable> operations = new ArrayList<>();
        for (Entry entry : entries) operations.add(entry.prepare(context, resolution));
        operations.forEach(Runnable::run);
        Map<ResourceId, uk.co.enderfall.sdk.api.block.PortableBlock> behaviors = new HashMap<>();
        resolution.resolved.forEach((id, options) -> {
            if (options.behavior != null) behaviors.put(id, options.behavior);
        });
        if (!behaviors.isEmpty()) {
            Map<ResourceId, uk.co.enderfall.sdk.api.block.PortableBlock> registered = Map.copyOf(behaviors);
            context.events().subscribe(uk.co.enderfall.sdk.api.event.SdkEvents.INTERACTION, event -> {
                if (event.side() != uk.co.enderfall.sdk.api.event.InteractionEvent.Side.SERVER
                        || event.kind() != uk.co.enderfall.sdk.api.event.InteractionEvent.Kind.USE_BLOCK
                        || event.cancelled() || event.handled()) return;
                var behavior = registered.get(event.target());
                if (behavior != null) behavior.onUse(context, event);
            });
        }
    }

    public static final class Blocks extends Group {
        private Blocks(String namespace) { super(namespace); }
        public BlockRef block(String path) { return block(path, p -> { }); }
        public BlockRef block(String path, java.util.function.Supplier<? extends uk.co.enderfall.sdk.api.block.PortableBlock> factory,
                Consumer<BlockOptions> configure) {
            Objects.requireNonNull(factory, "factory");
            Objects.requireNonNull(configure, "configure");
            return block(path, properties -> {
                var behavior = Objects.requireNonNull(factory.get(), "Block factory returned null");
                properties.behavior = behavior;
                behavior.configure(properties);
                configure.accept(properties);
            });
        }
        public BlockRef block(String path, Consumer<BlockOptions> configure) {
            ResourceId id = id(path);
            add(new BlockEntry(id, Objects.requireNonNull(configure, "configure")));
            return new BlockRef(id);
        }
    }
    private static final class BlockEntry extends Entry {
        final Consumer<BlockOptions> configure;
        BlockEntry(ResourceId id, Consumer<BlockOptions> configure) { super("block", id, 0); this.configure = configure; }
        @Override Runnable prepare(ModContext context, Resolution resolution) {
            BlockOptions options = resolution.block(key.id);
            BlockEntitySpec storage;
            if (options.existingStorage != null) {
                storage = options.existingStorage;
                if (!storage.block().id().equals(key.id)) throw new IllegalArgumentException("Storage belongs to a different block: " + key.id);
            } else if (options.storage != null) {
                BlockEntitySpec.Builder builder = BlockEntitySpec.builder(new BlockRef(key.id));
                options.storage.accept(builder);
                storage = builder.build();
            } else storage = null;
            return () -> {
                if (storage != null) context.blocks().registerPersistentWithItem(key.id.path(), options.spec, options.item, storage);
                else if (options.item != null) context.blocks().registerWithItem(key.id.path(), options.spec, options.item);
                else context.blocks().register(key.id, options.spec);
            };
        }
    }
    public static final class BlockOptions {
        private final BlockRef block;
        public BlockRef block() { return block; }
        private final Resolution resolution;
        private final BlockSpec.Builder builder = BlockSpec.builder();
        private BlockSpec spec;
        private uk.co.enderfall.sdk.api.block.PortableBlock behavior;
        private ItemSpec item;
        private Consumer<BlockEntitySpec.Builder> storage;
        private BlockEntitySpec existingStorage;
        private BlockOptions(Resolution resolution, BlockRef block) { this.resolution = resolution; this.block = block; }
        public BlockOptions copyFrom(BlockRef source) {
            builder.copyFrom(resolution.block(source.id()).spec); return this;
        }
        public BlockOptions copyFrom(BlockSpec source) { builder.copyFrom(source); return this; }
        /** Native source ID must exist on every selected target. No silent substitute is used. */
        public BlockOptions copyFrom(ResourceId source) { builder.copyFrom(source); return this; }
        public BlockOptions strength(float hardness, float resistance) { builder.strength(hardness, resistance); return this; }
        public BlockOptions strength(float value) { return strength(value, value); }
        public BlockOptions horizontalFacing() { builder.horizontalFacing(); return this; }
        public BlockOptions sixWayFacing() { builder.sixWayFacing(); return this; }
        public BlockOptions waterlogged() { builder.waterlogged(); return this; }
        public BlockOptions scheduledTicks() { builder.scheduledTicks(); return this; }
        public BlockOptions states(uk.co.enderfall.sdk.api.block.BlockStateDefinition definition) { builder.states(definition); return this; }
        public BlockOptions stateShapes(uk.co.enderfall.sdk.api.block.BlockStateShapes shapes) { builder.stateShapes(shapes); return this; }
        public BlockOptions shape(uk.co.enderfall.sdk.api.block.BlockShape value) { builder.shape(value); return this; }
        public BlockOptions outlineShape(uk.co.enderfall.sdk.api.block.BlockShape value) { builder.outlineShape(value); return this; }
        public BlockOptions collisionShape(uk.co.enderfall.sdk.api.block.BlockShape value) { builder.collisionShape(value); return this; }
        public BlockOptions friction(float value) { builder.friction(value); return this; }
        public BlockOptions jumpFactor(float value) { builder.jumpFactor(value); return this; }
        public BlockOptions luminance(int value) { builder.luminance(value); return this; }
        public BlockOptions requiresTool() { builder.requiresTool(); return this; }
        public BlockOptions sound(SoundPreset value) { builder.sound(value); return this; }
        public BlockOptions withItem() { return withItem(p -> { }); }
        public BlockOptions withItem(Consumer<ItemSpec.Builder> configure) {
            ItemSpec.Builder value = ItemSpec.builder(); configure.accept(value); item = value.build(); return this;
        }
        /** Creates the block item and storage together, with ownership assigned automatically. */
        public BlockOptions storage(Consumer<BlockEntitySpec.Builder> configure) {
            storage = Objects.requireNonNull(configure, "configure");
            existingStorage = null;
            if (item == null) withItem();
            return this;
        }
        public BlockOptions storage(BlockEntitySpec value) {
            existingStorage = Objects.requireNonNull(value, "value");
            storage = null;
            if (item == null) withItem();
            return this;
        }
    }
    public static final class Items extends Group {
        private Items(String namespace) { super(namespace); }
        public ItemRef item(String path) { return item(path, p -> { }); }
        public ItemRef item(String path, Consumer<ItemSpec.Builder> configure) {
            ResourceId id = id(path); Objects.requireNonNull(configure, "configure");
            add(new Entry("item", id, 0) {
                @Override Runnable prepare(ModContext context, Resolution resolution) {
                    ItemSpec.Builder builder = ItemSpec.builder(); configure.accept(builder);
                    ItemSpec spec = builder.build();
                    return () -> context.items().register(id, spec);
                }
            });
            return new ItemRef(id);
        }
    }
    public static final class Recipes extends Group {
        private Recipes(String namespace) { super(namespace); }
        public WorkbenchRecipeTypeRef workbench(String path, int slots) { return type(path, slots, false); }
        public WorkbenchRecipeTypeRef machine(String path, int slots) { return type(path, slots, true); }
        private WorkbenchRecipeTypeRef type(String path, int slots, boolean machine) {
            ResourceId id = id(path); WorkbenchRecipeTypeRef ref = new WorkbenchRecipeTypeRef(id, slots);
            add(new Entry("recipe_type", id, 1) {
                @Override Runnable prepare(ModContext context, Resolution resolution) {
                    return () -> { if (machine) context.recipes().registerMachineType(id, slots);
                        else context.recipes().registerWorkbenchType(id, slots); };
                }
            });
            return ref;
        }
    }
    public static final class Menus extends Group {
        private Menus(String namespace) { super(namespace); }
        /** The SDK supplies the synchronized client screen; no second client registration. */
        public MenuRef menu(String path, String title, Consumer<MenuSpec.Builder> configure, MenuActionHandler handler) {
            ResourceId id = id(path); Objects.requireNonNull(configure, "configure"); Objects.requireNonNull(handler, "handler");
            add(new Entry("menu", id, 2) {
                @Override Runnable prepare(ModContext context, Resolution resolution) {
                    MenuSpec.Builder builder = MenuSpec.builder(title); configure.accept(builder); MenuSpec spec = builder.build();
                    return () -> context.menus().register(id, spec, handler);
                }
            });
            return new MenuRef(id);
        }
        public WorkbenchRef workbench(String path, String title, WorkbenchRecipeTypeRef type,
                Consumer<WorkbenchSpec.Builder> configure, WorkbenchCraftHandler handler) {
            ResourceId id = id(path); Objects.requireNonNull(configure, "configure"); Objects.requireNonNull(handler, "handler");
            add(new Entry("menu", id, 2) {
                @Override Runnable prepare(ModContext context, Resolution resolution) {
                    WorkbenchSpec.Builder builder = WorkbenchSpec.builder(title, type); configure.accept(builder); WorkbenchSpec spec = builder.build();
                    return () -> context.workbenches().register(id, spec, handler);
                }
            });
            return new WorkbenchRef(id);
        }

        /**
         * Registers a block-owned vanilla-synchronized storage screen. The SDK binds the
         * block interaction and client screen; consumer code performs no second registration.
         */
        public uk.co.enderfall.sdk.api.ui.StorageContainerRef container(String path, String title,
                BlockEntitySpec storage,
                Consumer<uk.co.enderfall.sdk.api.ui.StorageContainerSpec.Builder> configure) {
            ResourceId id = id(path);
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(configure, "configure");
            add(new Entry("menu", id, 2) {
                @Override Runnable prepare(ModContext context, Resolution resolution) {
                    var builder = uk.co.enderfall.sdk.api.ui.StorageContainerSpec.builder(title, storage);
                    configure.accept(builder);
                    var spec = builder.build();
                    return () -> context.containers().register(id, spec);
                }
            });
            return new uk.co.enderfall.sdk.api.ui.StorageContainerRef(id);
        }

        public uk.co.enderfall.sdk.api.ui.StorageContainerRef container(String path, String title,
                BlockEntitySpec storage) {
            return container(path, title, storage, builder -> { });
        }
    }
    public static final class Tabs extends Group {
        private Tabs(String namespace) { super(namespace); }
        public CreativeTabRef tab(String path, String titleKey, ItemRef icon, Consumer<CreativeTabSpec.Builder> configure) {
            ResourceId id = id(path); Objects.requireNonNull(configure, "configure");
            add(new Entry("creative_tab", id, 3) {
                @Override Runnable prepare(ModContext context, Resolution resolution) {
                    CreativeTabSpec.Builder builder = CreativeTabSpec.builder(titleKey, icon); configure.accept(builder);
                    CreativeTabSpec spec = builder.build();
                    return () -> context.creativeTabs().register(id, spec);
                }
            });
            return new CreativeTabRef(id);
        }
    }
    /** Client-only entry point; keep renderer callbacks in src/client. */
    public static void renderer(ClientModContext context, BlockRef block, Consumer<BlockEntityRenderSpec.Builder> configure) {
        BlockEntityRenderSpec.Builder builder = BlockEntityRenderSpec.builder();
        configure.accept(builder);
        context.blockEntityRenderers().register(block, builder.build());
    }
}
