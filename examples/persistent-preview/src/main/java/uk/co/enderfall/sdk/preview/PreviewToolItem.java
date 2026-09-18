package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.block.BlockToolRef;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.item.ItemDataKey;
import uk.co.enderfall.sdk.api.item.PortableItem;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.api.registry.Rarity;

/** Class-shaped portable item proving configuration and loader-neutral use routing. */
public final class PreviewToolItem implements PortableItem {
    public static final BlockToolRef CONFIGURATION_TOOL = BlockToolRef.of(
            "enderfall_persistent_preview", "configuration_tool");
    public static final ItemDataKey<Integer> USE_COUNT = ItemDataKey.integer(
            ResourceId.parse("enderfall_persistent_preview:portable_tool_uses"),
            0, 1_000_000);
    public static final ItemDataKey<Integer> SELECTED_PROPERTY = ItemDataKey.integer(
            ResourceId.parse("enderfall_persistent_preview:portable_tool_property"), 0, 63);

    @Override public void configure(ItemSpec.Builder properties) {
        properties.maxStackSize(1)
                .durability(128)
                .repairItem(ResourceId.parse("minecraft:iron_ingot"))
                .rarity(Rarity.UNCOMMON)
                .data(USE_COUNT)
                .data(SELECTED_PROPERTY)
                .tooltip("tooltip.enderfall_persistent_preview.portable_tool.summary")
                .shiftHint("tooltip.enderfall_persistent_preview.hold_shift")
                .shiftTooltip("tooltip.enderfall_persistent_preview.portable_tool.details");
    }

    @Override public void onUse(ModContext context, InteractionEvent event) {
        int uses = incrementUses(event);
        context.players().actionBar(event.playerId(), "Portable stack use count: " + uses + " on "
                + context.platform().minecraftVersion() + " " + context.platform().loader());
        event.handle();
    }

    @Override public void onUseOnBlock(ModContext context, InteractionEvent event) {
        event.blockLocation().ifPresent(location -> {
            var data = event.itemData().orElseThrow(() ->
                    new IllegalStateException("Portable tool did not receive its declared stack data"));
            int selectedProperty = data.getOrDefault(SELECTED_PROPERTY, 0);
            var block = new BlockRef(event.target());
            var result = event.sneaking()
                    ? context.blockStates().selectNextToolProperty(
                            block, location, CONFIGURATION_TOOL, selectedProperty)
                    : context.blockStates().cycleToolProperty(
                            block, location, CONFIGURATION_TOOL, selectedProperty);
            result.ifPresent(change -> {
                data.set(SELECTED_PROPERTY, change.selectionIndex());
                if (!change.changed()) {
                    context.players().actionBar(event.playerId(), "Selected property: "
                            + change.propertyName() + " (" + (change.selectionIndex() + 1)
                            + "/" + change.propertyCount() + ")");
                    event.handle();
                    return;
                }
                String hand = event.hand().map(value -> value.name().toLowerCase()).orElse("unknown hand");
                var stack = event.itemStack().orElseThrow(() ->
                        new IllegalStateException("Portable tool did not receive its held stack"));
                int uses = incrementUses(event);
                boolean broke = !event.creativeMode() && stack.damage(1);
                String durability = event.creativeMode() ? "creative durability"
                        : broke ? "tool broke"
                        : stack.remainingDurability() + "/" + stack.maxDamage() + " durability";
                context.players().actionBar(event.playerId(), "Portable use-on-block #" + uses
                        + " via " + hand + " - " + change.propertyName() + ": "
                        + change.previousValue() + " -> " + change.value() + " - " + durability);
                event.handle();
            });
        });
    }

    private static int incrementUses(InteractionEvent event) {
        var data = event.itemData().orElseThrow(() ->
                new IllegalStateException("Portable tool did not receive its declared stack data"));
        int updated = Math.min(1_000_000, data.getOrDefault(USE_COUNT, 0) + 1);
        data.set(USE_COUNT, updated);
        return updated;
    }
}
