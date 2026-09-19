package uk.co.enderfall.sdk.preview;

import java.util.List;
import java.util.Locale;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.registry.ItemRef;
import uk.co.enderfall.sdk.api.ui.MenuButton;
import uk.co.enderfall.sdk.api.ui.MenuLabel;
import uk.co.enderfall.sdk.api.ui.MenuListAction;
import uk.co.enderfall.sdk.api.ui.MenuSelectionEntry;
import uk.co.enderfall.sdk.api.ui.MenuSelectionList;
import uk.co.enderfall.sdk.api.ui.MenuSpec;
import uk.co.enderfall.sdk.api.ui.MenuState;
import uk.co.enderfall.sdk.api.ui.MenuTextInput;

/** Server-owned recipe-style browser proving filtering, paging and validated selection. */
public final class PreviewRecipeBrowser {
    private static final MenuSelectionList RECIPES = MenuSelectionList.of("recipes", 15, 52, 210, 5);
    private static final List<MenuSelectionEntry> ALL = List.of(
            entry("enderfall_persistent_preview:assembly", "Crystal assembly", "amethyst_shard", 4,
                    "Workbench recipe: four crystal inputs"),
            entry("enderfall_persistent_preview:washed_crystal", "Washed crystal", "prismarine_crystals", 1,
                    "Machine recipe: washed with water"),
            entry("enderfall_persistent_preview:resonant_frame", "Resonant frame", "echo_shard", 2,
                    "Timed processing recipe"),
            new MenuSelectionEntry("enderfall_persistent_preview:locked_upgrade", "Locked upgrade", false)
                    .item(item("barrier"), 1).details("Disabled server-owned entry"),
            entry("minecraft:crafting_table", "Crafting table", "crafting_table", 1, "Vanilla crafting block"),
            entry("minecraft:furnace", "Furnace", "furnace", 1, "Vanilla cooking block"),
            entry("minecraft:chest", "Chest", "chest", 1, "Vanilla storage block"),
            entry("minecraft:barrel", "Barrel", "barrel", 1, "Vanilla storage block"),
            entry("minecraft:hopper", "Hopper", "hopper", 1, "Vanilla item transfer"),
            entry("minecraft:anvil", "Anvil", "anvil", 1, "Vanilla repair block"),
            entry("minecraft:smithing_table", "Smithing table", "smithing_table", 1,
                    "Vanilla smithing block"));

    private PreviewRecipeBrowser() { }

    private static MenuSelectionEntry entry(String id, String label, String icon, int count, String details) {
        return MenuSelectionEntry.enabled(id, label).item(item(icon), count).details(details);
    }

    private static ItemRef item(String path) {
        return new ItemRef(ResourceId.of("minecraft", path));
    }

    public static void register(ModContext context) {
        var menu = context.menus().register("recipe_browser", MenuSpec.builder("Portable recipe browser")
                .size(240, 200)
                .label(MenuLabel.text("Filter", 15, 27))
                .textInput(MenuTextInput.singleLine("recipe.filter", "Search recipes...", 50, 20, 110, 32))
                .button(MenuButton.of("apply_filter", "Search", 165, 20, 60))
                .selectionList(RECIPES)
                .label(MenuLabel.text("{recipe.status}", 15, 180))
                .build(), action -> {
                    String query = action.input("recipe.filter").strip();
                    List<MenuSelectionEntry> visible = filtered(query);
                    int currentPage = parsePage(action.state().value("recipes.page"));
                    String selected = action.state().value("recipes.selected");
                    String status = selected.isBlank() ? "Choose a recipe" : "Selected: " + selected;
                    int page = currentPage;
                    if (action.action().equals("apply_filter")) {
                        page = 0;
                        status = visible.isEmpty() ? "No matching recipes" : "Choose a recipe";
                    } else {
                        MenuListAction listAction = RECIPES.action(action).orElseThrow();
                        if (listAction.type() == MenuListAction.Type.PREVIOUS_PAGE) page--;
                        else if (listAction.type() == MenuListAction.Type.NEXT_PAGE) page++;
                        else {
                            selected = listAction.entryId();
                            status = "Selected: " + listAction.label();
                            context.players().message(action.playerId(), "Selected recipe " + selected);
                        }
                    }
                    action.update(page(visible, page, query, selected, status));
                });

        context.commands().register(CommandSpec.builder("enderfall_recipes")
                .description("Opens the portable recipe selection preview.")
                .executes(command -> {
                    var player = command.sourcePlayerId();
                    if (player.isEmpty()) {
                        command.reply("Run this command as a player.");
                        return 0;
                    }
                    context.menus().open(player.get(), menu,
                            page(ALL, 0, "", "", "Choose a recipe"));
                    return 1;
                }).build());
    }

    private static MenuState page(List<MenuSelectionEntry> entries, int requestedPage,
            String query, String selected, String status) {
        int pages = Math.max(1, (entries.size() + RECIPES.visibleRows() - 1) / RECIPES.visibleRows());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        return MenuState.builder().selectionPage(RECIPES, entries, page, selected)
                .value("recipe.filter", query)
                .value("recipe.status", status)
                .build();
    }

    private static List<MenuSelectionEntry> filtered(String query) {
        String search = query.toLowerCase(Locale.ROOT);
        if (search.isBlank()) return ALL;
        return ALL.stream().filter(entry -> entry.label().toLowerCase(Locale.ROOT).contains(search)
                || entry.id().toLowerCase(Locale.ROOT).contains(search)).toList();
    }

    private static int parsePage(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
