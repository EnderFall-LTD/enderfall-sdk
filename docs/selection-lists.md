# Portable selection lists

`MenuSelectionList` adds server-owned, paged choices to any synchronized portable menu.
It is suitable for recipes, furniture variants, upgrades, destinations, or configuration
presets and needs no consumer client class or packet.

```java
private static final MenuSelectionList RECIPES =
        MenuSelectionList.of("recipes", 15, 45, 210, 5);

MenuRef menu = context.menus().register("recipes",
        MenuSpec.builder("Recipes")
                .size(240, 190)
                .selectionList(RECIPES)
                .build(), action -> {
                    MenuListAction choice = RECIPES.action(action).orElseThrow();
                    if (choice.type() == MenuListAction.Type.SELECT) {
                        context.players().message(action.playerId(), choice.entryId());
                    }
                });
```

The server creates a page with stable IDs and display labels:

```java
List<MenuSelectionEntry> entries = List.of(
        MenuSelectionEntry.enabled("example:oak_chair", "Oak chair")
                .item(new ItemRef(ResourceId.parse("example:oak_chair")), 4)
                .details("Crafts four oak chairs"),
        new MenuSelectionEntry("example:locked_chair", "Locked chair", false));

MenuState state = MenuState.builder()
        .selectionPage(RECIPES, entries, 0, "")
        .build();
context.menus().open(playerId, menu, state);
```

Visible row actions, empty rows, disabled entries, and previous/next actions are checked
against the active server session before consumer code runs. The client sends only the
declared row action; it never supplies the trusted entry ID. Labels and enabled states
update when `MenuActionContext.update` or `MenuManager.update` publishes a new page. The
selected server-owned ID is marked in the list. Scrolling over the rows requests the
previous or next permitted page, while scrolling elsewhere remains available to other
screen controls. The generated bridge accounts for the different native scroll callback
used by Minecraft 1.20.1.

An entry can optionally add a portable item-stack icon and count with `item`, plus a
bounded one-line hover description with `details`. The SDK resolves and renders those
through the target's native item registry; consumer code does not import Minecraft or a
loader. Unknown or malformed synchronized icon IDs omit the visual without weakening
the server-owned row action.

Each rich row uses six bounded menu-state entries plus paging metadata. The normal
64-entry menu-state limit still applies, including unrelated labels, gauges, and text
fields. One list can show up to eight rows and a menu can declare two lists as long as
the combined button and state limits are respected.

Filtering is deliberately server-owned. Add a `MenuTextInput` and Search button, validate
the submitted query through the normal menu action, filter the authoritative entry list,
then send page zero again. See `PreviewRecipeBrowser` and `/enderfall_recipes` in the
persistent preview.
