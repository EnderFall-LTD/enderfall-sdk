# EnderFall SDK Demo

This is a deliberately structured consumer mod built from one portable source tree for
all nine EnderFall targets. It is a showcase, not the minimal starter template.

The entrypoint only coordinates feature classes:

- `DemoItems` registers standalone items.
- `DemoBlocks` registers blocks and their paired items.
- `DemoCreativeTab` composes the demo tab.
- `DemoConfig`, `DemoNetworking`, `DemoCommands`, and `DemoEvents` own their systems.
- `DemoRecipes` and `DemoWorkbench` own the custom serializer and inventory-backed menu.
- `DemoGameplay` owns player progression and server-authoritative gameplay actions.
- `DemoData` generates recipes, tags, loot tables, and translations.
- `src/main/resources` contains original textures and hand-authored custom models.

From the SDK repository root, publish the current local SDK and build the demo:

```powershell
.\gradlew.bat publishWorkspace checkDemoMod
```

Or, after `publishWorkspace`, run from this directory:

```powershell
.\gradlew.bat buildAll
.\gradlew.bat runClient '-Penderfall.target=26.2-fabric'
```

The output JARs are collected under `build/releases`. Build success proves compilation,
metadata, resource generation, remapping/reobfuscation, and packaging. It does not by
itself prove in-game interactions; record that separately when manually testing.

Playable loop and useful in-game checks:

1. Create a world with commands enabled and run `/enderfall_demo_kit`.
2. Run `/enderfall_demo_guide` for the in-game instructions.
3. Open the EnderFall SDK Demo creative tab and inspect all six entries.
4. Right-click Void Crystals to consume them and charge your resonance meter.
5. Place and right-click the workbench. Put **4 Void Crystals** in the left slot,
   **1 Ender Alloy Block** in the middle slot, and **1 Echo Shard** in the right slot.
   The result slot displays a Resonance Core only when the custom positional recipe matches.
6. Take the Resonance Core from the result slot. The server consumes the counted inputs,
   awards charge and experience, and vanilla menu synchronization updates both inventories.
7. Right-click the Resonance Core to consume it, fully heal, and fill resonance.
8. Right-click the Resonance Rod to spend charge, heal, gain experience, and exercise its
   server-tick cooldown. The lamp displays the current charge in the action bar.
9. Run `/enderfall_demo_status` and `/enderfall_demo_reset` to inspect or reset the
   session-scoped player progression.
10. Run `/enderfall_demo_pulse hello` while connected to a server and confirm the pulse and
   acknowledgement in the client and server logs.
11. Verify the five vanilla recipes, one custom workbench recipe, drops, translations, models, and
    `config/enderfall_sdk_demo-demo-common.toml`.

The resonance meter and statistics deliberately live for the current server session. Durable
custom player state is a separate API slice and is not claimed by this demo yet.

The workbench demonstrates the experimental inventory-menu slice: a portable recipe type,
target-native serializers/codecs, real input/output slots, player inventory and hotbar slots,
shift-click movement, server-side matching/consumption, and client/server synchronization.
Inputs are returned when the menu closes. Persistent block-entity storage and fluids remain
separate slices documented in `../../docs/feature-status.md`.
