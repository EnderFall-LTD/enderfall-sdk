package uk.co.enderfall.sdk.api.ui;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/** A bounded text editor submitted with a declared portable-menu button action. */
public record MenuTextInput(String key, String placeholder, int x, int y, int width, int height,
        int maximumCharacters, int maximumUtf8Bytes, int maximumLines, boolean multiline) {
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");

    public MenuTextInput {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholder, "placeholder");
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid menu text-input key: " + key);
        }
        if (placeholder.length() > 256) {
            throw new IllegalArgumentException("Menu text-input placeholder exceeds 256 characters");
        }
        if (width < 20 || width > 300 || height < 18 || height > 200) {
            throw new IllegalArgumentException("Menu text-input dimensions are outside the supported range");
        }
        if (!multiline && height > 40) {
            throw new IllegalArgumentException("Single-line menu inputs cannot exceed 40 pixels high");
        }
        if (maximumCharacters < 1 || maximumCharacters > 1_024) {
            throw new IllegalArgumentException("Menu text input must allow 1-1024 characters");
        }
        if (maximumUtf8Bytes < 1 || maximumUtf8Bytes > 4_096) {
            throw new IllegalArgumentException("Menu text input must allow 1-4096 UTF-8 bytes");
        }
        if (maximumLines < 1 || maximumLines > 64 || (!multiline && maximumLines != 1)) {
            throw new IllegalArgumentException("Menu text-input line limit is invalid");
        }
    }

    public static MenuTextInput singleLine(String key, String placeholder, int x, int y, int width,
            int maximumCharacters) {
        return new MenuTextInput(key, placeholder, x, y, width, 20, maximumCharacters,
                maximumBytes(maximumCharacters), 1, false);
    }

    public static MenuTextInput multiline(String key, String placeholder, int x, int y, int width,
            int height, int maximumCharacters, int maximumLines) {
        return new MenuTextInput(key, placeholder, x, y, width, height, maximumCharacters,
                maximumBytes(maximumCharacters), maximumLines, true);
    }

    /** Validates untrusted submitted text and returns it unchanged. */
    public String validate(String value) {
        Objects.requireNonNull(value, "value");
        if (value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Menu input " + key + " contains unsupported control characters");
        }
        if (!multiline && value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Menu input " + key + " must be a single line");
        }
        if (value.codePointCount(0, value.length()) > maximumCharacters) {
            throw new IllegalArgumentException("Menu input " + key + " exceeds " + maximumCharacters
                    + " characters");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > maximumUtf8Bytes) {
            throw new IllegalArgumentException("Menu input " + key + " exceeds " + maximumUtf8Bytes
                    + " UTF-8 bytes");
        }
        long lines = value.isEmpty() ? 1L : value.chars().filter(character -> character == '\n').count() + 1L;
        if (lines > maximumLines) {
            throw new IllegalArgumentException("Menu input " + key + " exceeds " + maximumLines + " lines");
        }
        return value;
    }

    private static int maximumBytes(int maximumCharacters) {
        if (maximumCharacters < 1 || maximumCharacters > 1_024) {
            throw new IllegalArgumentException("Menu text input must allow 1-1024 characters");
        }
        return maximumCharacters * 4;
    }
}
