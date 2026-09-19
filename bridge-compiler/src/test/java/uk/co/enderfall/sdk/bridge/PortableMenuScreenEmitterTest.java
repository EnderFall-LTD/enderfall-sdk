package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class PortableMenuScreenEmitterTest {
    @Test void emitsAllTargetsDeterministicallyAndOnlyWhenDeclared() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            assertTrue(PortableMenuScreenEmitter.emitIfPresent(target, Set.of()).isEmpty());
            var first = PortableMenuScreenEmitter.emitIfPresent(target, paths(id));
            assertEquals(1, first.size());
            assertArrayEquals(first.get(0).content(), PortableMenuScreenEmitter.emitIfPresent(target, paths(id)).get(0).content());
            assertTrue(first.get(0).relativePath().endsWith(id.startsWith("26.2") ? "26PortableMenuScreen.java" : "PortableMenuScreen.java"));
        }
        assertEquals(text("1.20.1-forge"), text("1.20.1-neoforge"));
    }

    @Test void selectsRenderingAndScreenAccessTogether() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            boolean extracted = id.startsWith("26.2");
            assertEquals(extracted, source.contains("GuiGraphicsExtractor"));
            assertEquals(extracted, source.contains("super.extractRenderState("));
            assertEquals(extracted, source.contains("graphics.centeredText("));
            assertEquals(extracted, source.contains("graphics.text("));
            assertEquals(!extracted, source.contains("super.render("));
            assertEquals(!extracted, source.contains("graphics.drawString("));
            assertEquals(extracted, source.contains("client.gui.setScreen("));
            assertEquals(extracted, source.contains("client.gui.screen() instanceof"));
        }
    }

    @Test void retainsMainThreadDispatchAndSessionFiltering() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            assertEquals(3, source.split("client.execute", -1).length - 1);
            assertEquals(2, source.split("screen.sessionId\\(\\) == sessionId", -1).length - 1);
            assertTrue(source.contains("screen.update(state);"));
            assertTrue(source.contains("screen.closeFromServer();"));
        }
    }

    @Test void preventsDuplicateCloseAndServerCloseEcho() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            assertEquals(2, source.split("notifyClosed\\(\\);", -1).length - 1);
            assertTrue(source.contains("remoteClose = true;\n        onClose();"));
            assertTrue(source.contains("if (!remoteClose && !closeNotified)"));
            assertTrue(source.contains("closeNotified = true;\n            closeSender.run();"));
        }
    }

    @Test void retainsButtonBindingsStateResolutionAndGeometry() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            assertTrue(source.contains("buttonBindings.clear();"));
            assertTrue(source.contains("ignored -> actionSender.accept(submission(definition.action()))"));
            assertTrue(source.contains("for (MenuTextInput definition : view.spec().textInputs())"));
            assertTrue(source.contains("return new PortableMenuSubmission(action, values);"));
            assertTrue(source.contains(".bounds(left + definition.x(), top + definition.y(), definition.width(), definition.height())"));
            assertTrue(source.contains("view = new PortableMenuView(view.sessionId(), view.menu(), view.spec(), state);"));
            assertTrue(source.contains("view.spec().buttonText(binding.definition(), state)"));
            assertTrue(source.contains("button.active = view.spec().actionEnabled(definition.action(), view.state());"));
            assertTrue(source.contains("binding.button().active = view.spec().actionEnabled(binding.definition().action(), state);"));
            assertTrue(source.contains("for (MenuLabel label : view.spec().labels())"));
            assertTrue(source.contains("public boolean isPauseScreen() {\n        return false;"));
            assertTrue(source.contains("view.spec().scrollAction(mouseX - left, mouseY - top,"));
            assertTrue(source.contains(id.startsWith("1.20.1")
                    ? "mouseScrolled(double mouseX, double mouseY, double amount)"
                    : "double horizontalAmount, double verticalAmount)"));
        }
    }

    @Test void emitsTargetNativeSelectionIconsCountsAndTooltips() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            boolean extracted = id.startsWith("26.2");
            boolean legacy = id.startsWith("1.20.1");
            assertTrue(source.contains("view.spec().selectionVisuals(view.state())"));
            assertTrue(source.contains("new net.minecraft.world.item.ItemStack("));
            assertTrue(source.contains(extracted ? "graphics.item(stack, iconX, iconY);"
                    : "graphics.renderItem(stack, iconX, iconY);"));
            assertTrue(source.contains(extracted ? "graphics.itemDecorations(font, stack, iconX, iconY);"
                    : "graphics.renderItemDecorations(font, stack, iconX, iconY);"));
            assertTrue(source.contains(extracted ? "graphics.setTooltipForNextFrame(font,"
                    : "graphics.renderTooltip(font,"));
            assertTrue(source.contains(extracted ? "net.minecraft.resources.Identifier.parse("
                    : legacy ? "net.minecraft.resources.ResourceLocation.tryParse("
                    : "net.minecraft.resources.ResourceLocation.parse("));
            assertTrue(source.contains(id.endsWith("-forge") || id.equals("1.20.1-neoforge")
                    ? "ForgeRegistries.ITEMS.getValue(id)"
                    : id.startsWith("1.21.4") || extracted
                            ? "BuiltInRegistries.ITEM.getValue(id)"
                            : "BuiltInRegistries.ITEM.get(id)"));
        }
    }

    @Test void rejectsUnreviewedTargetsBeforeEmission() {
        TargetSpec base = TargetCatalog.standard().require("26.2-fabric");
        TargetSpec changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> PortableMenuScreenEmitter.emitIfPresent(changed, paths(base.id())));
    }

    private static Set<String> paths(String id) {
        boolean fabric = id.endsWith("-fabric");
        return Set.of("uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge")
                + "/v1_21_4/" + (fabric ? "Fabric" : "NeoForge") + "PortableMenuScreen.java");
    }
    private static String text(String id) throws BridgeGenerationException {
        return new String(PortableMenuScreenEmitter.emitIfPresent(TargetCatalog.standard().require(id), paths(id)).get(0).content(), StandardCharsets.UTF_8);
    }
}
