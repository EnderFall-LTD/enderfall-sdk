# Directional blocks

Generated feature runtimes now support opt-in horizontal facing for ordinary and
persistent blocks:

```java
public final class MyWorkbenchBlock implements PortableBlock {
    @Override public void configure(Registration.BlockOptions properties) {
        properties.horizontalFacing();
    }
}

// Registry class: identity, material properties, and optional block item.
BLOCKS.block("workbench", MyWorkbenchBlock::new, p -> p.withItem());
```

The native state property is named `facing`, with `north`, `east`, `south`,
and `west`. New blocks default to north; player placement faces opposite the
player's horizontal viewing direction. Vanilla rotation and mirror operations
update that property. Custom outline/collision cuboids are authored facing north
and rotate with the state (north 0, east 90, south 180, west 270 degrees).

The feature uses native block states for persistence and synchronization, not an
extra SDK network packet. It does not add properties to blocks that have not
opted in. Copying properties does not copy the source block's facing behavior.

## Models and datagen

Custom model JSON still belongs to the author. Register its four blockstate
variants from portable datagen:

```java
context.dataGeneration().register(data ->
    data.horizontalBlockState(context.id("workbench"),
        context.id("block/workbench")));
```

This produces four variants with matching rotations and UV locking. It does not
generate the model or item model. Do not also call `blockModel` for that same ID:
that existing helper already writes a non-directional blockstate, and duplicate
resource paths are rejected. A manually authored blockstate JSON is also valid.

Block-entity renderer transforms are separate: enabling facing does not yet
automatically rotate item/fluid/model display transforms. General state reads in
renderer callbacks remain future work.

## Implementation and limits

State definitions are established during native construction using directional
subclasses; default facing is initialized after the entire object is constructed.
Both ordinary and persistent blocks share the facing and shape code. Rotated
shapes are precomputed per registered block, not rebuilt per frame or placement.

This is a built-in facing behavior used alongside the typed state API. Portable
block classes may customize their declared properties during placement with
`PortableBlock.onPlace(BlockPlacementContext)`. Waterlogging and neighbor-driven
connections are still pending. Unsupported
baseline/reference runtimes reject the feature rather than ignoring it.

The preview workbench opts in and has an asymmetric rear brace, with matching
model variants and shapes. Its existing ID and inventory schema are unchanged.
Use a disposable world when testing changed block-state definitions.

## Gameplay acceptance (pending)

Place workbenches while facing each cardinal direction. Check the rear brace is
opposite the front, and that targeting and collision follow it in every direction.
Check native structure rotation/mirroring, save/reload, client/server agreement,
and the existing inventory/menu. Non-directional tanks and timed workbenches
should retain their original state layouts and geometry.

Compilation across the nine targets and fixture parity do not replace those
in-game checks.

## Six-way facing

Use `properties.sixWayFacing()` in the portable block class for north/east/south/
west/up/down. Placement faces opposite the player's nearest look direction,
including looking up/down. Shapes are authored facing north; vertical variants
use the equivalent of `shape.rotateX(1)` for up and `rotateX(-1)` for down.
Native rotation/mirror operations preserve vertical directions as Minecraft defines.

Generate model variants with `data.sixWayBlockState(blockId, modelId)`; up uses
model X rotation 270 and down uses 90. Do not also write a horizontal or plain
blockstate for that ID. Horizontal and six-way modes are exclusive: the last
configuration call selects the mode. Property copying does not copy either mode.

The preview includes `enderfall_persistent_preview:orientation_test`, an ordinary
block with asymmetric geometry for six-orientation testing; it is not a barrel or
storage container. Existing workbenches retain horizontal facing. Test all six
orientations, matching outlines/collision, structure rotations, and save/reload.
Placing it against the underside of a block starts it in the compact shape, proving
the portable custom-placement callback; other placements start expanded and use
continues toggling the same custom state.
Six-way native bindings compile on all nine generated targets; gameplay verification
remains pending. A six-way property is only one prerequisite for a full barrel:
container menus, opening state and sounds remain separate work.
