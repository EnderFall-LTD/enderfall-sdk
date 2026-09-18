package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.item.ItemDataKey;
import uk.co.enderfall.sdk.api.item.PortableItem;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

/** Furniture-style portable item whose author and text survive normal stack serialization. */
public final class PreviewLetterItem implements PortableItem {
    public static final ItemDataKey<String> AUTHOR = ItemDataKey.string(
            ResourceId.parse("enderfall_persistent_preview:letter_author"), 64);
    public static final ItemDataKey<String> TEXT = ItemDataKey.string(
            ResourceId.parse("enderfall_persistent_preview:letter_text"), 1_152);

    @Override public void configure(ItemSpec.Builder properties) {
        properties.maxStackSize(1)
                .data(AUTHOR)
                .data(TEXT)
                .tooltip("tooltip.enderfall_persistent_preview.portable_letter.summary")
                .shiftHint("tooltip.enderfall_persistent_preview.hold_shift")
                .shiftTooltip("tooltip.enderfall_persistent_preview.portable_letter.details");
    }

    @Override public void onUse(ModContext context, InteractionEvent event) {
        if (event.side() != InteractionEvent.Side.SERVER) return;
        var data = event.itemData().orElseThrow(() ->
                new IllegalStateException("Portable letter did not receive its declared stack data"));
        String author = data.get(AUTHOR).orElse("Unsigned");
        String text = data.get(TEXT).orElse("Empty");
        context.players().actionBar(event.playerId(), author + ": " + text);
        event.handle();
    }
}
