# Combined machine recipes (experimental)

`MachineRecipeSpec` describes positional item/fluid inputs and outputs, a recipe
type ID, and processing ticks. Each section is limited to 16 entries. Recipes
require an input and an output, with duration 1..1,728,000 ticks. Lists are immutable.
`FluidIngredient` accepts an exact fluid ID or a fluid tag and a positive amount.
Quantities use SDK units: 81,000 units per bucket, not millibuckets.

`MachineFluidPlan` checks immutable tank snapshots without consuming anything.
It returns missing-input, output-blocked, or ready with proposed replacement tanks.
Inputs and outputs map to separate positional tanks. Tags are resolved by the
owning runtime's supplied predicate. Existing output fluids cannot be mixed, and
capacity checks avoid integer overflow. A blocked result retains the original tanks.

## Registration

```java
var type = context.recipes().registerMachineType("washing", 1);
context.workbenches().register("washer", WorkbenchSpec.builder("Washer", type)
    .persistentMachine(storage, List.of("coolant"), List.of("effluent"))
    .build(), craft -> { });
```

Register the persistent block first. Storage needs `inputSlots + 1` inventory
slots and all named tanks. The current menu supports 1–5 positional item inputs
and one stored item output; each fluid section supports up to 16 positional tanks.
Input and output tanks must be distinct. Larger item layouts permitted by the
general recipe record are rejected by this loader and generator.

## Data-pack format

Files live at `data/<namespace>/enderfall_machine/<path>.json` on every target.
This is an SDK data-pack loader, not a vanilla crafting serializer or recipe-book
entry. Higher-priority packs override the same resource; candidates are ordered
by resource ID. `DataGenerationContext.machineRecipe(id, spec)` emits this format:

```json
{
  "type": "example:washing",
  "duration_ticks": 60,
  "item_inputs": [{"item": "minecraft:amethyst_shard", "count": 1}],
  "fluid_inputs": [{"fluid": "minecraft:water", "amount": 81000}],
  "item_outputs": [{"item": "minecraft:prismarine_crystals", "count": 1}],
  "fluid_outputs": [{"fluid": "minecraft:water", "amount": 40500}]
}
```

Use `tag` instead of `item` or `fluid` for native tag matching. All four arrays
are required, even empty fluid sections. One bucket is 81,000 SDK units (1,000 mB).
Recipe layouts must match the machine's item and tank counts to be selected.
Resources are limited to 64 KiB, nesting to 12, and discovery to 4,096 recipes.
Invalid resources are logged and excluded. Recipes are cached per resource manager;
replacing it on data-pack reload reloads the definitions. A changed definition hash
or duration resets progress.

## Processing

The timed processor validates items, remainders, fluid identity and output capacity
before completion. Blocked outputs pause without consuming ingredients. Completion
commits item, field and tank replacements together, then notifies viewers. Retained
tank handles remain valid. Ingredients stay available to automation while working;
removing them cancels progress. No offline catch-up or reservation occurs.

The first matching recipe wins even if blocked. Input slots need room for crafting
remainders. Custom item components, chance outputs, shared input/output tanks and
recipe-book integration are outside this feature. Real gameplay checks are still
required for save/reopen, reload, blocked outputs and automation races.
