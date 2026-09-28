package uk.co.enderfall.sdk.api.client.ui;

import java.util.List;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.Experimental;

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
