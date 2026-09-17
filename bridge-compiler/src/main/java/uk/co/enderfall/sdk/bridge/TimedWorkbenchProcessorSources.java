package uk.co.enderfall.sdk.bridge;

/** Native recipe operations for the isolated persistence preview, authored centrally. */
final class TimedWorkbenchProcessorSources {
    private TimedWorkbenchProcessorSources() { }

    static String emit() {
        return emit(true);
    }

    static String emit(boolean modern) { return emit(modern, true); }
    static String emit(boolean modern, boolean fabric) {
        return emit(BlockEntityNativePolicy.require((modern ? "1.21.4-" : "1.21.1-") + (fabric ? "fabric" : "neoforge")));
    }
    static String emit(BlockEntityNativePolicy policy) {
        boolean modern = policy.modernRecipes(), fabric = policy.fabric(), legacy = policy.legacy();
        return """
                package uk.co.enderfall.sdk.runtime.${PACKAGE};

                import java.util.ArrayList;
                import java.util.Map;
                import net.minecraft.server.level.ServerLevel;
                import net.minecraft.world.item.ItemStack;
                import uk.co.enderfall.sdk.api.ResourceId;
                import uk.co.enderfall.sdk.runtime.blockentity.PortableProcessingCycle;
                import uk.co.enderfall.sdk.runtime.blockentity.PortableMachineStatus;
                import uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity;

                /** Development-only loaded-block processing, independent of menu viewers. */
                final class ${PREFIX}TimedWorkbenchProcessor {
                    private ${PREFIX}TimedWorkbenchProcessor() { }

                    static void bind(StoredBlockEntity.Binding owner, ${PREFIX}RecipeBinding recipes, int duration) {
                        if (duration < 1 || duration > 1_728_000) throw new IllegalArgumentException("Invalid processing duration");
                        if (owner.definition().inventorySlots() != recipes.inputSlots() + 1) {
                            throw new IllegalArgumentException("Timed workbench requires inputs plus one stored output slot");
                        }
                        owner.onMachineTick(entity -> tick(entity, recipes, duration));
                        owner.enableMachinePorts(recipes.inputSlots());
                    }

                    private static void tick(StoredBlockEntity owner, ${PREFIX}RecipeBinding recipes, int duration) {
                        if (!(owner.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
                            throw new IllegalStateException("Timed processing requires the owning server thread");
                        }
                        var slots = new ArrayList<ItemStack>();
                        for (int slot = 0; slot < owner.inventorySize(); slot++) slots.add(owner.stack(slot));
                        var input = ${RECIPE_INPUT};
                        var match = ${RECIPE_ACCESS}.getRecipeFor(recipes.type()${GET}, input, level);
                        var previous = owner.processingState();
                        if (match.isEmpty()) {
                            owner.processingStatus(PortableMachineStatus.IDLE);
                            if (!previous.equals(PortableProcessingCycle.State.idle())) {
                                owner.commitProcessing(slots, Map.of(), PortableProcessingCycle.State.idle());
                            }
                            return;
                        }
                        var holder = match.get();
                        var recipe = ${RECIPE_VALUE};
                        // Hash the effective serialized definition so data-pack recipe edits reset progress.
                        String encoded = ${ENCODE_RECIPE};
                        String revision;
                        try {
                            revision = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        } catch (java.security.NoSuchAlgorithmException impossible) {
                            throw new IllegalStateException(impossible);
                        }
                        var job = new PortableProcessingCycle.Job(
                                ResourceId.parse(${RECIPE_ID}.toString()), revision, duration);
                        var completed = new ArrayList<ItemStack>();
                        for (ItemStack stack : slots) completed.add(stack.copy());
                        int outputSlot = recipes.inputSlots();
                        ItemStack result = recipe.assemble(input, level.registryAccess());
                        boolean fits = !result.isEmpty() && merge(completed, outputSlot, result, owner.inventory().getMaxStackSize());
                        boolean outputFits = fits;
                        for (int slot = 0; slot < outputSlot && fits; slot++) {
                            int count = recipe.countedIngredients().get(slot).count();
                            ItemStack remainder = ${REMAINDER};
                            completed.get(slot).shrink(count);
                            if (!remainder.isEmpty()) {
                                long remainderCount = (long) remainder.getCount() * count;
                                if (remainderCount > Integer.MAX_VALUE) fits = false;
                                else fits = merge(completed, slot, remainder.copyWithCount((int) remainderCount),
                                        owner.inventory().getMaxStackSize());
                            }
                        }
                        var step = PortableProcessingCycle.advance(previous, job, fits);
                        owner.processingStatus(fits ? PortableMachineStatus.PROCESSING
                                : outputFits ? PortableMachineStatus.REMAINDER_BLOCKED : PortableMachineStatus.OUTPUT_BLOCKED);
                        if (step.status() == PortableProcessingCycle.Status.COMPLETE) {
                            owner.commitProcessing(completed, Map.of(), step.next());
                        } else if (!previous.equals(step.next())) {
                            owner.commitProcessing(slots, Map.of(), step.next());
                        }
                    }

                    private static boolean merge(java.util.List<ItemStack> slots, int slot, ItemStack addition, int containerLimit) {
                        ItemStack existing = slots.get(slot);
                        if (!existing.isEmpty() && !ItemStack.${SAME_STACK}(existing, addition)) return false;
                        int capacity = Math.min(containerLimit, addition.getMaxStackSize());
                        long total = (long) existing.getCount() + addition.getCount();
                        if (total > capacity) return false;
                        slots.set(slot, addition.copyWithCount((int) total));
                        return true;
                    }
                    ${LEGACY_ENCODER}
                    ${MACHINE}
                }
                """.replace("${RECIPE_INPUT}", legacy ? "new net.minecraft.world.SimpleContainer(slots.subList(0, recipes.inputSlots()).toArray(ItemStack[]::new))" : "new ${PREFIX}WorkbenchInput(slots.subList(0, recipes.inputSlots()))")
                .replace("${RECIPE_VALUE}", legacy ? "holder" : "holder.value()")
                .replace("${ENCODE_RECIPE}", legacy ? "encodeLegacyRecipe(recipe)" : "recipes.serializer()${GET}.codec().codec().encodeStart(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), recipe).getOrThrow().toString()")
                .replace("${LEGACY_ENCODER}", legacy ? legacyEncoder() : "")
                .replace("${MACHINE}", MachineRecipeSources.emit(policy))
                .replace("${SAME_STACK}", legacy ? "isSameItemSameTags" : "isSameItemSameComponents")
                .replace("${PACKAGE}", policy.runtimePackage()).replace("${PREFIX}", policy.prefix())
                .replace("${RECIPE_ACCESS}", modern ? "level.recipeAccess()" : "level.getRecipeManager()")
                .replace("${GET}", fabric ? "" : ".get()")
                .replace("recipe.assemble(input, level.registryAccess())", policy.unobfuscated() ? "recipe.assemble(input)" : "recipe.assemble(input, level.registryAccess())")
                .replace("${RECIPE_ID}", legacy ? "recipe.getId()" : policy.unobfuscated() ? "holder.id().identifier()" : modern ? "holder.id().location()" : "holder.id()")
                .replace("ItemStack remainder = ${REMAINDER};", policy.unobfuscated()
                        ? "var nativeRemainder = ${REMAINDER}; ItemStack remainder = nativeRemainder == null ? ItemStack.EMPTY : nativeRemainder.create();"
                        : "ItemStack remainder = ${REMAINDER};")
                .replace("${REMAINDER}", !fabric ? (modern ? "slots.get(slot).getCraftingRemainder()" : "slots.get(slot).getCraftingRemainingItem()") : modern ? "slots.get(slot).getItem().getCraftingRemainder()"
                        : "slots.get(slot).getItem().hasCraftingRemainingItem() ? new ItemStack(slots.get(slot).getItem().getCraftingRemainingItem()) : ItemStack.EMPTY");
    }

    private static String legacyEncoder() {
        return """
                    private static String encodeLegacyRecipe(${PREFIX}WorkbenchRecipe recipe) {
                        var object = new com.google.gson.JsonObject();
                        var entries = new com.google.gson.JsonArray();
                        for (var entry : recipe.countedIngredients()) {
                            var value = new com.google.gson.JsonObject();
                            value.add("ingredient", entry.ingredient().toJson());
                            value.addProperty("count", entry.count());
                            entries.add(value);
                        }
                        object.add("ingredients", entries);
                        object.addProperty("result", recipe.result().save(new net.minecraft.nbt.CompoundTag()).toString());
                        return object.toString();
                    }
                """;
    }
}
