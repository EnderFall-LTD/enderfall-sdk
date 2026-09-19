package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class WorkbenchScreenEmitterTest {
    @Test void emitsEveryReviewedTargetDeterministically() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            var first = WorkbenchScreenEmitter.emitIfPresent(target, paths(id));
            assertEquals(1, first.size());
            assertArrayEquals(first.get(0).content(), WorkbenchScreenEmitter.emitIfPresent(target, paths(id)).get(0).content());
        }
        assertEquals(text("1.20.1-forge"), text("1.20.1-neoforge"));
    }

    @Test void rejectsScreenWithoutMenuAndAllowsAbsentScreen() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            assertTrue(WorkbenchScreenEmitter.emitIfPresent(target, Set.of()).isEmpty());
            assertTrue(WorkbenchScreenEmitter.emitIfPresent(target, Set.of(root(id) + "WorkbenchMenu.java")).isEmpty());
            assertThrows(BridgeGenerationException.class, () -> WorkbenchScreenEmitter.emitIfPresent(target,
                    Set.of(root(id) + "WorkbenchScreen.java")));
        }
    }

    @Test void selectsImmediateOrExtractedRenderingWithoutMixingApis() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            boolean extracted = id.startsWith("26.2");
            assertEquals(extracted, source.contains("GuiGraphicsExtractor"));
            assertEquals(extracted, source.contains("super.extractRenderState(graphics, mouseX, mouseY, partialTick);"));
            assertEquals(!extracted, source.contains("renderTooltip(graphics, mouseX, mouseY);"));
            assertEquals(extracted, source.contains("graphics.text(font, title"));
            assertEquals(!extracted, source.contains("graphics.drawString(font, title"));
            if (!extracted) assertTrue(source.contains(id.startsWith("1.20.1")
                    ? "renderBackground(graphics);" : "renderBackground(graphics, mouseX, mouseY, partialTick);"));
        }
    }

    @Test void retainsGeometryColorsLabelsAndForgeBlendHook() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            assertTrue(source.contains("inventoryLabelY = 72;"));
            assertTrue(source.contains("menu.definition().spec().backgroundColor()"));
            assertTrue(source.contains("for (Slot slot : menu.slots)"));
            assertTrue(source.contains("0xFF09070D")); assertTrue(source.contains("0xFF493C5A"));
            assertTrue(source.contains("0xFFB786FF"));
            assertEquals(id.equals("1.20.1-forge") || id.equals("1.20.1-neoforge"), source.contains("RenderSystem.enableBlend();"));
            assertTrue(source.contains(id.startsWith("26.2") ? "super(menu, inventory, title, 176, 166);" : "imageWidth = 176;"));
        }
    }

    @Test void emitsNativeBrowserSelectionPagingAndIngredientCounts() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            boolean extracted = id.startsWith("26.2");
            assertTrue(source.contains("menu.recipeChoiceCount()"));
            assertTrue(source.contains("menu.requiredCount(slot)"));
            assertTrue(source.contains("menu.recipeCategoryName()"));
            assertTrue(source.contains("menu.recipeCount()"));
            assertTrue(source.contains("action = 102"));
            assertTrue(source.contains("action = 103"));
            assertTrue(source.contains("handleInventoryButtonClick(menu.containerId, action)"));
            assertTrue(source.contains("action = scroll > 0.0D ? 100 : 101"));
            assertTrue(source.contains(extracted
                    ? "mouseClicked(MouseButtonEvent event, boolean doubleClick)"
                    : "mouseClicked(double mouseX, double mouseY, int button)"));
        }
    }

    private static String root(String id) {
        boolean fabric = id.endsWith("-fabric");
        return "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/" + (fabric ? "Fabric" : "NeoForge");
    }
    private static Set<String> paths(String id) { return Set.of(root(id) + "WorkbenchScreen.java", root(id) + "WorkbenchMenu.java"); }
    private static String text(String id) throws BridgeGenerationException {
        return new String(WorkbenchScreenEmitter.emitIfPresent(TargetCatalog.standard().require(id), paths(id)).get(0).content(), StandardCharsets.UTF_8);
    }
}
