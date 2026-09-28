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
                import net.minecraft.world.inventory.DataSlot;
                import net.minecraft.world.inventory.MenuType;
                import net.minecraft.world.inventory.Slot;
                import net.minecraft.world.item.ItemStack;
                import uk.co.enderfall.sdk.api.ui.InventoryQuickMoveRule;
                import uk.co.enderfall.sdk.api.ui.InventorySlotRole;
                import uk.co.enderfall.sdk.api.ui.InventorySlotSpec;
                import uk.co.enderfall.sdk.api.blockentity.BlockEntityInt;
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
                    private final List<BlockEntityInt> synchronizedFields;
                    private final DataSlot[] synchronizedData;

                    ${PREFIX}InventoryMenu(int containerId, Inventory playerInventory,
                            Binding binding, Container owner) {
                        super(binding.menuType().get(), containerId);
                        this.binding = Objects.requireNonNull(binding, "binding");
                        var spec = binding.definition().spec();
                        this.owner = owner == null ? new SimpleContainer(spec.storage().inventorySlots()) : owner;
                        checkContainerSize(this.owner, spec.storage().inventorySlots());
                        this.owner.startOpen(playerInventory.player);
                        synchronizedFields = spec.synchronizedFields();
                        synchronizedData = new DataSlot[synchronizedFields.size() * 2];
                        var persistentOwner = owner instanceof
                                uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity stored
                                ? stored : null;
                        for (int index = 0; index < synchronizedFields.size(); index++) {
                            BlockEntityInt field = synchronizedFields.get(index);
                            for (int half = 0; half < 2; half++) {
                                final int shift = half * 16;
                                DataSlot data = persistentOwner == null ? DataSlot.standalone() : new DataSlot() {
                                    @Override public int get() { return (persistentOwner.value(field) >>> shift) & 0xFFFF; }
                                    @Override public void set(int ignored) { }
                                };
                                synchronizedData[index * 2 + half] = data;
                                addDataSlot(data);
                            }
                        }

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

                    int synchronizedValue(BlockEntityInt field) {
                        int index = synchronizedFields.indexOf(Objects.requireNonNull(field, "field"));
                        if (index < 0) throw new IllegalArgumentException(
                                "Field is not synchronized by this inventory: " + field.name());
                        return (synchronizedData[index * 2].get() & 0xFFFF)
                                | ((synchronizedData[index * 2 + 1].get() & 0xFFFF) << 16);
                    }

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
                        if (portable == null) drawPanel(graphics);
                        else renderPortable(graphics, mouseX, mouseY, partialTick, false);
                        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
                        if (portable != null) renderPortable(graphics, mouseX, mouseY, partialTick, true);
                    }
                """ : """
                    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
                        if (portable == null) drawPanel(graphics);
                        else renderPortable(graphics, mouseX, mouseY, partialTick, false);
                    }

                    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                        renderBackground(graphics${BACKGROUND_ARGS});
                        super.render(graphics, mouseX, mouseY, partialTick);
                        if (portable != null) renderPortable(graphics, mouseX, mouseY, partialTick, true);
                        renderTooltip(graphics, mouseX, mouseY);
                    }
                """.replace("${BACKGROUND_ARGS}", legacy ? "" : ", mouseX, mouseY, partialTick");
        String labels = extracted ? """
                    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
                        if (portable != null) return;
                        graphics.text(font, title, titleLabelX, titleLabelY, 0xFFEADFFF, false);
                        if (menu.definition().spec().playerInventory())
                            graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFD8CCE8, false);
                    }
                """ : """
                    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
                        if (portable != null) return;
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
                import java.util.LinkedHashMap;
                import java.util.Map;
                import uk.co.enderfall.sdk.api.ResourceId;
                import uk.co.enderfall.sdk.api.blockentity.BlockEntityInt;
                import uk.co.enderfall.sdk.api.client.ui.ClientScreenRef;
                import uk.co.enderfall.sdk.api.client.ui.InventoryScreenContext;
                import uk.co.enderfall.sdk.api.client.ui.PortableInventoryScreen;
                import uk.co.enderfall.sdk.api.client.ui.UiRenderFrame;
                import uk.co.enderfall.sdk.api.ui.StorageContainerRef;

                /** Texture-independent screen for an authored portable inventory. */
                final class ${PREFIX}InventoryScreen extends AbstractContainerScreen<${PREFIX}InventoryMenu> {
                    private final PortableInventoryScreen portable;
                    private final Map<ResourceId, net.minecraft.world.entity.LivingEntity> entityPreviews =
                            new LinkedHashMap<>();
                    private final InventoryScreenContext portableContext = new PortableContext();
                    private boolean portableInitialized;
                    private boolean portableRemoved;

                    ${PREFIX}InventoryScreen(${PREFIX}InventoryMenu menu, Inventory inventory, Component title) {
                        ${CONSTRUCTOR}
                        portable = ${PREFIX}InventoryClient.createPortableView(menu.definition());
                        inventoryLabelX = menu.definition().spec().playerInventoryX();
                        inventoryLabelY = Math.max(6, menu.definition().spec().playerInventoryY() - 12);
                    }

                    @Override protected void init() {
                        super.init();
                        if (portable == null) return;
                        if (portableInitialized) portable.resized(portableContext);
                        else {
                            portableInitialized = true;
                            portable.initialize(portableContext);
                        }
                    }

                    @Override protected void containerTick() {
                        super.containerTick();
                        if (portable != null) portable.tick(portableContext);
                    }

                    @Override public void removed() {
                        if (portable != null && !portableRemoved) {
                            portableRemoved = true;
                            entityPreviews.clear();
                            portable.removed();
                        }
                        super.removed();
                    }

                ${RENDER_BG}
                    private void renderPortable(${GRAPHICS} graphics, int mouseX, int mouseY,
                            float partialTick, boolean foreground) {
                        var context = new ${PREFIX}ClientScreenBridge.NativeRenderContext(graphics, entityPreviews,
                                new UiRenderFrame(width, height, mouseX, mouseY, partialTick,
                                        Math.max(0L, System.nanoTime())));
                        try {
                            if (foreground) portable.renderForeground(context, portableContext);
                            else portable.renderBackground(context, portableContext);
                        } finally {
                            context.finish();
                        }
                    }

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

                    ${INPUT}

                    private final class PortableContext implements InventoryScreenContext {
                        @Override public StorageContainerRef container() { return menu.definition().reference(); }
                        @Override public String title() { return ${PREFIX}InventoryScreen.this.title.getString(); }
                        @Override public int width() { return ${PREFIX}InventoryScreen.this.width; }
                        @Override public int height() { return ${PREFIX}InventoryScreen.this.height; }
                        @Override public int left() { return leftPos; }
                        @Override public int top() { return topPos; }
                        @Override public int menuWidth() { return imageWidth; }
                        @Override public int menuHeight() { return imageHeight; }
                        @Override public int value(BlockEntityInt field) { return menu.synchronizedValue(field); }
                        @Override public void close() { ${PREFIX}InventoryScreen.this.onClose(); }
                        @Override public void open(ClientScreenRef screen) {
                            ${PREFIX}ClientScreenBridge.open(screen.id());
                        }
                    }
                }
                """
                .replace("${PACKAGE}", policy.runtimePackage())
                .replace("${PREFIX}", policy.prefix())
                .replace("${GRAPHICS}", graphics)
                .replace("${CONSTRUCTOR}", constructor)
                .replace("${RENDER_BG}", indent(renderBg, 0))
                .replace("${LABELS}", indent(labels, 0))
                .replace("${INPUT}", inventoryInput(extracted, legacy));
    }

    private static String client(BlockEntityNativePolicy policy) {
        if (policy.fabric()) {
            return """
                    package uk.co.enderfall.sdk.runtime.${PACKAGE};
                    import java.util.LinkedHashMap;
                    import java.util.Map;
                    import java.util.function.Supplier;
                    import net.minecraft.client.gui.screens.MenuScreens;
                    import net.minecraft.world.inventory.MenuType;
                    import uk.co.enderfall.sdk.api.ResourceId;
                    import uk.co.enderfall.sdk.api.client.ui.PortableInventoryScreen;
                    import uk.co.enderfall.sdk.runtime.PortableStorageContainerDefinition;
                    final class ${PREFIX}InventoryClient {
                        private static final Map<ResourceId, Supplier<? extends PortableInventoryScreen>> VIEWS =
                                new LinkedHashMap<>();
                        private ${PREFIX}InventoryClient() { }
                        static synchronized void registerPortableView(ResourceId id,
                                Supplier<? extends PortableInventoryScreen> factory) {
                            if (VIEWS.putIfAbsent(id, factory) != null)
                                throw new IllegalStateException("Duplicate portable inventory screen " + id);
                        }
                        static synchronized PortableInventoryScreen createPortableView(
                                PortableStorageContainerDefinition definition) {
                            var factory = VIEWS.get(definition.reference().id());
                            return factory == null ? null : java.util.Objects.requireNonNull(factory.get(),
                                    "Portable inventory screen factory returned null for " + definition.reference().id());
                        }
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
                import java.util.LinkedHashMap;
                import java.util.Map;
                import java.util.function.Supplier;
                import net.minecraft.client.gui.screens.MenuScreens;
                import net.minecraft.world.inventory.MenuType;
                import uk.co.enderfall.sdk.api.ResourceId;
                import uk.co.enderfall.sdk.api.client.ui.PortableInventoryScreen;
                import uk.co.enderfall.sdk.runtime.PortableStorageContainerDefinition;
                ${IMPORTS}
                final class ${PREFIX}InventoryClient {
                    private static final Map<ResourceId, Supplier<? extends PortableInventoryScreen>> VIEWS =
                            new LinkedHashMap<>();
                    private ${PREFIX}InventoryClient() { }
                    static synchronized void registerPortableView(ResourceId id,
                            Supplier<? extends PortableInventoryScreen> factory) {
                        if (VIEWS.putIfAbsent(id, factory) != null)
                            throw new IllegalStateException("Duplicate portable inventory screen " + id);
                    }
                    static synchronized PortableInventoryScreen createPortableView(
                            PortableStorageContainerDefinition definition) {
                        var factory = VIEWS.get(definition.reference().id());
                        return factory == null ? null : java.util.Objects.requireNonNull(factory.get(),
                                "Portable inventory screen factory returned null for " + definition.reference().id());
                    }
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

    private static String inventoryInput(boolean extracted, boolean legacy) {
        if (extracted) return """
                    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
                        return portable != null && portable.mouseClicked(event.x(), event.y(), event.button())
                                || super.mouseClicked(event, doubled);
                    }
                    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
                        return portable != null && portable.mouseReleased(event.x(), event.y(), event.button())
                                || super.mouseReleased(event);
                    }
                    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
                            double dragX, double dragY) {
                        return portable != null && portable.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY)
                                || super.mouseDragged(event, dragX, dragY);
                    }
                    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
                        return portable != null && portable.mouseScrolled(x, y, horizontal, vertical)
                                || super.mouseScrolled(x, y, horizontal, vertical);
                    }
                    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
                        return portable != null && portable.keyPressed(event.key(), event.scancode(), event.modifiers())
                                || super.keyPressed(event);
                    }
                    @Override public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
                        return portable != null && portable.keyReleased(event.key(), event.scancode(), event.modifiers())
                                || super.keyReleased(event);
                    }
                    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
                        return portable != null && portable.characterTyped(event.codepoint(), 0) || super.charTyped(event);
                    }
                """;
        String scroll = legacy
                ? "@Override public boolean mouseScrolled(double x, double y, double amount) {\n"
                    + "        return portable != null && portable.mouseScrolled(x, y, 0, amount) || super.mouseScrolled(x, y, amount);\n    }"
                : "@Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {\n"
                    + "        return portable != null && portable.mouseScrolled(x, y, horizontal, vertical) || super.mouseScrolled(x, y, horizontal, vertical);\n    }";
        return """
                    @Override public boolean mouseClicked(double x, double y, int button) {
                        return portable != null && portable.mouseClicked(x, y, button) || super.mouseClicked(x, y, button);
                    }
                    @Override public boolean mouseReleased(double x, double y, int button) {
                        return portable != null && portable.mouseReleased(x, y, button) || super.mouseReleased(x, y, button);
                    }
                    @Override public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
                        return portable != null && portable.mouseDragged(x, y, button, dragX, dragY)
                                || super.mouseDragged(x, y, button, dragX, dragY);
                    }
                    ${SCROLL}
                    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
                        return portable != null && portable.keyPressed(key, scanCode, modifiers)
                                || super.keyPressed(key, scanCode, modifiers);
                    }
                    @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
                        return portable != null && portable.keyReleased(key, scanCode, modifiers)
                                || super.keyReleased(key, scanCode, modifiers);
                    }
                    @Override public boolean charTyped(char character, int modifiers) {
                        return portable != null && portable.characterTyped(character, modifiers)
                                || super.charTyped(character, modifiers);
                    }
                """.replace("${SCROLL}", scroll);
    }
}
