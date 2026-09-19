package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MenuAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared slot-container flow for legacy direct recipes and 26.2 template-result recipes. */
final class ContainerWorkbenchMenuEmitter {
    private ContainerWorkbenchMenuEmitter() { }

    private record Plan(boolean fabric, boolean legacy, boolean persistent) {
        Plan(boolean fabric, boolean legacy) { this(fabric, legacy, false); }
        boolean verbose() { return fabric == legacy; }
        boolean compact() { return fabric && !legacy; }
        String canonicalPrefix() { return fabric ? "Fabric" : "NeoForge"; }
        String canonicalRoot() { return "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/"; }
        String root() { return legacy && !fabric ? "uk/co/enderfall/sdk/runtime/forge/v1_20_1/" : canonicalRoot(); }
        String prefix() { return fabric ? legacy ? "Fabric1201" : "Fabric" : legacy ? "LegacyForge" : "NeoForge26"; }
        String menu() { return prefix() + (persistent ? "PersistentWorkbenchMenu" : "WorkbenchMenu"); }
        String recipe() { return prefix() + "WorkbenchRecipe"; }
        String binding() { return prefix() + "WorkbenchBinding"; }
        String input() { return prefix() + "WorkbenchInput"; }
        String handle() { return fabric ? "" : ".get()"; }
        String holder() { return legacy ? recipe() : "RecipeHolder<" + recipe() + ">"; }
        String selected() { return legacy ? "recipe" : "holder.value()"; }
    }

    static String persistentPreview(boolean fabric, boolean legacy) {
        String source = render(new Plan(fabric, legacy, true));
        return legacy ? source : BlockEntity26Sources.names(source);
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed menu target " + target.id());
        if (target.menuAbi() != MenuAbi.V1_20_1 && target.menuAbi() != MenuAbi.V26_2) return List.of();
        Plan p = new Plan(target.loaderAbi() == LoaderAbi.FABRIC, target.menuAbi() == MenuAbi.V1_20_1);
        String canonical = p.canonicalRoot() + p.canonicalPrefix();
        String menu = canonical + "WorkbenchMenu.java";
        if (!paths.contains(menu)) return List.of();
        var required = new java.util.HashSet<>(Set.of(menu, canonical + "WorkbenchBinding.java",
                canonical + "RecipeBinding.java", canonical + "WorkbenchRecipe.java"));
        if (!p.legacy()) required.add(canonical + "WorkbenchInput.java");
        if (!paths.containsAll(required)) throw new BridgeGenerationException(target.id() + " requires complete workbench menu dependencies");
        String filename = p.compact() ? "Fabric26WorkbenchMenu.java" : p.menu() + ".java";
        return List.of(new RuntimeSource(menu, p.root() + filename, render(p).getBytes(StandardCharsets.UTF_8)));
    }

    private static String render(Plan p) {
        Code c = new Code(p);
        header(c, p);
        c.line(0, "final class " + p.menu() + " extends AbstractContainerMenu {");
        c.line(4, "private final " + p.binding() + " binding;");
        c.line(4, "private final Inventory playerInventory;");
        c.line(4, "private final SimpleContainer inputs;");
        c.line(4, "private final ResultContainer result = new ResultContainer();");
        c.line(4, "private final SimpleContainer recipeChoices;");
        c.line(4, "private List<" + p.holder() + "> matchingRecipes = List.of();");
        c.line(4, "private final DataSlot selectedRecipeIndex = DataSlot.standalone();");
        c.line(4, "private final DataSlot recipePage = DataSlot.standalone();");
        c.line(4, "private final DataSlot recipePages = DataSlot.standalone();");
        c.line(4, "private final DataSlot[] requiredCounts;");
        c.line(4, "private " + p.holder() + " selectedRecipe;");
        if (p.persistent()) c.line(4, "private final Runnable inputListener; private uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity owner;");
        c.blank();
        if (p.fabric() && p.legacy()) {
            c.line(4, p.menu() + "(MenuType<" + (p.persistent() ? "?" : p.menu()) + "> type, int containerId,");
            c.line(27, "Inventory playerInventory, " + p.binding() + " binding" + ownerArgument(p) + ") {");
        } else c.line(4, p.menu() + "(int containerId, Inventory playerInventory, " + p.binding() + " binding" + ownerArgument(p) + ") {");
        c.line(8, "super(" + (p.fabric() && p.legacy() ? "type" : "binding.menuType()" + p.handle()) + ", containerId);");
        c.line(8, "this.binding = binding;"); c.line(8, "this.playerInventory = playerInventory;");
        c.line(8, "recipeChoices = new SimpleContainer(binding.definition().spec().recipeBrowserEntries());");
        c.line(8, "requiredCounts = new DataSlot[binding.recipes().inputSlots()];");
        c.line(8, "addDataSlot(selectedRecipeIndex); addDataSlot(recipePage); addDataSlot(recipePages);");
        c.line(8, "for (int slot = 0; slot < requiredCounts.length; slot++) {");
        c.line(12, "requiredCounts[slot] = DataSlot.standalone(); addDataSlot(requiredCounts[slot]);");
        c.line(8, "}");
        if (p.persistent()) {
            c.line(8, "inputs = owner.inventory();");
            c.line(8, "if (inputs.getContainerSize() != binding.recipes().inputSlots() || !inputs.stillValid(playerInventory.player)) throw new IllegalArgumentException(\"Invalid persistent menu owner\");");
            c.line(8, "this.owner = owner; inputListener = () -> slotsChanged(inputs);");
            c.line(8, "owner.addInventoryListener(inputListener);");
        } else {
        c.line(8, "inputs = new SimpleContainer(binding.recipes().inputSlots()) {");
        c.line(12, "@Override"); c.line(12, "public void setChanged() {");
        c.line(16, "super.setChanged();");
        c.line(16, "if (!playerInventory.player.level().isClientSide()) {");
        c.line(20, p.menu() + ".this.slotsChanged(this);");
        c.line(16, "}"); c.line(12, "}"); c.line(8, "};");
        }
        if (p.verbose()) c.blank();
        c.line(8, "int inputStart = 44 - ((binding.recipes().inputSlots() - 1) * 9);");
        c.line(8, "for (int slot = 0; slot < binding.recipes().inputSlots(); slot++) {");
        c.line(12, "addSlot(new Slot(inputs, slot, inputStart + slot * 18, 35));"); c.line(8, "}");
        c.line(8, "addSlot(new Slot(result, 0, 124, 35) {");
        c.line(12, "@Override public boolean mayPlace(ItemStack stack) { return false; }");
        if (p.verbose()) c.blank();
        if (p.compact()) c.line(12, "@Override public void onTake(Player player, ItemStack stack) { completeCraft(player); super.onTake(player, stack); }");
        else {
            c.override(12, "public void onTake(Player player, ItemStack stack) {", p.verbose());
            c.line(16, "completeCraft(player" + (p.verbose() ? ", stack" : "") + ");");
            c.line(16, "super.onTake(player, stack);"); c.line(12, "}");
        }
        c.line(8, "});");
        c.line(8, "for (int choice = 0; choice < recipeChoices.getContainerSize(); choice++) {");
        c.line(12, "addSlot(new Slot(recipeChoices, choice, 44 + choice * 18, 59) {");
        c.line(16, "@Override public boolean mayPlace(ItemStack stack) { return false; }");
        c.line(16, "@Override public boolean mayPickup(Player player) { return false; }");
        c.line(12, "});");
        c.line(8, "}");
        c.line(8, "addPlayerInventory(playerInventory);"); c.line(8, "updateRecipes();"); c.line(4, "}"); c.gap();
        accessors(c, p);
        quickMove(c, p);
        c.gap(); c.override(4, "public void removed(Player player) {", !p.compact());
        c.line(8, "super.removed(player);");
        if (p.persistent()) c.line(8, "owner.removeInventoryListener(inputListener);");
        else c.guard(8, "!player.level().isClientSide()", "clearContainer(player, inputs);", p.verbose());
        c.line(4, "}"); c.gap();
        if (!p.legacy()) {
            c.line(4, "private " + p.input() + " recipeInput() {");
            c.line(8, "return new " + p.input() + "(inputs.getItems().stream().map(ItemStack::copy).toList());");
            c.line(4, "}"); c.gap();
        }
        updateRecipes(c, p); c.gap(); updateResult(c, p); c.gap(); browserActions(c, p); c.gap(); craft(c, p); c.gap();
        c.line(4, "private void addPlayerInventory(Inventory inventory) {");
        c.line(8, "for (int row = 0; row < 3; row++) {"); c.line(12, "for (int column = 0; column < 9; column++) {");
        c.line(16, "addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));");
        c.line(12, "}"); c.line(8, "}");
        if (p.compact()) c.line(8, "for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));");
        else { c.line(8, "for (int column = 0; column < 9; column++) {"); c.line(12, "addSlot(new Slot(inventory, column, 8 + column * 18, 142));"); c.line(8, "}"); }
        c.line(4, "}");
        if (p.persistent()) c.out.append(WorkbenchMenuEmitter.persistentOpening().replace("${PREFIX}", p.prefix())
                .replace("${CONSTRUCTOR_TYPE}", p.fabric() && p.legacy() ? "binding.menuType(), " : ""));
        c.line(0, "}");
        return c.out.toString();
    }

    private static String ownerArgument(Plan p) { return p.persistent() ? ", uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity owner" : ""; }
    private static String validity(Plan p) { return p.persistent() ? "inputs.stillValid(player)" : "true"; }

    private static void accessors(Code c, Plan p) {
        if (p.verbose()) {
            c.line(4, "PortableWorkbenchDefinition definition() {"); c.line(8, "return binding.definition();"); c.line(4, "}"); c.blank();
            c.line(4, "int machineSlots() {"); c.line(8, "return binding.recipes().inputSlots() + 1 + recipeChoices.getContainerSize();"); c.line(4, "}"); c.blank();
        } else {
            c.line(4, "PortableWorkbenchDefinition definition() { return binding.definition(); }");
            c.line(4, "int machineSlots() { return binding.recipes().inputSlots() + 1 + recipeChoices.getContainerSize(); }");
            if (!p.compact()) c.blank();
            c.line(4, "@Override public boolean stillValid(Player player) { return " + validity(p) + "; }");
            if (!p.compact()) c.blank();
        }
        c.line(4, "int recipeChoiceStart() { return binding.recipes().inputSlots() + 1; }");
        c.line(4, "int recipeChoiceCount() { return recipeChoices.getContainerSize(); }");
        c.line(4, "int recipePage() { return recipePage.get(); }");
        c.line(4, "int recipePages() { return recipePages.get(); }");
        c.line(4, "int selectedRecipeIndex() { return selectedRecipeIndex.get(); }");
        c.line(4, "int requiredCount(int slot) { return slot >= 0 && slot < requiredCounts.length ? requiredCounts[slot].get() : 0; }");
        c.blank();
        if (p.compact()) c.line(4, "@Override public void slotsChanged(Container container) { super.slotsChanged(container); updateRecipes(); }");
        else {
            c.override(4, "public void slotsChanged(Container container) {", true);
            c.line(8, "super.slotsChanged(container);"); c.line(8, "updateRecipes();"); c.line(4, "}");
        }
        c.blank();
        if (p.verbose()) {
            if (p.legacy()) { c.override(4, "public boolean stillValid(Player player) {", true); c.line(8, "return " + validity(p) + ";"); c.line(4, "}"); }
            else c.line(4, "@Override public boolean stillValid(Player player) { return " + validity(p) + "; }");
            c.blank();
        }
    }

    private static void quickMove(Code c, Plan p) {
        c.override(4, "public ItemStack quickMoveStack(Player player, int index) {", !p.compact());
        c.line(8, "ItemStack original = ItemStack.EMPTY;"); c.line(8, "Slot slot = slots.get(index);");
        c.line(8, "if (slot != null && slot.hasItem()) {");
        c.line(12, "ItemStack moving = slot.getItem();"); c.line(12, "original = moving.copy();");
        c.line(12, "int machineSlots = machineSlots();");
        c.line(12, "if (index >= recipeChoiceStart() && index < machineSlots) return ItemStack.EMPTY;");
        c.line(12, "if (index < machineSlots) {");
        c.guard(16, "!moveItemStackTo(moving, machineSlots, slots.size(), true)", "return ItemStack.EMPTY;", p.verbose());
        if (p.compact()) c.line(12, "} else if (!moveItemStackTo(moving, 0, binding.recipes().inputSlots(), false)) return ItemStack.EMPTY;");
        else { c.line(12, "} else if (!moveItemStackTo(moving, 0, binding.recipes().inputSlots(), false)) {"); c.line(16, "return ItemStack.EMPTY;"); c.line(12, "}"); }
        if (p.verbose()) {
            c.line(12, "if (moving.isEmpty()) {"); c.line(16, "slot.set(ItemStack.EMPTY);"); c.line(12, "} else {"); c.line(16, "slot.setChanged();"); c.line(12, "}");
        } else c.line(12, "if (moving.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();");
        c.guard(12, "moving.getCount() == original.getCount()", "return ItemStack.EMPTY;", p.verbose());
        c.line(12, "slot.onTake(player, moving);"); c.line(8, "}"); c.line(8, "return original;"); c.line(4, "}");
    }

    private static void updateRecipes(Code c, Plan p) {
        String existingId = p.legacy() ? "selectedRecipe.getId().toString()"
                : "selectedRecipe.id().identifier().toString()";
        String candidateId = p.legacy() ? "matchingRecipes.get(index).getId().toString()"
                : "matchingRecipes.get(index).id().identifier().toString()";
        c.line(4, "private void updateRecipes() {");
        c.line(8, "String previousId = selectedRecipe == null ? \"\" : " + existingId + ";");
        c.line(8, "selectedRecipe = null; matchingRecipes = List.of();");
        c.line(8, "selectedRecipeIndex.set(-1); recipePage.set(0); recipePages.set(1);");
        c.line(8, "for (int row = 0; row < recipeChoices.getContainerSize(); row++) recipeChoices.setItem(row, ItemStack.EMPTY);");
        c.line(8, "result.setItem(0, ItemStack.EMPTY);");
        c.line(8, "Player player = playerInventory.player;");
        c.guard(8, p.legacy() ? "player.level().isClientSide()" : "!(player instanceof ServerPlayer serverPlayer)", "return;", p.verbose());
        if (p.legacy()) {
            c.line(8, "matchingRecipes = player.level().getRecipeManager().getAllRecipesFor(binding.recipes().type()" + p.handle() + ").stream()");
            c.line(16, ".filter(recipe -> recipe.matches(inputs, player.level()))");
            c.line(16, ".sorted(Comparator.comparing(recipe -> recipe.getId().toString())).limit(256).toList();");
        } else {
            c.line(8, p.input() + " input = recipeInput();");
            c.line(8, "List<" + p.holder() + "> discovered = new ArrayList<>();");
            c.line(8, "for (RecipeHolder<?> candidate : serverPlayer.level().recipeAccess().getRecipes()) {");
            c.line(12, "if (candidate.value() instanceof " + p.recipe() + " recipe");
            c.line(20, "&& recipe.getType() == binding.recipes().type()" + p.handle());
            c.line(20, "&& recipe.matches(input, serverPlayer.level())) {");
            c.line(16, "@SuppressWarnings(\"unchecked\")");
            c.line(16, p.holder() + " typed = (" + p.holder() + ") (RecipeHolder<?>) candidate;");
            c.line(16, "discovered.add(typed);"); c.line(12, "}"); c.line(8, "}");
            c.line(8, "discovered.sort(Comparator.comparing(holder -> holder.id().identifier().toString()));");
            c.line(8, "matchingRecipes = List.copyOf(discovered.subList(0, Math.min(256, discovered.size())));");
        }
        c.line(8, "if (!matchingRecipes.isEmpty()) {");
        c.line(12, "int selected = 0;");
        c.line(12, "if (!previousId.isBlank()) for (int index = 0; index < matchingRecipes.size(); index++) {");
        c.line(16, "if (" + candidateId + ".equals(previousId)) { selected = index; break; }");
        c.line(12, "}");
        c.line(12, "selectedRecipeIndex.set(selected);");
        c.line(12, "int visible = Math.max(1, recipeChoices.getContainerSize());");
        c.line(12, "recipePages.set(Math.max(1, (matchingRecipes.size() + visible - 1) / visible));");
        c.line(12, "recipePage.set(selected / visible);");
        c.line(8, "}");
        c.line(8, "updateRecipeChoices(); updateResult();");
        c.line(4, "}");

        c.gap(); c.line(4, "private void updateRecipeChoices() {");
        c.line(8, "for (int row = 0; row < recipeChoices.getContainerSize(); row++) {");
        c.line(12, "int index = recipePage.get() * recipeChoices.getContainerSize() + row;");
        String result = p.legacy() ? "matchingRecipes.get(index).result().copy()"
                : "matchingRecipes.get(index).value().result().copy()";
        c.line(12, "recipeChoices.setItem(row, index < matchingRecipes.size() ? " + result + " : ItemStack.EMPTY);");
        c.line(8, "}"); c.line(8, "broadcastChanges();"); c.line(4, "}");
    }

    private static void updateResult(Code c, Plan p) {
        c.line(4, "private void updateResult() {");
        c.line(8, "selectedRecipe = null; result.setItem(0, ItemStack.EMPTY);");
        c.line(8, "for (DataSlot count : requiredCounts) count.set(0);");
        c.line(8, "Player player = playerInventory.player;");
        c.guard(8, p.legacy() ? "player.level().isClientSide()" : "!(player instanceof ServerPlayer serverPlayer)", "return;", p.verbose());
        c.line(8, "int selected = selectedRecipeIndex.get();");
        c.line(8, "if (selected < 0 || selected >= matchingRecipes.size()) { broadcastChanges(); return; }");
        c.line(8, "selectedRecipe = matchingRecipes.get(selected);");
        if (!p.legacy()) c.line(8, p.input() + " input = recipeInput();");
        String matches = p.legacy() ? "selectedRecipe.matches(inputs, player.level())"
                : "selectedRecipe.value().matches(input, serverPlayer.level())";
        c.line(8, "if (!" + matches + ") { updateRecipes(); return; }");
        c.line(8, "result.setRecipeUsed(selectedRecipe);");
        c.line(8, "result.setItem(0, " + (p.legacy()
                ? "selectedRecipe.assemble(inputs, player.level().registryAccess())"
                : "selectedRecipe.value().assemble(input)") + ");");
        String ingredients = p.legacy() ? "selectedRecipe.countedIngredients()"
                : "selectedRecipe.value().countedIngredients()";
        c.line(8, "var ingredients = " + ingredients + ";");
        c.line(8, "for (int slot = 0; slot < requiredCounts.length; slot++) requiredCounts[slot].set(ingredients.get(slot).count());");
        c.line(8, "broadcastChanges();"); c.line(4, "}");
    }

    private static void browserActions(Code c, Plan p) {
        c.override(4, "public boolean clickMenuButton(Player player, int id) {", !p.compact());
        c.line(8, "int visible = recipeChoices.getContainerSize();");
        c.line(8, "if (id >= 0 && id < visible) {");
        c.line(12, "int selected = recipePage.get() * visible + id;");
        c.line(12, "if (selected >= matchingRecipes.size()) return false;");
        c.line(12, "selectedRecipeIndex.set(selected); updateResult(); return true;");
        c.line(8, "}");
        c.line(8, "if (id == 100 && recipePage.get() > 0) { recipePage.set(recipePage.get() - 1); updateRecipeChoices(); return true; }");
        c.line(8, "if (id == 101 && recipePage.get() + 1 < recipePages.get()) { recipePage.set(recipePage.get() + 1); updateRecipeChoices(); return true; }");
        c.line(8, "return false;"); c.line(4, "}");
    }

    private static void craft(Code c, Plan p) {
        c.line(4, "private void completeCraft(Player player" + (p.verbose() ? ", ItemStack crafted" : "") + ") {");
        c.line(8, p.holder() + (p.legacy() ? " recipe" : " holder") + " = selectedRecipe;");
        if (!p.legacy()) c.line(8, p.input() + " input = recipeInput();");
        String guard = (p.legacy() ? "recipe" : "holder") + " == null || !" + p.selected() + ".matches(" + (p.legacy() ? "inputs" : "input") + ", player.level())";
        if (p.compact()) c.line(8, "if (" + guard + ") { updateResult(); return; }");
        else { c.line(8, "if (" + guard + ") {"); c.line(12, "updateResult();"); c.line(12, "return;"); c.line(8, "}"); }
        boolean directCounts = p.legacy() && p.fabric();
        if (!directCounts) c.line(8, "List<" + p.recipe() + ".Entry> ingredients = " + p.selected() + ".countedIngredients();");
        String entries = directCounts ? "recipe.countedIngredients()" : "ingredients";
        String remove = "inputs.removeItem(slot, " + entries + ".get(slot).count());";
        if (p.compact()) c.line(8, "for (int slot = 0; slot < ingredients.size(); slot++) " + remove);
        else { c.line(8, "for (int slot = 0; slot < " + entries + ".size(); slot++) {"); c.line(12, remove); c.line(8, "}"); }
        c.line(8, "if (player instanceof ServerPlayer serverPlayer) {");
        c.line(12, directCounts ? "ResourceId recipeId = ResourceId.of(recipe.getId().getNamespace(), recipe.getId().getPath());"
                : "var recipeId = " + (p.legacy() ? "recipe.getId()" : "holder.id().identifier()") + ";");
        c.line(12, "ItemStack completed = " + p.selected() + ".result();");
        c.line(12, "var resultId = " + (p.legacy() && !p.fabric() ? "ForgeRegistries.ITEMS" : "BuiltInRegistries.ITEM") + ".getKey(completed.getItem());");
        c.line(12, "binding.definition().craftListener().accept(new PortableWorkbenchCraft(serverPlayer.getUUID()," + (directCounts ? " recipeId," : ""));
        if (!directCounts) c.line(20, "ResourceId.of(recipeId.getNamespace(), recipeId.getPath()),");
        c.line(20, "ResourceId.of(resultId.getNamespace(), resultId.getPath()), completed.getCount()));");
        c.line(8, "}"); c.line(8, "updateResult();"); c.line(4, "}");
    }

    private static void header(Code c, Plan p) {
        var imports = new TreeSet<>(List.of("java.util.ArrayList", "java.util.Comparator", "java.util.List",
                "net.minecraft.server.level.ServerPlayer", "net.minecraft.world.Container",
                "net.minecraft.world.SimpleContainer", "net.minecraft.world.entity.player.Inventory", "net.minecraft.world.entity.player.Player",
                "net.minecraft.world.inventory.AbstractContainerMenu", "net.minecraft.world.inventory.DataSlot", "net.minecraft.world.inventory.ResultContainer",
                "net.minecraft.world.inventory.Slot", "net.minecraft.world.item.ItemStack", "uk.co.enderfall.sdk.api.ResourceId",
                "uk.co.enderfall.sdk.runtime.PortableWorkbenchCraft", "uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition"));
        if (p.legacy() && !p.fabric()) imports.add("net.minecraftforge.registries.ForgeRegistries");
        else imports.add("net.minecraft.core.registries.BuiltInRegistries");
        if (p.persistent()) imports.add("net.minecraft.server.level.ServerLevel");
        if (!p.legacy()) imports.add("net.minecraft.world.item.crafting.RecipeHolder");
        if (p.legacy() && p.fabric()) imports.add("net.minecraft.world.inventory.MenuType");
        c.line(0, "package " + p.root().substring(0, p.root().length() - 1).replace('/', '.') + ";"); c.blank();
        imports.forEach(name -> c.line(0, "import " + name + ";")); c.blank();
        String description = p.legacy() ? p.fabric() ? "Real 1.20.1 container menu with synchronized input, output, inventory, and shift-click slots."
                : "Real Forge-family 1.20.1 container with vanilla inventory synchronization."
                : p.fabric() ? "Minecraft 26.2 replacement for Fabric's real portable workbench menu."
                : "Real 26.2 container menu using vanilla slot synchronization and server-side recipe matching.";
        c.line(0, "/** " + description + " */");
    }

    /** Native formatting is retained to keep existing source/class golden checks independent. */
    private static final class Code {
        private final Plan plan;
        private final StringBuilder out = new StringBuilder();
        Code(Plan plan) { this.plan = plan; }
        void line(int indent, String text) { out.append(" ".repeat(indent)).append(text).append('\n'); }
        void blank() { out.append('\n'); }
        void gap() { if (!plan.compact()) blank(); }
        void override(int indent, String method, boolean separate) {
            if (separate) line(indent, "@Override");
            line(indent, (separate ? "" : "@Override ") + method);
        }
        void guard(int indent, String condition, String statement, boolean multiline) {
            if (!multiline) line(indent, "if (" + condition + ") " + statement);
            else { line(indent, "if (" + condition + ") {"); line(indent + 4, statement); line(indent, "}"); }
        }
    }
}
