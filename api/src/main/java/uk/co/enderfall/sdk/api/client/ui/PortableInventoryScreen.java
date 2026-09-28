package uk.co.enderfall.sdk.api.client.ui;

import uk.co.enderfall.sdk.api.annotation.Experimental;

/**
 * Portable presentation and input callbacks for a synchronized authored inventory.
 * Native slots, carried stacks and quick-move behavior remain server authoritative.
 */
@Experimental("Authored portable inventory screens")
public interface PortableInventoryScreen {
    default void initialize(InventoryScreenContext context) { }

    void renderBackground(UiRenderContext graphics, InventoryScreenContext context);

    default void renderForeground(UiRenderContext graphics, InventoryScreenContext context) { }

    default void tick(InventoryScreenContext context) { }

    default void resized(InventoryScreenContext context) { }

    default void removed() { }

    default boolean mouseClicked(double x, double y, int button) { return false; }

    default boolean mouseReleased(double x, double y, int button) { return false; }

    default boolean mouseDragged(double x, double y, int button, double dragX, double dragY) { return false; }

    default boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return false; }

    default boolean keyPressed(int key, int scanCode, int modifiers) { return false; }

    default boolean keyReleased(int key, int scanCode, int modifiers) { return false; }

    default boolean characterTyped(int codePoint, int modifiers) { return false; }
}
