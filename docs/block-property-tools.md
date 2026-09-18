# Block-property tools (experimental)

Portable wrench, hammer, chisel and configuration tools can edit typed custom block
state without importing native property or world classes. Give the policy a stable ID,
then let each block choose the properties that policy may edit:

```java
public static final BlockToolRef WRENCH = BlockToolRef.of("example", "wrench");
public static final BlockProperty<Boolean> OPEN = BlockProperty.bool("open");
public static final BlockProperty<Integer> STYLE = BlockProperty.integer("style", 0, 3);
public static final BlockStateDefinition STATES = BlockStateDefinition.builder()
        .property(OPEN, false)
        .property(STYLE, 0)
        .build();

@Override
public void configure(Registration.BlockOptions properties) {
    properties.states(STATES).toolProperties(WRENCH, OPEN, STYLE);
}
```

The tool item keeps its selected index in declared per-stack data. Crouching selects
the next property; ordinary use cycles the selected property's declared value order:

```java
private static final ItemDataKey<Integer> SELECTION = ItemDataKey.integer(
        ResourceId.parse("example:wrench_selection"), 0, 63);

@Override
public void configure(ItemSpec.Builder properties) {
    properties.maxStackSize(1).durability(500).data(SELECTION);
}

@Override
public void onUseOnBlock(ModContext context, InteractionEvent event) {
    BlockLocation location = event.blockLocation().orElseThrow();
    MutableItemData data = event.itemData().orElseThrow();
    int selection = data.getOrDefault(SELECTION, 0);
    BlockRef block = new BlockRef(event.target());

    Optional<BlockToolResult> result = event.sneaking()
            ? context.blockStates().selectNextToolProperty(block, location, WRENCH, selection)
            : context.blockStates().cycleToolProperty(block, location, WRENCH, selection);
    result.ifPresent(change -> {
        data.set(SELECTION, change.selectionIndex());
        if (change.changed() && !event.creativeMode()) {
            event.itemStack().orElseThrow().damage(1);
        }
        event.handle();
    });
}
```

Both operations verify that the target is a registered block belonging to this mod,
the chunk is already loaded, and the named policy is declared for that block. Selection
indices are normalized, values wrap deterministically, and a world mutation is atomic.
Unsupported blocks return `Optional.empty()` so vanilla or another mod may handle the
interaction. Property policies are not inherited from native material copies and may
only reference properties in the block's own state definition.

Keep selection in item data rather than an item-class field. A portable item definition
is created once per registered item, so a field would be shared by every player and
every stack. The persistent preview's `Portable Tool Test` demonstrates the safe pattern.
