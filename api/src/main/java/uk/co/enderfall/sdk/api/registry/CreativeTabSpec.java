package uk.co.enderfall.sdk.api.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CreativeTabSpec {
    private final String titleTranslationKey;
    private final ItemRef icon;
    private final List<ItemRef> entries;

    private CreativeTabSpec(Builder builder) {
        titleTranslationKey = builder.titleTranslationKey;
        icon = builder.icon;
        entries = List.copyOf(builder.entries);
    }

    public static Builder builder(String titleTranslationKey, ItemRef icon) {
        return new Builder(titleTranslationKey, icon);
    }

    public String titleTranslationKey() {
        return titleTranslationKey;
    }

    public ItemRef icon() {
        return icon;
    }

    public List<ItemRef> entries() {
        return entries;
    }

    public static final class Builder {
        private final String titleTranslationKey;
        private final ItemRef icon;
        private final List<ItemRef> entries = new ArrayList<>();

        private Builder(String titleTranslationKey, ItemRef icon) {
            this.titleTranslationKey = Objects.requireNonNull(titleTranslationKey, "titleTranslationKey");
            this.icon = Objects.requireNonNull(icon, "icon");
        }

        public Builder entry(ItemRef item) {
            entries.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        public CreativeTabSpec build() {
            return new CreativeTabSpec(this);
        }
    }
}
