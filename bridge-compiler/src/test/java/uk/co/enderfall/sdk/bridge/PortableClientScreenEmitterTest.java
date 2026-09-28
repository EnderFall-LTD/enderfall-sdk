package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class PortableClientScreenEmitterTest {
    @Test void emitsReviewedImmediateAndExtractionPoliciesForEveryTarget() throws Exception {
        for (var target : TargetCatalog.standard().targets()) {
            boolean fabric = target.loader().id().equals("fabric");
            String canonical = "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge")
                    + "/v1_21_4/" + (fabric ? "Fabric" : "NeoForge") + "PlatformAdapter.java";
            var sources = PortableClientScreenEmitter.emitIfPresent(target, Set.of(canonical));
            assertEquals(1, sources.size(), target.id());
            String source = new String(sources.get(0).content(), StandardCharsets.UTF_8);
            assertFalse(source.contains("${"), target.id());
            assertTrue(source.contains("implements ClientScreenContext"), target.id());
            assertTrue(source.contains("implements UiRenderContext"), target.id());
            assertTrue(source.contains("void nineSlice("), target.id());
            assertTrue(source.contains("void tile("), target.id());
            assertTrue(source.contains("void item(ItemStackRef"), target.id());
            assertTrue(source.contains("void itemTooltip(ItemStackRef"), target.id());
            assertTrue(source.contains("Portable tooltip exceeds 64 lines"), target.id());
            assertFalse(source.contains("Tinted portable sprites are not implemented"), target.id());
            if (target.minecraftVersion().id().equals("26.2")) {
                assertTrue(source.contains("GuiGraphicsExtractor"), target.id());
                assertTrue(source.contains("extractRenderState"), target.id());
                assertTrue(source.contains("graphics.nextStratum()"), target.id());
                assertTrue(source.contains("textureHeight, color(argb)"), target.id());
            } else {
                assertTrue(source.contains("GuiGraphics"), target.id());
                assertTrue(source.contains("public void render("), target.id());
                assertTrue(source.contains(target.minecraftVersion().id().equals("1.21.4")
                        ? "textureHeight, color(argb)" : "RenderSystem.setShaderColor"), target.id());
            }
        }
    }

    @Test void doesNotAddClientBridgeWithoutAPlatformDeclaration() throws Exception {
        var target = TargetCatalog.standard().require("1.21.4-fabric");
        assertTrue(PortableClientScreenEmitter.emitIfPresent(target, Set.of()).isEmpty());
    }
}
