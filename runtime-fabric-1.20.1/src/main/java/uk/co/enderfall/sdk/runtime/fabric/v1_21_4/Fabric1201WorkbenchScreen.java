package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Texture-free custom workbench screen; vanilla handles synchronized slot interaction. */
final class Fabric1201WorkbenchScreen extends AbstractContainerScreen<Fabric1201WorkbenchMenu> {
    Fabric1201WorkbenchScreen(Fabric1201WorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 72;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        int color = menu.definition().spec().backgroundColor();
        graphics.fill(left, top, left + imageWidth, top + imageHeight, color);
        graphics.fill(left + 4, top + 4, left + imageWidth - 4, top + imageHeight - 4, 0xFF2B2238);
        for (Slot slot : menu.slots) {
            graphics.fill(left + slot.x - 1, top + slot.y - 1, left + slot.x + 17, top + slot.y + 17, 0xFF09070D);
            graphics.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, 0xFF493C5A);
        }
        graphics.fill(left + 105, top + 41, left + 118, top + 44, 0xFFB786FF);
        graphics.fill(left + 115, top + 37, left + 121, top + 48, 0xFFB786FF);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFEADFFF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFD8CCE8, false);
    }
}
