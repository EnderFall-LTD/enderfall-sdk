package uk.co.enderfall.sdk.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResourceIdTest {
    @Test
    void parsesAndOrdersIdentifiers() {
        ResourceId id = ResourceId.parse("example_mod:tools/hammer");
        assertEquals("example_mod", id.namespace());
        assertEquals("tools/hammer", id.path());
        assertEquals("example_mod:tools/hammer", id.toString());
    }

    @Test
    void rejectsUppercaseAndMissingNamespace() {
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("stone"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.of("Example", "stone"));
    }

    @Test
    void rejectsPathsThatCouldEscapeDataGenerationRoots() {
        assertThrows(IllegalArgumentException.class, () -> ResourceId.of("example", "../outside"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.of("example", "inside/../../outside"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.of("example", "/absolute"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.of("example", "double//separator"));
    }
}
