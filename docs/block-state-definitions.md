# Portable block-state definitions (experimental)

Generated feature runtimes now register custom state schemas on ordinary and
persistent blocks and apply their declared defaults. Reference runtimes reject
custom schemas explicitly. Server-world reads, atomic updates, custom initial
placement and pure neighbor-derived updates are connected. State-dependent native
geometry is connected. Existing
horizontal/six-way facing continues to work alongside custom state properties.

Properties and geometry belong with the block definition, not the registry list:

```java
static final BlockProperty<Boolean> OPEN = BlockProperty.bool("open");
static final BlockStateDefinition STATES = BlockStateDefinition.builder()
        .property(OPEN, false).build();
static final BlockStateShapes SHAPES = BlockStateShapes.create(STATES,
        state -> state.get(OPEN) ? BlockShape.empty() : BlockShape.fullCube());

// Inside CustomBlock.define(BlockDefinition block):
block.states(STATES);
block.stateShapes(SHAPES);
```

The `.build()` here snapshots a schema; consumer block registration still does not
require `.build()`. Boolean, bounded non-negative integer and Java enum properties
are supported. Enums require an explicit stable serialized name mapping. Keep the
same property objects for reads and writes: identical names do not imply identity.

`STATES.defaultState().with(OPEN, true)` returns an immutable value, not a world
update. `serializedValues()` and `STATES.parse(values)` provide strict round trips.
Decoding rejects missing, unknown and invalid entries rather than silently resetting
saved state. Definitions are limited to 4096 combinations. Geometry callbacks run
once per combination; collision/render queries only look up cached shapes.

`stateShapes` sets both native collision and selection outlines, taking precedence
over static shapes. It must reference the exact schema assigned with `states`.
Geometry is authored facing north and automatically rotated when horizontal or
six-way facing is enabled. Ordinary and persistent blocks use the same generated
implementation. Material copying intentionally copies neither schema nor geometry.
This does not yet provide separate dynamic collision/outline definitions. Rendered
models still come from blockstate assets.

## Custom placement

Keep placement rules in the portable block class, matching the responsibility of a
normal Minecraft block subclass:

```java
@Override public PortableBlockState onPlace(BlockPlacementContext placement) {
    return placement.state().with(OPEN, placement.clickedFace() == BlockDirection.DOWN);
}
```

The callback receives the state after built-in horizontal/six-way facing has been
chosen, the clicked face, the player's horizontal facing and the player's nearest
look direction. Six-way blocks can independently select clicked-face or view-directed
native placement in their registration options. The callback changes only the block's declared portable properties; native
facing is preserved. Returning null or a state from another definition fails the
placement instead of silently producing a different state. Property-copy operations
do not copy the behavior instance.

## Neighbor-derived state

Keep connection logic in the block class as well:

```java
@Override public PortableBlockState onNeighborUpdate(BlockNeighborContext neighbor) {
    if (neighbor.direction() != BlockDirection.WEST) return neighbor.state();
    return neighbor.state().with(CONNECTED_WEST, neighbor.sameBlock());
}
```

The callback identifies the changed direction and neighbor block ID. `sameBlock()`
handles the common self-connecting case, while `neighborState()` exposes declared
portable properties when the adjacent block is also an SDK block. Vanilla or other
native blocks intentionally expose only their registry ID. The callback is a pure
native `updateShape` bridge: return a replacement for this block's declared custom
state and do not perform world writes. The generator emits Minecraft's older
1.20.1/1.21.1 signature or its 1.21.4/26.2 replacement automatically.

This hook is enough for direct per-side connection properties. Rules that must inspect
several other positions still need a future bounded world-view API.

## Scheduled transitions

Opt in from the block class with `properties.scheduledTicks()`. Existing `onPlace`
and `onNeighborUpdate` overrides continue working. A block that needs delayed work
can instead return a `BlockTransition`:

```java
@Override public BlockTransition onNeighborChanged(BlockNeighborContext neighbor) {
    PortableBlockState changed = neighbor.state().with(CONNECTED, neighbor.sameBlock());
    return BlockTransition.after(changed, 4);
}

@Override public BlockTransition onScheduledTick(BlockScheduledTickContext tick) {
    return BlockTransition.stable(tick.state().with(SETTLING, false));
}
```

Delays are at least one server tick. Scheduled callbacks receive the exact dimension,
position and game time, run on the server thread, preserve native state, and may
schedule one further callback. Returning null, another block's schema, or an invalid
delay fails explicitly.

## Waterlogging

Call `properties.waterlogged()` in the block class. The generated native block then
uses Minecraft's real `waterlogged` property, detects water during placement, supports
water-bucket insertion and pickup, exposes the correct fluid state, and schedules
vanilla water ticks after neighbor changes. Do not also declare a custom property
named `waterlogged`. Waterlogging doubles the native state count and therefore counts
toward the 4096-state safety limit.

The persistent preview's `connecting_table` combines per-side connections,
state-dependent geometry, waterlogging, and a four-tick settling transition in one
unchanged Java 17 block class.

## Reading and changing a placed block

Use the registered block reference and an exact dimension/position. Access never
loads chunks and is restricted to the server thread:

```java
context.blockStates().get(BARREL, location).ifPresent(state -> {
    boolean open = state.get(OPEN);
});
context.blockStates().set(BARREL, location, OPEN, true);
context.blockStates().update(BARREL, location, state -> state.with(OPEN, !state.get(OPEN)));
```

Reads return empty and updates return false when the dimension/chunk is unavailable
or the position contains another block. Unknown block references, the wrong schema,
invalid values, off-thread calls and null update results fail explicitly. Updates
preserve native properties such as facing and notify clients and neighbors. An
unchanged transformation returns false.

Data generation can select a separate authored model for every complete state:

```java
data.blockStates(blockId, STATES, state -> ResourceId.of("my_mod",
        state.get(OPEN) ? "block/barrel_open" : "block/barrel_closed"));
```

This generates blockstate JSON only. It does not create models or imply the barrel
inventory/openers behavior has been implemented. Register the same schema with
`block.states(STATES)` before using these assets. Native state values use the stable
serialized names, including enum names supplied by the author. The built-in facing
property cannot be redeclared, and its combinations count toward the 4096 limit.
