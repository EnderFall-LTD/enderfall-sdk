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
        String description = p.modern() ? "Real " + p.prefix() + " 1.21.4 container with vanilla slot synchronization."
                : p.fabric() ? "Minecraft 1.21.1 replacement for Fabric's portable workbench menu."
                : "Minecraft 1.21.1 replacement for the shared NeoForge workbench menu.";
        String holderId = p.modern() ? ".id().location()" : ".id()";
        return MENU.replace("${LOADER}", p.loader())
                .replace("${PREFIX}", p.prefix())
                .replace("${HANDLE}", p.fabric() ? "" : ".get()")
                .replace("${SERVER_IMPORT}", p.modern() || p.persistent()
                        ? "import net.minecraft.server.level.ServerLevel;\n" : "")
                .replace("${DESCRIPTION}", description)
                .replace("${CLASS}", p.prefix() + (p.persistent() ? "PersistentWorkbenchMenu" : "WorkbenchMenu"))
                .replace("${OWNER_FIELD}", p.persistent()
                        ? "    private final Runnable inputListener; private uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity owner;\n" : "")
                .replace("${OWNER_ARGUMENT}", p.persistent()
                        ? ", uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity owner" : "")
                .replace("${INPUT_INITIALIZATION}", inputInitialization(p))
                .replace("${VALIDITY}", p.persistent() ? "inputs.stillValid(player)" : "true")
                .replace("${REMOVED}", p.persistent() ? "owner.removeInventoryListener(inputListener);"
                        : "if (!player.level().isClientSide()) clearContainer(player, inputs);")
                .replace("${SERVER_GUARD}", p.modern()
                        ? "if (!(player.level() instanceof ServerLevel level)) return;"
                        : "if (player.level().isClientSide()) return;")
                .replace("${RECIPE_ACCESS}", p.modern() ? "level.recipeAccess()" : "player.level().getRecipeManager()")
                .replace("${LEVEL}", p.modern() ? "level" : "player.level()")
                .replace("${HOLDER_ID}", holderId)
                .replace("${PERSISTENT_OPENING}", p.persistent()
                        ? persistentOpening().replace("${PREFIX}", p.prefix()).replace("${CONSTRUCTOR_TYPE}", "") : "");
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
            package uk.co.enderfall.sdk.runtime.${LOADER}.v1_21_4;

            import java.util.ArrayList;
            import java.util.Comparator;
            import java.util.List;
            import net.minecraft.core.registries.BuiltInRegistries;
            ${SERVER_IMPORT}import net.minecraft.server.level.ServerPlayer;
            import net.minecraft.world.Container;
            import net.minecraft.world.SimpleContainer;
            import net.minecraft.world.entity.player.Inventory;
            import net.minecraft.world.entity.player.Player;
            import net.minecraft.world.inventory.AbstractContainerMenu;
            import net.minecraft.world.inventory.DataSlot;
            import net.minecraft.world.inventory.ResultContainer;
            import net.minecraft.world.inventory.Slot;
            import net.minecraft.world.item.ItemStack;
            import net.minecraft.world.item.crafting.RecipeHolder;
            import uk.co.enderfall.sdk.api.ResourceId;
            import uk.co.enderfall.sdk.runtime.PortableWorkbenchCraft;
            import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;

            /** ${DESCRIPTION} */
            final class ${CLASS} extends AbstractContainerMenu {
                private final ${PREFIX}WorkbenchBinding binding;
                private final Inventory playerInventory;
                private final SimpleContainer inputs;
                private final ResultContainer result = new ResultContainer();
                private final SimpleContainer recipeChoices;
                private List<RecipeHolder<${PREFIX}WorkbenchRecipe>> matchingRecipes = List.of();
                private final DataSlot selectedRecipeIndex = DataSlot.standalone();
                private final DataSlot recipePage = DataSlot.standalone();
                private final DataSlot recipePages = DataSlot.standalone();
                private final DataSlot[] requiredCounts;
                private RecipeHolder<${PREFIX}WorkbenchRecipe> selectedRecipe;
            ${OWNER_FIELD}
                ${CLASS}(int containerId, Inventory playerInventory, ${PREFIX}WorkbenchBinding binding${OWNER_ARGUMENT}) {
                    super(binding.menuType()${HANDLE}, containerId);
                    this.binding = binding;
                    this.playerInventory = playerInventory;
                    recipeChoices = new SimpleContainer(binding.definition().spec().recipeBrowserEntries());
                    requiredCounts = new DataSlot[binding.recipes().inputSlots()];
                    addDataSlot(selectedRecipeIndex); addDataSlot(recipePage); addDataSlot(recipePages);
                    for (int slot = 0; slot < requiredCounts.length; slot++) {
                        requiredCounts[slot] = DataSlot.standalone(); addDataSlot(requiredCounts[slot]);
                    }
            ${INPUT_INITIALIZATION}        int inputStart = 44 - ((binding.recipes().inputSlots() - 1) * 9);
                    for (int slot = 0; slot < binding.recipes().inputSlots(); slot++) {
                        addSlot(new Slot(inputs, slot, inputStart + slot * 18, 35));
                    }
                    addSlot(new Slot(result, 0, 124, 35) {
                        @Override public boolean mayPlace(ItemStack stack) { return false; }
                        @Override public void onTake(Player player, ItemStack stack) {
                            completeCraft(player);
                            super.onTake(player, stack);
                        }
                    });
                    for (int choice = 0; choice < recipeChoices.getContainerSize(); choice++) {
                        addSlot(new Slot(recipeChoices, choice, 44 + choice * 18, 59) {
                            @Override public boolean mayPlace(ItemStack stack) { return false; }
                            @Override public boolean mayPickup(Player player) { return false; }
                        });
                    }
                    addPlayerInventory(playerInventory);
                    updateRecipes();
                }

                PortableWorkbenchDefinition definition() { return binding.definition(); }
                int machineSlots() { return binding.recipes().inputSlots() + 1 + recipeChoices.getContainerSize(); }
                int recipeChoiceStart() { return binding.recipes().inputSlots() + 1; }
                int recipeChoiceCount() { return recipeChoices.getContainerSize(); }
                int recipePage() { return recipePage.get(); }
                int recipePages() { return recipePages.get(); }
                int selectedRecipeIndex() { return selectedRecipeIndex.get(); }
                int requiredCount(int slot) { return slot >= 0 && slot < requiredCounts.length ? requiredCounts[slot].get() : 0; }
                @Override public boolean stillValid(Player player) { return ${VALIDITY}; }
                @Override public void slotsChanged(Container container) { super.slotsChanged(container); updateRecipes(); }

                @Override public ItemStack quickMoveStack(Player player, int index) {
                    ItemStack original = ItemStack.EMPTY;
                    Slot slot = slots.get(index);
                    if (slot != null && slot.hasItem()) {
                        ItemStack moving = slot.getItem();
                        original = moving.copy();
                        int machineSlots = machineSlots();
                        if (index >= recipeChoiceStart() && index < machineSlots) return ItemStack.EMPTY;
                        if (index < machineSlots) {
                            if (!moveItemStackTo(moving, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
                        } else if (!moveItemStackTo(moving, 0, binding.recipes().inputSlots(), false)) return ItemStack.EMPTY;
                        if (moving.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
                        if (moving.getCount() == original.getCount()) return ItemStack.EMPTY;
                        slot.onTake(player, moving);
                    }
                    return original;
                }

                @Override public void removed(Player player) {
                    super.removed(player);
                    ${REMOVED}
                }

                private ${PREFIX}WorkbenchInput recipeInput() {
                    return new ${PREFIX}WorkbenchInput(inputs.getItems().stream().map(ItemStack::copy).toList());
                }

                private void updateRecipes() {
                    String previousId = selectedRecipe == null ? "" : selectedRecipe${HOLDER_ID}.toString();
                    selectedRecipe = null; matchingRecipes = List.of();
                    selectedRecipeIndex.set(-1); recipePage.set(0); recipePages.set(1);
                    for (int row = 0; row < recipeChoices.getContainerSize(); row++) recipeChoices.setItem(row, ItemStack.EMPTY);
                    result.setItem(0, ItemStack.EMPTY);
                    Player player = playerInventory.player;
                    ${SERVER_GUARD}
                    ${PREFIX}WorkbenchInput input = recipeInput();
                    List<RecipeHolder<${PREFIX}WorkbenchRecipe>> discovered = new ArrayList<>();
                    for (RecipeHolder<?> candidate : ${RECIPE_ACCESS}.getRecipes()) {
                        if (candidate.value() instanceof ${PREFIX}WorkbenchRecipe recipe
                                && recipe.getType() == binding.recipes().type()${HANDLE}
                                && recipe.matches(input, ${LEVEL})) {
                            @SuppressWarnings("unchecked")
                            RecipeHolder<${PREFIX}WorkbenchRecipe> typed =
                                    (RecipeHolder<${PREFIX}WorkbenchRecipe>) (RecipeHolder<?>) candidate;
                            discovered.add(typed);
                        }
                    }
                    discovered.sort(Comparator.comparing(holder -> holder${HOLDER_ID}.toString()));
                    matchingRecipes = List.copyOf(discovered.subList(0, Math.min(256, discovered.size())));
                    if (!matchingRecipes.isEmpty()) {
                        int selected = 0;
                        if (!previousId.isBlank()) for (int index = 0; index < matchingRecipes.size(); index++) {
                            if (matchingRecipes.get(index)${HOLDER_ID}.toString().equals(previousId)) { selected = index; break; }
                        }
                        selectedRecipeIndex.set(selected);
                        int visible = Math.max(1, recipeChoices.getContainerSize());
                        recipePages.set(Math.max(1, (matchingRecipes.size() + visible - 1) / visible));
                        recipePage.set(selected / visible);
                    }
                    updateRecipeChoices(); updateResult();
                }

                private void updateRecipeChoices() {
                    for (int row = 0; row < recipeChoices.getContainerSize(); row++) {
                        int index = recipePage.get() * recipeChoices.getContainerSize() + row;
                        recipeChoices.setItem(row, index < matchingRecipes.size()
                                ? matchingRecipes.get(index).value().result().copy() : ItemStack.EMPTY);
                    }
                    broadcastChanges();
                }

                private void updateResult() {
                    selectedRecipe = null; result.setItem(0, ItemStack.EMPTY);
                    for (DataSlot count : requiredCounts) count.set(0);
                    Player player = playerInventory.player;
                    ${SERVER_GUARD}
                    int selected = selectedRecipeIndex.get();
                    if (selected < 0 || selected >= matchingRecipes.size()) { broadcastChanges(); return; }
                    selectedRecipe = matchingRecipes.get(selected);
                    ${PREFIX}WorkbenchInput input = recipeInput();
                    if (!selectedRecipe.value().matches(input, ${LEVEL})) { updateRecipes(); return; }
                    result.setRecipeUsed(selectedRecipe);
                    result.setItem(0, selectedRecipe.value().assemble(input, ${LEVEL}.registryAccess()));
                    var ingredients = selectedRecipe.value().countedIngredients();
                    for (int slot = 0; slot < requiredCounts.length; slot++) requiredCounts[slot].set(ingredients.get(slot).count());
                    broadcastChanges();
                }

                @Override public boolean clickMenuButton(Player player, int id) {
                    int visible = recipeChoices.getContainerSize();
                    if (id >= 0 && id < visible) {
                        int selected = recipePage.get() * visible + id;
                        if (selected >= matchingRecipes.size()) return false;
                        selectedRecipeIndex.set(selected); updateResult(); return true;
                    }
                    if (id == 100 && recipePage.get() > 0) { recipePage.set(recipePage.get() - 1); updateRecipeChoices(); return true; }
                    if (id == 101 && recipePage.get() + 1 < recipePages.get()) { recipePage.set(recipePage.get() + 1); updateRecipeChoices(); return true; }
                    return false;
                }

                private void completeCraft(Player player) {
                    RecipeHolder<${PREFIX}WorkbenchRecipe> holder = selectedRecipe;
                    ${PREFIX}WorkbenchInput input = recipeInput();
                    if (holder == null || !holder.value().matches(input, player.level())) { updateRecipes(); return; }
                    List<${PREFIX}WorkbenchRecipe.Entry> ingredients = holder.value().countedIngredients();
                    for (int slot = 0; slot < ingredients.size(); slot++) inputs.removeItem(slot, ingredients.get(slot).count());
                    if (player instanceof ServerPlayer serverPlayer) {
                        var recipeId = holder${HOLDER_ID};
                        ItemStack completed = holder.value().result();
                        var resultId = BuiltInRegistries.ITEM.getKey(completed.getItem());
                        binding.definition().craftListener().accept(new PortableWorkbenchCraft(serverPlayer.getUUID(),
                                ResourceId.of(recipeId.getNamespace(), recipeId.getPath()),
                                ResourceId.of(resultId.getNamespace(), resultId.getPath()), completed.getCount()));
                    }
                    updateRecipes();
                }

                private void addPlayerInventory(Inventory inventory) {
                    for (int row = 0; row < 3; row++) {
                        for (int column = 0; column < 9; column++) {
                            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
                        }
                    }
                    for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
                }
            ${PERSISTENT_OPENING}}
            """;
}
