package uk.co.enderfall.sdk.api.registry;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.*;

class RegistrationTest {
    private record InteractionListener(
            uk.co.enderfall.sdk.api.event.EventPriority priority,
            uk.co.enderfall.sdk.api.event.EventListener<uk.co.enderfall.sdk.api.event.InteractionEvent> listener) { }

    @Test
    @SuppressWarnings("unchecked")
    void separatelyAttachedPortableItemRunsBeforeClickedBlock() throws Exception {
        var listeners = new ArrayList<InteractionListener>();
        var bus = (uk.co.enderfall.sdk.api.event.EventBus) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { uk.co.enderfall.sdk.api.event.EventBus.class },
                (proxy, method, args) -> {
                    listeners.add(new InteractionListener(
                            (uk.co.enderfall.sdk.api.event.EventPriority) args[1],
                            (uk.co.enderfall.sdk.api.event.EventListener<uk.co.enderfall.sdk.api.event.InteractionEvent>) args[2]));
                    return null;
                });
        var blockRegistrar = (BlockRegistrar) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { BlockRegistrar.class }, (proxy, method, args) -> new BlockRef((ResourceId) args[0]));
        var itemRegistrar = (ItemRegistrar) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ItemRegistrar.class }, (proxy, method, args) -> new ItemRef((ResourceId) args[0]));
        var context = (ModContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ModContext.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "modId" -> "test";
                    case "blocks" -> blockRegistrar;
                    case "items" -> itemRegistrar;
                    case "events" -> bus;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var blockUses = new java.util.concurrent.atomic.AtomicInteger();
        var itemUses = new java.util.concurrent.atomic.AtomicInteger();
        var blocks = Registration.blocks("test");
        BlockRef block = blocks.block("six_way", () -> new uk.co.enderfall.sdk.api.block.PortableBlock() {
            @Override public void configure(Registration.BlockOptions options) { }
            @Override public void onUse(ModContext ignored, uk.co.enderfall.sdk.api.event.InteractionEvent event) {
                blockUses.incrementAndGet();
                event.handle();
            }
        }, options -> { });
        var items = Registration.items("test");
        ItemRef tool = items.item("tool", () -> new uk.co.enderfall.sdk.api.item.PortableItem() {
            @Override public void configure(ItemSpec.Builder options) { }
            @Override public void onUseOnBlock(ModContext ignored,
                    uk.co.enderfall.sdk.api.event.InteractionEvent event) {
                itemUses.incrementAndGet();
                event.handle();
            }
        }, options -> { });

        Registration.register(context, blocks);
        Registration.register(context, items);
        assertEquals(2, listeners.size());
        listeners.sort(Comparator.comparing(InteractionListener::priority));
        var event = new uk.co.enderfall.sdk.api.event.InteractionEvent(
                uk.co.enderfall.sdk.api.event.InteractionEvent.Kind.USE_BLOCK,
                uk.co.enderfall.sdk.api.event.InteractionEvent.Side.SERVER, UUID.randomUUID(), block.id(),
                new uk.co.enderfall.sdk.api.blockentity.BlockLocation(
                        ResourceId.of("minecraft", "overworld"), 1, 2, 3),
                tool.id(), uk.co.enderfall.sdk.api.event.InteractionEvent.Hand.MAIN_HAND, false);
        for (InteractionListener listener : listeners) listener.listener().handle(event);

        assertEquals(1, itemUses.get());
        assertEquals(0, blockUses.get());
        assertTrue(event.handled());
    }

    @Test
    @SuppressWarnings("unchecked")
    void customItemFactoryConfiguresOnceAndReceivesOnlyUnhandledServerUses() throws Exception {
        var factories = new java.util.concurrent.atomic.AtomicInteger();
        var uses = new java.util.concurrent.atomic.AtomicInteger();
        var blockUses = new java.util.concurrent.atomic.AtomicInteger();
        var specs = new HashMap<ResourceId, ItemSpec>();
        ItemRegistrar registrar = (ItemRegistrar) Proxy.newProxyInstance(ItemRegistrar.class.getClassLoader(),
                new Class<?>[] { ItemRegistrar.class }, (proxy, method, args) -> {
                    ResourceId id = (ResourceId) args[0];
                    specs.put(id, (ItemSpec) args[1]);
                    return new ItemRef(id);
                });
        var listener = new java.util.concurrent.atomic.AtomicReference<uk.co.enderfall.sdk.api.event.EventListener<uk.co.enderfall.sdk.api.event.InteractionEvent>>();
        var bus = (uk.co.enderfall.sdk.api.event.EventBus) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { uk.co.enderfall.sdk.api.event.EventBus.class },
                (proxy, method, args) -> {
                    listener.set((uk.co.enderfall.sdk.api.event.EventListener<uk.co.enderfall.sdk.api.event.InteractionEvent>) args[args.length - 1]);
                    return null;
                });
        var context = (ModContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ModContext.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "modId" -> "test";
                    case "items" -> registrar;
                    case "events" -> bus;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var items = Registration.items("test");
        ItemRef tool = items.item("tool", () -> {
            factories.incrementAndGet();
            return new uk.co.enderfall.sdk.api.item.PortableItem() {
                @Override public void configure(ItemSpec.Builder properties) {
                    properties.maxStackSize(32).rarity(Rarity.UNCOMMON);
                }
                @Override public void onUse(ModContext ignored,
                        uk.co.enderfall.sdk.api.event.InteractionEvent event) {
                    uses.incrementAndGet();
                    event.handle();
                }
                @Override public void onUseOnBlock(ModContext ignored,
                        uk.co.enderfall.sdk.api.event.InteractionEvent event) {
                    blockUses.incrementAndGet();
                    assertEquals(uk.co.enderfall.sdk.api.event.InteractionEvent.Hand.OFF_HAND,
                            event.hand().orElseThrow());
                    assertTrue(event.sneaking());
                    event.handle();
                }
            };
        }, properties -> properties.maxStackSize(16));
        assertEquals(0, factories.get());
        Registration.register(context, items);
        assertEquals(1, factories.get());
        assertEquals(16, specs.get(tool.id()).maxStackSize());
        assertEquals(Rarity.UNCOMMON, specs.get(tool.id()).rarity());

        UUID player = UUID.randomUUID();
        for (var side : uk.co.enderfall.sdk.api.event.InteractionEvent.Side.values()) {
            listener.get().handle(new uk.co.enderfall.sdk.api.event.InteractionEvent(
                    uk.co.enderfall.sdk.api.event.InteractionEvent.Kind.USE_ITEM, side, player, tool.id()));
        }
        var blockUse = new uk.co.enderfall.sdk.api.event.InteractionEvent(
                uk.co.enderfall.sdk.api.event.InteractionEvent.Kind.USE_BLOCK, player, tool.id());
        listener.get().handle(blockUse);
        var toolOnBlock = new uk.co.enderfall.sdk.api.event.InteractionEvent(
                uk.co.enderfall.sdk.api.event.InteractionEvent.Kind.USE_BLOCK,
                uk.co.enderfall.sdk.api.event.InteractionEvent.Side.SERVER, player,
                ResourceId.of("minecraft", "stone"),
                new uk.co.enderfall.sdk.api.blockentity.BlockLocation(
                        ResourceId.of("minecraft", "overworld"), 1, 2, 3),
                tool.id(), uk.co.enderfall.sdk.api.event.InteractionEvent.Hand.OFF_HAND, true);
        listener.get().handle(toolOnBlock);
        var consumed = new uk.co.enderfall.sdk.api.event.InteractionEvent(
                uk.co.enderfall.sdk.api.event.InteractionEvent.Kind.USE_ITEM, player, tool.id());
        consumed.handle();
        listener.get().handle(consumed);
        consumed.cancel();
        listener.get().handle(consumed);
        assertEquals(1, uses.get());
        assertEquals(1, blockUses.get());
        assertTrue(toolOnBlock.handled());
    }

    @Test
    @SuppressWarnings("unchecked")
    void customBlockFactoryConfiguresOnceAndReceivesOnlyUnhandledServerUses() throws Exception {
        var calls = new ArrayList<String>();
        var specs = new HashMap<ResourceId, BlockSpec>();
        var delegate = context(calls, specs);
        var listener = new java.util.concurrent.atomic.AtomicReference<uk.co.enderfall.sdk.api.event.EventListener<uk.co.enderfall.sdk.api.event.InteractionEvent>>();
        var bus = (uk.co.enderfall.sdk.api.event.EventBus) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { uk.co.enderfall.sdk.api.event.EventBus.class },
                (p, m, a) -> {
                    listener.set((uk.co.enderfall.sdk.api.event.EventListener<uk.co.enderfall.sdk.api.event.InteractionEvent>) a[a.length - 1]);
                    return null;
                });
        var context = (ModContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ModContext.class },
                (p, m, a) -> m.getName().equals("events") ? bus : m.invoke(delegate, a));
        var factories = new java.util.concurrent.atomic.AtomicInteger();
        var uses = new java.util.concurrent.atomic.AtomicInteger();
        var blocks = Registration.blocks("test");
        var block = blocks.block("custom", () -> {
            factories.incrementAndGet();
            return new uk.co.enderfall.sdk.api.block.PortableBlock() {
                public void configure(Registration.BlockOptions p) { p.strength(2).sound(SoundPreset.WOOD); }
                public void onUse(ModContext c, uk.co.enderfall.sdk.api.event.InteractionEvent e) { uses.incrementAndGet(); e.handle(); }
            };
        }, p -> p.strength(5));
        blocks.block("copy", p -> p.copyFrom(block));
        assertEquals(0, factories.get());
        Registration.register(context, blocks);
        assertEquals(1, factories.get());
        assertEquals(5, specs.get(block.id()).hardness());
        assertEquals(SoundPreset.WOOD, specs.get(block.id()).sound());
        assertTrue(specs.get(block.id()).behavior().isPresent());
        assertTrue(specs.get(ResourceId.of("test", "copy")).behavior().isEmpty(),
                "Copying properties must not copy behavior");
        var player = UUID.randomUUID();
        for (var side : uk.co.enderfall.sdk.api.event.InteractionEvent.Side.values()) {
            var event = new uk.co.enderfall.sdk.api.event.InteractionEvent(
                    uk.co.enderfall.sdk.api.event.InteractionEvent.Kind.USE_BLOCK, side, player, block.id());
            listener.get().handle(event);
        }
        var consumed = new uk.co.enderfall.sdk.api.event.InteractionEvent(uk.co.enderfall.sdk.api.event.InteractionEvent.Kind.USE_BLOCK, player, block.id());
        consumed.handle(); listener.get().handle(consumed);
        consumed.cancel(); listener.get().handle(consumed);
        var copy = new uk.co.enderfall.sdk.api.event.InteractionEvent(uk.co.enderfall.sdk.api.event.InteractionEvent.Kind.USE_BLOCK, player, ResourceId.of("test", "copy"));
        listener.get().handle(copy);
        assertEquals(1, uses.get());
        assertFalse(copy.handled(), "Copying properties must not copy behavior");
    }

    @Test void recipesPrecedeMenusRegardlessOfGroupOrder() {
        List<String> calls = new ArrayList<>();
        var recipes = Registration.recipes("test");
        var type = recipes.workbench("assembly", 3);
        var menus = Registration.menus("test");
        menus.workbench("bench", "Bench", type, p -> {}, craft -> {});
        var context = (ModContext) Proxy.newProxyInstance(ModContext.class.getClassLoader(), new Class<?>[] { ModContext.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("modId")) return "test";
                    Class<?> service = method.getReturnType();
                    return Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] { service }, (p, m, a) -> {
                        calls.add(method.getName());
                        if (method.getName().equals("recipes")) return type;
                        return new uk.co.enderfall.sdk.api.ui.WorkbenchRef((ResourceId) a[0]);
                    });
                });
        Registration.register(context, menus, recipes);
        assertEquals(List.of("recipes", "workbenches"), calls);
    }

    @Test void callbacksCannotAddUnregisteredDeclarationsDuringAttachment() {
        var blocks = Registration.blocks("test");
        blocks.block("outer", p -> blocks.block("hidden"));
        var calls = new ArrayList<String>();
        assertThrows(IllegalStateException.class, () -> Registration.register(context(calls, new HashMap<>()), blocks));
        assertTrue(calls.isEmpty());
    }

    private static ModContext context(List<String> calls, Map<ResourceId, BlockSpec> blocks) {
        BlockRegistrar registrar = (BlockRegistrar) Proxy.newProxyInstance(BlockRegistrar.class.getClassLoader(),
                new Class<?>[] { BlockRegistrar.class }, (proxy, method, args) -> {
                    ResourceId id = args[0] instanceof ResourceId value ? value : ResourceId.of("test", (String) args[0]);
                    calls.add("block:" + id); blocks.put(id, (BlockSpec) args[1]); return new BlockRef(id);
                });
        ItemRegistrar items = (ItemRegistrar) Proxy.newProxyInstance(ItemRegistrar.class.getClassLoader(),
                new Class<?>[] { ItemRegistrar.class }, (proxy, method, args) -> {
                    ResourceId id = (ResourceId) args[0]; calls.add("item:" + id); return new ItemRef(id);
                });
        return (ModContext) Proxy.newProxyInstance(ModContext.class.getClassLoader(), new Class<?>[] { ModContext.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "modId" -> "test";
                    case "blocks" -> registrar;
                    case "items" -> items;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    @Test void declarationsAreDeferredAndCopiesResolveAcrossUnorderedGroups() {
        var first = Registration.blocks("test");
        var second = Registration.blocks("test");
        BlockRef base = first.block("base", p -> p.strength(2).sound(SoundPreset.WOOD));
        BlockRef copy = second.block("copy", p -> p.copyFrom(base).strength(5, 10).withItem());
        List<String> calls = new ArrayList<>(); Map<ResourceId, BlockSpec> specs = new HashMap<>();
        assertEquals(ResourceId.of("test", "copy"), copy.id());
        assertTrue(calls.isEmpty());
        var context = context(calls, specs);
        Registration.register(context, second, first);
        assertEquals(5, specs.get(copy.id()).hardness());
        assertEquals(SoundPreset.WOOD, specs.get(copy.id()).sound());
        assertEquals(2, specs.get(base.id()).hardness());
        assertThrows(IllegalStateException.class, () -> second.block("late"));
        assertThrows(IllegalStateException.class, () -> Registration.register(context, first));
    }
    @Test void cyclesAndMissingSourcesFailBeforeNativeWrites() {
        var blocks = Registration.blocks("test");
        blocks.block("a", p -> p.copyFrom(new BlockRef(ResourceId.of("test", "b"))));
        blocks.block("b", p -> p.copyFrom(new BlockRef(ResourceId.of("test", "a"))));
        var calls = new ArrayList<String>();
        assertThrows(IllegalArgumentException.class, () -> Registration.register(context(calls, new HashMap<>()), blocks));
        assertTrue(calls.isEmpty());
        var missing = Registration.blocks("test");
        missing.block("missing", p -> p.copyFrom(new BlockRef(ResourceId.of("test", "absent"))));
        assertThrows(IllegalArgumentException.class, () -> Registration.register(context(calls, new HashMap<>()), missing));
        assertTrue(calls.isEmpty());
    }
    @Test void blockItemCollisionAndInvalidConfigurationArePreflighted() {
        var blocks = Registration.blocks("test"); blocks.block("same", p -> p.withItem());
        var items = Registration.items("test"); items.item("same");
        var calls = new ArrayList<String>();
        assertThrows(IllegalArgumentException.class, () -> Registration.register(context(calls, new HashMap<>()), blocks, items));
        assertTrue(calls.isEmpty());
        var invalid = Registration.items("test"); invalid.item("bad", p -> p.durability(10));
        assertThrows(IllegalArgumentException.class, () -> Registration.register(context(calls, new HashMap<>()), invalid));
        assertTrue(calls.isEmpty());
    }
    @Test void copyBaseAndExplicitOverridesAreRetained() {
        ResourceId oak = ResourceId.of("minecraft", "oak_planks");
        BlockSpec source = BlockSpec.builder().copyFrom(oak).strength(3, 6).build();
        BlockSpec copy = BlockSpec.builder().copyFrom(source).friction(0.8f).build();
        assertEquals(Optional.of(oak), copy.copySource());
        assertTrue(copy.overrides(BlockSpec.Property.STRENGTH));
        assertTrue(copy.overrides(BlockSpec.Property.FRICTION));
        assertFalse(copy.overrides(BlockSpec.Property.SOUND));
        assertFalse(BlockSpec.builder().strength(9, 9).copyFrom(oak).build().overrides(BlockSpec.Property.STRENGTH));
        assertThrows(IllegalArgumentException.class, () -> BlockSpec.builder().strength(Float.NaN, 1));
    }
}
