# Furniture-mod portability milestone

The target is to rebuild LYIVX's Furniture Mod against SDK APIs in one portable
source tree, with SDK-owned generation for every supported Minecraft/loader target.
Third-party integrations may live in optional target-specific modules. Client
registration remains a separate entry point for dedicated-server safety, not
duplicated source per target. This does not require existing native subclasses to
compile unchanged, and it does not promise automatic support for future versions.

This is an initial source-backed gap assessment of representative classes in
`Furnection/LYIVXs Furniture Mod 1.21.4/common/src/main/java/net/lyivx/ls_furniture`,
not a completed exhaustive method-by-method port audit. The original mod is untouched.

## Source-shape compatibility requirement

The port must remain recognizable to existing maintainers and add-on authors. Keep
the existing responsibility boundaries where they are sound: blocks remain block
classes, block entities own placed data, menus own server container rules, screens
own client presentation, renderers own dynamic rendering, and registry classes group
registrations. For example, a future portable `BarrelModBlock`,
`BarrelModBlockEntity`, `WorkstationMenu`, `WorkstationScreen`, and renderer should
still be separate types with those jobs. The SDK must not force the whole mod into a
single declarative file or expose generated loader classes as the add-on API.

Native Minecraft inheritance cannot remain source-compatible across every target,
so portable classes implement SDK contracts instead of extending one target's native
classes. Within that necessary boundary, preserve public resource IDs, saved-data
semantics, packet intent, menu responsibilities, and useful domain method names where
practical. Optimize internals incrementally; do not redesign the mod merely because it
is being made portable. Add-ons should depend on stable portable interfaces, refs and
explicit extension points, never SDK-generated or loader-owned implementation classes.

## Required work, in dependency order

| Stage | Concrete furniture evidence | SDK work and acceptance |
| --- | --- | --- |
| 1. Custom block definitions | `common/blocks/BarrelModBlock.java`, `TableBlock.java` | Factory/configuration, server-use hooks, static cuboid shapes, typed boolean/integer/enum state, state-dependent shapes, server-world state mutation, opt-in horizontal/six-way facing, custom initial-state placement, neighbor-derived state, scheduled transitions and real waterlogging are implemented. The portable connecting-table fixture compiles unchanged on all targets; live connection, bucket, fluid-tick and save/reload acceptance remains. |
| 2. General containers | `common/blocks/entity/BarrelModBlockEntity.java` | A 9-54 slot vanilla-synchronized storage API, shift-click routing, configurable hopper access, first/last viewer tracking, sounds, portable open block state and opt-in lazy vanilla loot tables are implemented. Prove simultaneous viewers, death/disconnect, pending-loot save/reload, exactly-once loot generation, drops and hopper transfer in live targets. Existing timed workbench menus are not a substitute. |
| 3. Items and durable data | `registry/ModComponents.java`, `common/items/WrenchItem.java` | Class-based portable item definitions, server-side right-click/use-on-block routing, declarative literal/translated tooltips, typed held-stack data, expected-item-checked arbitrary carried-slot author/text updates, reusable bounded text-editor sessions, anvil repair sources, declared wrench/hammer-style property policies, client prediction, pre-commit validation/resource claims and committed-change callbacks are implemented. Verify copies, stacks, dropped items and reconnects preserve data. |
| 4. Recipes and richer screens | `common/menus/WorkstationMenu.java`, `client/screens/WorkstationScreen.java` | Secure single/multiline text widgets now join state labels/buttons. Selectable/filterable recipes, scrollable recipe lists, selection validation and synchronized rich results remain. Current positional recipes cover only part of this. |
| 5. Specialized furniture behavior | `common/blocks/entity/CounterOvenBlockEntity.java`, `common/blocks/ModBedBlock.java`, `common/entity/SeatEntity.java` | Furnace/fuel semantics, multipart storage/blocks, beds, seat mounting/dismounting and lifecycle. Do not replace these with visually similar inert blocks. |
| 6. Rendering and presentation | `client/renderers/CustomChestRenderer.java`, `ChoppingBoardRenderer.java`, `client/util/SimpleFluidRenderer.java`, client color registrars | Expand current item/model/fluid rendering for required state-dependent animation, tinting, entity rendering and previews. Verify resource reload, lighting, rotations and dedicated-server isolation. Existing renderer support is a starting point, not complete parity. |
| 7. Complete port acceptance | All registrations, behaviors, assets, recipes, network handlers and integrations | Finish the full inventory, port every built-in feature, and run functional scenarios per target. Third-party integration absence must not prevent the base mod starting. |

The names above identify reviewed source or discovered feature entry points, not
a claim that every method in every file has been audited. Remaining audit includes
the complete asset/datagen paths, loader-specific code, configuration, network
authorization, interactions between furniture families and optional dependencies.

## First implementation in this milestone

General state groundwork is now available as experimental pure Java definitions:
typed boolean/integer/enum properties, immutable state values, strict serialization,
bounded state enumeration, cached state-dependent shapes and model-variant data
generation. Server-world reads and atomic writes preserve native facing and notify
clients and neighbors. Generated feature runtimes register schemas and defaults for
ordinary and persistent blocks. Portable block classes can select their initial
custom state from a loader-neutral placement context and derive a replacement state
when a specific neighbour changes. See [block-state definitions](block-state-definitions.md).
Scheduled ticks and waterlogging now have generated implementations. The complete
connecting-table gameplay scenario remains open, so stage 1 is not yet accepted.

## General storage implementation

`Registration.menus(namespace).container(...)` binds an existing persistent block
inventory to Minecraft's standard 9-wide storage screen. A 27-slot cabinet therefore
needs only `inventorySlots(27)` plus one menu declaration; the SDK installs the server
interaction and every target uses vanilla slot synchronization and shift-click rules.
One through six rows are supported. Optional `openState(...)` drives a declared boolean
block property from first-viewer/last-viewer transitions, and `ContainerSoundProfile`
provides built-in chest/barrel profiles or custom sound IDs. Viewer reconciliation
closes stale visual state after death, disconnect or spectator transitions. Registered
plain storage exposes every slot to hoppers on every face by default. A storage schema
may instead use `inventoryAccess(...)` to assign insert, extract or bidirectional
access to each face, either for every slot or a bounded slot list. These automation
rules do not restrict ordinary player menu interaction.

Calling `lootTableInventory()` on a storage definition opts that inventory into
vanilla `LootTable`/`LootTableSeed` block-entity data. It does not choose a loot table:
world generation, a structure, a command or another authorized server system supplies
the table ID and optional seed. Pending loot survives save/reload and is expanded once
when a player, hopper or block-removal path first needs the contents. Player-placed
containers without that data remain empty. Generated source and native compilation
cover all nine targets; exactly-once behavior is still a live gameplay acceptance gate.

The persistent preview now includes `PreviewStorageCabinetBlock`, `PreviewContainers`
and a 27-slot cabinet declared entirely in portable source. All generated targets
compile, but live multi-viewer, hopper and save/reload acceptance remains separate.

- `PortableBlock` is a loader-neutral definition interface.
- Registration factories execute once per block ID at initialization preflight.
- Definition configuration precedes per-registration overrides.
- Server-use callbacks are dispatched by block ID through the existing isolated
  interaction bus; cancelled/handled/client events are excluded.
- Property copying never copies behavior instances or persistent storage.
- `PreviewWorkbenchBlock` demonstrates the factory path with existing persistent
  storage. It is not a completed barrel port.

## Static shape implementation

`BlockShape` now provides bounded immutable cuboid unions and authored quarter-turn
rotation. Ordinary and persistent blocks share one generated native shape shell.
The preview workbench has matching tabletop/leg model and collision definitions.
All nine generated targets compile; API/runtime tests, generation contract tests
and fixture parity pass. Actual in-game targeting, collision and visual checks
are still pending. See [block shapes](block-shapes.md).

## Completion standard

A stage is not complete merely because generated Java compiles. Each behavior
needs equivalent outcomes across the selected targets: placements and states,
inventory and data persistence, interactions, visuals, and client/server handling.
Record compilation, automated contracts and actual gameplay separately.

Until the missing stages pass, the SDK cannot honestly be described as capable
of fully rebuilding this furniture mod. Missing built-in furniture functionality
is SDK work to implement, not something to permanently push into consumer native
folders. Optional integration modules remain the planned exception.

Reference runtime removal is a separate build migration; the furniture milestone
does not authorize deletion of old reference sources.
