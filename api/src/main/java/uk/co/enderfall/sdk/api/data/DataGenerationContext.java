package uk.co.enderfall.sdk.api.data;

import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeSpec;

public interface DataGenerationContext {
    /** Emits one model variant per complete state. Does not register native state properties. */
    default void blockStates(ResourceId block,
            uk.co.enderfall.sdk.api.block.BlockStateDefinition definition,
            java.util.function.Function<uk.co.enderfall.sdk.api.block.PortableBlockState, ResourceId> model) {
        throw new UnsupportedOperationException("General blockstate data generation unavailable");
    }
    /** Six variants for a separately authored north-facing model. */
    default void sixWayBlockState(ResourceId block, ResourceId model) {
        throw new UnsupportedOperationException("Six-way blockstate data generation unavailable");
    }
    /** Four facing variants for a separately authored north-facing model, e.g. mod:block/table. */
    default void horizontalBlockState(ResourceId block, ResourceId model) {
        throw new UnsupportedOperationException("Horizontal blockstate data generation unavailable");
    }
    /** Three variants for a separately authored vertical Y-axis model, e.g. a log or pillar. */
    default void axisBlockState(ResourceId block, ResourceId model) {
        throw new UnsupportedOperationException("Axis blockstate data generation unavailable");
    }
    default void machineRecipe(ResourceId id, uk.co.enderfall.sdk.api.recipe.MachineRecipeSpec recipe) {
        throw new UnsupportedOperationException("Machine data generation unavailable");
    }
    void shapedRecipe(ResourceId id, ShapedRecipeSpec recipe);

    void shapelessRecipe(ResourceId id, ShapelessRecipeSpec recipe);

    void workbenchRecipe(ResourceId id, WorkbenchRecipeSpec recipe);

    void itemModel(ResourceId item, ModelSpec model);

    void blockModel(ResourceId block, ModelSpec model);

    void selfDrop(ResourceId block);

    void tag(ResourceId id, TagSpec tag);

    void translation(String locale, String key, String value);
}
