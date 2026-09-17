package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.RecipeAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Emits recipe/menu binding declarations from registry-handle semantics, not source patches. */
final class WorkbenchBindingEmitter {
    private static final String ROOT = "uk/co/enderfall/sdk/runtime/";

    private WorkbenchBindingEmitter() { }

    private enum Handle {
        DIRECT("", null), SUPPLIER("Supplier", "java.util.function.Supplier"),
        REGISTRY_OBJECT("RegistryObject", "net.minecraftforge.registries.RegistryObject");

        private final String wrapper;
        private final String importedType;
        Handle(String wrapper, String importedType) {
            this.wrapper = wrapper;
            this.importedType = importedType;
        }
        String wrap(String type) { return this == DIRECT ? type : wrapper + '<' + type + '>'; }
    }

    private record Field(String type, String name) {
        String declaration() { return type + ' ' + name; }
    }

    // Layout preserves the existing source/class golden files during the migration.
    // Registry semantics and field declarations are independent of this formatting policy.
    private record Layout(boolean firstInline, int indent, boolean joinLast) { }

    private record Plan(String canonicalPackage, String outputPackage, String canonicalPrefix,
                        String prefix, Handle handle, boolean combined, boolean identifier,
                        Layout recipeLayout, Layout menuLayout) {
        static Plan forTarget(TargetSpec target) throws BridgeGenerationException {
            if (!TargetCatalog.standard().require(target.id()).equals(target)) {
                throw new BridgeGenerationException("Unreviewed workbench binding target " + target.id());
            }
            boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
            boolean legacy = target.recipeAbi() == RecipeAbi.LEGACY_METHODS;
            boolean identifier = !fabric && target.recipeAbi() == RecipeAbi.SERIALIZER_RECORD_26;
            String canonicalPackage = ROOT + (fabric ? "fabric" : "neoforge") + "/v1_21_4/";
            String canonicalPrefix = fabric ? "Fabric" : "NeoForge";
            Handle handle = fabric ? Handle.DIRECT
                    : legacy ? Handle.REGISTRY_OBJECT : Handle.SUPPLIER;
            String prefix = fabric && legacy ? "Fabric1201"
                    : legacy ? "LegacyForge" : identifier ? "NeoForge26" : canonicalPrefix;
            boolean combined = fabric && legacy || identifier;
            return new Plan(canonicalPackage, !fabric && legacy ? ROOT + "forge/v1_20_1/" : canonicalPackage,
                    canonicalPrefix, prefix, handle, combined, identifier,
                    new Layout(fabric || identifier, combined ? 31 : fabric ? 27 : 8, fabric && legacy),
                    new Layout(fabric || identifier, combined ? 34 : fabric ? 30 : 8, false));
        }
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> canonicalPaths)
            throws BridgeGenerationException {
        Plan plan = Plan.forTarget(target);
        String prefix = plan.canonicalPackage() + plan.canonicalPrefix();
        boolean recipe = canonicalPaths.contains(prefix + "RecipeBinding.java");
        boolean menu = canonicalPaths.contains(prefix + "WorkbenchBinding.java");
        // Until feature declarations replace the canonical manifest, these two entries
        // select the workbench feature. Their source text does not drive emission.
        if (!recipe && !menu) return List.of();
        if (!recipe || !menu) {
            throw new BridgeGenerationException(target.id() + " requires both recipe and workbench binding declarations");
        }
        return emit(target);
    }

    static List<RuntimeSource> emit(TargetSpec target) throws BridgeGenerationException {
        Plan plan = Plan.forTarget(target);
        List<Field> recipe = new ArrayList<>();
        if (plan.identifier()) recipe.add(new Field("Identifier", "id"));
        recipe.add(new Field(plan.handle().wrap("RecipeType<" + plan.prefix() + "WorkbenchRecipe>"), "type"));
        recipe.add(new Field(plan.handle().wrap("RecipeSerializer<" + plan.prefix() + "WorkbenchRecipe>"), "serializer"));
        recipe.add(new Field("int", "inputSlots"));
        List<Field> menu = List.of(new Field("PortableWorkbenchDefinition", "definition"),
                new Field(plan.prefix() + "RecipeBinding", "recipes"),
                new Field(plan.handle().wrap("MenuType<" + plan.prefix() + "WorkbenchMenu>"), "menuType"));
        String recipeRecord = record(plan.prefix() + "RecipeBinding", recipe, plan.recipeLayout());
        String menuRecord = record(plan.prefix() + "WorkbenchBinding", menu, plan.menuLayout());
        if (plan.combined()) {
            return List.of(source(plan, "WorkbenchBinding", header(plan, true, true)
                    + recipeRecord + '\n' + menuRecord));
        }
        return List.of(source(plan, "RecipeBinding", header(plan, true, false) + recipeRecord),
                source(plan, "WorkbenchBinding", header(plan, false, true) + menuRecord));
    }

    private static RuntimeSource source(Plan plan, String kind, String content) {
        return new RuntimeSource(plan.canonicalPackage() + plan.canonicalPrefix() + kind + ".java",
                plan.outputPackage() + plan.prefix() + kind + ".java", content.getBytes(StandardCharsets.UTF_8));
    }

    private static String header(Plan plan, boolean recipe, boolean menu) {
        var imports = new TreeSet<String>();
        if (plan.handle().importedType != null) imports.add(plan.handle().importedType);
        if (plan.identifier()) imports.add("net.minecraft.resources.Identifier");
        if (recipe) {
            imports.add("net.minecraft.world.item.crafting.RecipeSerializer");
            imports.add("net.minecraft.world.item.crafting.RecipeType");
        }
        if (menu) {
            imports.add("net.minecraft.world.inventory.MenuType");
            imports.add("uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition");
        }
        StringBuilder source = new StringBuilder("package ")
                .append(plan.outputPackage().substring(0, plan.outputPackage().length() - 1).replace('/', '.'))
                .append(";\n\n");
        imports.forEach(name -> source.append("import ").append(name).append(";\n"));
        return source.append('\n').toString();
    }

    private static String record(String name, List<Field> fields, Layout layout) {
        StringBuilder source = new StringBuilder("record ").append(name).append('(');
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) source.append(',');
            boolean joined = layout.joinLast() && index == fields.size() - 1;
            if (joined) source.append(' ');
            else if (index > 0 || !layout.firstInline()) source.append('\n').append(" ".repeat(layout.indent()));
            source.append(fields.get(index).declaration());
        }
        return source.append(") {\n}\n").toString();
    }
}
