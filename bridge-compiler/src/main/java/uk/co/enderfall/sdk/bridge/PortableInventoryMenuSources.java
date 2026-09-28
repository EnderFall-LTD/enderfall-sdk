package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Generates the block-owned authored inventory menu and its target-native screen. */
final class PortableInventoryMenuSources {
    private PortableInventoryMenuSources() { }

    static List<RuntimeSource> emit(BlockEntityNativePolicy policy) {
        String root = "uk/co/enderfall/sdk/runtime/" + policy.runtimePackage().replace('.', '/') + "/";
        return List.of(
                source(root + policy.prefix() + "InventoryMenu.java", menu(policy)),
                source(root + policy.prefix() + "InventoryScreen.java", screen(policy)),
                source(root + policy.prefix() + "InventoryClient.java", client(policy)));
    }

    private static RuntimeSource source(String path, String content) {
        return new RuntimeSource(path, path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String menu(BlockEntityNativePolicy policy) {
        String itemId = policy.legacy() && !policy.fabric()
                ? "net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())"
                : "net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())";
        return """
                package uk.co.enderfall.sdk.runtime.${PACKAGE};

                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;
                import java.util.Objects;
                import java.util.function.Supplier;
                import net.minecraft.world.Container;
                import net.minecraft.world.SimpleContainer;
                import net.minecraft.world.entity.player.Inventory;
                import net.minecraft.world.entity.player.Player;
                import net.minecraft.world.inventory.AbstractContainerMenu;
                import net.minecraft.world.inventory.MenuType;
                import net.minecraft.world.inventory.Slot;
                import net.minecraft.world.item.ItemStack;
                import uk.co.enderfall.sdk.api.ui.InventoryQuickMoveRule;
                import uk.co.enderfall.sdk.api.ui.InventorySlotRole;
                import uk.co.enderfall.sdk.api.ui.InventorySlotSpec;
                import uk.co.enderfall.sdk.runtime.PortableStorageContainerDefinition;

                /** Vanilla-synchronized authored inventory layout with server-enforced slot rules. */
                final class ${PREFIX}InventoryMenu extends AbstractContainerMenu {
                    record Binding(PortableStorageContainerDefinition definition,
                                   Supplier<MenuType<${PREFIX}InventoryMenu>> menuType) {
                        Binding {
                            Objects.requireNonNull(definition, "definition");
                            Objects.requireNonNull(menuType, "menuType");
                        }
                    }

                    private final Binding binding;
                    private final Container owner;
                    private final Map<String, List<Integer>> groups = new LinkedHashMap<>();

                    ${PREFIX}InventoryMenu(int containerId, Inventory playerInventory,
                            Binding binding, Container owner) {
                        super(binding.menuType().get(), containerId);
                        this.binding = Objects.requireNonNull(binding, "binding");
                        var spec = binding.definition().spec();
                        this.owner = owner == null ? new SimpleContainer(spec.storage().inventorySlots()) : owner;
                        checkContainerSize(this.owner, spec.storage().inventorySlots());
                        this.owner.startOpen(playerInventory.player);

                        for (InventorySlotSpec slot : spec.slots()) {
                            addGrouped(slot.group(), addSlot(new PortableSlot(this.owner, slot)));
                        }
                        if (spec.playerInventory()) {
                            int x = spec.playerInventoryX();
                            int y = spec.playerInventoryY();
                            for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
                                addGrouped(InventoryQuickMoveRule.PLAYER_MAIN,
                                        addSlot(new Slot(playerInventory, column + row * 9 + 9,
                                                x + column * 18, y + row * 18)));
                            }
                            for (int column = 0; column < 9; column++) {
                                addGrouped(InventoryQuickMoveRule.PLAYER_HOTBAR,
                                        addSlot(new Slot(playerInventory, column, x + column * 18, y + 58)));
                            }
                        }
                    }

                    PortableStorageContainerDefinition definition() { return binding.definition(); }

                    private void addGrouped(String group, Slot slot) {
                        groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(slots.indexOf(slot));
                    }

                    @Override
                    public boolean stillValid(Player player) { return owner.stillValid(player); }

                    @Override
                    public void removed(Player player) {
                        super.removed(player);
                        owner.stopOpen(player);
                    }

                    @Override
                    public ItemStack quickMoveStack(Player player, int sourceIndex) {
                        if (sourceIndex < 0 || sourceIndex >= slots.size()) return ItemStack.EMPTY;
                        Slot source = slots.get(sourceIndex);
                        if (!source.hasItem()) return ItemStack.EMPTY;
                        ItemStack stack = source.getItem();
                        ItemStack original = stack.copy();
                        String sourceGroup = groupOf(sourceIndex);
                        if (sourceGroup == null) return ItemStack.EMPTY;
                        InventoryQuickMoveRule rule = binding.definition().spec().quickMoveRules().stream()
                                .filter(candidate -> candidate.sourceGroup().equals(sourceGroup)).findFirst().orElse(null);
                        if (rule == null || !moveToGroups(stack, rule.targetGroups())) return ItemStack.EMPTY;
                        if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
                        else source.setChanged();
                        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
                        source.onTake(player, stack);
                        return original;
                    }

                    private boolean moveToGroups(ItemStack stack, List<String> targetGroups) {
                        boolean changed = false;
                        for (String group : targetGroups) {
                            List<Integer> targets = groups.getOrDefault(group, List.of());
                            // Merge compatible stacks before opening new slots, while retaining authored order.
                            for (int index : targets) {
                                Slot target = slots.get(index);
                                if (!target.hasItem() || !target.mayPlace(stack)) continue;
                                int before = stack.getCount();
                                moveItemStackTo(stack, index, index + 1, false);
                                changed |= stack.getCount() != before;
                                if (stack.isEmpty()) return true;
                            }
                            for (int index : targets) {
                                Slot target = slots.get(index);
                                if (target.hasItem() || !target.mayPlace(stack)) continue;
                                int before = stack.getCount();
                                moveItemStackTo(stack, index, index + 1, false);
                                changed |= stack.getCount() != before;
                                if (stack.isEmpty()) return true;
                            }
                        }
                        return changed;
                    }

                    private String groupOf(int nativeIndex) {
                        for (var entry : groups.entrySet()) if (entry.getValue().contains(nativeIndex)) return entry.getKey();
                        return null;
                    }

                    private static final class PortableSlot extends Slot {
                        private final InventorySlotSpec spec;
                        private PortableSlot(Container owner, InventorySlotSpec spec) {
                            super(owner, spec.storageIndex(), spec.x(), spec.y());
                            this.spec = spec;
                        }
                        @Override public boolean mayPlace(ItemStack stack) {
                            if (spec.role() == InventorySlotRole.OUTPUT) return false;
                            if (spec.acceptedItems().isEmpty()) return true;
                            var id = ${ITEM_ID};
                            return id != null && spec.acceptedItems().stream().anyMatch(accepted -> accepted.toString().equals(id.toString()));
                        }
                        @Override public int getMaxStackSize() { return spec.maximumCount(); }
                    }
                }
                """
                .replace("${PACKAGE}", policy.runtimePackage())
                .replace("${PREFIX}", policy.prefix())
                .replace("${ITEM_ID}", itemId);
    }

    private static String screen(BlockEntityNativePolicy policy) {
        boolean extracted = policy.unobfuscated();
        boolean legacy = policy.legacy();
        String graphics = extracted ? "GuiGraphicsExtractor" : "GuiGraphics";
        String constructor = extracted
                ? "super(menu, inventory, title, menu.definition().spec().width(), menu.definition().spec().height());"
                : "super(menu, inventory, title);\n        imageWidth = menu.definition().spec().width();\n        imageHeight = menu.definition().spec().height();";
        String renderBg = extracted ? """
                    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                        graphics.fill(0, 0, width, height, 0xB0100D18);
                        drawPanel(graphics);
                        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
                    }
                """ : """
                    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
                        drawPanel(graphics);
                    }

                    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                        renderBackground(graphics${BACKGROUND_ARGS});
                        super.render(graphics, mouseX, mouseY, partialTick);
                        renderTooltip(graphics, mouseX, mouseY);
                    }
                """.replace("${BACKGROUND_ARGS}", legacy ? "" : ", mouseX, mouseY, partialTick");
        String labels = extracted ? """
                    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
                        graphics.text(font, title, titleLabelX, titleLabelY, 0xFFEADFFF, false);
                        if (menu.definition().spec().playerInventory())
                            graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFD8CCE8, false);
                    }
                """ : """
                    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
                        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFEADFFF, false);
                        if (menu.definition().spec().playerInventory())
                            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFD8CCE8, false);
                    }
                """;
        return """
                package uk.co.enderfall.sdk.runtime.${PACKAGE};

                import net.minecraft.client.gui.${GRAPHICS};
                import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
                import net.minecraft.network.chat.Component;
                import net.minecraft.world.entity.player.Inventory;
                import net.minecraft.world.inventory.Slot;

                /** Texture-independent screen for an authored portable inventory. */
                final class ${PREFIX}InventoryScreen extends AbstractContainerScreen<${PREFIX}InventoryMenu> {
                    ${PREFIX}InventoryScreen(${PREFIX}InventoryMenu menu, Inventory inventory, Component title) {
                        ${CONSTRUCTOR}
                        inventoryLabelX = menu.definition().spec().playerInventoryX();
                        inventoryLabelY = Math.max(6, menu.definition().spec().playerInventoryY() - 12);
                    }

                ${RENDER_BG}
                    private void drawPanel(${GRAPHICS} graphics) {
                        int color = 0xEE15111F;
                        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, color);
                        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF2B2238);
                        for (Slot slot : menu.slots) {
                            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                                    leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF09070D);
                            graphics.fill(leftPos + slot.x, topPos + slot.y,
                                    leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF493C5A);
                        }
                    }

                ${LABELS}
                }
                """
                .replace("${PACKAGE}", policy.runtimePackage())
                .replace("${PREFIX}", policy.prefix())
                .replace("${GRAPHICS}", graphics)
                .replace("${CONSTRUCTOR}", constructor)
                .replace("${RENDER_BG}", indent(renderBg, 0))
                .replace("${LABELS}", indent(labels, 0));
    }

    private static String client(BlockEntityNativePolicy policy) {
        if (policy.fabric()) {
            return """
                    package uk.co.enderfall.sdk.runtime.${PACKAGE};
                    import net.minecraft.client.gui.screens.MenuScreens;
                    import net.minecraft.world.inventory.MenuType;
                    final class ${PREFIX}InventoryClient {
                        private ${PREFIX}InventoryClient() { }
                        static void register(MenuType<${PREFIX}InventoryMenu> type) {
                            MenuScreens.register(type, ${PREFIX}InventoryScreen::new);
                        }
                    }
                    """.replace("${PACKAGE}", policy.runtimePackage()).replace("${PREFIX}", policy.prefix());
        }
        String imports = policy.legacy()
                ? "import net.minecraftforge.eventbus.api.IEventBus;\nimport net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;"
                : "import net.neoforged.bus.api.IEventBus;\nimport net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;";
        String body = policy.legacy()
                ? "modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> MenuScreens.register(type.get(), ${PREFIX}InventoryScreen::new)));"
                : "modBus.addListener((RegisterMenuScreensEvent event) -> event.register(type.get(), ${PREFIX}InventoryScreen::new));";
        return """
                package uk.co.enderfall.sdk.runtime.${PACKAGE};
                import java.util.function.Supplier;
                import net.minecraft.client.gui.screens.MenuScreens;
                import net.minecraft.world.inventory.MenuType;
                ${IMPORTS}
                final class ${PREFIX}InventoryClient {
                    private ${PREFIX}InventoryClient() { }
                    static void register(IEventBus modBus, Supplier<MenuType<${PREFIX}InventoryMenu>> type) {
                        ${BODY}
                    }
                }
                """.replace("${PACKAGE}", policy.runtimePackage())
                .replace("${PREFIX}", policy.prefix())
                .replace("${IMPORTS}", imports)
                .replace("${BODY}", body.replace("${PREFIX}", policy.prefix()));
    }

    private static String indent(String value, int ignored) { return value.stripTrailing(); }
}
