package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.item.PortableItem;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.api.registry.Rarity;

/** Class-shaped portable item proving configuration and loader-neutral use routing. */
public final class PreviewToolItem implements PortableItem {
    @Override public void configure(ItemSpec.Builder properties) {
        properties.maxStackSize(1).durability(128).rarity(Rarity.UNCOMMON);
    }

    @Override public void onUse(ModContext context, InteractionEvent event) {
        context.players().actionBar(event.playerId(), "Portable tool callback on "
                + context.platform().minecraftVersion() + " " + context.platform().loader());
        event.handle();
    }

    @Override public void onUseOnBlock(ModContext context, InteractionEvent event) {
        if (!event.target().equals(PreviewBlocks.ORIENTATION_TEST.id())) return;
        event.blockLocation().ifPresent(location -> {
            if (context.blockStates().update(PreviewBlocks.ORIENTATION_TEST, location,
                    state -> state.with(PreviewOrientationBlock.COMPACT,
                            !state.get(PreviewOrientationBlock.COMPACT)))) {
                String hand = event.hand().map(value -> value.name().toLowerCase()).orElse("unknown hand");
                context.players().actionBar(event.playerId(), "Portable use-on-block via " + hand
                        + (event.sneaking() ? " while crouching" : ""));
                event.handle();
            }
        });
    }
}
