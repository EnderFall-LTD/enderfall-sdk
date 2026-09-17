# Feature status and next API slices

This page separates working portable features from proposed contracts. A feature is only
marked implemented after the same portable source compiles for every supported target and
its loader adapters have runtime evidence.

## Implemented portable surface

| Area | Available today | Demo coverage |
| --- | --- | --- |
| Items | Basic item properties, durability, stack size, rarity, fire resistance | Void crystal, resonance rod, and 3D resonance core |
| Blocks | Basic blocks, paired block items, strength, light, friction, jump factor, sounds | Alloy block, luminous lamp, shaped workbench |
| Creative tabs | Portable title, icon, and entries | One tab containing all demo content |
| Models/resources | Generated flat/handheld/cube models plus shared hand-authored JSON/resources | Three item sprites, block textures, and multi-element workbench/core models |
| Data | Shaped/shapeless recipes, custom workbench recipes, tags, translations, self-drop loot | Five vanilla recipes, one custom recipe, two tags, two locales, three loot tables |
| Commands | Typed arguments, permissions, suggestions, plain and translated replies | Guide, status, reset, kit, and network-pulse commands |
| Configuration | Typed TOML, validation, safe repair, common/client/server scopes | Bounded progression costs, healing, charge, cooldown, and networking values |
| Networking | Typed codecs, negotiation, size limits, directions, main-thread handlers | Server/client pulse and acknowledgement |
| Events | Lifecycle, server/client tick, player join/leave, sided item/block interaction, successful handling and cancellation | Lifecycle, cooldown timing, join guidance, and server-authoritative abilities |
| Player gameplay | Player snapshots, atomic inventory costs, item rewards and overflow, chat/action-bar feedback, healing, experience | Crystal absorption, workbench infusion ritual, core activation, rod ability, and development kit |
| Synchronized screens | Portable state-templated labels/buttons plus the experimental inventory-backed workbench surface | The workbench uses real input, output, player-inventory, and hotbar slots |

The showcase is in `examples/demo-mod`. All of its Java under `src/main` and `src/client`
is portable and contains no Minecraft, Fabric, Forge, or NeoForge imports.

Known inventory limitation: player item counts, costs, and rewards currently accept only
the calling mod's registered items. Vanilla/other-mod item lookup still needs a defined
portable contract and runtime tests; a manually constructed `ItemRef` is not a workaround.

## Experimental workbench slice

The workbench API now registers a portable positional recipe type and a real container menu.
Every target adapter compiles its native recipe serializer/codec, menu type, client screen,
server-side recipe lookup, counted input consumption, shift-click rules, and unused-input return.
The same `DemoRecipes`, `DemoWorkbench`, and `DemoData` source is used for all nine targets.

This slice has dedicated-server recipe-load evidence on 1.20.1 Fabric, 1.21.4 NeoForge,
and 26.2 NeoForge, plus a clean client lifecycle/resource reload on 1.20.1 Fabric.
Manual slot, shift-click, and crafting passes are still required across the complete client
matrix before it is promoted from experimental to stable.

The generated-runtime [gameplay harness](gameplay-testing.md) has additionally passed
automated native slot input, normal/shift crafting, counted consumption, input return,
menu-state synchronization, and packet round trips on all nine generated targets
using identical portable sources. Forge required a diagnostic retry after an
intermittent login timeout, which remains a reliability issue to investigate.
This focused matrix is not full foundation-feature or visual acceptance.

## Not implemented yet

| Area | Current status | Why it is not presented as working |
| --- | --- | --- |
| Fluids | No stable API or adapter | Requires source/flowing registrations, fluid block and bucket coordination, render handlers, tags, and version-specific behaviour |
| Custom item/block behaviour | Player-facing use behaviour works through sided interaction events and `PlayerManager` | Direct world mutation, held-stack damage, effects, and block-state context are not yet defined |
| Block entities and persistent state | No stable API or adapter | Serialization, ticking, update packets, inventory ownership, placement/removal lifecycle, and data migration must be specified together |

## Proposed developer-facing contracts

These sketches are the design target for the next implementation phase. They are not
callable APIs yet.

### Next menu expansion: block-entity ownership

```java
BlockEntityRef<WorkbenchState> workbenchEntity = context.blockEntities().register(
        "resonance_workbench",
        BlockEntitySpec.<WorkbenchState>builder(WorkbenchState.CODEC)
                .inventory(3)
                .ticks(WorkbenchState::tick)
                .build());

MenuRef workbench = context.menus().registerInventory("resonance_workbench",
        InventoryMenuSpec.builder(workbenchEntity)
                .slot("input", 0, 36, 52)
                .slot("catalyst", 1, 62, 52)
                .output("result", 2, 116, 52)
                .playerInventory(8, 84)
                .build());
```

The workbench slice now owns common registration, native screen construction, temporary
menu inventory, quick-move rules, server matching, and safe input return. The next slice
must add persistent per-position storage and data migration without changing portable menu code.

### General custom processing recipes

```java
RecipeTypeRef<ProcessingRecipe> resonance = context.recipes().register(
        "resonance",
        ProcessingRecipeType.builder()
                .inputCount(2)
                .maximumOutputs(2)
                .supportsDuration()
                .supportsEnergy()
                .build());
```

The first portable recipe type should deliberately target machine-style recipes rather
than arbitrary executable serializers. EnderFall can then own deterministic JSON,
validation, reload, synchronization, matching, and recipe-view data without exposing
Minecraft codec changes to consumers.

### Fluid families

```java
FluidFamilyRef liquidEnder = context.fluids().register("liquid_ender",
        FluidSpec.builder()
                .colour(0xFF6B2BAA)
                .viscosity(1_500)
                .density(1_200)
                .lightLevel(4)
                .bucket()
                .worldBlock()
                .build());
```

One call should reserve and validate the source fluid, flowing fluid, bucket item, world
block, render data, translations, and required tags as an atomic family. Partial
registration must fail before loader registries freeze.

## Admission rule

Each new surface must ship vertically: public API, runtime-core validation, all nine
adapter implementations, data/resource support, unit tests, unchanged portable fixture,
dedicated-server/client smoke assertions, documentation, and migration rules. This keeps
the convenient one-call API honest and prevents loader-specific differences from leaking
back into consumer source.
