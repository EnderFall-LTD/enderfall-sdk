package uk.co.enderfall.sdk.api.data;

import uk.co.enderfall.sdk.api.ResourceId;

public interface DataGenerationContext {
    void shapedRecipe(ResourceId id, ShapedRecipeSpec recipe);

    void shapelessRecipe(ResourceId id, ShapelessRecipeSpec recipe);

    void itemModel(ResourceId item, ModelSpec model);

    void blockModel(ResourceId block, ModelSpec model);

    void selfDrop(ResourceId block);

    void tag(ResourceId id, TagSpec tag);

    void translation(String locale, String key, String value);
}
