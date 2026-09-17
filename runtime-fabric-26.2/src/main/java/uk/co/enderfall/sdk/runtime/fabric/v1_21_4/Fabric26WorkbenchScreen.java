package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Minecraft 26.2 extracted-render-state screen for the portable workbench. */
final class FabricWorkbenchScreen extends AbstractContainerScreen<FabricWorkbenchMenu> {
    FabricWorkbenchScreen(FabricWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = 72;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB0100D18);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight,
                menu.definition().spec().backgroundColor());
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF2B2238);
        for (Slot slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                    leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF09070D);
            graphics.fill(leftPos + slot.x, topPos + slot.y,
                    leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF493C5A);
        }
        graphics.fill(leftPos + 105, topPos + 41, leftPos + 118, topPos + 44, 0xFFB786FF);
        graphics.fill(leftPos + 115, topPos + 37, leftPos + 121, topPos + 48, 0xFFB786FF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFFEADFFF, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFD8CCE8, false);
    }
}
