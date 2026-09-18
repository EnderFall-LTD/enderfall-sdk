# Persistent workbench development demo

An isolated development fixture of the experimental block-owned inventory. Normal
demo builds and published runtime contents are unchanged. Use a disposable world:
save/reload and item-loss/duplication behaviour still need gameplay validation.

## Storage cabinet test

Place **Storage Cabinet** from the preview creative tab and open it. It is a plain
27-slot persistent inventory, not a recipe workbench. Check ordinary clicks and
shift-click in both directions, then close and reopen it. The top texture changes
to the open-barrel texture while at least one player is viewing it and returns when
the last viewer closes; barrel open/close sounds should follow the same first/last
viewer boundary.

Feed items from the top, extract them from the bottom, and verify side hoppers can do
both. The top must refuse extraction and the bottom must refuse insertion. Save and
reload with partial stacks, break the cabinet and confirm every stack drops exactly once.
Also test two simultaneous viewers, player death, disconnect and spectator changes.
Generated source and compilation cover all nine targets, but these are still live
gameplay acceptance checks rather than claims inferred from a successful build.

The preview tab also contains a `Portable Tool Test`. Right-click it in either hand;
an action-bar message must identify the current Minecraft version and loader exactly
once. Its Java class implements `PortableItem`, configures durability/rarity itself and
is compiled unchanged for every target. Use it on the six-way direction test to toggle
that block's compact state; the message must report the actual hand and whether the
player was crouching. Its use count is durable per-stack data and its translated details
appear while Shift is held. Each successful compact-state toggle costs one durability in
survival and reports the remaining value; creative mode performs the same action without
damaging the tool. An iron ingot repairs it in an anvil on every target, exercising the
same portable `repairItem` declaration through both legacy and component-era Minecraft
repair systems.

The six-way orientation block declares its editable `compact` property for the preview
configuration-tool policy. Use the tool normally to cycle the selected value; crouch-use
selects the next declared property and reports its name. The selection is stored on that
individual tool stack, so two tools and two players may retain different selections.

The `Portable Letter Test` proves a Furnection-style secure editor update. Put it in a
carried inventory slot and run `/enderfall_letter_edit <slot>`, where the hotbar is 0-8,
the main inventory is 9-35, and `-1` means off-hand. Enter multiple lines and choose
**Save** to retain its current author, **Sign** to save with your name, or **Cancel** to
discard the draft. Move or replace the letter while the editor is open: the server's
expected-item check must reject the save instead of changing the replacement stack.
Right-click the letter to display its stored author and text. Save/rejoin and repeat to
exercise native NBT on 1.20.1 and custom-data components on newer targets. The older
`/enderfall_letter <slot> <text>` command remains as a low-level data-path diagnostic.
The command bridge constructs its complete native argument tree before attaching it, so
the final integer argument is an executable node rather than autocomplete-only metadata.

The cabinet also opts into vanilla-compatible lazy loot tables. In a disposable
world, place an unopened populated test cabinet with:

```text
/setblock ~ ~ ~ enderfall_persistent_preview:storage_cabinet{LootTable:"enderfall_persistent_preview:chests/storage_cabinet_test",LootTableSeed:1L}
```

Its inventory must remain pending across save/reload and generate exactly one
diamond on first player, hopper or break access. Reopening must not generate again.
The table ID and seed are native world data; ordinary player-placed cabinets remain
empty. This command uses the Java block-entity NBT syntax supported by the test target.

## Connecting table and waterlogging test

Place several **Connecting Waterlogged Table** blocks side by side. Each block's
model, selection outline and collision should grow an arm only toward matching
tables. Breaking a middle block must remove the two exposed arms. A tiny red marker
appears for four server ticks after a connection actually changes, exercising the
portable scheduled-tick callback without continuously ticking the block.

Place one in a water source or use a water bucket on it. Water must remain inside
the non-full shape, flow/update normally around it, and be recoverable with an empty
bucket. Repeat after save/reload. The block class and registration are identical for
all nine targets; compile parity is automated, while these visible gameplay checks
remain manual acceptance.

## Workbench shape test

`enderfall_persistent_preview:orientation_test` is a separate six-way direction
test block in the preview creative tab. It uses barrel/dispenser placement: its
front points opposite the player's nearest look direction, including up and down.
It is deliberately modelled as a north-authored wooden tube with a copper front, so
looking down at the floor places its long axis vertically while looking horizontally
places it on its side. The model, selection outline and collision must rotate together.
Right-click it with an empty hand: its portable `compact` property should toggle both
the model and shape between long and short tubes without changing its facing. It has
no inventory and does not change the working horizontal workbench.

`enderfall_persistent_preview:axis_test` separately proves log/pillar placement.
Place it on the top or bottom of a block for a vertical Y post, on east/west faces
for X, and on north/south faces for Z. Opposite faces should match because an axis
has no positive or negative direction. Its striped model, selection outline and
collision must all rotate together.

The ordinary persistent workbench now has a tabletop and four legs with matching
portable collision and outline shapes. Check targeting through its gaps, collision
against its legs/top, and its existing inventory/menu in a disposable world. See
[block shapes](../../docs/block-shapes.md). The timed workbench geometry is unchanged.

The ordinary workbench also now faces the player on placement. Its asymmetric
rear brace makes rotation visible: place one from each cardinal direction and
check both model and collision rotate together. See
[directional blocks](../../docs/directional-blocks.md). In-game verification is pending.

## Crystal Washer machine test

Give yourself `enderfall_persistent_preview:mixer` and two `fluid_tank` blocks.
Place a tank immediately above the washer and another below it. Fill the upper
tank with water; it pumps down into the washer. Open the washer and put amethyst
shards in its input slot. Every 60 ticks, one shard and one bucket of water become
one prismarine crystal and half a bucket of output water. The output pumps into
the lower tank. The machine menu shows items/progress, not tank gauges.

Check blocked item output and a full fluid output (remove the lower tank), ingredient
removal during processing, save/reopen, and `/reload` with a changed recipe.
Recipe file: `data/enderfall_persistent_preview/enderfall_machine/washed_crystal.json`.

## Launch commands

The integrated fixture registers a decorative apple plus three smaller live slot
displays on the ordinary workbench, on all nine targets. Add items to its three
slots, close the menu, and check the displays; removing items should clear them.
The central apple remains decorative and now gently bobs up and down using a
portable item animation. Stored slot items remain static. Check lighting, resource reloads, save/reopen
and a second client joining after items were inserted. These native runtime checks
remain pending even after compilation passes.

The shared fixture now supports all nine development targets. For example:

```powershell
.\gradlew.bat :runtime-generated-neoforge-26.2:runClient '-Penderfall.persistence=true' '-Penderfall.persistenceFixture=true' --no-daemon --console=plain
```

Replace the target project with any supported generated runtime. This mode uses
`run/persistence-fixture/client` within that project and the ordinary SDK bootstrap.
Do not combine `enderfall.persistenceFixture` with `enderfall.blockEntityPreview`:
they are alternate launchers for the same mod. See the
[cross-target instructions](../../docs/block-entities.md) for server runs and checks.

### Original Fabric-only launcher

From the SDK repository root in PowerShell:

```powershell
.\gradlew.bat :runtime-generated-fabric-1.21.4:runClient '-Penderfall.blockEntityPreview=true' --no-daemon --console=plain
```

This uses `runtime-generated-fabric-1.21.4/run/persistent-preview/client`, separate
from the normal development client. The property is required to enable the fixture
and native preview classes. The client has launched, entered a single-player world,
and shut down cleanly; this does not establish persistence or crafting correctness.

To test the ordinary consumer bootstrap in the integrated SDK development profile,
add `'-Penderfall.persistence=true'` to that command. Without it, the original
explicit preview bootstrap remains in use. Neither mode is release acceptance.

## Try the fluid transfer fixture

In the integrated profile, right-click a tank with an empty hand to open its live
contents screen. Fluid ID, amount, capacity and fill percentage refresh as tanks
transfer. Bucket clicks still fill/drain. The view closes if its block disappears,
unloads, or the player becomes invalid or moves more than eight blocks away.
The gauge uses a fixed blue colour, even for lava; read the fluid ID for its type.

In the integrated profile, `/enderfall_gauge` opens a server-synchronized visual
test with 0%, 50% and 100% buttons. This uses synthetic values, not a placed tank;
check that the fill rises from the bottom and matches the displayed percentage.
The original isolated preview profile does not enable this command.

Use a disposable world and obtain `enderfall_persistent_preview:fluid_tank` from
the preview tab. This prismarine-textured block holds four buckets. Its top accepts
fluid, bottom allows extraction, and horizontal faces allow both operations.
Place two directly above one another: the upper tank pumps into the lower tank at
one bucket per second at 20 TPS while loaded. Fill the upper tank with water or lava
and retrieve it from a horizontal face of the lower tank with an empty bucket.
Stacked empty buckets use vanilla inventory/overflow handling.

Check full destinations, incompatible fluids and world save/reload with partial
quantities. Bucket interactions now report server-confirmed success/rejection and
the stored fluid, percentage and exact SDK units in the action bar. Empty-hand
right-click reports a fresh snapshot too. Creative mode keeps the held bucket;
use the status message and extraction to verify transfer. One bucket is 81,000 units.
This fixture currently has no textured gauge. Breaking it does **not** preserve
fluid in the dropped block item; empty it first. These are test instructions, not
claims that the gameplay checks have passed.

## Try the workbench

1. Create a disposable Creative world with commands enabled.
2. Find **Persistent Workbench** in **EnderFall Persistence Preview**, or run
   `/give @s enderfall_persistent_preview:workbench`.
3. Place it and right-click it with an empty hand.
4. Put **2 iron ingots**, **1 redstone dust**, and **1 quartz** into the three input
   slots, left to right. The custom assembly recipe should produce **1 amethyst shard**.
5. Take the result and check that only the recipe quantities are consumed.

To check persistence, leave extra ingredients in the inputs, close and reopen the
menu, then save/quit and re-enter the world. Check exact quantities after each step.
Break the block with ingredients inside and check that contents drop exactly once.
Multiple viewers and disconnect/reconnect also remain acceptance checks.

The original workbench keeps its instant-craft behaviour and calculated output.

## Try the timed workbench

The separate **Timed Workbench** uses a white quartz model. Get it from the same
creative tab or `/give @s enderfall_persistent_preview:timed_workbench`.

Use the same recipe: 2 iron ingots, 1 redstone dust, 1 quartz. It is configured to
process each batch in **100 server ticks** (five seconds at 20 TPS), with a synchronized
purple progress bar and a real stored output slot. Processing runs while the block
is loaded and ticking, even when its menu is closed. Taking output does not craft again.

The screen now shows server-owned status: waiting for a recipe, processing, output
blocked, remainder blocked, or machine fault. Blocked progress is amber; a fault is
red and requires checking the log. Hover the bar for its percentage. Remainder
blocked means a returned container item cannot fit back into its original input slot;
free that slot's capacity rather than expecting the machine to drop or discard it.
Status is recalculated after loading, not stored as stale world data.

The user reported the initial timed craft working in-game. The newer status display
still needs a client restart and visual check; this is not save/reload or multi-viewer proof.

Check that a full output pauses processing without consuming inputs. Save/reload
halfway through a batch, check output retention, and test shift-click and two viewers.
These gameplay checks are pending; compilation is not proof of those behaviours.
The new block ID deliberately leaves existing three-slot workbench saves unchanged.
Unattended crafting does not invoke the player-owned instant-craft callback.

## Hopper automation (timed workbench only)

The timed block now exposes fixed world-facing inventory ports. These do not rotate
with the player placing the block. Point each feeding hopper into the workbench:

| Hopper position | Input supplied | Demo ingredient |
| --- | --- | --- |
| Above | Slot 1 | Iron ingots |
| North side | Slot 2 | Redstone dust |
| East side | Slot 3 | Quartz |
| Directly below | Extracts finished output only | Amethyst shards |

The bottom hopper can point toward a chest to carry output away. South and west are
closed for the three-input demo; for SDK recipes with four or five inputs they feed
slots 4 and 5 respectively. Input slots cannot be drained through these ports, and
the output slot rejects insertion. The instant workbench exposes no hopper ports.

This is positional routing, not automatic ingredient sorting: a wrongly supplied
item can occupy its input slot and prevent a recipe matching. Remove incorrect items
or crafting remainders manually. Processing pauses if output/remainders cannot fit.

Restart the development client to load this change. Build and policy checks are not
a live hopper test: verify insertion routes, output extraction, full-output recovery,
and inventory preservation across save/reload in a disposable world.

## Source layout

- `PersistentDemo`: portable entrypoint and creative tab.
- `PreviewBlocks`: block, block item, and persistent inventory definition.
- `PreviewContainers`: general storage-menu registration, separate from recipes.
- `PreviewRecipes`: custom recipe type and persistent menu registration.
- `src/loader/fabric`: development-only bootstrap selecting the SDK preview platform.
- `src/main/resources`: explicit 1.21.4 recipe, model, translation, and loot fixtures.

Portable Java compiles against only the SDK API with Java 17 bytecode. The launcher
uses Java 21 and the target's Fabric API. Resources are static fixtures, not a new
portable block-entity data-generation API.

Build the fixture without launching:

```powershell
.\gradlew.bat :runtime-generated-fabric-1.21.4:persistentDemoJar --no-daemon --console=plain
```

The resulting `build/persistentDemo/enderfall-persistent-preview-dev.jar` under the
generated runtime project is development-only. It requires the separately compiled
SDK preview classes on the launch classpath; do not distribute it as a standalone mod.

### Experimental standalone model display

On all nine generated targets, the timed workbench declares a separate oak-plank lid model
at 1.02 blocks above its base. It uses `ModelRef` and `.model(...)`, not an item.
The lid opens to 75 degrees over ten ticks when the first player opens its menu,
and closes over ten ticks when the last viewer closes it. It is no longer a loop.
Check that the hinge edge stays attached, the lid receives world lighting and
survives a resource reload. Check smooth motion and pause/resume as well.
Test with two viewers: closing one menu should not close the lid until both leave.
Playback resets on chunk reload. Rapid reversal currently starts the authored
opposite pose rather than blending from the interrupted pose.
This feature still needs visual acceptance. On 26.2, also check that chunk unloading
does not leave stale model parts and that resource reloads update the lid texture.

### Fluid display checks

FML development launches include the relocated shared runtime in the `enderfall_sdk`
mod's source sets. `prepareFmlSharedRuntime` checks API presence and parser relocation.
Do not put raw TomlJ/ANTLR dependencies on these launch classpaths: Forge 1.20.1's
access-transformer parser requires a different ANTLR runtime. This development
wiring does not change the portable fixture or its published artifact format.

On generated targets, the tank has an open prismarine frame and draws the
public reservoir contents inside it. Fill a standalone tank with one water bucket:
the fluid should reach one quarter of the interior height. Add three more buckets
to fill it, then drain it and check that the visual empties. Test lava separately
in an empty tank; it should use its native texture and emitted light. Stacked
tanks still transfer downwards, so use a standalone tank for height checks.

Also check chunk unload/reload and resource reload. These visual checks are still
pending. The 26.2 backend uses captured render state; check both water and lava
there as well. Custom third-party fluid appearance hooks are not yet validated.
