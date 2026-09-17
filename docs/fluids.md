# Fluid tanks and transfers (experimental)

The API now defines `FluidTank` and immutable `FluidVolume`. The shared runtime
implements a single-fluid tank with capacity limits, simulated fill/drain queries,
validated snapshot replacement and atomic transfers between two SDK tanks on the
same owning game thread. Different fluids do not mix. Empty tanks can accept a new
fluid. Simulation does not reserve capacity; execution checks the current contents.

`PortableFluidTank.save()` now produces a deterministic version-1 binary snapshot,
bounded to 1,039 bytes. `restore(byte[])` validates the entire payload before changing
state: malformed IDs, unsupported versions, truncation, trailing data and quantities
above the configured capacity are rejected. Capacity is never trusted from save data.
`dirty()`, `revision()` and `markPersisted(revision)` let native owners acknowledge
only snapshots actually saved; acknowledging a stale revision fails. Loading changed
contents marks the tank dirty conservatively. These methods do not perform disk I/O.

Quantities use integer SDK units: `FluidVolume.BUCKET` is 81,000. This is an internal
portable contract, not a claim that every loader uses that unit. Future native bridges
must convert exactly and retain unrepresentable residual quantities, never round up.
The first iteration identifies fluids by resource ID only; fluid-specific components
are not supported and must not be silently discarded by future native bridges.

Block definitions can now declare up to 16 named `FluidTankSpec` entries with
`.tank(spec)`. Shared `PortableBlockEntityStorage.tank(spec)` validates the exact
definition and returns a view whose actual mutations mark the owner dirty. Its
version-2 envelope saves tanks together with inventory and integer fields and
validates everything before replacement. Tankless definitions continue writing
version 1. Old saves initialize newly declared tanks as empty; unknown saved tanks
are rejected rather than discarded. Retained tank views remain valid after restore.

The central native generator now exposes `state.tank(spec)` to server tick callbacks.
Every retained handle checks the server thread and that its original block entity
is still attached. Actual fills/drains invoke the native dirty-save hook; reads,
simulations and rejected operations do not. Existing native save envelopes carry
the shared storage snapshot including tanks.

`FluidTankSpec` accepts an immutable map of absolute `FluidFace` values to INPUT,
OUTPUT, BOTH or CLOSED modes. Unspecified faces are CLOSED. The generated
`fluidPort` view maps native directions to this policy. This is not yet registered
as a third-party loader fluid capability.

The generated block interaction handles vanilla water/lava buckets on configured
faces. Filling or draining requires a whole bucket; insufficient space/fluid leaves
both unchanged. Stacked empty buckets exchange one bucket at a time, using Minecraft's
container helper to retain the remaining stack and add or drop the result when needed. Rejected
recognized buckets do not fall through into world-fluid placement. Creative-mode
item handling delegates to Minecraft's filled-container helper. Other fluid buckets,
arbitrary fluid containers are not supported yet.

Native bucket interactions send a bounded server-owned action-bar snapshot after
success or rejection, including fluid ID, percentage and exact quantities. Standalone
tank blocks without a custom use handler also report contents on normal block use.
This feedback does not replace custom menu handlers or add a continuously updating
gauge. Creative held-item retention is vanilla behaviour, not an indicator of failure.

For example, declare a four-bucket reservoir with top input and bottom output:

```java
var reservoir = new FluidTankSpec("reservoir", 4 * FluidVolume.BUCKET,
    Map.of(FluidFace.UP, FluidPortMode.INPUT, FluidFace.DOWN, FluidPortMode.OUTPUT));
var storage = BlockEntitySpec.builder(block).tank(reservoir).build();
```

`state.pushFluid(reservoir, FluidFace.DOWN, amount, simulate)` transfers to the first
compatible adjacent SDK-owned tank, in definition order, respecting source output
and destination input faces. It does not load chunks. Both owners are dirtied only
after a real transfer. This path is SDK-to-SDK, not arbitrary third-party storage.

For synchronized text displays, build a server snapshot with
`MenuState.builder().tank("tank", state.tank(reservoir)).build()` and send it using
the existing `MenuManager.open/update` methods. Labels can use `{tank.fluid}`,
`{tank.amount}`, `{tank.capacity}` and `{tank.percent}`. Amounts are SDK units;
percentage calculation is overflow-safe. This helper alone does not poll tanks.

Experimental `MenuManager.openLive(playerId, menu, intervalTicks, source)` now
owns the polling loop in shared runtime-core. A `MenuStateSource` returns an
`Optional<MenuState>` snapshot on the server thread. Return empty when the player
can no longer access the backing block, or it is unloaded/removed; never load a
chunk to keep a screen alive. Validate access before reading a retained tank handle.
An initially empty result leaves the existing menu unchanged. Later empty results
or exceptions close the live session. Intervals are 1-1200 server ticks, evaluated
at END phase; unchanged maps do not send update packets. Close, replacement,
disconnect and server shutdown release the source. Sources must be read-only and
must not call menu operations from inside `snapshot()`.

For an SDK-owned persistent block, register its screen and call
`context.menus().bindTank(menu, block, tankSpec)` during initialization, after
registering the block. The integrated persistence profile binds ordinary block
use to that screen and supplies the four `tank.*` fields every five server ticks
when changed. Bucket interactions remain separate. Only one menu/use binding is
allowed per block; unknown tanks and conflicting workbench bindings fail.
The server closes the view on death, spectator mode, dimension change, distance
over eight blocks, chunk unload, or replacement/removal of the original entity.
No chunk is loaded to maintain the screen. This is read-only inspection, not tank
mutation or a permission system for other machine actions.

The fixture's tank now uses this binding. `/enderfall_gauge` remains the independent
synthetic renderer test. Live tank-screen gameplay validation remains pending.

The integrated persistence development runtime also supports a declarative gauge:

```java
MenuSpec.builder("Tank")
    .gauge(new MenuGauge("tank.percent", 20, 35, 24, 80, 0xFF4488FF))
    .label(MenuLabel.text("{tank.fluid}: {tank.percent}%", 55, 40))
    .build();
```

The gauge fills from the bottom, has a contrasting border and empty background,
and clamps percentages to 0..100. Missing/malformed values show empty. Layouts
allow up to eight gauges and reject out-of-panel bounds. Registration fails on a
runtime without gauge support. Use the integrated `enderfall.persistence=true`
profile through the ordinary bootstrap; the original isolated preview bootstrap
does not enable gauges. This first renderer is solid-colour, not a fluid texture.

This is **not yet a verified in-game tank feature**. Remaining work includes
loader-native third-party transfer capability hooks, broader container handling,
textured gauges and real bucket/save/reload/transfer/menu acceptance.
Arbitrary third-party handlers are deliberately excluded from the atomic transfer
operation until their transaction semantics are handled explicitly.
