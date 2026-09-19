package uk.co.enderfall.sdk.api.ui;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable, bounded string state synchronized from a menu's server session to its screen. */
public final class MenuState {
    public static final int MAXIMUM_ENTRIES = 64;
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][a-z0-9_.-]{0,63})}");
    private final Map<String, String> values;

    private MenuState(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MenuState empty() {
        return new MenuState(Map.of());
    }

    public Map<String, String> values() {
        return values;
    }

    public String value(String key) {
        Objects.requireNonNull(key, "key");
        return values.getOrDefault(key, "");
    }

    /** Substitutes known {@code {key}} placeholders and leaves unknown placeholders empty. */
    public String resolve(String template) {
        Objects.requireNonNull(template, "template");
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(value(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static final class Builder {
        private final Map<String, String> values = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds one bounded page for a selection list. Entry IDs remain server-owned and
         * are recovered only from the active session when a row action is received.
         */
        public Builder selectionPage(MenuSelectionList list, List<MenuSelectionEntry> entries,
                int page, String selectedId) {
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(entries, "entries");
            Objects.requireNonNull(selectedId, "selectedId");
            if (entries.size() > 10_000) throw new IllegalArgumentException("Selection list exceeds 10000 entries");
            int pages = Math.max(1, (entries.size() + list.visibleRows() - 1) / list.visibleRows());
            if (page < 0 || page >= pages) throw new IllegalArgumentException("Selection-list page is invalid");
            Builder candidate = new Builder();
            candidate.values.putAll(values);
            int offset = page * list.visibleRows();
            for (int row = 0; row < list.visibleRows(); row++) {
                int index = offset + row;
                if (index < entries.size()) {
                    MenuSelectionEntry entry = Objects.requireNonNull(entries.get(index), "entry");
                    candidate.value(list.idKey(row), entry.id());
                    candidate.value(list.labelKey(row), entry.label());
                    candidate.value(list.enabledKey(row), entry.enabled());
                    candidate.value(list.iconKey(row), entry.icon().map(icon -> icon.id().toString()).orElse(""));
                    candidate.value(list.countKey(row), entry.count());
                    candidate.value(list.tooltipKey(row), entry.tooltip());
                } else {
                    candidate.value(list.idKey(row), "");
                    candidate.value(list.labelKey(row), "");
                    candidate.value(list.enabledKey(row), false);
                    candidate.value(list.iconKey(row), "");
                    candidate.value(list.countKey(row), 1);
                    candidate.value(list.tooltipKey(row), "");
                }
            }
            candidate.value(list.previousEnabledKey(), page > 0);
            candidate.value(list.nextEnabledKey(), page + 1 < pages);
            candidate.value(list.pageKey(), page);
            candidate.value(list.pagesKey(), pages);
            candidate.value(list.selectedKey(), selectedId);
            values.clear();
            values.putAll(candidate.values);
            return this;
        }

        /**
         * Captures a tank on its owning thread into four display fields: fluid, amount,
         * capacity and percent. Send the resulting state through MenuManager.open/update.
         * This does not install a polling loop. Long fluid IDs are truncated for display only.
         */
        @uk.co.enderfall.sdk.api.annotation.Experimental
        public Builder tank(String prefix, uk.co.enderfall.sdk.api.fluid.FluidTank tank) {
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(tank, "tank");
            long capacity = tank.capacity();
            var contents = tank.contents();
            long amount = contents.map(uk.co.enderfall.sdk.api.fluid.FluidVolume::amount).orElse(0L);
            if (capacity <= 0 || amount > capacity) throw new IllegalArgumentException("Invalid tank display snapshot");
            String fluid = contents.map(volume -> volume.fluid().toString()).orElse("");
            if (fluid.length() > 512) fluid = fluid.substring(0, 509) + "...";
            int percent = java.math.BigInteger.valueOf(amount).multiply(java.math.BigInteger.valueOf(100))
                    .divide(java.math.BigInteger.valueOf(capacity)).intValueExact();
            Builder candidate = new Builder();
            candidate.values.putAll(values);
            candidate.value(prefix + ".fluid", fluid).value(prefix + ".amount", amount)
                    .value(prefix + ".capacity", capacity).value(prefix + ".percent", percent);
            values.clear();
            values.putAll(candidate.values);
            return this;
        }

        public Builder value(String key, Object value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (!KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Invalid menu state key: " + key);
            }
            String text = value.toString();
            if (text.codePointCount(0, text.length()) > 1_024
                    || text.getBytes(StandardCharsets.UTF_8).length > 4_096) {
                throw new IllegalArgumentException(
                        "Menu state value exceeds 1024 characters or 4096 UTF-8 bytes: " + key);
            }
            if (!values.containsKey(key) && values.size() >= MAXIMUM_ENTRIES) {
                throw new IllegalArgumentException("Menu state exceeds " + MAXIMUM_ENTRIES + " entries");
            }
            values.put(key, text);
            return this;
        }

        public MenuState build() {
            return new MenuState(values);
        }
    }
}
