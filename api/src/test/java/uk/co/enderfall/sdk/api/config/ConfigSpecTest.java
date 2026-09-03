package uk.co.enderfall.sdk.api.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigSpecTest {
    @Test
    void rejectsTraversalDuplicateKeysAndOversizedDefaults() {
        ConfigSpec.Builder builder = ConfigSpec.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder.string("../outside", "value", "invalid path"));
        builder.booleanValue("feature.enabled", true, "toggle");
        assertThrows(IllegalArgumentException.class,
                () -> builder.booleanValue("feature.enabled", false, "duplicate"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.list("names", ConfigType.STRING, List.of("one", "two"), 1, "bounded"));
    }
}
