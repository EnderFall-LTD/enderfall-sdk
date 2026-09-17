package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MenuAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared 1.21.x slot-container implementation with explicit registry access policy. */
final class WorkbenchMenuEmitter {
    private WorkbenchMenuEmitter() { }

    private record Plan(boolean fabric, boolean modern, boolean persistent) {
        Plan(boolean fabric, boolean modern) { this(fabric, modern, false); }
        boolean compact() { return fabric && !modern; }
        String gap() { return compact() ? "" : "\n"; }
        String prefix() { return fabric ? "Fabric" : "NeoForge"; }
        String loader() { return fabric ? "fabric" : "neoforge"; }
        String root() { return "uk/co/enderfall/sdk/runtime/" + loader() + "/v1_21_4/" + prefix(); }
    }

    static String persistentPreview() { return render(new Plan(true, true, true)); }
    static String persistentPreview(boolean modern) { return render(new Plan(true, modern, true)); }
    static String persistentPreview(boolean modern, boolean fabric) { return render(new Plan(fabric, modern, true)); }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) {
            throw new BridgeGenerationException("Unreviewed menu target " + target.id());
        }
        if (target.menuAbi() != MenuAbi.V1_21_4 && target.menuAbi() != MenuAbi.V1_21_1) return List.of();
        Plan p = new Plan(target.loaderAbi() == LoaderAbi.FABRIC, target.menuAbi() == MenuAbi.V1_21_4);
        String menu = p.root() + "WorkbenchMenu.java";
        if (!paths.contains(menu)) return List.of();
        Set<String> required = Set.of(menu, p.root() + "WorkbenchBinding.java",
                p.root() + "RecipeBinding.java", p.root() + "WorkbenchRecipe.java", p.root() + "WorkbenchInput.java");
        if (!paths.containsAll(required)) {
            throw new BridgeGenerationException(target.id() + " requires complete workbench menu, binding, recipe, and input declarations");
        }
        return List.of(new RuntimeSource(menu, p.root() + (p.modern() ? "" : "1211") + "WorkbenchMenu.java", render(p).getBytes(StandardCharsets.UTF_8)));
    }

    private static String render(Plan p) {
        // Keep native method layout stable for independent source and class parity checks.
        String changed = (p.fabric() || !p.modern())
                ? "    @Override public void slotsChanged(Container container) { super.slotsChanged(container); updateResult(); }\n"
                : "\n    @Override public void slotsChanged(Container container) {\n        super.slotsChanged(container);\n        updateResult();\n    }\n";
        String quickMove = (p.fabric() || !p.modern()) ? "    @Override " : "    @Override\n    ";
        String stale = (p.fabric() || !p.modern())
                ? "        if (holder == null || !holder.value().matches(input, player.level())) { updateResult(); return; }\n"
                : "        if (holder == null || !holder.value().matches(input, player.level())) {\n            updateResult();\n            return;\n        }\n";
        String onTake = p.compact()
                ? "            @Override public void onTake(Player player, ItemStack stack) { completeCraft(player); super.onTake(player, stack); }\n"
                : "            @Override public void onTake(Player player, ItemStack stack) {\n                completeCraft(player);\n                super.onTake(player, stack);\n            }\n";
        String description = p.modern() ? "Real " + p.prefix() + " 1.21.4 container with vanilla slot synchronization."
                : p.fabric() ? "Minecraft 1.21.1 replacement for Fabric's portable workbench menu."
                : "Minecraft 1.21.1 replacement for the shared NeoForge workbench menu.";
        return MENU.formatted(p.loader(), p.prefix(), p.fabric() ? "" : ".get()", changed, quickMove, stale,
                p.modern() || p.persistent() ? "import net.minecraft.server.level.ServerLevel;\n" : "", description, onTake, p.gap(),
                p.modern() ? "if (!(player.level() instanceof ServerLevel level)) return;" : "if (player.level().isClientSide()) return;",
                p.modern() ? "level.recipeAccess()" : "player.level().getRecipeManager()",
                p.modern() ? "level" : "player.level()", p.modern() ? ".location()" : "",
                p.prefix() + (p.persistent() ? "PersistentWorkbenchMenu" : "WorkbenchMenu"),
                p.persistent() ? "    private final Runnable inputListener; private uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity owner;\n" : "",
                p.persistent() ? ", uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity owner" : "",
                inputInitialization(p),
                p.persistent() ? "inputs.stillValid(player)" : "true",
                p.persistent() ? "owner.removeInventoryListener(inputListener);"
                        : "if (!player.level().isClientSide()) clearContainer(player, inputs);",
                p.persistent() ? persistentOpening().replace("${PREFIX}", p.prefix()).replace("${CONSTRUCTOR_TYPE}", "") : "");
    }

    static String persistentOpening() {
        return """

                    static void bindBlock(uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity.Binding block,
                            ${PREFIX}WorkbenchBinding binding) {
                        var storage = binding.definition().spec().storage().orElseThrow(
                                () -> new IllegalArgumentException("Workbench requires persistent storage"));
                        if (!storage.block().equals(block.definition().block())
                                || storage.inventorySlots() != block.definition().inventorySlots()
                                || storage.inventorySlots() != binding.recipes().inputSlots()
                                || !storage.fields().equals(block.definition().fields())) {
                            throw new IllegalArgumentException("Persistent block and workbench definitions differ");
                        }
                        block.onUse((player, event) -> {
                            if (player instanceof ServerPlayer serverPlayer) {
                                openAt(serverPlayer, binding, event.blockLocation().orElseThrow());
                            }
                            event.handle();
                        });
                    }

                    static void openAt(ServerPlayer player, ${PREFIX}WorkbenchBinding binding,
                            uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
                        ServerLevel level = player.serverLevel();
                        if (!level.getServer().isSameThread()) {
                            throw new IllegalStateException("Persistent menus must open on the server thread");
                        }
                        if (!level.dimension().location().toString().equals(location.dimension().toString())) {
                            throw new IllegalArgumentException("Persistent workbench is in a different dimension");
                        }
                        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(location.x(), location.y(), location.z());
                        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                            throw new IllegalArgumentException("Persistent workbench chunk is not loaded");
                        }
                        if (!(level.getBlockEntity(pos) instanceof
                                uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity owner)) {
                            throw new IllegalArgumentException("Position has no EnderFall persistent block entity");
                        }
                        var expected = binding.definition().spec().storage().orElseThrow(
                                () -> new IllegalArgumentException("Workbench has no persistent storage definition"));
                        if (!expected.block().equals(owner.definition().block())
                                || expected.inventorySlots() != owner.inventorySize()
                                || !expected.fields().equals(owner.definition().fields())
                                || !owner.inventory().stillValid(player)) {
                            throw new IllegalArgumentException("Persistent workbench owner, schema, or player reach mismatch");
                        }
                        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                                (id, inventory, ignored) -> new ${PREFIX}PersistentWorkbenchMenu(${CONSTRUCTOR_TYPE}id, inventory, binding, owner),
                                net.minecraft.network.chat.Component.literal(binding.definition().spec().title())));
                    }
                """;
    }

    private static String inputInitialization(Plan p) {
        if (p.persistent()) return """
                        inputs = owner.inventory();
                        if (inputs.getContainerSize() != binding.recipes().inputSlots()) {
                            throw new IllegalArgumentException("Persistent workbench input slot count mismatch");
                        }
                        if (!inputs.stillValid(playerInventory.player)) {
                            throw new IllegalArgumentException("Persistent workbench is not accessible to this player");
                        }
                        this.owner = owner; inputListener = () -> slotsChanged(inputs);
                        owner.addInventoryListener(inputListener);
                """;
        return """
                        inputs = new SimpleContainer(binding.recipes().inputSlots()) {
                            @Override
                            public void setChanged() {
                                super.setChanged();
                                if (!playerInventory.player.level().isClientSide()) {
                                    %sWorkbenchMenu.this.slotsChanged(this);
                                }
                            }
                        };
                """.formatted(p.prefix());
    }

    // The declaration paths select this feature; no canonical source text is read as a template.
    private static final String MENU = """
            package uk.co.enderfall.sdk.runtime.%1$s.v1_21_4;
            
            import java.util.List;
            import java.util.Optional;
            import net.minecraft.core.registries.BuiltInRegistries;
            %7$simport net.minecraft.server.level.ServerPlayer;
            import net.minecraft.world.Container;
            import net.minecraft.world.SimpleContainer;
            import net.minecraft.world.entity.player.Inventory;
            import net.minecraft.world.entity.player.Player;
            import net.minecraft.world.inventory.AbstractContainerMenu;
            import net.minecraft.world.inventory.ResultContainer;
            import net.minecraft.world.inventory.Slot;
            import net.minecraft.world.item.ItemStack;
            import net.minecraft.world.item.crafting.RecipeHolder;
            import uk.co.enderfall.sdk.api.ResourceId;
            import uk.co.enderfall.sdk.runtime.PortableWorkbenchCraft;
            import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;
            
            /** %8$s */
            final class %15$s extends AbstractContainerMenu {
                private final %2$sWorkbenchBinding binding;
                private final Inventory playerInventory;
                private final SimpleContainer inputs;
                private final ResultContainer result = new ResultContainer();
                private RecipeHolder<%2$sWorkbenchRecipe> selectedRecipe;
            %16$s
                %15$s(int containerId, Inventory playerInventory, %2$sWorkbenchBinding binding%17$s) {
                    super(binding.menuType()%3$s, containerId);
                    this.binding = binding;
                    this.playerInventory = playerInventory;
            %18$s        int inputStart = 44 - ((binding.recipes().inputSlots() - 1) * 9);
                    for (int slot = 0; slot < binding.recipes().inputSlots(); slot++) {
                        addSlot(new Slot(inputs, slot, inputStart + slot * 18, 35));
                    }
                    addSlot(new Slot(result, 0, 124, 35) {
                        @Override public boolean mayPlace(ItemStack stack) { return false; }
            %9$s        });
                    addPlayerInventory(playerInventory);
                    updateResult();
                }
            %10$s    PortableWorkbenchDefinition definition() { return binding.definition(); }
                int machineSlots() { return binding.recipes().inputSlots() + 1; }
                @Override public boolean stillValid(Player player) { return %19$s; }
            %4$s
            %5$spublic ItemStack quickMoveStack(Player player, int index) {
                    ItemStack original = ItemStack.EMPTY;
                    Slot slot = slots.get(index);
                    if (slot != null && slot.hasItem()) {
                        ItemStack moving = slot.getItem();
                        original = moving.copy();
                        int machineSlots = machineSlots();
                        if (index < machineSlots) {
                            if (!moveItemStackTo(moving, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
                        } else if (!moveItemStackTo(moving, 0, machineSlots - 1, false)) return ItemStack.EMPTY;
                        if (moving.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
                        if (moving.getCount() == original.getCount()) return ItemStack.EMPTY;
                        slot.onTake(player, moving);
                    }
                    return original;
                }
            %10$s    @Override public void removed(Player player) {
                    super.removed(player);
                    %20$s
                }
            %10$s    private %2$sWorkbenchInput recipeInput() {
                    return new %2$sWorkbenchInput(inputs.getItems().stream().map(ItemStack::copy).toList());
                }
            %10$s    private void updateResult() {
                    selectedRecipe = null;
                    result.setItem(0, ItemStack.EMPTY);
                    Player player = playerInventory.player;
                    %11$s
                    %2$sWorkbenchInput input = recipeInput();
                    Optional<RecipeHolder<%2$sWorkbenchRecipe>> match = %12$s.getRecipeFor(
                            binding.recipes().type()%3$s, input, %13$s);
                    if (match.isPresent()) {
                        selectedRecipe = match.get();
                        result.setRecipeUsed(selectedRecipe);
                        result.setItem(0, selectedRecipe.value().assemble(input, %13$s.registryAccess()));
                    }
                    broadcastChanges();
                }
            %10$s    private void completeCraft(Player player) {
                    RecipeHolder<%2$sWorkbenchRecipe> holder = selectedRecipe;
                    %2$sWorkbenchInput input = recipeInput();
            %6$s        List<%2$sWorkbenchRecipe.Entry> ingredients = holder.value().countedIngredients();
                    for (int slot = 0; slot < ingredients.size(); slot++) inputs.removeItem(slot, ingredients.get(slot).count());
                    if (player instanceof ServerPlayer serverPlayer) {
                        var recipeId = holder.id()%14$s;
                        ItemStack completed = holder.value().result();
                        var resultId = BuiltInRegistries.ITEM.getKey(completed.getItem());
                        binding.definition().craftListener().accept(new PortableWorkbenchCraft(serverPlayer.getUUID(),
                                ResourceId.of(recipeId.getNamespace(), recipeId.getPath()),
                                ResourceId.of(resultId.getNamespace(), resultId.getPath()), completed.getCount()));
                    }
                    updateResult();
                }
            %10$s    private void addPlayerInventory(Inventory inventory) {
                    for (int row = 0; row < 3; row++) {
                        for (int column = 0; column < 9; column++) {
                            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
                        }
                    }
                    for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
                }
            %21$s}
            """;
}
