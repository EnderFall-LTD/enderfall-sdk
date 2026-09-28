# General client screens

The experimental general-screen bridge lets a client entrypoint register a screen once in
portable Java. The generated runtime supplies the native `Screen`, rendering and input
adapter for the selected Minecraft/loader target. It is deliberately lower-level than the
synchronized menu API: UI libraries keep ownership of their element tree, layout, styling
and state model.

```java
public final class ExampleClient implements EnderfallClientMod {
    @Override
    public void initialize(ClientModContext context) {
        ClientScreenRef catalogue = context.screens().register(
                context.id("catalogue"),
                new ClientScreenSpec("Catalogue", false, true),
                CatalogueScreen::new);

        // Call this from a client-side action when the screen should be displayed.
        context.screens().open(catalogue);
    }
}
```

A `PortableClientScreen` receives lifecycle, tick, resize, keyboard, character, mouse,
drag and two-axis scroll callbacks. Its `UiRenderContext` currently bridges:

- solid ARGB fills;
- native-font measuring, deterministic wrapping, left/centre/right text and shadows;
- raw texture regions, tiling and nine-slicing;
- nested rectangular clipping;
- translation, scaling and rotation;
- scoped opacity for fills/text and scoped logical depth;
- loader-neutral item-stack icons and count/durability decorations;
- cached living-entity previews addressed through `EntityTypeRef`;
- bounded text tooltips and native item-name tooltips;
- automatic native-stack cleanup if consumer rendering throws.

Authored element trees can use `UiFocusManager` without adopting native Minecraft widgets.
Update it with the current `UiFocusTarget` list after layout, forward primary clicks and
key presses, and query `UiWidgetState` while drawing:

```java
private final UiFocusManager focus = new UiFocusManager();

focus.updateTargets(List.of(
        UiFocusTarget.enabled("search", searchBounds),
        new UiFocusTarget("accept", acceptBounds, canAccept)));

@Override
public boolean keyPressed(int key, int scanCode, int modifiers) {
    return focus.keyPressed(key, modifiers);
}
```

Target list order is Tab order, Shift-Tab traverses backwards, disabled targets are skipped,
and pointer hit-testing chooses the last (visually topmost) overlapping target. `UiKeys` and
`UiModifiers` name the normalized values delivered by all generated screen bridges.

Client libraries can subscribe to resource reloads without importing loader classes:

```java
context.resources().onReload(context.id("ui_assets"), assets::reload);
context.resources().reload().whenComplete((unused, failure) -> {
    if (failure != null) context.logger().error("UI resource reload failed", failure);
});
```

Listeners run on the Minecraft client thread after resources have been applied, including
the initial client load. Listener failures are logged and isolated, while `reload()`
completes only after the requested reload finishes.

Client-only development commands use the same typed command builder as server commands but
register through `ClientModContext.clientCommands()`. They execute locally, never send a
command packet to the server, and can safely drive tasks such as an EnderUI asset reload:

```java
context.clientCommands().register(CommandSpec.builder("enderui")
        .argument(Arguments.optional(Arguments.word("operation")))
        .suggests((command, remaining) -> List.of("reload"))
        .executes(command -> {
            context.resources().reload();
            command.reply("EnderUI resources reloaded");
            return 1;
        })
        .build());
```

Client commands deliberately reject permission levels and server-only player arguments.
This keeps their authority boundary explicit: they may change local UI state, but cannot
perform gameplay actions on behalf of the server.

The bridge compiles against all nine supported targets, including the extraction renderer
used by 26.2. `GENERAL_CLIENT_SCREENS` is advertised only by generated runtimes containing
this bridge.

## Current boundary

This is the first renderer slice, not the complete EnderUI backend. Sprite tint and stacked
opacity, living-entity previews, reusable focus navigation, resource reload listeners and
requested hot reloads are supported consistently. Block-owned arbitrary inventory menus are available
through [`StorageContainerSpec`](authored-inventories.md). Item rendering uses
`ItemStackRef`, so portable screens never import a native `ItemStack`; recipe and entity
identities likewise use `RecipeRef` and `EntityTypeRef` while their runtime conversions are
implemented feature by feature.
Calling an unfinished operation fails clearly instead of silently rendering differently on
one target.

General client screens are local client UI. Any state that affects gameplay must still be
owned and validated by the logical server through networking or a synchronized inventory
menu.
