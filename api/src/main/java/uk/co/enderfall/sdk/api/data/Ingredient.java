package uk.co.enderfall.sdk.api.data;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

public record Ingredient(ResourceId id, Kind kind) {
    public Ingredient {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
    }

    public static Ingredient item(ResourceId id) {
        return new Ingredient(id, Kind.ITEM);
    }

    public static Ingredient tag(ResourceId id) {
        return new Ingredient(id, Kind.TAG);
    }

    public enum Kind {
        ITEM,
        TAG
    }
}
