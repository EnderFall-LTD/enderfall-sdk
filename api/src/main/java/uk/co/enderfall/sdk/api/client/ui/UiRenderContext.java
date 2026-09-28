package uk.co.enderfall.sdk.api.client.ui;

import java.util.List;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.item.ItemStackRef;
import uk.co.enderfall.sdk.api.entity.EntityTypeRef;

/**
 * Version-neutral immediate drawing backend for portable client screens.
 *
 * <p>Every push must be paired with its corresponding pop in the same callback. Native
 * bridges reset all stacks after a callback, including when consumer rendering fails.</p>
 */
@Experimental("General portable client rendering")
public interface UiRenderContext {
    UiRenderFrame frame();

    void fill(UiRect bounds, int argb);

    void sprite(ResourceId texture, UiRect destination, int textureWidth, int textureHeight,
                int sourceX, int sourceY, int sourceWidth, int sourceHeight, int argb);

    void nineSlice(ResourceId texture, UiRect destination, int textureWidth, int textureHeight,
                   UiInsets border, int argb);

    void tile(ResourceId texture, UiRect destination, int textureWidth, int textureHeight,
              int tileWidth, int tileHeight, int sourceX, int sourceY, int argb);

    UiTextMetrics measureText(String text);

    List<String> wrapText(String text, int maximumWidth);

    void text(String text, float anchorX, float y, int argb, boolean shadow, UiTextAlign alignment);

    /** Draws a native item icon. Decorations include the count and durability bar. */
    void item(ItemStackRef stack, int x, int y, boolean decorations);

    default void item(ItemStackRef stack, int x, int y) { item(stack, x, y, true); }

    /** Draws portable tooltip lines above ordinary screen content. */
    void tooltip(List<String> lines, int x, int y);

    default void tooltip(String line, int x, int y) { tooltip(List.of(line), x, y); }

    /** Draws the target-native display name for a portable stack. */
    void itemTooltip(ItemStackRef stack, int x, int y);

    /**
     * Draws a living entity within the supplied rectangle. Pointer coordinates control
     * the familiar inventory-screen look direction and are expressed in GUI pixels.
     */
    void livingEntity(EntityTypeRef type, UiRect bounds, float pointerX, float pointerY);

    void pushClip(UiRect bounds);

    void popClip();

    void pushPose();

    void popPose();

    void translate(float x, float y, float depth);

    void scale(float x, float y);

    void rotate(float degrees);

    void pushOpacity(float opacity);

    void popOpacity();

    void pushDepth(int depth);

    void popDepth();
}
