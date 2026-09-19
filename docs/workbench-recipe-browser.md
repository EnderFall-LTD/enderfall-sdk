# Workbench recipe browser (experimental)

An instant item workbench can discover every matching recipe from its registered
data-pack recipe type and present the results without consumer networking or a
loader-specific screen class.

```java
var recipes = Registration.recipes(context.modId());
var menus = Registration.menus(context.modId());
var assembly = recipes.workbench("assembly", 3);

WorkbenchRef workbench = menus.workbench(
        "workbench_menu",
        "Assembly Workbench",
        assembly,
        properties -> properties
                .persistent(storage)
                .recipeBrowser(),
        craft -> context.logger().info("Crafted {}", craft.recipeId()));
```

`recipeBrowser()` shows five results per page. Use `recipeBrowser(visibleEntries)`
to select one to five. The SDK adds synchronized preview slots, required ingredient
counts, selected-result highlighting, paging buttons and mouse-wheel paging. Clicking
a result selects it; taking the real output performs the craft.

The logical server discovers and sorts matching recipes by resource ID, validates every
selection, recalculates it whenever inputs change and consumes the selected recipe's
exact counted ingredients. The client only renders synchronized menu state and sends
Minecraft's normal menu-button action. A forged index, a stale recipe or changed inputs
cannot craft a different result.

Preview entries are protected ghost slots: they cannot be picked up, shift-clicked into
or used as inventories. The real result remains the ordinary synchronized output slot.
The selected recipe is remembered by ID while it still matches, so data ordering or a
new match does not silently switch the player's choice.

This first slice supports instant item workbenches. Timed and fluid machine recipes do
not accept `recipeBrowser()` yet; their progress, tanks and output rules need a different
layout and synchronization contract. Search text and arbitrary recipe-category tabs are
also separate future additions.

## Test it in the persistent preview

Place the Persistent Workbench and insert, from left to right, two iron ingots, one
redstone dust and one quartz. Three matching output choices should appear. Select each
choice, change pages with the arrows or wheel when applicable, then take the real output.
Only the selected recipe should craft and only its declared quantities should be consumed.

