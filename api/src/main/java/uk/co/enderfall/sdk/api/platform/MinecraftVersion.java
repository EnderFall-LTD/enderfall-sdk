package uk.co.enderfall.sdk.api.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A validated Minecraft release identifier such as {@code 1.21.4} or {@code 26.2}. */
public record MinecraftVersion(String value) implements Comparable<MinecraftVersion> {
    // Each segment is length-bounded as well as shape-checked. parts() parses segments as ints, so
    // an unbounded digit run would validate here and then throw NumberFormatException from
    // compareTo, far from the value that caused it.
    private static final java.util.regex.Pattern FORMAT =
            java.util.regex.Pattern.compile("[0-9]{1,9}(?:\\.[0-9]{1,9}){1,3}");

    public MinecraftVersion {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsupported Minecraft version format: " + value);
        }
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        List<Integer> left = parts(value);
        List<Integer> right = parts(other.value);
        int length = Math.max(left.size(), right.size());
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(index < left.size() ? left.get(index) : 0,
                    index < right.size() ? right.get(index) : 0);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static List<Integer> parts(String version) {
        List<Integer> result = new ArrayList<>();
        for (String part : version.split("\\.")) {
            result.add(Integer.parseInt(part));
        }
        return result;
    }

    @Override
    public String toString() {
        return value;
    }
}
