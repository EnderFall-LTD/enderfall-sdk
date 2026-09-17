package uk.co.enderfall.sdk.api.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable, bounded string state synchronized from a menu's server session to its screen. */
public final class MenuState {
    public static final int MAXIMUM_ENTRIES = 32;
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
            if (text.length() > 512) {
                throw new IllegalArgumentException("Menu state value exceeds 512 characters: " + key);
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
