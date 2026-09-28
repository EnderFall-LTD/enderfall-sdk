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
- automatic native-stack cleanup if consumer rendering throws.

The bridge compiles against all nine supported targets, including the extraction renderer
used by 26.2. `GENERAL_CLIENT_SCREENS` is advertised only by generated runtimes containing
this bridge.

## Current boundary

This is the first renderer slice, not the complete EnderUI backend. Texture tint/opacity is
currently restricted to opaque white, and item stacks, entity previews, tooltips, focus
widgets, resource reload, hot reload and arbitrary inventory menus are still being added.
Calling an unfinished operation fails clearly instead of silently rendering differently on
one target.

General client screens are local client UI. Any state that affects gameplay must still be
owned and validated by the logical server through networking or a synchronized inventory
menu.
