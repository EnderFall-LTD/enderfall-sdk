# Portable block entities: implementation status

The shared storage foundation and generated persistence stack compile on all nine
targets: Fabric/NeoForge 1.21.1, 1.21.4 and 26.2, plus Fabric/Forge/NeoForge 1.20.1.
This includes native registration, saved inventory, instant/timed recipes, stored
outputs, menus, progress/status screens and sided-container automation.

These development sources are **not packaged into normal runtime JARs yet**.
The opt-in SDK development build now connects them to the ordinary consumer
bootstrap; the default build retains its previous unsupported-feature errors. The isolated
`persistent-preview` example includes instant and timed block-owned workbenches.
The user reported initial timed crafting working on Fabric 1.21.4. This is not
proof of save/reload, hopper, break/drop, multiplayer or other-target behaviour.

## SDK target expansion

`BlockEntityNativePolicy` selects storage rules for all nine targets. They share
inventory, processing, tick, ownership and sided-automation logic. Loader rules
select immediate or deferred registration; version rules select native save APIs,
item codecs and block-removal hooks. No consumer source changes are involved.

The independent SDK compile lanes are:

```powershell
.\gradlew.bat :runtime-generated-fabric-1.21.1:compileBlockEntityStorageJava :runtime-generated-fabric-1.21.4:compileBlockEntityStorageJava --no-daemon --console=plain
```

These generate only native storage under each project's `build/generated/blockEntityStorage`.
They do not install a preview mod, change published artifacts, or advertise complete
persistence support. `compileBlockEntityPreviewJava` additionally checks native
platform registration, instant/timed menus, recipe processing and client screens.
Compilation is separate from normal-runtime integration and gameplay acceptance.

## Integrated development artifacts

From the SDK root in PowerShell:

```powershell
.\gradlew.bat verifyPersistenceArtifacts '-Penderfall.persistence=true' --no-daemon --console=plain
```

This builds and inspects all nine loader-ready development runtime JARs, checking
that storage, processing and the feature-enabled consumer bootstrap are actually
packaged. Their filenames contain `persistence-dev`; Maven publishing is disabled
for this profile. Generated sources and compiled classes use separate directories
from the baseline parity build. The profile is an SDK build option, not a new
portable mod-author API or a promise of release-ready gameplay support.

Do not run baseline byte-parity checks with this profile enabled: these artifacts
intentionally add behaviour. Run ordinary parity checks without the property.

## One fixture across all nine targets

The fixture's three portable Java classes are compiled once against the API with
Java 17. `PersistenceFixtureMain` generates only loader glue and target-format
resources; no version branches or native imports are added to those classes.

```powershell
.\gradlew.bat verifyPersistenceFixtures '-Penderfall.persistence=true' '-Penderfall.persistenceFixture=true' --no-daemon --console=plain
```

The verification builds nine development fixture JARs and compares their portable
class bytes, including the Java 17 class-file version. These are development-mapped
fixtures for the SDK's run tasks, not independently distributable release mods.

For example, launch the same fixture on an older Forge target:

```powershell
.\gradlew.bat :runtime-generated-forge-1.20.1:runClient '-Penderfall.persistence=true' '-Penderfall.persistenceFixture=true' --no-daemon --console=plain
```

Or on NeoForge 26.2:

```powershell
.\gradlew.bat :runtime-generated-neoforge-26.2:runClient '-Penderfall.persistence=true' '-Penderfall.persistenceFixture=true' --no-daemon --console=plain
```

Replace `runClient` with `runServer` for a dedicated-server fixture. Every target
uses its own `run/persistence-fixture/client` or `server` directory. Server EULA
acceptance and gameplay checks remain explicit; the fixture does not accept the
EULA, create acceptance results, or claim gameplay success automatically.

## Shared definitions

The experimental Java 17 API defines a block's inventory size and bounded saved
integer fields without importing Minecraft or loader classes:

```java
BlockEntityInt progress = new BlockEntityInt("progress", 0, 0, 200);
BlockEntitySpec machine = BlockEntitySpec.builder(workbenchBlock)
        .inventorySlots(3) // three persisted inputs; current instant-craft result is derived
        .field(progress)
        .build();
```

`BlockEntityState` exposes typed field reads/writes; field names are persistent keys.
Specifications are immutable. Duplicate names, undeclared fields, and invalid values
fail instead of silently creating mismatched state.

### Lazy structure loot

Storage blocks that can be populated by structures, world generation or commands may
opt into vanilla-compatible lazy loot-table data:

```java
BlockEntitySpec cabinet = BlockEntitySpec.builder(cabinetBlock)
        .inventorySlots(27)
        .lootTableInventory()
        .build();
```

The declaration enables native `LootTable` and `LootTableSeed` persistence; it does
not assign a table to every placed cabinet. A pending table is retained across world
saves and populated exactly once when a player opens it, a hopper inspects it, or the
block is removed and its contents must drop. Normal player-placed cabinets remain
empty. Missing or invalid loot-table IDs follow the target Minecraft version's vanilla
handling. The preview contains a deterministic one-diamond table for live validation.

### Portable registration and opening contract

The experimental entry points are now implemented in the API and shared routing layer:

```java
context.blocks().registerPersistentWithItem("workbench", blockProperties, itemProperties, machine);
WorkbenchRef menu = context.workbenches().register("workbench_menu",
        WorkbenchSpec.builder("Workbench", recipes).persistent(machine).build(), craft -> { });
context.workbenches().openAt(playerId, menu,
        new BlockLocation(dimensionId, blockX, blockY, blockZ));
```

The storage's block ID must match the block being registered. This creates a new
block/type/item together, not a retrofit of an already registered plain block.
Registration respects consumer namespaces, duplicate IDs and lifecycle freezing.
Persistent menus require `openAt`; normal `open` cannot silently discard position.
Temporary menus retain their existing API and reject persistent opening.

**This example is not runnable against shipped targets yet.** Baseline adapters still
report persistent support disabled. Only the isolated Fabric 1.21.4 preview platform
reports support and wires these routes. Shared routing rejects unsupported registration
before reserving IDs, with the mod ID and target in the error.
`InteractionEvent.blockLocation()` now exposes
an optional exact position, supplied by the preview block-use hook. Existing constructors
and the baseline platform event hooks leave it empty; do not guess coordinates from
the block type or player's position.

## Implemented shared runtime

`PortableBlockEntityStorage<S>` owns inventory independently of menus. It provides
defensive stack copies, saved integer state, revision-based dirty tracking, and a
bounded binary snapshot format. Successful serialization alone does not mark data as
persisted: the native world-save integration must acknowledge the saved revision.

Restore validates into temporary storage before replacing live state. Truncated,
oversized, trailing, wrong-block, or incompatible data fails without emptying the
inventory. Added integer fields receive defaults. Removed fields and changed slot
counts require explicit migration. All access is on the owning game thread.

The internal `BlockEntityStackCodec<S>` preserves native item components/NBT as opaque
bytes. It is not a public item wrapper or Java serialization. Native implementations
must use registry-aware item serialization and enforce stack limits. This envelope
does not promise cross-Minecraft-version save conversion, and is not a network packet
format: saves allow up to 8 MiB total and 64 KiB per stack.

## Native persistence development slice

`BlockEntityPreviewSources` centrally generates `StoredBlockEntity`, its block/type
registration binding, and a registry-aware native stack codec. It calls the shared
storage implementation from native NBT save/load hooks, uses the supplied registry
lookup even when loading before a level is attached, and calls `setChanged` after
server-side mutations. Client-side mutations are rejected. Item NBT encoding and
decoding are bounded; malformed data is not replaced with an empty stack.

The implementation follows the native registration and persistence hooks described
in [Fabric's 1.21.4 block-entity guide](https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.4/develop/blocks/block-entities.md).
Writing chunk NBT is not proof of a disk flush, so the hook does not falsely mark a
snapshot as durably persisted. It does not send inventory contents in chunk updates.

The generated block now owns a native `SimpleContainer`. Vanilla slots can use live
stacks, while `setChanged` validates and copies the whole container into shared saved
storage before notifying viewers. A failed copy/validation leaves saved storage
unchanged and restores the native slots from that snapshot. Save also copies the
current native inventory. Direct SDK stack reads remain defensive copies.

`WorkbenchMenuEmitter` has a persistent development mode that uses this container
instead of creating temporary inputs. It retains the shared recipe/slot implementation,
requires exactly the recipe's input-slot count, checks the actual block entity and
eight-block access distance, and attaches/removes a container listener with the menu.
Closing this menu does not clear its inputs. The output remains a derived instant-craft
result, not a stored machine output; timed processing is a later implementation slice.

The native preview now includes an `openAt` helper. It checks the server thread,
player dimension, loaded chunk without requesting a load, block-entity identity,
storage schema, and player reach before creating the persistent server menu. It is
is now called by the preview platform's `openPersistentWorkbench` implementation.

`bindBlock` now connects the preview's block-use hook to that opener. Binding checks
the storage definition and recipe input-slot count, and rejects a second handler on
the same block. The hook constructs a portable event using the actual dimension and
clicked block coordinates, passes through unbound/spectator interactions, and preserves
handled/cancelled outcomes. A bound workbench opens on the server and acknowledges the
click on the client. This event is local to the preview binding; it is not additionally
published to the SDK-wide interaction bus. Platform wiring must avoid duplicate delivery.

The generated block's removal hook drops contents on the server when replaced by a
different block, clears the inventory, and updates comparator neighbours. Same-block
state changes do not trigger drops. These hooks need real break/reopen/multi-viewer
validation before shipping; compilation alone cannot establish loss/duplication safety.

Compile this slice without changing the production runtime or reference-parity output:

```powershell
.\gradlew.bat :runtime-generated-fabric-1.21.4:compileBlockEntityPreviewJava --no-daemon --console=plain --offline
```

Sources/classes live under that project's `build/generated/blockEntityPreview` and
`build/classes/blockEntityPreview`. The task is opt-in; it is not wired into release
JARs or publication. Unsupported preview targets fail explicitly before source output.
Do not use configuration-on-demand for this native task: the existing parity setup
also requires the reference project to be configured.

`PlatformServiceEmitter` now composes a `FabricPersistentPlatformAdapter` from the same
normal platform operations plus persistent registration/opening operations. It registers
the native block/type and block item, populates the existing block/item lookup maps,
reuses normal recipe/menu/client-screen registration, binds block clicks, and routes
explicit position-based opens. Unknown or mismatched storage and multiple workbench
bindings for one block are rejected. No baseline platform output is replaced.

`FabricPersistentPreviewBootstrap.initialize(...)` explicitly creates this platform,
initializes a portable mod and attaches the normal runtime services. Duplicate attempts
are rejected before registration, including retries after failed initialization. It
must be called instead of, never alongside, the normal consumer bootstrap. This helper
is development-only: normal generated consumer metadata does not select it and the
preview classes are still excluded from runtime JARs. An opt-in
[persistent workbench fixture](https://github.com/EnderFall-LTD/enderfall-sdk/tree/main/examples/persistent-preview) now selects
this bootstrap in a separate development-client directory. Its portable classes
compile against only the SDK API; Fabric bootstrap glue is kept outside that root.
Its resources are explicit 1.21.4 fixtures, not portable data-generation support.
The client registered the preview mod, entered a single-player world and exited
cleanly. Crafting, retained inventory and break/drop correctness are not established
by those startup logs. The ordinary demo remains unchanged.
It must not be enabled in ordinary gameplay until block breaking, container ownership,
and persistence behaviour are verified. Compilation
proves native method compatibility, not successful world save/reload.

## Portable server ticking (preview only)

`BlockEntitySpec.Builder.serverTicker(BlockEntityTicker)` now installs optional
server-side behaviour on the generated Fabric 1.21.4 preview. Definitions without
a callback, client worlds, and mismatched native block-entity types receive no ticker.

```java
BlockEntityInt elapsed = new BlockEntityInt("elapsed", 0, 0, 200);
BlockEntitySpec machine = BlockEntitySpec.builder(workbenchBlock)
        .inventorySlots(3)
        .field(elapsed)
        .serverTicker(state -> {
            int ticks = state.get(elapsed);
            if (ticks < 200) state.set(elapsed, ticks + 1);
        })
        .build();
```

The callback runs once per native server block-entity tick while the block is ticking,
not while unloaded. Saved values are per block; the callback itself is shared across
instances. Reads/writes enforce the owning server thread and changed values mark
the native block dirty. Never retain the state for asynchronous work.

A callback runtime exception is logged with the block ID (including mod namespace),
target, dimension and position, and disables that instance's ticker until reloaded.
Other instances keep ticking. Updates before failure are not rolled back; this is
not a transactional recipe-processing API. Fatal JVM errors are not swallowed.

This callback currently exposes saved integer fields, not inventory operations or
client progress synchronization. The example above is a timer, not a crafting
machine. Timed recipes use the separate `persistentTimed` workbench binding described
below, which implements consumption, stored output and synchronized progress.

## Implemented processing and remaining acceptance

The preview's timed machines also expose native `WorldlyContainer` ports, driven by
the shared `PortableMachinePorts` policy. Inputs 1..5 map to up/north/east/south/west;
down exposes only the stored output. Missing inputs expose no slot, insertion into
output and extraction from inputs are rejected. Untimed definitions expose no ports.
This is fixed positional routing, not recipe-aware sorting or configurable facing.
Native mutations delegate to the existing owned container and its server-side guards.
Live hopper throughput, save/reload and loss/duplication checks remain pending.

The shared runtime now includes `PortableProcessingCycle`, a pure timed-processing
decision engine. It advances only on supplied server ticks, pauses on output-capacity
failure, resets on missing recipes, and restarts when the effective recipe revision
or duration changes. Native recipe matching must supply that revision from the actual
definition, not just the recipe ID. Completion is a proposed transition, not a craft
callback or inventory mutation.

`PortableBlockEntityStorage.replaceInventoryAndFields` validates the full replacement
inventory and selected saved integer fields before committing either. This allows
input consumption, stored output and progress reset to share one storage revision.
Invalid stacks or field values leave both unchanged.

The generated preview now has a server-only `commitProcessing` operation: it validates
slot capacities, copies stacks and validates saved fields before replacing inventory
and processing state, marks the block dirty, then notifies viewers. Notification
failures are logged after commit rather than reporting a failed transaction to retry.
The native save/load hooks store active recipe identity, revision, duration and elapsed
ticks using the bounded, versioned `ProcessingStateCodec`. Malformed processing data
is rejected before restoring inventory; older snapshots without processing data load
as idle. This is implemented and compile-checked, not live save/reload proof.

`FabricTimedWorkbenchProcessor` is now generated alongside the preview. Its internal
binding requires one extra stored-output slot and installs a loaded-block server tick
handler independent of menu viewers. It selects native recipes, fingerprints their
serialized definitions, checks item/component-aware output capacity, and commits
consumption/output with the shared processing transition. Crafting remainders return
to their original input slot; if leftovers and remainders cannot coexist there, the
machine pauses without consuming anything. There is no overflow drop or hidden void.
It does not emit the player-owned instant-craft callback for unattended processing.

The preview now activates the processor through
`WorkbenchSpec.builder(title, recipes).persistentTimed(storage, ticks)`. Storage must
have exactly the input count plus one output slot. Unsupported runtimes reject this
separately from instant persistent workbenches, before reserving the menu ID.

The isolated demo registers a new `timed_workbench` with four slots and a 100-tick
duration. The existing three-slot `workbench` is unchanged, avoiding a save-schema
change. Generated server/client menus share the same slot layout; the output rejects
insertion and does not perform another craft when taken. Server-owned progress is
scaled to 0..1000 for vanilla menu-data synchronization and rendered as a progress bar.
Closing removes the viewer listener without clearing block inventory. No live timed
crafting or save/reload correctness is claimed until the gameplay checks pass.

1. Validate container, processing, hopper and break/drop behaviour with the shared
   fixture on all nine generated targets; their implementation and compilation are complete.
2. Validate close, reopen, world save/reload and multiple viewers before enabling
   persistence in the default consumer runtime profile.
3. Complete dedicated-server/client-isolation checks and record gameplay results
   separately from the passing compilation and portable fixture byte-parity checks.

This foundation is covered by focused Java tests. No live world persistence or native
block-entity gameplay is claimed yet; the earlier eleven-check workbench matrix
predates this feature and cannot establish those behaviours.
