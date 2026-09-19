# Portable API

Common entrypoints implement `EnderfallMod`; optional client entrypoints implement
`EnderfallClientMod`.

```java
public final class ExampleMod implements EnderfallMod {
    @Override
    public void initialize(ModContext context) {
        context.items().register("example_item", ItemSpec.builder().build());
    }
}
```

The stable foundation covers lifecycle/environment information, basic items and blocks,
creative tabs, tick/server/player/interaction events, server-authoritative player inventory
transactions and feedback, commands, typed configuration, play-phase packets, portable
recipes/tags/translations/loot/models, structured logging, and dependency checks.

Portable gameplay handlers can distinguish logical sides, claim an interaction, and use the
player service without importing a native player or item stack:

```java
InventoryCost ritual = InventoryCost.builder()
        .item(crystal, 4)
        .item(alloyBlockItem, 1)
        .build();

context.events().subscribe(SdkEvents.INTERACTION, event -> {
    if (event.side() == InteractionEvent.Side.SERVER && event.target().equals(workbench.id())) {
        if (context.players().tryConsume(event.playerId(), ritual)) {
            context.players().give(event.playerId(), resonanceCore, 1);
            context.players().actionBar(event.playerId(), "Infusion complete");
        }
        event.handle();
    }
});
```

Inventory-backed workbenches are also registered once in common code. The SDK creates the
loader-native recipe serializer, menu type, synchronized slots, and client screen:

```java
WorkbenchRecipeTypeRef infusing = context.recipes().registerWorkbenchType("infusing", 3);
WorkbenchRef workbench = context.workbenches().register("resonance_workbench",
        WorkbenchSpec.builder("Resonance Workbench", infusing)
                .recipeBrowser()
                .build(), crafted -> {
            context.logger().info("{} crafted {}", crafted.playerId(), crafted.result().id());
        });

context.dataGeneration().register(data -> data.workbenchRecipe(
        context.id("resonance_core_infusing"),
        new WorkbenchRecipeSpec(infusing, List.of(
                CountedIngredient.of(Ingredient.item(voidCrystal.id()), 4),
                CountedIngredient.of(Ingredient.item(alloyBlock.id()), 1),
                CountedIngredient.of(Ingredient.item(ResourceId.of("minecraft", "echo_shard")), 1)),
                new RecipeResult(resonanceCore.id(), 1))));

context.workbenches().open(playerId, workbench);
```

Recipe matching and input consumption happen on the logical server. The result slot, player
inventory, hotbar, and shift-click movement use Minecraft's native menu synchronization.
Closing a temporary workbench returns unused inputs. A workbench declared with
`persistent(storage)` keeps its input inventory in the placed block entity across closing,
save/reload and later reopening.

For instant item workbenches, `recipeBrowser()` discovers matching data-pack recipes on
the server and adds selectable synchronized output previews. Mods do not register a
packet or target-specific screen for this. See the
[workbench recipe browser](workbench-recipe-browser.md) for its security and scope.

`tryConsume` checks the complete `InventoryCost` before removing anything. Player operations
are server-authoritative and must be called from server-side callbacks. `handle()` returns a
successful consumed interaction to Minecraft, while `cancel()` returns a failed interaction.

Current limitation: `PlayerManager` inventory operations resolve only items registered by
the calling mod. Constructing an `ItemRef` for a vanilla or another mod's item does not
make those operations work. This restriction does not apply to recipe ingredients, which
are resolved separately through Minecraft's recipe registries.

Entities, block entities, general-purpose inventory menus, arbitrary rendering, complex world generation,
fluids, persistent custom player state, custom data components, and broad native wrappers are not yet
implemented as portable APIs. Use a native source root for those until a portable module
defines and passes the complete target contract. The planned one-call registration model
for inventory menus/block entities, custom processing recipes, and fluid families is described
in `feature-status.md`; the basic synchronized-screen surface described above is working.

`ResourceId` rejects absolute, empty, `.` and `..` path segments. The data generator
also normalizes every destination and refuses to write outside its configured output root.

APIs marked `@Experimental` are outside compatibility guarantees. APIs marked
`@CapabilityGated` have identical source signatures on every target, but callers must
check `context.capabilities()` where an old game genuinely cannot implement the feature.
