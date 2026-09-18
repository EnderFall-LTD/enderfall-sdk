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
     * <p>The hand is present for native events. Declare static portable tooltips through
     * {@link ItemSpec.Builder#tooltip(TooltipLine)}. Declared mutable held-stack data is
     * available through {@link InteractionEvent#itemData()} on native events.</p>
     */
    default void onUse(ModContext context, InteractionEvent event) { }

    /**
     * Lightweight client prediction for {@link #onUse(ModContext, InteractionEvent)}.
     * It must be deterministic from the event alone and must not mutate game state.
     * Return {@link InteractionPrediction#HANDLE} when the server callback is expected
     * to handle the direct use, preventing duplicate vanilla actions.
     */
    default InteractionPrediction predictUse(InteractionEvent event) {
        return InteractionPrediction.PASS;
    }

    /**
     * Server-side callback when this item is used on a block. Native hooks supply the
     * held item, hand, crouching state and exact block location. Handle or cancel the
     * event explicitly; otherwise the block callback and native fallback may continue.
     */
    default void onUseOnBlock(ModContext context, InteractionEvent event) { }

    /**
     * Lightweight client prediction for {@link #onUseOnBlock(ModContext, InteractionEvent)}.
     * Tools that authoritatively handle a known block should return
     * {@link InteractionPrediction#HANDLE}; conditional tools can inspect the target,
     * hand and crouching state without invoking server-only services.
     */
    default InteractionPrediction predictUseOnBlock(InteractionEvent event) {
        return InteractionPrediction.PASS;
    }
}
