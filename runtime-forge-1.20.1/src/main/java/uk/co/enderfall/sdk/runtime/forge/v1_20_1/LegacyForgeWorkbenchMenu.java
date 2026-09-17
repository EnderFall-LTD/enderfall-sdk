package uk.co.enderfall.sdk.runtime.forge.v1_20_1;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.runtime.PortableWorkbenchCraft;
import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;

/** Real Forge-family 1.20.1 container with vanilla inventory synchronization. */
final class LegacyForgeWorkbenchMenu extends AbstractContainerMenu {
    private final LegacyForgeWorkbenchBinding binding;
    private final Inventory playerInventory;
    private final SimpleContainer inputs;
    private final ResultContainer result = new ResultContainer();
    private LegacyForgeWorkbenchRecipe selectedRecipe;

    LegacyForgeWorkbenchMenu(int containerId, Inventory playerInventory, LegacyForgeWorkbenchBinding binding) {
        super(binding.menuType().get(), containerId);
        this.binding = binding;
        this.playerInventory = playerInventory;
        inputs = new SimpleContainer(binding.recipes().inputSlots()) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (!playerInventory.player.level().isClientSide()) {
                    LegacyForgeWorkbenchMenu.this.slotsChanged(this);
                }
            }
        };
        int inputStart = 44 - ((binding.recipes().inputSlots() - 1) * 9);
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
        addPlayerInventory(playerInventory);
        updateResult();
    }

    PortableWorkbenchDefinition definition() { return binding.definition(); }
    int machineSlots() { return binding.recipes().inputSlots() + 1; }

    @Override public boolean stillValid(Player player) { return true; }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        updateResult();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack moving = slot.getItem();
            original = moving.copy();
            int machineSlots = machineSlots();
            if (index < machineSlots) {
                if (!moveItemStackTo(moving, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(moving, 0, machineSlots - 1, false)) {
                return ItemStack.EMPTY;
            }
            if (moving.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            if (moving.getCount() == original.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, moving);
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) clearContainer(player, inputs);
    }

    private void updateResult() {
        selectedRecipe = null;
        result.setItem(0, ItemStack.EMPTY);
        Player player = playerInventory.player;
        if (player.level().isClientSide()) return;
        player.level().getRecipeManager().getRecipeFor(binding.recipes().type().get(), inputs, player.level())
                .ifPresent(recipe -> {
                    selectedRecipe = recipe;
                    result.setRecipeUsed(recipe);
                    result.setItem(0, recipe.assemble(inputs, player.level().registryAccess()));
                });
        broadcastChanges();
    }

    private void completeCraft(Player player) {
        LegacyForgeWorkbenchRecipe recipe = selectedRecipe;
        if (recipe == null || !recipe.matches(inputs, player.level())) {
            updateResult();
            return;
        }
        List<LegacyForgeWorkbenchRecipe.Entry> ingredients = recipe.countedIngredients();
        for (int slot = 0; slot < ingredients.size(); slot++) {
            inputs.removeItem(slot, ingredients.get(slot).count());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            var recipeId = recipe.getId();
            ItemStack completed = recipe.result();
            var resultId = ForgeRegistries.ITEMS.getKey(completed.getItem());
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
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }
}
