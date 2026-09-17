# Portable block-entity rendering (experimental foundation)

The Java 17 API, shared registration validation, fixed-item and inventory-slot
bridges are implemented in the integrated persistence development profile for all
nine targets. Older versions draw immediately; 26.2 extracts item render state and
submits it without accessing the world or inventory. Native compilation is not
visual acceptance; the fixture still requires client and dedicated-server checks.

The initial scope is fixed items and items from persistent inventory slots:

```java
// In EnderfallClientMod.initialize(ClientModContext context).
var display = BlockEntityRenderSpec.builder()
    .item(new ItemRef(ResourceId.of("minecraft", "apple")), RenderTransform.at(0.5f, 1.02f, 0.5f)
        .rotate(90, 0, 0).scaled(0.35f))
    .build();
context.blockEntityRenderers().register(choppingBoardBlock, display);
```

Register the persistent block in common initialization before registering its
renderer. Only the owning mod may register a renderer for it, once, before
registration freezes. The service itself imports no Minecraft or client classes.
Dedicated-server registration fails explicitly.

Coordinates are block-local, with the origin at the minimum corner, measured in
blocks. Transform operations are translation, rotation around X, Y, Z (degrees),
then positive uniform scale. The native implementation uses FIXED item
display orientation and world light sampled at each display's translated position,
with the supplied overlay values and balanced pose-stack
push/pop even on failure. Definitions are immutable and bounded to
64 displays; non-finite transforms, invalid slots and empty plans are rejected.

Next implementation steps:

The shared visual-snapshot format is connected to vanilla chunk/update tags.
Common registration opts slots into disclosure:

```java
BlockEntitySpec.builder(choppingBoardBlock)
    .inventorySlots(3)
    .renderSlot(0)
    .renderSlot(1)
    .build();
```

No slots are exposed by default. Up to 64 slots can be declared, each within the
inventory bounds. This is permission to disclose item data to nearby tracking
clients; do not expose confidential inventory. The declaration does not change
the world-save format. Register a matching `.inventorySlot(index, transform)` in
the client render spec; undeclared slots are rejected at registration.

`RenderInventorySnapshot` captures detached copies of only these slots. Its
versioned, deterministic envelope is limited to 32,000 bytes and represents a full
replacement, including empty slots. Decoding checks all slot IDs, lengths, ordering
and trailing bytes before calling the native stack codec. A failed decode yields
no replacement snapshot; callers must not clear an existing snapshot first. Native
codecs still own component/NBT parsing limits and registry resolution. Oversized
encoding fails explicitly rather than truncating item data. The native bridge
handles that failure by sending an empty visual snapshot and logging one warning
until encoding recovers; actual inventory is untouched. Updates are requested
after committed inventory changes, independently of open menus. Initial chunk
updates include the same visual snapshot, never storage fields or processing data.

Remaining native integration:

- Verify insertion/removal, empty-slot clearing, initial chunk load, late join,
  component-rich items, oversized visual data, lighting, resource reloads and
  dedicated-server isolation in actual game sessions on each native ABI family.

The lighting sample uses the floor of each block-local coordinate (including
negative coordinates), so an item above a solid workbench samples the air above
it rather than the dark block interior. Unloaded sample chunks use the existing
block-entity light instead of forcing a chunk load. In 26.2 this light is captured
per item during extraction; submit remains world-independent. Visual retesting of
this lighting correction is still required.

## Custom model declaration foundation

Custom models can be described with a model resource reference and a block-local
transform. Experimental drawing backends exist for **all nine targets**. Compilation
and source parity do not establish visual parity; in-game acceptance is still pending.

```java
var lid = new ModelRef(ResourceId.of("my_mod", "block/machine/lid"));
var plan = BlockEntityRenderSpec.builder()
    .model(lid, RenderTransform.at(0, 0, 0))
    .build();
```

That reference means `assets/my_mod/models/block/machine/lid.json`; omit the
`models/` prefix and `.json` suffix. It is not a block or item registry ID.
The model's origin is the block's minimum corner, with no automatic item-style
centering or display pose. Up to 64 model displays are allowed and plans are
immutable. Resource existence is not checked by the declaration constructor.

The supported targets register standalone models before baking and resolve them from
the current model manager at render time. The initial backend uses an opaque/cutout,
untinted material with world lighting; translucent and state-dependent models are
not supported. Native missing-model fallback and loader resource diagnostics apply.
The preview timed workbench declares an oak-plank lid above its normal block model.
Visual rendering and resource reload checks remain pending.

Fabric uses `ModelLoadingPlugin` and `FabricBakedModelManager` to register and retrieve
these models, as described in its [model-loading API](https://github.com/FabricMC/fabric/blob/1.21.4/fabric-model-loading-api-v1/src/client/java/net/fabricmc/fabric/api/client/model/loading/v1/ModelLoadingPlugin.java).
Null lookups fall back to Minecraft's missing model rather than reaching the renderer.

On 26.2, Fabric extra-model keys and NeoForge standalone-model keys retain the
model's identity across reloads. Each extraction looks up the current baked model,
collects its parts with a position-seeded random source, and captures the transform
and light. Submission uses only this snapshot. The reusable render state's model
list is cleared before extraction, including when the entity is no longer valid.

Still required: visual/reload validation and richer missing-resource diagnostics.
Named animated parts and arbitrary callbacks remain outside this first step.

## Item model display poses

Item displays default to `ItemRenderPose.FIXED`. To select a different resource-model
display transform, pass a pose to either builder method:

```java
BlockEntityRenderSpec.builder()
    .inventorySlot(0, RenderTransform.at(.5f, 1.05f, .5f), ItemRenderPose.GROUND)
    .build();
```

`NONE`, `GROUND`, `GUI`, `HEAD`, and first/third-person left/right hand poses are also
available. These select the item's model transform; they do not create a player,
hand, camera or GUI. SDK translation, rotation and scale still position the display.
Resource packs can change the selected model transform. Old two-argument builder
calls and the two-argument `ItemDisplay` constructor retain the fixed default.
The native mapping is compiled for each target. Visual parity remains to be tested.

## Experimental fluid drawing

`BlockEntityRenderSpec.builder().fluid(tankSpec, bounds)` declares an axis-aligned
fluid display. `FluidRenderBounds` uses block-local coordinates in 0..1 and rejects
inverted, zero-size or non-finite bounds. `FluidCuboidMesh` generates six outward
faces with a bottom-up surface proportional to amount/capacity; empty tanks produce
no geometry. It does not round to millibuckets. At most 16 fluid displays are allowed.

The generated pre-26 bridges draw still-textured cuboids through a translucent
block-atlas buffer. Native appearance hooks resolve textures and location-aware
tint each frame, without retaining sprites across resource reloads. Block-local
UVs preserve texture scale as contents drain. Lighting samples above the tank and
preserves native fluid emission. Only declared public client snapshots are read.
Both native rendering paths use the same face-oriented UV mapping in
`FluidCuboidMesh`: north/east faces reverse the horizontal coordinate, and the
underside reverses its vertical coordinate. This avoids mirrored side textures
and keeps the mapping independent of loader/version.
The common tank definition must exactly match the display's tank specification.

26.2 resolves the baked fluid model during extraction and captures immutable
geometry, atlas UVs, tint and light. Submission schedules custom translucent geometry
using only that snapshot, without retaining a world or tank reference. A missing
tint source uses untinted white, matching vanilla's fallback. The shared baked-model
path does not yet promise support for loader-specific custom fluid rendering hooks.
Native fluid
appearance, transparency ordering, resource reloads and lighting still need visual
testing; compilation is not proof of those behaviours.

Tank contents can now be explicitly exposed with `.renderTank("reservoir")` on
the block entity's storage specification. The named tank must be declared there;
no tanks are public by default. Up to 16 names are supported. Only fluid identity
and amount are sent, not private storage fields. Updates follow committed tank
changes and are included in initial chunk snapshots, independently of open menus.

Item and tank snapshots share a 32,000-byte visual payload budget. Both are decoded
before either client snapshot is replaced, so malformed data cannot partially
replace the visual state. Tank entries are deterministic and capacity checked.
Live tank snapshot, late-join and fluid rendering validation remains pending.

Fabric's 26.1 migration replaces much of its earlier fluid-handler API with
`FluidModel`: https://www.fabricmc.net/2026/03/14/261.html . NeoForge 1.21.4 exposes
still/flowing textures and tint through `IClientFluidTypeExtensions`:
https://github.com/neoforged/NeoForge/blob/1.21.4/src/main/java/net/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions.java .
The SDK must resolve appearance per native ABI; neither API alone is the complete
portable tank mesh renderer.

Standalone vanilla JSON models are supported as described above. Animated callbacks,
custom shaders and custom render layers are not implemented by this foundation.
No changes to the separate furniture mod are required or made.

### Pivot points

Items and standalone models accept a model-local pivot, in block units:

```java
RenderTransform.at(0, 1.02f, 0)
        .pivot(.5f, 0, 15f / 16f)
        .rotate(45, 0, 0);
```

This holds the back edge of the preview lid in place while tilting it upwards.
The matrix is placement translation, pivot translation, X/Y/Z rotations, scale,
then negative pivot translation. The pivot remains at placement plus pivot, including
when scaling. Fluent call order does not change this composition. Item coordinates
use the chosen native item display pose as their basis; JSON block models use their
block-local coordinates. Existing transforms retain a zero pivot and their original
behavior, including the seven-argument constructor.

Pivots are finite and bounded to -16..16 on each axis. This is a static transform,
not an animation API on its own. Check that the preview lid's back edge stays
attached and its faces remain lit while moving.

### Pose transition math (experimental)

`RenderTransition` samples between two transforms without native classes or mutable
playback state:

```java
var closed = RenderTransform.at(0, 1.02f, 0).pivot(.5f, 0, 15f / 16f);
var opening = new RenderTransition(closed, closed.rotate(90, 0, 0),
        10, RenderTransition.Easing.SMOOTHSTEP);
var halfway = opening.sample(5); // elapsed ticks, including fractional ticks
```

`LINEAR` gives constant component speed; `SMOOTHSTEP` eases at both endpoints.
Samples before zero or after the duration hold the endpoint pose. Non-finite times
and non-positive durations are rejected. Angles follow authored values: 0 to 360
performs a full turn, not a shortest-path rotation. Keep pivots identical for a
fixed hinge. `RenderTransform.interpolate(target, progress)` also exposes the
underlying interpolation with strictly validated progress in [0,1].

Use `builder.animatedModel(model, new RenderAnimation(transition))` to play a
continuous forward/backward loop. All instances use world game time plus partial
ticks, starting at world tick zero, rather than restarting when entering view.
Pre-26 bridges sample during drawing; 26.2 samples during extraction and submits
only the immutable pose snapshot. Static model registration remains unchanged.

The preview lid uses named menu-driven playback rather than a continuous loop.
Verify smooth motion, a fixed hinge, pause/resume and chunk unload/reload.
This does not add interaction triggers, per-block playback state,
or server-synchronized animation events. Sampling a transition once when registering
a static display still does not animate it.

Items use the same loop through `animatedItem(item, animation)` or
`animatedInventorySlot(slot, animation)`. Both have an optional final
`ItemRenderPose` argument and default to `FIXED`. Inventory-slot animations still
require an explicitly exposed slot; empty slots draw nothing. The renderer samples
lighting at the animated placement. On 26.2, item poses are sampled during extraction,
not submission. The preview workbench apple bobs between 1.15 and 1.45 block units
over 30 ticks each way. This visual animation does not move or modify stored items.

### Playback modes and scheduled starts

`new RenderAnimation(transition)` retains `PING_PONG` playback. An optional second
argument selects `RenderAnimation.Playback.LOOP` (restart each cycle) or `ONCE`
(hold the last pose after completion). For continuous rotation, use `LINEAR`
easing and an authored 0-to-360-degree rotation with `LOOP`. Different endpoint
positions or scales will visibly jump at a loop boundary; no automatic blending
is added.

`animation.startingAt(absoluteGameTick)` returns a new immutable plan. It holds the
first pose before that world tick. The default start is world tick zero, so a
one-shot animation may already be finished when an existing world is opened.
This is not a placement-relative timer or interaction trigger. Native renderers
already sample this shared playback logic for both models and items; no new
loader-specific animation implementation is required.

### Server-controlled model playback (experimental)

`AnimationPlaybackState` is an experimental immutable descriptor containing a
validated animation name, absolute server start tick, and optional stop tick.
Sampling uses elapsed time, so a descriptor received late does not restart at its
first pose. Stop freezes the sampled pose. Definitions used with this descriptor
must have start tick zero; the descriptor supplies the actual start time.

The runtime's `NamedAnimationController` validates declared names and notifies its
owner only when playback changes. It supports one active named animation per
controller. Declare common names with `BlockEntitySpec.Builder.animation("open")`.
Common server-ticker code calls `state.playAnimation("open")` or
`state.stopAnimation()`. Calls use the owning server tick and thread; no custom
consumer packet is required. Repeated play calls restart playback, so trigger on
a state change rather than every tick.

Client registration uses `namedModel(model, idlePose, Map.of("open", opening))`.
Definitions must use relative start zero; undeclared names fail registration.
Without an active descriptor, or for a name not used by this model, the idle pose
is drawn. Named playback currently applies to models; item animations remain loops.

`menuAnimations("open", "close")` on common storage connects timed workbench
menus automatically: first viewer starts open, last viewer starts close. The
preview uses ten-tick one-shot motions between closed and 75 degrees. Additional
viewers do not restart the motion. Switching animations starts the authored pose;
interrupting a transition does not yet blend continuously from the interrupted pose.

The bounded versioned descriptor travels with vanilla block-entity tracking updates
and initial chunk data. Items, tanks and animation are decoded before any client
visual state is replaced; the combined limit remains 32,000 bytes. Animation state
is transient and resets on block reload, not saved to disk. It is visual state,
not authoritative gameplay storage. Late-arrival sampling is unit tested; real
multiplayer late-join, viewer-count and unload behavior still require live testing.
