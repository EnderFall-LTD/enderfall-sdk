# Delivery roadmap

## Active expansion: furniture-mod portability

Use [the furniture-port milestone](docs/furniture-port.md) as the concrete feature
checklist. Custom block factories/configuration/server-use hooks, static cuboid
outline/collision shapes, typed custom state, state-dependent geometry, server-world
state mutation, opt-in horizontal/six-way facing, custom initial-state placement,
rotation, mirroring, neighbor-derived state, scheduled transitions and real
waterlogging, barrel/dispenser six-way direction and log/pillar axis placement are
implemented. General 9-54 slot storage containers now have portable
registration, vanilla synchronization, shift-click, hopper access, viewer tracking,
open state, sound profiles, configurable per-face slot access and opt-in vanilla-style
lazy loot-table population. Connecting-table, storage and loot-table fixtures still
need cross-target gameplay acceptance. Portable item behaviour and durable item data
are the next implementation stage.
The port must preserve recognizable
block, block-entity, menu, screen, renderer and registry class responsibilities for
maintainers and add-on authors. Full furniture parity is not yet achieved.

## Implemented in the source baseline

- [x] Java 17 loader-neutral API and typed foundation specifications.
- [x] Shared registration gate, event isolation, TOML repair, bounded codecs,
  capability manifest, deterministic data output, and diagnostics.
- [x] Nine-entry exact target catalog and Java 17/21/25 toolchain selection.
- [x] Settings plugin, generated loader metadata, aggregate tasks, source-boundary
  checks, duplicate-path failures, portable-source hashes, and deterministic JAR names.
- [x] JUnit and Gradle TestKit suites plus a portable full-feature contract fixture.
- [x] Experimental synchronized custom screens with declarative labels/buttons,
  server-owned sessions, bounded state updates, and all-target native renderers.
- [x] Minimal CC0 starter, one-shot identity task, wrappers, and dev container.
- [x] Maven/BOM/plugin publication scaffolding, signing hooks, SBOMs, checksums,
  API comparison, pinned actions, and fail-closed release workflow.
- [x] Establish the central bridge compiler, reviewed typed ABI catalog, canonical
  runtime boundary, and generated-only target source roots.
- [x] Generate Minecraft 1.21.4 Fabric and prove its sources, compiled classes,
  resources, and final remapped JAR match the working reference exactly.
- [x] Generate Minecraft 1.21.1 Fabric from the same canonical tree using three
  fail-closed ABI transformations; prove source, class, pack, and non-metadata
  remapped-JAR entry parity while correcting its missing Fabric API metadata.
- [x] Generate Minecraft 26.2 Fabric using Java 25, official unobfuscated names,
  directional payload, recipe, menu, and screen transformations; prove complete
  source/class/resource parity and whole-JAR byte identity.
- [x] Generate Minecraft 1.21.4 NeoForge from a centrally reviewed loader profile;
  prove exact source, class, resource, coordinate, and shaded-JAR byte identity.
- [x] Generate Minecraft 1.21.1 NeoForge by reusing twelve central-profile classes
  and applying two fail-closed recipe/menu ABI transformations; prove full parity.
- [x] Generate Minecraft 26.2 NeoForge with central registration, directional payload,
  permission, recipe, menu, and screen rules; prove whole-JAR byte identity.
- [x] Generate Minecraft 1.20.1 Fabric with legacy channel, serializer, menu, and
  screen rules; prove source/class/resource and remapped-JAR byte identity.
- [x] Generate 1.20.1 Forge and NeoForge from one shared legacy FML rule set and
  build script; prove identical Java output and both development/reobfuscated JAR parity.

## Generated-runtime migration

- [x] Add experimental state-driven menu gauges to the integrated persistence
  profile, with bounded layouts, native immediate/extracted renderers and explicit
  rejection on unsupported runtimes. Solid-colour rendering is implemented;
  textured fluid rendering and in-game visual acceptance remain open.

- [x] Add an opt-in reference-free development configuration:
  `-Penderfall.referenceRuntimes=false` excludes the nine handwritten Gradle projects
  and their parity tasks while retaining generated targets and coordinate checks.
  Publishing remains blocked in this mode; reference parity is still required there.
- [ ] Retire the reference source folders after reference-free builds, remaining
  validation tooling and release wiring have been verified. No reference files or
  saved development worlds are deleted by the configuration switch.

- [x] Expand generated persistence registration, instant/timed menus, processing and
  client hooks to all seven pre-26 targets; their full development sources compile.
  Shared storage/container operations remain central, with explicit native ABI rules.
- [x] Compile the complete 26.2 Fabric/NeoForge persistence stack, including value-based
  save/load, block-entity removal, template recipe results and extracted-state screens.
- [x] Wire the ordinary consumer bootstrap to persistence through an opt-in SDK
  development profile, with isolated source/class outputs and publication disabled.
  All nine integrated runtime source sets compile; artifact and gameplay checks are separate.
- [x] Generate cross-target persistence fixture launchers and resources around the
  same API-only Java 17 sources, with isolated client/server run directories.
- [x] Verify identical portable Java 17 class bytes in all nine fixture JARs.
- [x] Verify all nine development runtime JARs contain native storage, processing
  and the feature-enabled ordinary consumer bootstrap.
- [x] Confirm ordinary-bootstrap workbench registration and client resource loading
  on Fabric 1.21.4 and NeoForge 26.2. This is startup evidence, not gameplay acceptance.
- [ ] Pass save/reload, processing, hopper, break/drop and multiplayer acceptance on all
  nine targets. Development-source compilation does not enable consumer capabilities.

- [x] Implement the experimental portable block-entity storage foundation: bounded
  saved integer fields, block-owned inventory snapshots, defensive copies, and dirty
  revision tracking. See [implementation status](docs/block-entities.md).
- [x] Generate and compile an isolated Fabric 1.21.4 native block-entity development
  slice with block/type registration, registry-aware item persistence, and dirty hooks.
  This is not packaged or runtime-verified; other targets and gameplay binding remain open.
- [x] Add a generated persistent workbench-menu mode using the block-owned native
  container, access validity, viewer listeners, and close-without-clearing behaviour;
  add server block-removal drop hooks. Native preview compiles; live behaviour and demo
  opening integration remain pending.
- [ ] Generate native block-entity registration/save hooks, bind persistent workbench
  inventories, and add server processing with synchronized progress to the demo.
- [x] Add portable persistent-block registration, workbench storage binding, and exact
  dimension/position opening routes with explicit unsupported-target errors. Add the
  guarded native preview opener; connecting platform services and demo clicks remains open.
- [x] Add optional exact locations to portable block interaction events and connect
  the preview native block-use callback to its guarded persistent-menu opener.
  Baseline event hooks and demo activation remain unchanged pending platform wiring.
- [x] Wire persistent block/item/menu registration and exact-position opening into
  a generated preview platform composed from shared platform operations; provide an
  explicit development bootstrap. Normal consumers and runtime JARs remain unchanged.
- [x] Add an isolated Fabric 1.21.4 persistent-workbench development demo with separate
  portable registration classes, API-only Java 17 compilation, and opt-in launch glue.
  Client registration, single-player entry and clean shutdown passed. Static recipe/model
  fixtures are included; live crafting, save/reload, and break/drop assertions remain open.
  See [preview instructions](examples/persistent-preview/README.md).
- [x] Add an optional portable saved-state server ticker and generated Fabric 1.21.4
  preview binding, with server-thread guards and per-instance callback failure isolation.
  Inventory processing, client progress synchronization and live ticking validation
  remain separate work; normal target runtimes do not enable this preview feature.
- [x] Add shared timed-processing decisions (exact tick duration, blocked-output pause,
  recipe-change reset) and atomic inventory/saved-field commits for completion.
  Native recipe matching, persisted active-job identity, stored-output slots and
  menu progress synchronization are not connected yet; the preview still crafts instantly.
- [x] Connect atomic inventory/field/processing commits to the generated native preview
  and persist active recipe identity, revision, duration and elapsed ticks in bounded
  versioned data. Validate processing metadata before restoring inventory; notify
  viewers only after commit. Native recipe ticking and visible progress remain pending.
- [x] Generate the preview timed-recipe processor and loaded-block tick binding with
  native recipe matching, definition fingerprints, component-aware output merging,
  bounded remainder placement and atomic completion. This is not activated in the
  demo yet: timed registration, a stored-output menu and synchronized progress remain open.
- [x] Wire experimental timed registration, independent support checks, matching
  server/client stored-output menus and synchronized progress into the Fabric 1.21.4
  preview. Add a separate 100-tick timed demo block, preserving the old block ID and
  inventory schema. Live processing, output retention, reload and multi-viewer checks remain pending.
- [x] Add server-synchronized timed-machine status, distinct output/remainder blockage,
  fault feedback and a progress tooltip. Initial timed crafting was reported working
  by the user; the new status presentation and persistence/multi-viewer checks remain unverified.
- [x] Add shared fixed-face hopper policy and generated native sided-container wiring
  for timed machines: distinct input faces, bottom-only output extraction, no input
  extraction or output insertion. Instant workbenches remain closed to automation.
  Live hopper and save/reload checks remain pending.
- [x] Add general per-face inventory automation rules with insert, extract and
  bidirectional modes, optional slot lists, safe all-face container defaults and one
  portable declaration shared by every generated loader/version runtime.

- [x] Add an opt-in generated-runtime gameplay harness with one native test-driver
  template, server-side inventory/callback assertions, independent evidence gates,
  and matching client/server portable-source hashes.
- [x] Add an explicit historical gameplay-matrix audit that requires every catalog
  target, matching portable hashes, clean exits, and actual log checkpoints; newer
  failures override older passes.
- [x] Complete the focused menu/workbench gameplay scenario on all nine generated
  targets using identical portable sources and clean client/server exits; retain
  failed attempts and explicitly ordered retry evidence.
- [x] Expand the scenario to eleven checks, including repeated full-inventory craft
  rejection and successful crafting after capacity is restored; run and independently
  audit all nine targets with identical portable sources and clean exits.
- [ ] Add full-inventory close/drop, partial-stack capacity, and disconnect/reconnect
  gameplay coverage; these are not established by the eleven-check matrix.
- [ ] Diagnose the intermittent legacy login stall and establish repeatable launches;
  two consecutive observed/unobserved Forge repeats passed, but do not establish
  the cause of its earlier timeout.
- [x] Add non-overwriting named gameplay runs, an opt-in read-only legacy login
  observer, and failure-marker checks that cannot be undone by later success logs.
- [x] Run generated 1.21.4 Fabric through isolated client and dedicated-server
  lifecycle smoke tests without resolving its handwritten reference artifact.
- [x] Run the same generated-only client/server lifecycle gates for 1.21.1 Fabric.
- [x] Run the same generated-only client/server lifecycle gates for 26.2 Fabric.
- [x] Run the same generated-only client/server lifecycle gates for 1.21.4 NeoForge.
- [x] Run the same generated-only client/server lifecycle gates for 1.21.1 NeoForge.
- [x] Run the same generated-only client/server lifecycle gates for 26.2 NeoForge.
- [x] Run the same generated-only client/server lifecycle gates for 1.20.1 Fabric.
- [x] Run the same generated-only client/server lifecycle gates for 1.20.1 Forge/NeoForge.
- [ ] Complete the broader generated 1.21.4 Fabric in-game contract (commands,
  configs, interactions, and edge cases) before retiring its reference sources.
- [ ] Run the same in-game contract gates for generated 1.21.1 Fabric before retiring
  its inherited/reference source layout.
- [ ] Run the same in-game contract gates for generated 26.2 Fabric before retiring
  its inherited/reference source layout.
- [ ] Run the same in-game contract gates for generated 1.21.4 NeoForge before
  retiring its reference source layout.
- [ ] Run the same in-game contract gates for generated 1.21.1 NeoForge before
  retiring its inherited/reference source layout.
- [ ] Run the same in-game contract gates for generated 26.2 NeoForge and 1.20.1
  Fabric before retiring their inherited/reference source layouts.
- [x] Replace copied baseline code with reusable loader and Minecraft ABI emitters.
- [x] Generate recipe/workbench registration binding records for all nine targets
  from one shared field model and typed registry-handle policy, removing their
  legacy and 26.2 source patches without changing generated runtime behaviour.
- [x] Generate complete 1.21.1/1.21.4 Fabric/NeoForge codec recipe classes from one
  shared implementation, preserving source hashes, serializer timing, and bytecode.
- [x] Generate all three 1.20.1 recipe/serializer implementations from shared
  matching, JSON, and wire logic with explicit registry policies and unchanged output.
- [x] Generate both 26.2 recipe implementations from shared template-result and
  codec logic, preserving deferred binding timing and all existing output hashes.
- [x] Migrate slot-menu generation from contextual patches to shared semantic emitters.
- [x] Generate the 1.21.4 Fabric/NeoForge slot menus from one shared implementation,
  preserving native slot handling, recipe revalidation, and synchronization output.
- [x] Extend shared slot-menu generation to both 1.21.1 loaders, removing their
  menu patches while preserving recipe-manager access and recipe ID handling.
- [x] Extend shared slot-menu generation to all 1.20.1 and 26.2 targets.
- [x] Generate all nine workbench screens from shared geometry and rendering policies.
- [x] Generate all nine label/button portable screens from one shared implementation
  with typed rendering and screen-access policies.
- [x] Migrate native services, loader initialization, and registration hooks into
  shared operation emitters with reviewed native policies.
- [x] Generate modern NeoForge client hooks for 1.21.1, 1.21.4, and 26.2 from
  shared lifecycle, tick, screen-registration, and payload-send policies.
- [x] Generate all four Fabric and both legacy FML client hooks, preserving
  legacy channel limits, dispatch, connection checks, and smoke connector behavior.
- [x] Generate all nine command bridges from shared argument/suggestion/execution
  logic and explicit native registration, permission, and profile-access policies.
- [x] Generate bounded payloads for all six modern targets and all five standalone
  recipe-input records; NeoForge 26.2 retains its combined shared recipe input.
- [x] Add a semantic-emitter coverage gate: every declaration must be shared or
  explicitly omitted on the nine reviewed targets, with no copied-source exception.
- [x] Prove canonical Java implementation independence by replacing every Java
  body with a declaration comment in a fixture and comparing all nine output digests.
- [x] Remove obsolete contextual source transformers and patch-only tests; retain
  manifest integrity, dependency validation, and unchanged source/artifact goldens.
- [x] Publish generated runtimes through the normal local workspace workflow;
  disable reference-runtime Maven publishing and gate generated publications on parity.
- [x] Generate loader-owned SDK marker entrypoints for all nine targets, preserving
  empty initialization and keeping consumer contexts isolated.
- [x] Generate consumer entrypoint bootstraps for all nine targets from shared
  initialization logic with explicit event-bus and class-loader policies.
- [x] Generate platform identity, environment, and mod queries for all nine targets
  from shared query logic and explicit native loader policies.
- [ ] Run the in-game contract gates for generated 1.20.1 Forge and NeoForge before
  removing each corresponding reference adapter.
- [x] Replace per-target generated-runtime entry scripts with catalog-created
  internal projects and shared Loom/ModDevGradle build-family scripts, preserving
  project IDs and artifact paths. Reference fixture build scripts remain until retirement.

## Required before 0.1.0-beta.1 can be released

- [x] Implement direct 1.21.4 Fabric and NeoForge adapters.
- [x] Implement direct 26.2 Fabric and NeoForge adapters.
- [x] Generate and compile loader bootstrap classes through Loom/ModDevGradle.
- [x] Package one real `enderfall_sdk` runtime mod per initial target.
- [x] Wire `runClient`, `runServer`, and `generateData` to actual loader tasks.
- [x] Automate dedicated-server ready-state, clean shutdown, and client-class-isolation
  smoke tests for every catalog target.
- [x] Automate real client startup, resource reload, lifecycle/tick readiness, and clean
  shutdown for every catalog target.
- [ ] Automate client registration, resources, commands, events, config, and networking
  assertions for all four initial targets.
- [x] Run an external same-loader client/server connection and packet round trip on every
  initial target.

## Required before 0.2.0-beta.1

- [x] Add the 1.21.1 Fabric/NeoForge adapters.
- [x] Add 1.20.1 Fabric/Forge/NeoForge adapters using ModDevGradle legacy support for
  both Forge-family builds.
- [x] Run the automated dedicated-server smoke suite for all five added targets.
- [x] Run the automated client lifecycle smoke suite for all five added targets.
- [x] Run an external same-loader client/server connection on all five added targets.

`verifyRuntimeMatrix` remains an unconditional failure until the pending automated
foundation-feature checks are replaced by machine-readable runtime evidence. The exact
same-loader connection matrix now has machine-readable evidence, but it is only one part
of release acceptance.
