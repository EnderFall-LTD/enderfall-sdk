package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortableItemSourcesTest {
    @Test
    void legacyUsesLegacyTooltipSignatureAndScreenModifierState() {
        String source = PortableItemSources.emit("1.20.1-fabric");
        assertTrue(source.contains("net.minecraft.world.level.Level level"));
        assertTrue(source.contains("Screen.hasShiftDown()"));
        assertTrue(source.contains("case SHIFT_DOWN -> shiftDown"));
        assertTrue(source.contains("stack.getOrCreateTagElement(\"enderfall_sdk\")"));
    }

    @Test
    void componentEraUsesTooltipContext() {
        String source = PortableItemSources.emit("1.21.4-neoforge");
        assertTrue(source.contains("Item.TooltipContext context"));
        assertTrue(source.contains("java.util.List<Component> lines"));
        assertTrue(source.contains("DataComponents.CUSTOM_DATA"));
        assertTrue(source.contains("CustomData.update"));
    }

    @Test
    void unobfuscatedTargetUsesConsumerTooltipApiAndMinecraftModifierState() {
        String source = PortableItemSources.emit("26.2-fabric");
        assertTrue(source.contains("TooltipDisplay display"));
        assertTrue(source.contains("java.util.function.Consumer<Component> lines"));
        assertTrue(source.contains("Minecraft.getInstance().hasShiftDown()"));
        assertTrue(source.contains("getCompoundOrEmpty(\"enderfall_sdk\")"));
        assertTrue(source.contains("Item data can only be changed on the server"));
    }
}
