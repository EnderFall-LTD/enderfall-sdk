package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.registry.ItemRef;
import uk.co.enderfall.sdk.api.registry.Registration;

public final class PreviewItems {
    private PreviewItems() { }

    private static final Registration.Items ITEMS = Registration.items("enderfall_persistent_preview");
    public static final ItemRef PORTABLE_TOOL = ITEMS.item("portable_tool", PreviewToolItem::new);
    public static final ItemRef PORTABLE_LETTER = ITEMS.item("portable_letter", PreviewLetterItem::new);

    public static void register(ModContext context) {
        Registration.register(context, ITEMS);
    }
}
