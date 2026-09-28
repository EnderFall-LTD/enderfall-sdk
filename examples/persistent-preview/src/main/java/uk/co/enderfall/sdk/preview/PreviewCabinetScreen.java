package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.client.ui.InventoryScreenContext;
import uk.co.enderfall.sdk.api.client.ui.PortableInventoryScreen;
import uk.co.enderfall.sdk.api.client.ui.UiRect;
import uk.co.enderfall.sdk.api.client.ui.UiRenderContext;
import uk.co.enderfall.sdk.api.client.ui.UiTextAlign;

/** EnderUI-style authored presentation layered around native synchronized slots. */
public final class PreviewCabinetScreen implements PortableInventoryScreen {
    @Override
    public void renderBackground(UiRenderContext graphics, InventoryScreenContext screen) {
        int left = screen.left();
        int top = screen.top();
        int width = screen.menuWidth();
        int height = screen.menuHeight();
        graphics.fill(new UiRect(left, top, width, height), 0xF0120D1B);
        graphics.fill(new UiRect(left + 3, top + 3, width - 6, height - 6), 0xFF2C213B);
        graphics.fill(new UiRect(left + 6, top + 23, width - 12, 62), 0xFF181220);
        graphics.fill(new UiRect(left + 6, top + 96, width - 12, 81), 0xFF21182D);
    }

    @Override
    public void renderForeground(UiRenderContext graphics, InventoryScreenContext screen) {
        graphics.text(screen.title(), screen.left() + screen.menuWidth() / 2f, screen.top() + 9,
                0xFFFFFFFF, true, UiTextAlign.CENTER);
        graphics.text("Portable authored inventory", screen.left() + screen.menuWidth() / 2f,
                screen.top() + screen.menuHeight() - 7, 0xFFB998FF, false, UiTextAlign.CENTER);
    }
}
