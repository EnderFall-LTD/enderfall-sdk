package uk.co.enderfall.sdk.bridge;

/** Shared SDK machine data-pack loader and processing; only registry ABI names vary. */
final class MachineRecipeSources {
    private MachineRecipeSources() { }
    static String emit(BlockEntityNativePolicy policy) {
        return """
                    private record MachineData(ResourceId id, String revision, uk.co.enderfall.sdk.api.recipe.MachineRecipeSpec spec) { }
                    private static final java.util.Map<net.minecraft.server.packs.resources.ResourceManager, java.util.List<MachineData>> MACHINE_CACHE = new java.util.WeakHashMap<>();
                    static void bindMachine(StoredBlockEntity.Binding owner, ${PREFIX}RecipeBinding recipes,
                            uk.co.enderfall.sdk.api.ui.WorkbenchSpec spec) {
                        if (owner.definition().inventorySlots() != recipes.inputSlots() + 1) throw new IllegalArgumentException("Machine inventory layout mismatch");
                        owner.onMachineTick(entity -> tickMachine(entity, recipes.inputSlots(), spec));
                        owner.enableMachinePorts(recipes.inputSlots());
                    }
                    private static ${ID} machineId(ResourceId id) { return ${ID_PARSE}; }
                    private static java.util.List<MachineData> machineData(ServerLevel level) {
                        var manager = level.getServer().getResourceManager();
                        var cached = MACHINE_CACHE.get(manager);
                        if (cached != null) return cached;
                        var loaded = new ArrayList<MachineData>();
                        var resources = manager.listResources("enderfall_machine", id -> id.getPath().endsWith(".json"));
                        if (resources.size() > 4096) throw new IllegalArgumentException("Too many machine recipes");
                        for (var entry : resources.entrySet().stream().sorted(java.util.Comparator.comparing(e -> e.getKey().toString())).toList()) {
                            try (var stream = entry.getValue().open()) {
                                byte[] bytes = stream.readNBytes(65537);
                                if (bytes.length > 65536) throw new IllegalArgumentException("Machine recipe exceeds 64 KiB");
                                String json = java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                                int depth = 0; boolean quoted = false; boolean escaped = false;
                                for (int i = 0; i < json.length(); i++) {
                                    char c = json.charAt(i);
                                    if (quoted) { if (escaped) escaped = false; else if (c == 92) escaped = true; else if (c == 34) quoted = false; }
                                    else if (c == 34) quoted = true;
                                    else if (c == '{' || c == '[') { if (++depth > 12) throw new IllegalArgumentException("Recipe nesting too deep"); }
                                    else if (c == '}' || c == ']') depth--;
                                }
                                var root = net.minecraft.util.GsonHelper.parse(json);
                                var itemInputs = new ArrayList<uk.co.enderfall.sdk.api.recipe.CountedIngredient>();
                                for (var node : machineArray(root, "item_inputs")) {
                                    var object = node.getAsJsonObject();
                                    boolean tag = object.has("tag");
                                    if (tag == object.has("item")) throw new IllegalArgumentException("Expected item or tag");
                                    var ingredient = new uk.co.enderfall.sdk.api.data.Ingredient(machineResource(object, tag ? "tag" : "item"),
                                            tag ? uk.co.enderfall.sdk.api.data.Ingredient.Kind.TAG : uk.co.enderfall.sdk.api.data.Ingredient.Kind.ITEM);
                                    itemInputs.add(new uk.co.enderfall.sdk.api.recipe.CountedIngredient(ingredient, machineInt(object, "count")));
                                }
                                var fluidInputs = new ArrayList<uk.co.enderfall.sdk.api.recipe.FluidIngredient>();
                                for (var node : machineArray(root, "fluid_inputs")) {
                                    var object = node.getAsJsonObject(); boolean tag = object.has("tag");
                                    if (tag == object.has("fluid")) throw new IllegalArgumentException("Expected fluid or tag");
                                    fluidInputs.add(new uk.co.enderfall.sdk.api.recipe.FluidIngredient(machineResource(object, tag ? "tag" : "fluid"),
                                            tag ? uk.co.enderfall.sdk.api.recipe.FluidIngredient.Kind.TAG : uk.co.enderfall.sdk.api.recipe.FluidIngredient.Kind.FLUID,
                                            object.get("amount").getAsBigDecimal().longValueExact()));
                                }
                                var itemOutputs = new ArrayList<uk.co.enderfall.sdk.api.data.RecipeResult>();
                                for (var node : machineArray(root, "item_outputs")) {
                                    var object = node.getAsJsonObject();
                                    itemOutputs.add(new uk.co.enderfall.sdk.api.data.RecipeResult(machineResource(object, "item"), machineInt(object, "count")));
                                }
                                var fluidOutputs = new ArrayList<uk.co.enderfall.sdk.api.fluid.FluidVolume>();
                                for (var node : machineArray(root, "fluid_outputs")) {
                                    var object = node.getAsJsonObject();
                                    fluidOutputs.add(new uk.co.enderfall.sdk.api.fluid.FluidVolume(machineResource(object, "fluid"), object.get("amount").getAsBigDecimal().longValueExact()));
                                }
                                var spec = new uk.co.enderfall.sdk.api.recipe.MachineRecipeSpec(machineResource(root, "type"), itemInputs, fluidInputs,
                                        itemOutputs, fluidOutputs, machineInt(root, "duration_ticks"));
                                if (itemInputs.isEmpty() || itemInputs.size() > 5 || itemOutputs.size() != 1) throw new IllegalArgumentException("Machine menu supports 1-5 inputs and one item output");
                                for (var output : itemOutputs) if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(machineId(output.item()))) throw new IllegalArgumentException("Unknown output item");
                                for (var output : fluidOutputs) if (!net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(machineId(output.fluid()))) throw new IllegalArgumentException("Unknown output fluid");
                                String revision = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                                loaded.add(new MachineData(ResourceId.parse(entry.getKey().toString()), revision, spec));
                            } catch (Exception invalid) {
                                System.getLogger("enderfall_sdk").log(System.Logger.Level.ERROR, "Rejected machine recipe " + entry.getKey(), invalid);
                            }
                        }
                        var result = java.util.List.copyOf(loaded); MACHINE_CACHE.put(manager, result);
                        System.getLogger("enderfall_sdk").log(System.Logger.Level.INFO, "Loaded " + result.size() + " SDK machine recipes");
                        return result;
                    }
                    private static ResourceId machineResource(com.google.gson.JsonObject object, String key) {
                        String text = object.get(key).getAsString();
                        if (text.length() > 1024) throw new IllegalArgumentException("Recipe ID too long");
                        return ResourceId.parse(text);
                    }
                    private static int machineInt(com.google.gson.JsonObject object, String key) { return object.get(key).getAsBigDecimal().intValueExact(); }
                    private static com.google.gson.JsonArray machineArray(com.google.gson.JsonObject root, String key) {
                        var array = root.getAsJsonArray(key);
                        if (array == null || array.size() > 16) throw new IllegalArgumentException("Invalid recipe section: " + key);
                        return array;
                    }
                    private static java.util.List<uk.co.enderfall.sdk.runtime.blockentity.MachineFluidPlan.Tank> machineTanks(
                            StoredBlockEntity owner, java.util.List<String> names) {
                        return names.stream().map(name -> {
                            var tank = owner.fluidTank(owner.definition().tanks().get(name));
                            return new uk.co.enderfall.sdk.runtime.blockentity.MachineFluidPlan.Tank(tank.capacity(), tank.contents());
                        }).toList();
                    }
                    private static void tickMachine(StoredBlockEntity owner, int inputCount, uk.co.enderfall.sdk.api.ui.WorkbenchSpec binding) {
                        if (!(owner.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) throw new IllegalStateException("Machine requires owning server thread");
                        var slots = new ArrayList<ItemStack>();
                        for (int i = 0; i < owner.inventorySize(); i++) slots.add(owner.stack(i));
                        MachineData match = null;
                        uk.co.enderfall.sdk.runtime.blockentity.MachineFluidPlan.Result fluidPlan = null;
                        for (var candidate : machineData(level)) {
                            var recipe = candidate.spec();
                            if (!recipe.type().equals(binding.recipeType().id()) || recipe.itemInputs().size() != inputCount
                                    || recipe.fluidInputs().size() != binding.fluidInputTanks().size() || recipe.fluidOutputs().size() != binding.fluidOutputTanks().size()) continue;
                            boolean matches = true;
                            for (int i = 0; i < inputCount; i++) {
                                var required = recipe.itemInputs().get(i); var actual = slots.get(i);
                                var id = machineId(required.ingredient().id());
                                if (actual.isEmpty() || actual.getCount() < required.count() || !(required.ingredient().kind() == uk.co.enderfall.sdk.api.data.Ingredient.Kind.TAG
                                        ? actual.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                                        : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(actual.getItem()).equals(id))) { matches = false; break; }
                            }
                            if (!matches) continue;
                            var plan = uk.co.enderfall.sdk.runtime.blockentity.MachineFluidPlan.plan(recipe,
                                    machineTanks(owner, binding.fluidInputTanks()), machineTanks(owner, binding.fluidOutputTanks()),
                                    (tag, fluid) -> net.minecraft.core.registries.BuiltInRegistries.FLUID.${REGISTRY_GET}(machineId(fluid)).defaultFluidState()
                                            .is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, machineId(tag))));
                            if (plan.status() == uk.co.enderfall.sdk.runtime.blockentity.MachineFluidPlan.Status.MISSING_INPUT) continue;
                            match = candidate; fluidPlan = plan; break;
                        }
                        var previous = owner.processingState();
                        if (match == null) {
                            owner.processingStatus(PortableMachineStatus.IDLE);
                            if (!previous.equals(PortableProcessingCycle.State.idle())) owner.commitProcessing(slots, Map.of(), PortableProcessingCycle.State.idle());
                            return;
                        }
                        var recipe = match.spec();
                        var completed = new ArrayList<ItemStack>(); for (var stack : slots) completed.add(stack.copy());
                        var result = recipe.itemOutputs().get(0);
                        var resultStack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.${REGISTRY_GET}(machineId(result.item())), result.count());
                        boolean fits = fluidPlan.status() == uk.co.enderfall.sdk.runtime.blockentity.MachineFluidPlan.Status.READY
                                && !resultStack.isEmpty() && merge(completed, inputCount, resultStack, owner.inventory().getMaxStackSize());
                        boolean outputFits = fits;
                        for (int i = 0; i < inputCount && fits; i++) {
                            int count = recipe.itemInputs().get(i).count();
                            ${REMAINDER}
                            completed.get(i).shrink(count);
                            if (!remainder.isEmpty()) {
                                long amount = (long) remainder.getCount() * count;
                                fits = amount <= Integer.MAX_VALUE && merge(completed, i, remainder.copyWithCount((int) amount), owner.inventory().getMaxStackSize());
                            }
                        }
                        var step = PortableProcessingCycle.advance(previous, new PortableProcessingCycle.Job(match.id(), match.revision(), recipe.durationTicks()), fits);
                        owner.processingStatus(fits ? PortableMachineStatus.PROCESSING : outputFits ? PortableMachineStatus.REMAINDER_BLOCKED : PortableMachineStatus.OUTPUT_BLOCKED);
                        if (step.status() == PortableProcessingCycle.Status.COMPLETE) {
                            var tanks = new java.util.LinkedHashMap<String, java.util.Optional<uk.co.enderfall.sdk.api.fluid.FluidVolume>>();
                            for (int i = 0; i < binding.fluidInputTanks().size(); i++) tanks.put(binding.fluidInputTanks().get(i), fluidPlan.inputs().get(i).contents());
                            for (int i = 0; i < binding.fluidOutputTanks().size(); i++) tanks.put(binding.fluidOutputTanks().get(i), fluidPlan.outputs().get(i).contents());
                            owner.commitMachineProcessing(completed, Map.of(), step.next(), tanks);
                        } else if (!previous.equals(step.next())) owner.commitProcessing(slots, Map.of(), step.next());
                    }
                """.replace("${ID}", policy.unobfuscated() ? "net.minecraft.resources.Identifier" : "net.minecraft.resources.ResourceLocation")
                .replace("${ID_PARSE}", policy.unobfuscated() ? "net.minecraft.resources.Identifier.parse(id.toString())" : "java.util.Objects.requireNonNull(net.minecraft.resources.ResourceLocation.tryParse(id.toString()))")
                .replace("${REGISTRY_GET}", policy.modernRecipes() || (policy.legacy() && !policy.fabric()) ? "getValue" : "get")
                .replace("net.minecraft.core.registries.BuiltInRegistries.ITEM", policy.legacy() && !policy.fabric() ? "net.minecraftforge.registries.ForgeRegistries.ITEMS" : "net.minecraft.core.registries.BuiltInRegistries.ITEM")
                .replace("net.minecraft.core.registries.BuiltInRegistries.FLUID", policy.legacy() && !policy.fabric() ? "net.minecraftforge.registries.ForgeRegistries.FLUIDS" : "net.minecraft.core.registries.BuiltInRegistries.FLUID")
                .replace("${REMAINDER}", remainder(policy));
    }
    private static String remainder(BlockEntityNativePolicy p) {
        String expression = !p.fabric() ? (p.modernRecipes() ? "slots.get(i).getCraftingRemainder()" : "slots.get(i).getCraftingRemainingItem()")
                : p.modernRecipes() ? "slots.get(i).getItem().getCraftingRemainder()"
                : "slots.get(i).getItem().hasCraftingRemainingItem() ? new ItemStack(slots.get(i).getItem().getCraftingRemainingItem()) : ItemStack.EMPTY";
        return p.unobfuscated() ? "var template = " + expression + "; ItemStack remainder = template == null ? ItemStack.EMPTY : template.create();"
                : "ItemStack remainder = " + expression + ";";
    }
}
