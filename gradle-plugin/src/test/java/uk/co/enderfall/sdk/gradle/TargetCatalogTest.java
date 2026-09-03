package uk.co.enderfall.sdk.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.gradle.model.TargetCatalog;

class TargetCatalogTest {
    @Test
    void catalogContainsTheReviewedNineTargetMatrix() {
        assertEquals(9, TargetCatalog.all().size());
        assertEquals(17, TargetCatalog.find("1.20.1", "forge").orElseThrow().javaVersion());
        assertEquals(21, TargetCatalog.find("1.21.4", "neoforge").orElseThrow().javaVersion());
        assertEquals(25, TargetCatalog.find("26.2", "fabric").orElseThrow().javaVersion());
        assertTrue(TargetCatalog.supportedIds().contains("1.20.1-neoforge"));
        assertEquals("1.20.1-fabric", TargetCatalog.all().iterator().next().id());
    }
}
