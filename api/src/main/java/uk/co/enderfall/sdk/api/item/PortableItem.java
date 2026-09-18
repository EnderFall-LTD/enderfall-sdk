package uk.co.enderfall.sdk.api.item;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

/**
 * Reusable loader-neutral item definition. One instance is created per registered item
 * ID, not per stack. Per-stack state belongs in declared portable item data, not fields
 * on this object.
 *
 * <p>This is not a native Minecraft {@code Item} subclass. Generated runtimes connect
 * its callbacks to the appropriate loader and Minecraft hooks.</p>
 */
@Experimental("Custom item definitions and behavior")
public interface PortableItem {
    /** Runs once during registration preflight, before declaration-level overrides. */
    void configure(ItemSpec.Builder properties);

    /**
     * Server-side right-click callback for this item. Call {@link InteractionEvent#handle()}
     * or {@link InteractionEvent#cancel()} explicitly; doing nothing preserves native and
     * other-mod fallback behavior.
     *
     * <p>The initial contract does not expose a hand or mutable stack. Use-on-block,
     * tooltips and durable stack data are separate portable contracts.</p>
     */
    default void onUse(ModContext context, InteractionEvent event) { }
}
