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
}
