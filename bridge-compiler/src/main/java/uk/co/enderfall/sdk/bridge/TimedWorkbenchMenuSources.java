package uk.co.enderfall.sdk.bridge;

/** Shared authored preview menu and client screen, with no per-target source folders. */
final class TimedWorkbenchMenuSources {
    private TimedWorkbenchMenuSources() { }
    static String menu() { return menu(true); }
    static String menu(boolean fabric) {
        return menu(fabric ? BlockEntityNativePolicy.FABRIC_1214 : BlockEntityNativePolicy.NEOFORGE_1214);
    }
    static String menu(BlockEntityNativePolicy policy) {
        String source = """
                package uk.co.enderfall.sdk.runtime.${PACKAGE};

                import net.minecraft.world.SimpleContainer;
                import net.minecraft.world.entity.player.Inventory;
                import net.minecraft.world.entity.player.Player;
                import net.minecraft.world.inventory.*;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.server.level.ServerPlayer;
                import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;
                import uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity;

                final class ${PREFIX}TimedWorkbenchMenu extends AbstractContainerMenu {
                    record Binding(PortableWorkbenchDefinition definition, ${PREFIX}RecipeBinding recipes,
                                   java.util.function.Supplier<MenuType<${PREFIX}TimedWorkbenchMenu>> type) { }
                    private final Binding binding;
                    private final SimpleContainer inventory;
                    private final StoredBlockEntity owner;
                    private final ContainerData progress;
                    private final Runnable listener;
                    private boolean animationViewerAttached;

                    ${PREFIX}TimedWorkbenchMenu(int id, Inventory playerInventory, Binding binding, StoredBlockEntity owner) {
                        super(binding.type().get(), id);
                        this.binding = binding;
                        this.owner = owner;
                        int inputs = binding.recipes().inputSlots();
                        inventory = owner == null ? new SimpleContainer(inputs + 1) : owner.inventory();
                        if (inventory.getContainerSize() != inputs + 1) throw new IllegalArgumentException("Timed slot count mismatch");
                        progress = owner == null ? new SimpleContainerData(2) : new ContainerData() {
                            public int get(int index) {
                                if (index == 1) return owner.processingStatus().code();
                                if (index != 0) throw new IndexOutOfBoundsException(index);
                                var state = owner.processingState();
                                return state.job() == null ? 0 : (int) (1000L * state.elapsedTicks() / state.job().durationTicks());
                            }
                            public void set(int index, int value) { throw new UnsupportedOperationException("Server-owned progress"); }
                            public int getCount() { return 2; }
                        };
                        addDataSlots(progress);
                        int start = 44 - (inputs - 1) * 9;
                        for (int slot = 0; slot < inputs; slot++) addSlot(new Slot(inventory, slot, start + slot * 18, 35));
                        addSlot(new Slot(inventory, inputs, 124, 35) {
                            @Override public boolean mayPlace(ItemStack stack) { return false; }
                        });
                        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
                            addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
                        }
                        for (int column = 0; column < 9; column++) addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
                        listener = () -> broadcastChanges();
                        if (owner != null) owner.addInventoryListener(listener);
                        if (owner != null) { owner.menuAnimationOpened(); animationViewerAttached = true; }
                    }

                    int progressPermille() { return Math.max(0, Math.min(1000, progress.get(0))); }
                    uk.co.enderfall.sdk.runtime.blockentity.PortableMachineStatus status() {
                        return uk.co.enderfall.sdk.runtime.blockentity.PortableMachineStatus.fromCode(progress.get(1));
                    }
                    int backgroundColor() { return binding.definition().spec().backgroundColor(); }
                    @Override public boolean stillValid(Player player) { return owner == null || inventory.stillValid(player); }
                    @Override public void removed(Player player) {
                        super.removed(player);
                        if (owner != null) owner.removeInventoryListener(listener);
                        if (owner != null && animationViewerAttached) {
                            animationViewerAttached = false;
                            owner.menuAnimationClosed();
                        }
                    }
                    @Override public ItemStack quickMoveStack(Player player, int index) {
                        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
                        Slot slot = slots.get(index);
                        if (!slot.hasItem()) return ItemStack.EMPTY;
                        ItemStack moving = slot.getItem();
                        ItemStack original = moving.copy();
                        int inputs = binding.recipes().inputSlots();
                        if (index <= inputs) {
                            if (!moveItemStackTo(moving, inputs + 1, slots.size(), true)) return ItemStack.EMPTY;
                        } else if (!moveItemStackTo(moving, 0, inputs, false)) return ItemStack.EMPTY;
                        if (moving.getCount() == original.getCount()) return ItemStack.EMPTY;
                        if (moving.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
                        slot.onTake(player, moving);
                        return original;
                    }

                    static void openAt(ServerPlayer player, Binding binding, uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
                        var level = player.serverLevel();
                        if (!level.getServer().isSameThread() || !level.dimension().location().toString().equals(location.dimension().toString())) {
                            throw new IllegalArgumentException("Timed workbench requires the owning server thread and dimension");
                        }
                        var pos = new net.minecraft.core.BlockPos(location.x(), location.y(), location.z());
                        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                            throw new IllegalArgumentException("Timed workbench chunk is not loaded");
                        }
                        var expected = binding.definition().spec().storage().orElseThrow();
                        if (!(level.getBlockEntity(pos) instanceof StoredBlockEntity owner)
                                || !owner.definition().block().equals(expected.block())
                                || owner.inventorySize() != expected.inventorySlots()
                                || !owner.definition().fields().equals(expected.fields())
                                || !owner.inventory().stillValid(player)) {
                            throw new IllegalArgumentException("Timed workbench owner or reach mismatch");
                        }
                        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                                (id, inventory, ignored) -> new ${PREFIX}TimedWorkbenchMenu(id, inventory, binding, owner),
                                net.minecraft.network.chat.Component.literal(binding.definition().spec().title())));
                    }
                }
                """.replace("${PACKAGE}", policy.runtimePackage()).replace("${PREFIX}", policy.prefix());
        return policy.unobfuscated() ? BlockEntity26Sources.names(source) : source;
    }

    static String client() { return client(true); }
    static String client(boolean fabric) {
        return client(fabric ? BlockEntityNativePolicy.FABRIC_1214 : BlockEntityNativePolicy.NEOFORGE_1214);
    }
    static String client(BlockEntityNativePolicy policy) {
        boolean fabric = policy.fabric();
        String source = """
                package uk.co.enderfall.sdk.runtime.${PACKAGE};

                import net.minecraft.client.gui.GuiGraphics;
                import net.minecraft.client.gui.screens.MenuScreens;
                import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
                import net.minecraft.network.chat.Component;
                import net.minecraft.world.entity.player.Inventory;
                import net.minecraft.world.inventory.MenuType;

                /** Client-only linkage, called only after checking the physical environment. */
                final class ${PREFIX}TimedWorkbenchClient {
                    ${CLIENT_REGISTRATION}
                    private static final class Screen extends AbstractContainerScreen<${PREFIX}TimedWorkbenchMenu> {
                        Screen(${PREFIX}TimedWorkbenchMenu menu, Inventory inventory, Component title) {
                            super(menu, inventory, title);
                            imageWidth = 176; imageHeight = 166; inventoryLabelY = 72;
                        }
                        @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
                            graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, menu.backgroundColor());
                            for (var slot : menu.slots) {
                                graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                                        leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF493C5A);
                            }
                            graphics.fill(leftPos + 28, topPos + 60, leftPos + 148, topPos + 66, 0xFF09070D);
                            graphics.fill(leftPos + 28, topPos + 60,
                                    leftPos + 28 + 120 * menu.progressPermille() / 1000, topPos + 66, statusColor());
                        }
                        private int statusColor() {
                            return switch (menu.status()) {
                                case OUTPUT_BLOCKED, REMAINDER_BLOCKED -> 0xFFFFC46B;
                                case FAULT -> 0xFFFF7777;
                                default -> 0xFFB786FF;
                            };
                        }
                        private Component statusText() {
                            return switch (menu.status()) {
                                case IDLE -> statusMessage("enderfall_sdk.machine.idle", "Waiting for recipe");
                                case PROCESSING -> statusMessage("enderfall_sdk.machine.processing", "Processing");
                                case OUTPUT_BLOCKED -> statusMessage("enderfall_sdk.machine.output_blocked", "Output blocked");
                                case REMAINDER_BLOCKED -> statusMessage("enderfall_sdk.machine.remainder_blocked", "Remainder blocked");
                                case FAULT -> statusMessage("enderfall_sdk.machine.fault", "Machine fault: see log");
                            };
                        }
                        private static Component statusMessage(String key, String fallback) {
                            return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : Component.literal(fallback);
                        }
                        @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
                            graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFEADFFF, false);
                            graphics.drawString(font, statusText(), 8, 21, statusColor(), false);
                            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFD8CCE8, false);
                        }
                        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                            ${RENDER_BACKGROUND};
                            super.render(graphics, mouseX, mouseY, partialTick);
                            renderTooltip(graphics, mouseX, mouseY);
                            if (mouseX >= leftPos + 28 && mouseX < leftPos + 148
                                    && mouseY >= topPos + 60 && mouseY < topPos + 66) {
                                graphics.renderTooltip(font, statusText().copy().append(" (" + menu.progressPermille() / 10 + "%)"), mouseX, mouseY);
                            }
                        }
                    }
                }
                """.replace("${CLIENT_REGISTRATION}", fabric
                        ? "static void register(MenuType<${PREFIX}TimedWorkbenchMenu> type) { MenuScreens.register(type, Screen::new); }"
                        : policy.legacy()
                        ? "static void register(${BUS} bus, java.util.function.Supplier<MenuType<${PREFIX}TimedWorkbenchMenu>> type) { bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() -> MenuScreens.register(type.get(), Screen::new))); }"
                        : "static void register(${BUS} bus, java.util.function.Supplier<MenuType<${PREFIX}TimedWorkbenchMenu>> type) { bus.addListener((${SCREEN_EVENT} event) -> event.register(type.get(), Screen::new)); }")
                .replace("${BUS}", policy.legacy() ? "net.minecraftforge.eventbus.api.IEventBus" : "net.neoforged.bus.api.IEventBus")
                .replace("${SCREEN_EVENT}", policy.legacy() ? "net.minecraftforge.client.event.RegisterMenuScreensEvent" : "net.neoforged.neoforge.client.event.RegisterMenuScreensEvent")
                .replace("${RENDER_BACKGROUND}", policy.legacy() ? "renderBackground(graphics)" : "renderBackground(graphics, mouseX, mouseY, partialTick)")
                .replace("${PACKAGE}", policy.runtimePackage()).replace("${PREFIX}", policy.prefix());
        return policy.unobfuscated() ? client26(source) : source;
    }

    private static String client26(String source) {
        return source.replace("GuiGraphics", "GuiGraphicsExtractor")
                .replace("net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : Component.literal(fallback)", "Component.translatableWithFallback(key, fallback)")
                .replace("super(menu, inventory, title);", "super(menu, inventory, title, 176, 166);")
                .replace("imageWidth = 176; imageHeight = 166; ", "")
                .replace("@Override protected void renderBg", "private void renderBackdrop")
                .replace("protected void renderLabels", "protected void extractLabels")
                .replace("graphics.drawString(", "graphics.text(")
                .replace("public void render(", "public void extractRenderState(")
                .replace("renderBackground(graphics, mouseX, mouseY, partialTick);", "graphics.fill(0, 0, width, height, 0xB0100D18); renderBackdrop(graphics, partialTick, mouseX, mouseY);")
                .replace("super.render(", "super.extractRenderState(")
                .replace("renderTooltip(graphics, mouseX, mouseY);", "")
                .replace("graphics.renderTooltip(font,", "graphics.setTooltipForNextFrame(");
    }
}
