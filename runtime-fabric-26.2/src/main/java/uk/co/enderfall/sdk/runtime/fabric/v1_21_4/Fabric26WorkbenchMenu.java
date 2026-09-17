package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
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

/** Minecraft 26.2 replacement for Fabric's real portable workbench menu. */
final class FabricWorkbenchMenu extends AbstractContainerMenu {
    private final FabricWorkbenchBinding binding;
    private final Inventory playerInventory;
    private final SimpleContainer inputs;
    private final ResultContainer result = new ResultContainer();
    private RecipeHolder<FabricWorkbenchRecipe> selectedRecipe;

    FabricWorkbenchMenu(int containerId, Inventory playerInventory, FabricWorkbenchBinding binding) {
        super(binding.menuType(), containerId);
        this.binding = binding;
        this.playerInventory = playerInventory;
        inputs = new SimpleContainer(binding.recipes().inputSlots()) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (!playerInventory.player.level().isClientSide()) {
                    FabricWorkbenchMenu.this.slotsChanged(this);
                }
            }
        };
        int inputStart = 44 - ((binding.recipes().inputSlots() - 1) * 9);
        for (int slot = 0; slot < binding.recipes().inputSlots(); slot++) {
            addSlot(new Slot(inputs, slot, inputStart + slot * 18, 35));
        }
        addSlot(new Slot(result, 0, 124, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public void onTake(Player player, ItemStack stack) { completeCraft(player); super.onTake(player, stack); }
        });
        addPlayerInventory(playerInventory);
        updateResult();
    }
    PortableWorkbenchDefinition definition() { return binding.definition(); }
    int machineSlots() { return binding.recipes().inputSlots() + 1; }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void slotsChanged(Container container) { super.slotsChanged(container); updateResult(); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
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
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) clearContainer(player, inputs);
    }
    private FabricWorkbenchInput recipeInput() {
        return new FabricWorkbenchInput(inputs.getItems().stream().map(ItemStack::copy).toList());
    }
    private void updateResult() {
        selectedRecipe = null;
        result.setItem(0, ItemStack.EMPTY);
        Player player = playerInventory.player;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        FabricWorkbenchInput input = recipeInput();
        Optional<RecipeHolder<FabricWorkbenchRecipe>> match = serverPlayer.level().recipeAccess().getRecipeFor(
                binding.recipes().type(), input, serverPlayer.level());
        if (match.isPresent()) {
            selectedRecipe = match.get();
            result.setRecipeUsed(selectedRecipe);
            result.setItem(0, selectedRecipe.value().assemble(input));
        }
        broadcastChanges();
    }
    private void completeCraft(Player player) {
        RecipeHolder<FabricWorkbenchRecipe> holder = selectedRecipe;
        FabricWorkbenchInput input = recipeInput();
        if (holder == null || !holder.value().matches(input, player.level())) { updateResult(); return; }
        List<FabricWorkbenchRecipe.Entry> ingredients = holder.value().countedIngredients();
        for (int slot = 0; slot < ingredients.size(); slot++) inputs.removeItem(slot, ingredients.get(slot).count());
        if (player instanceof ServerPlayer serverPlayer) {
            var recipeId = holder.id().identifier();
            ItemStack completed = holder.value().result();
            var resultId = BuiltInRegistries.ITEM.getKey(completed.getItem());
            binding.definition().craftListener().accept(new PortableWorkbenchCraft(serverPlayer.getUUID(),
                    ResourceId.of(recipeId.getNamespace(), recipeId.getPath()),
                    ResourceId.of(resultId.getNamespace(), resultId.getPath()), completed.getCount()));
        }
        updateResult();
    }
    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
    }
}
