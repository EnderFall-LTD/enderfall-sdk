# Registration groups

Experimental, additive API: existing registrars and specification builders still work.

## Blocks and items

Keep declarations in separate classes. Supply the namespace once per group; returned
references already contain their final IDs, even before initialization.

```java
public final class ModBlocks {
    public static final Registration.Blocks BLOCKS = Registration.blocks("my_mod");

    public static final BlockRef WORKSTATION = BLOCKS.block("workstation", p -> p
        .copyFrom(ResourceId.of("minecraft", "oak_planks"))
        .strength(3, 6)
        .withItem());

    public static final BlockRef REINFORCED = BLOCKS.block("reinforced", p -> p
        .copyFrom(WORKSTATION)
        .strength(5, 10)
        .withItem());
}

public final class ModItems {
    public static final Registration.Items ITEMS = Registration.items("my_mod");
    public static final ItemRef CRYSTAL = ITEMS.item("crystal");
    public static final ItemRef TOOL = ITEMS.item("tool",
        p -> p.maxStackSize(1).durability(256).fireResistant());
}
```

In common initialization:

```java
Registration.register(context, ModBlocks.BLOCKS, ModItems.ITEMS);
```

There is no consumer `.build()` call. Configuration callbacks run during attachment,
not class loading. Keep them declarative: do not perform native registration or
modify registration groups inside them. Attach each group once. Group attachment
freezes further declarations; native failures abort startup rather than retrying a
partially modified registry.

Duplicate IDs, block-item/item collisions, namespace mismatches, invalid specifications,
missing copied declarations and copy cycles are rejected before native registration.
Block copies may refer across groups passed to the same `register` call; group order
does not matter. This is not a global mod registry, and it does not use reflection
to discover classes.

## Custom block definitions

`BLOCKS.block("workstation", MyBlock::new, p -> p.strength(3).withItem())`
accepts a factory for an SDK `PortableBlock`, not a native Minecraft subclass.
The factory runs once per registered ID during preflight. Its `configure` method
runs before the declaration's property overrides. `onUse(context, event)` receives
unhandled, uncancelled server block-use events for that ID through the existing SDK
interaction bus. Calling `event.handle()` prevents native fallback; leave the event
unchanged to preserve other handlers. Property copies do not copy these callbacks.

Instances are shared definitions, not placed-block instances: per-position mutable
data belongs in declared persistent storage. [Static shapes](block-shapes.md) are
supported, together with opt-in [horizontal facing](directional-blocks.md).
Custom placement, general typed block states and state-dependent shapes are now
available, including neighbor-derived state updates, opt-in scheduled transitions
and real waterlogging. Bounded world queries and many specialized vanilla hooks are
still pending, so this initial interface is not yet a replacement
for every Minecraft `Block` method. See [the furniture-port roadmap](furniture-port.md).

## Copy semantics

- `copyFrom(BlockRef)` copies the portable properties of a declaration in the same
  attachment batch, including any native base. It does **not** copy its block item,
  storage, callbacks, or recipes.
- `copyFrom(BlockSpec)` copies an existing immutable portable specification.
- `copyFrom(ResourceId)` uses the target's native property-copy operation. The source
  must exist when properties are resolved. Prefer `BlockRef` for SDK declarations.
- A later copy replaces earlier property overrides. Calls after the copy override
  the copied properties.
- Native copying is available in generated feature runtimes
  (`-Penderfall.persistence=true`). Reference/baseline runtimes reject it explicitly.
- No fallback is silently substituted for an absent block.

The native implementation uses `Properties.copy` on 1.20.1 and
`Properties.ofFullCopy` on newer supported targets. These inherit Minecraft's own
copy semantics; they are not a backport of the source block. Models, loot, recipes,
block subclasses and state definitions are separate. State-dependent native property
callbacks can require state properties that a basic block does not have; do not use
such a copy as a substitute for implementing the block's behavior.

Explicit portable overrides currently cover strength, friction, jump factor,
luminance, tool requirement and sound. Item declarations expose the existing
stack-size, durability, fire-resistance and rarity settings, with
`ItemSpec.Builder.copyFrom(ItemSpec)`. This is not a claim that every Minecraft
property, item component, or arbitrary native registry is portable.

## Persistent blocks

For storage used only by the block:

```java
BLOCKS.block("tank", p -> p.strength(2).storage(s -> s.tank(RESERVOIR)));
```

Storage automatically creates the block item and assigns its owner.
For storage shared with a workbench definition, declare an immutable schema using
`Registration.storage(blockRef, s -> ...)`, then pass it to `p.storage(schema)`.
The persistent preview's `PreviewBlocks` demonstrates static declarations and
shared schemas without `.build()`.

## Recipes, menus and creative tabs

```java
Registration.Recipes RECIPES = Registration.recipes("my_mod");
WorkbenchRecipeTypeRef ASSEMBLY = RECIPES.workbench("assembly", 3);
WorkbenchRecipeTypeRef WASHING = RECIPES.machine("washing", 1);

Registration.Menus MENUS = Registration.menus("my_mod");
WorkbenchRef BENCH = MENUS.workbench("workbench", "Workbench", ASSEMBLY,
    p -> {}, craft -> {});

StorageContainerRef CABINET = MENUS.container("cabinet", "Oak Cabinet", CABINET_STORAGE,
    p -> p.openState(CabinetBlock.OPEN).sounds(ContainerSoundProfile.BARREL));

MenuRef STATUS = MENUS.menu("status", "Status", p -> p.size(220, 140),
    action -> {});

Registration.Tabs TABS = Registration.tabs("my_mod");
CreativeTabRef TAB = TABS.tab("main", "itemGroup.my_mod", CRYSTAL,
    p -> p.entry(CRYSTAL).entry(new ItemRef(WORKSTATION.id())));
```

Attach these groups alongside blocks and items. Recipe types are registered before
menus; creative tabs are registered last. Recipe JSON/datagen and gameplay callbacks
remain separate from registration. The SDK supplies the synchronized client screen
for these portable menus; authors do not register the same screen on both sides.
Storage containers use a declared persistent inventory with 9-54 slots in complete
rows of nine and Minecraft's standard synchronized storage UI. Shift-click routing
is native, and all slots currently accept hopper insertion and extraction from every
face. More selective sided/filter policies and arbitrary native screens remain outside
this initial facade.

## Client renderers

From the client entrypoint, with renderer code in `src/client`:

```java
Registration.renderer(clientContext, WORKSTATION, r -> r.inventorySlot(0, transform));
```

The renderer still goes through the existing client registrar and its capability,
duplicate-registration and lifecycle checks. It is never invoked automatically
during common initialization.

## Runtime folders

Do not delete the old runtime directories yet: default settings still include
them and parity/build tooling still references their layouts. Generated runtime
directories are target build outputs, not a need for consumer target-specific
source. Removal of reference sources requires a separate build/publishing/parity
migration. No runtime folders were removed for this feature.
