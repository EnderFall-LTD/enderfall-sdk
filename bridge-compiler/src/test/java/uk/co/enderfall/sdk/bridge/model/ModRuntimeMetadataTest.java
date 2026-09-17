package uk.co.enderfall.sdk.bridge.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModRuntimeMetadataTest {
    @Test
    void exposesTheReviewedRuntimeIdentity() {
        ModRuntimeMetadata metadata = ModRuntimeMetadata.enderfallSdk();

        assertEquals("enderfall_sdk", metadata.id());
        assertEquals("${version}", metadata.versionPlaceholder());
        assertEquals(List.of("EnderFall"), metadata.authors());
        assertEquals("uk.co.enderfall.sdk.runtime.fabric.v1_21_4.EnderfallFabricRuntime",
                metadata.fabricMainEntrypoint());
    }

    @Test
    void defensivelyCopiesAuthors() {
        List<String> authors = new ArrayList<>(List.of("EnderFall"));
        ModRuntimeMetadata metadata = new ModRuntimeMetadata(
                "enderfall_sdk", "${version}", "EnderFall SDK", authors, "Apache-2.0", "*",
                "uk.co.enderfall.sdk.runtime.fabric.v1_21_4.EnderfallFabricRuntime",
                "EnderFall SDK runtime resources");

        authors.add("Unexpected");

        assertEquals(List.of("EnderFall"), metadata.authors());
        assertThrows(UnsupportedOperationException.class, () -> metadata.authors().add("Unexpected"));
    }

    @Test
    void rejectsInvalidFabricEnvironment() {
        assertThrows(IllegalArgumentException.class, () -> new ModRuntimeMetadata(
                "enderfall_sdk", "${version}", "EnderFall SDK", List.of("EnderFall"), "Apache-2.0", "both",
                "uk.co.enderfall.sdk.runtime.fabric.v1_21_4.EnderfallFabricRuntime",
                "EnderFall SDK runtime resources"));
    }
}
