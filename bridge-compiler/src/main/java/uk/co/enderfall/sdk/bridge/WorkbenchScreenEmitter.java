package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MenuAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared workbench geometry and labels with explicit immediate/extracted rendering policies. */
final class WorkbenchScreenEmitter {
    private WorkbenchScreenEmitter() { }

    private record Plan(boolean fabric, MenuAbi abi) {
        boolean legacy() { return abi == MenuAbi.V1_20_1; }
        boolean extracted() { return abi == MenuAbi.V26_2; }
        boolean localCoordinates() { return fabric && legacy(); }
        boolean separateAnnotation() { return legacy() || (!fabric && extracted()); }
        boolean gap() { return !fabric || legacy(); }
        String canonicalRoot() { return "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/"; }
        String root() { return legacy() && !fabric ? "uk/co/enderfall/sdk/runtime/forge/v1_20_1/" : canonicalRoot(); }
        String prefix() { return fabric ? legacy() ? "Fabric1201" : "Fabric" : legacy() ? "LegacyForge" : extracted() ? "NeoForge26" : "NeoForge"; }
        String graphics() { return extracted() ? "GuiGraphicsExtractor" : "GuiGraphics"; }
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed screen target " + target.id());
        Plan p = new Plan(target.loaderAbi() == LoaderAbi.FABRIC, target.menuAbi());
        String canonical = p.canonicalRoot() + (p.fabric() ? "Fabric" : "NeoForge");
        String screen = canonical + "WorkbenchScreen.java";
        if (!paths.contains(screen)) return List.of();
        if (!paths.contains(canonical + "WorkbenchMenu.java")) throw new BridgeGenerationException(target.id() + " requires a workbench menu for its screen");
        String filename = p.fabric() && p.extracted() ? "Fabric26WorkbenchScreen.java" : p.prefix() + "WorkbenchScreen.java";
        return List.of(new RuntimeSource(screen, p.root() + filename, render(p).getBytes(StandardCharsets.UTF_8)));
    }

    private static String render(Plan p) {
        Code c = new Code(p);
        c.line(0, "package " + p.root().substring(0, p.root().length() - 1).replace('/', '.') + ";"); c.blank();
        if (p.legacy() && !p.fabric()) c.line(0, "import com.mojang.blaze3d.systems.RenderSystem;");
        c.line(0, "import net.minecraft.client.gui." + p.graphics() + ";");
        if (p.extracted()) c.line(0, "import net.minecraft.client.input.MouseButtonEvent;");
        c.line(0, "import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;");
        c.line(0, "import net.minecraft.network.chat.Component;");
        c.line(0, "import net.minecraft.world.entity.player.Inventory;"); c.line(0, "import net.minecraft.world.inventory.Slot;"); c.blank();
        String description = p.extracted() ? "Minecraft 26.2 extracted-render-state screen for the portable workbench" + (p.fabric() ? "." : " container.")
                : p.legacy() ? p.fabric() ? "Texture-free custom workbench screen; vanilla handles synchronized slot interaction."
                : "Texture-free screen for the real portable workbench menu."
                : "Texture-free screen for the real " + (p.fabric() ? "Fabric" : "portable") + " workbench.";
        c.line(0, "/** " + description + " */");
        c.line(0, "final class " + p.prefix() + "WorkbenchScreen extends AbstractContainerScreen<" + p.prefix() + "WorkbenchMenu> {");
        c.line(4, p.prefix() + "WorkbenchScreen(" + p.prefix() + "WorkbenchMenu menu, Inventory inventory, Component title) {");
        c.line(8, "super(menu, inventory, title" + (p.extracted() ? ", 176, 166" : "") + ");");
        if (!p.extracted()) { c.line(8, "imageWidth = 176;"); c.line(8, "imageHeight = 166;"); }
        c.line(8, "inventoryLabelY = 72;"); c.line(4, "}");
        if (p.localCoordinates()) renderEntry(c, p);
        c.gap();
        c.method(p.extracted() ? "public void extractRenderState(" + p.graphics() + " graphics, int mouseX, int mouseY, float partialTick) {"
                : "protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {");
        if (p.extracted()) c.line(8, "graphics.fill(0, 0, width, height, 0xB0100D18);");
        if (p.legacy() && !p.fabric()) c.line(8, "RenderSystem.enableBlend();");
        if (p.localCoordinates()) { c.line(8, "int left = leftPos;"); c.line(8, "int top = topPos;"); }
        boolean colorLocal = p.localCoordinates() || (!p.fabric() && p.extracted());
        String left = p.localCoordinates() ? "left" : "leftPos", top = p.localCoordinates() ? "top" : "topPos";
        if (colorLocal) c.line(8, "int color = menu.definition().spec().backgroundColor();");
        c.line(8, "graphics.fill(" + left + ", " + top + ", " + left + " + imageWidth, " + top + " + imageHeight," + (colorLocal ? " color);" : ""));
        if (!colorLocal) c.line(16, "menu.definition().spec().backgroundColor());");
        c.line(8, "graphics.fill(" + left + " + 4, " + top + " + 4, " + left + " + imageWidth - 4, " + top + " + imageHeight - 4, 0xFF2B2238);");
        c.line(8, "for (Slot slot : menu.slots) {");
        slotFill(c, p, left, top, " - 1", 17, "0xFF09070D");
        slotFill(c, p, left, top, "", 16, "0xFF493C5A");
        c.line(8, "}");
        c.line(8, "if (menu.recipeChoiceCount() > 0) {");
        c.line(12, "int selectedRow = menu.selectedRecipeIndex() - menu.recipePage() * menu.recipeChoiceCount();");
        c.line(12, "if (selectedRow >= 0 && selectedRow < menu.recipeChoiceCount()) {");
        c.line(16, "Slot selected = menu.slots.get(menu.recipeChoiceStart() + selectedRow);");
        c.line(16, "graphics.fill(" + left + " + selected.x - 2, " + top + " + selected.y - 2,");
        c.line(24, left + " + selected.x + 18, " + top + " + selected.y, 0xFFB786FF);");
        c.line(16, "graphics.fill(" + left + " + selected.x - 2, " + top + " + selected.y + 16,");
        c.line(24, left + " + selected.x + 18, " + top + " + selected.y + 18, 0xFFB786FF);");
        c.line(12, "}");
        c.line(8, "}");
        c.line(8, "graphics.fill(" + left + " + 105, " + top + " + 41, " + left + " + 118, " + top + " + 44, 0xFFB786FF);");
        c.line(8, "graphics.fill(" + left + " + 115, " + top + " + 37, " + left + " + 121, " + top + " + 48, 0xFFB786FF);");
        if (p.extracted()) c.line(8, "super.extractRenderState(graphics, mouseX, mouseY, partialTick);");
        c.line(4, "}"); c.gap();
        c.method("protected void " + (p.extracted() ? "extractLabels" : "renderLabels") + "(" + p.graphics() + " graphics, int mouseX, int mouseY) {");
        String text = p.extracted() ? "text" : "drawString";
        c.line(8, "graphics." + text + "(font, title, titleLabelX, titleLabelY, 0xFFEADFFF, false);");
        c.line(8, "graphics." + text + "(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFD8CCE8, false);");
        c.line(8, "if (menu.recipeChoiceCount() > 0) {");
        c.line(12, "String category = menu.recipeCategoryName() + \"  \" + (menu.selectedRecipeIndex() + 1) + \"/\" + menu.recipeCount();");
        c.line(12, "graphics." + text + "(font, Component.literal(\"<\"), 10, 19, 0xFFEADFFF, false);");
        c.line(12, "graphics." + text + "(font, Component.literal(category), (imageWidth - font.width(category)) / 2, 19, 0xFFD8CCE8, false);");
        c.line(12, "graphics." + text + "(font, Component.literal(\">\"), 160, 19, 0xFFEADFFF, false);");
        c.line(12, "graphics." + text + "(font, Component.literal(\"<\"), 30, 63, menu.recipePage() > 0 ? 0xFFEADFFF : 0xFF665E70, false);");
        c.line(12, "graphics." + text + "(font, Component.literal(\">\"), 142, 63, menu.recipePage() + 1 < menu.recipePages() ? 0xFFEADFFF : 0xFF665E70, false);");
        c.line(8, "}");
        c.line(8, "for (int slot = 0; slot < menu.definition().spec().recipeType().inputSlots(); slot++) {");
        c.line(12, "int required = menu.requiredCount(slot);");
        c.line(12, "if (required > 1) { Slot input = menu.slots.get(slot);");
        c.line(16, "graphics." + text + "(font, Component.literal(\"x\" + required), input.x + 8, input.y - 9, 0xFFD8CCE8, false);");
        c.line(12, "}");
        c.line(8, "}");
        c.line(4, "}");
        if (!p.extracted() && !p.localCoordinates()) renderEntry(c, p);
        browserInput(c, p);
        c.line(0, "}"); return c.out.toString();
    }

    private static void slotFill(Code c, Plan p, String left, String top, String offset, int size, String color) {
        String start = "graphics.fill(" + left + " + slot.x" + offset + ", " + top + " + slot.y" + offset + ",";
        String end = left + " + slot.x + " + size + ", " + top + " + slot.y + " + size + ", " + color + ");";
        if (p.localCoordinates()) c.line(12, start + " " + end);
        else { c.line(12, start); c.line(20, end); }
    }

    private static void renderEntry(Code c, Plan p) {
        c.gap(); c.method("public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {");
        c.line(8, "renderBackground(graphics" + (p.legacy() ? "" : ", mouseX, mouseY, partialTick") + ");");
        c.line(8, "super.render(graphics, mouseX, mouseY, partialTick);"); c.line(8, "renderTooltip(graphics, mouseX, mouseY);"); c.line(4, "}");
    }

    private static void browserInput(Code c, Plan p) {
        c.gap();
        if (p.extracted()) {
            c.method("public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {");
            c.line(8, "if (event.button() == 0 && submitRecipeBrowser(event.x(), event.y(), 0.0D)) return true;");
            c.line(8, "return super.mouseClicked(event, doubleClick);");
        } else {
            c.method("public boolean mouseClicked(double mouseX, double mouseY, int button) {");
            c.line(8, "if (button == 0 && submitRecipeBrowser(mouseX, mouseY, 0.0D)) return true;");
            c.line(8, "return super.mouseClicked(mouseX, mouseY, button);");
        }
        c.line(4, "}");
        c.gap();
        if (p.legacy()) c.method("public boolean mouseScrolled(double mouseX, double mouseY, double amount) {");
        else c.method("public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {");
        if (!p.legacy()) c.line(8, "double amount = verticalAmount;");
        c.line(8, "if (submitRecipeBrowser(mouseX, mouseY, amount)) return true;");
        c.line(8, "return super.mouseScrolled(mouseX, mouseY, " + (p.legacy() ? "amount" : "horizontalAmount, verticalAmount") + ");");
        c.line(4, "}");
        c.gap(); c.line(4, "private boolean submitRecipeBrowser(double mouseX, double mouseY, double scroll) {");
        c.line(8, "int visible = menu.recipeChoiceCount();");
        c.line(8, "if (visible == 0 || minecraft == null || minecraft.gameMode == null) return false;");
        c.line(8, "double x = mouseX - leftPos; double y = mouseY - topPos;");
        c.line(8, "int action = -1;");
        c.line(8, "if (scroll != 0.0D && x >= 44 && x < 44 + visible * 18 && y >= 57 && y < 77) {");
        c.line(12, "action = scroll > 0.0D ? 100 : 101;");
        c.line(8, "} else if (scroll == 0.0D && y >= 15 && y < 31) {");
        c.line(12, "if (x >= 6 && x < 25) action = 102;");
        c.line(12, "else if (x >= 151 && x < 170) action = 103;");
        c.line(8, "} else if (scroll == 0.0D && y >= 57 && y < 77) {");
        c.line(12, "if (x >= 27 && x < 41) action = 100;");
        c.line(12, "else if (x >= 139 && x < 153) action = 101;");
        c.line(12, "else if (x >= 44 && x < 44 + visible * 18) action = (int) ((x - 44) / 18);");
        c.line(8, "}");
        c.line(8, "if (action < 0) return false;");
        c.line(8, "minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);");
        c.line(8, "return true;"); c.line(4, "}");
    }

    private static final class Code {
        private final Plan plan;
        private final StringBuilder out = new StringBuilder();
        Code(Plan plan) { this.plan = plan; }
        void line(int indent, String text) { out.append(" ".repeat(indent)).append(text).append('\n'); }
        void blank() { out.append('\n'); }
        void gap() { if (plan.gap()) blank(); }
        void method(String declaration) {
            if (plan.separateAnnotation()) line(4, "@Override");
            line(4, (plan.separateAnnotation() ? "" : "@Override ") + declaration);
        }
    }
}
