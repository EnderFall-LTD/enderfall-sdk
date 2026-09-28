# Authored inventory menus

`StorageContainerSpec` supports both its original vanilla 9-wide chest layout and a fully
authored, block-owned inventory layout. An authored layout is still server authoritative:
the generated runtime owns the native menu type, slot synchronization, client screen,
reach checks, persistent block entity, opening lifecycle and shift-click processing.

```java
StorageContainerRef washer = menus.container("washer", "Crystal Washer", WASHER_STORAGE,
        menu -> menu
                .size(176, 166)
                .slot(InventorySlotSpec.accepting(0, 44, 28, "inputs",
                        List.of(ResourceId.of("minecraft", "amethyst_shard"))))
                .slot(InventorySlotSpec.output(1, 116, 28, "outputs"))
                .playerInventory(7, 83)
                .quickMove("inputs", InventoryQuickMoveRule.PLAYER_MAIN,
                        InventoryQuickMoveRule.PLAYER_HOTBAR)
                .quickMove("outputs", InventoryQuickMoveRule.PLAYER_MAIN,
                        InventoryQuickMoveRule.PLAYER_HOTBAR)
                .quickMove(InventoryQuickMoveRule.PLAYER_MAIN, "inputs")
                .quickMove(InventoryQuickMoveRule.PLAYER_HOTBAR, "inputs"));
```

Every persistent storage index must appear exactly once. `STORAGE` slots allow normal
two-way interaction, `INPUT` slots may restrict accepted item IDs and `OUTPUT` slots reject
player insertion. A slot may also lower its maximum stack count. Filters, output rules and
quick-move routing are enforced by the logical server, not trusted to the screen.

Player main inventory and hotbar binding is optional. When it is enabled, both are placed
from one origin and can be addressed as `player_main` and `player_hotbar` in routes. Routes
are ordered and explicit; the SDK does not guess where a machine should send an item.

The original call with no authored slots remains a vanilla one-to-six-row chest menu, so
existing cabinets, crates and barrels retain their prior appearance and behavior.

## Portable custom presentation

A client entrypoint can replace the generated fallback panel without registering a native
screen or importing Minecraft classes:

```java
context.inventoryScreens().register(ModContainers.WASHER, WasherScreen::new);
```

`WasherScreen` implements `PortableInventoryScreen`. `renderBackground` runs before native
slots and carried stacks; `renderForeground` runs afterwards. Both receive the same
`UiRenderContext` used by general portable screens plus an `InventoryScreenContext` containing
the menu identity, title, screen origin and dimensions. Mouse, scroll, keyboard, character,
tick, resize and removal callbacks are forwarded across all targets. Returning `false` from
an input callback leaves ordinary slot handling to Minecraft.

This split is intentional: EnderUI can own the complete element tree and visual presentation,
while the generated native menu continues to own synchronized items, drag splitting,
shift-clicking and server validation. If no client view is registered, the SDK's simple
fallback inventory screen remains available.

Machine progress and similar server-owned integers can be opted into the same menu channel:

```java
menu.synchronize(ModStorage.PROGRESS);
```

The portable view reads the current value with `screen.value(ModStorage.PROGRESS)`. Only
`BlockEntityInt` fields declared by the owning storage may be exposed, duplicates are rejected,
and each full signed 32-bit value is split over bounded vanilla data slots internally so the
portable API behaves identically on legacy and modern targets.

The same authored menu generator is compiled for all nine supported targets. The
persistent preview cabinet exercises custom coordinates, player inventory binding,
bidirectional quick-move routes and a custom portable presentation from unchanged Java 17
source. Live visual and interaction passes are still required before this experimental API
is promoted to stable.
