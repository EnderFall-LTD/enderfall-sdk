package uk.co.enderfall.sdk.bridge.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TargetCatalogTest {
    @Test
    void standardCatalogContainsTheCompleteReviewedMatrixInStableOrder() {
        TargetCatalog catalog = TargetCatalog.standard();

        assertEquals(List.of(
                "1.20.1-fabric",
                "1.20.1-forge",
                "1.20.1-neoforge",
                "1.21.1-fabric",
                "1.21.1-neoforge",
                "1.21.4-fabric",
                "1.21.4-neoforge",
                "26.2-fabric",
                "26.2-neoforge"), catalog.targetIds());
        assertEquals(9, catalog.targets().size());
        assertTrue(catalog.targets().stream().allMatch(target -> target.javaVersion() > 0));
        assertTrue(catalog.targets().stream().allMatch(target -> !target.loaderVersion().isBlank()));
        assertFalse(catalog.targets().stream().anyMatch(target -> target.loaderAbi() == null));
        assertFalse(catalog.targets().stream().anyMatch(target -> target.minecraftAbi() == null));
        assertFalse(catalog.targets().stream().anyMatch(target -> target.mappingAbi() == null));
        assertFalse(catalog.targets().stream().anyMatch(target -> target.recipeAbi() == null));
        assertFalse(catalog.targets().stream().anyMatch(target -> target.networkAbi() == null));
        assertFalse(catalog.targets().stream().anyMatch(target -> target.menuAbi() == null));

        TargetSpec fabric1201 = catalog.require("1.20.1-fabric");
        TargetSpec forge1201 = catalog.require("1.20.1-forge");
        TargetSpec fabric1211 = catalog.require("1.21.1-fabric");
        TargetSpec fabric1214 = catalog.require("1.21.4-fabric");
        assertNotEquals(fabric1201.networkAbi(), forge1201.networkAbi());
        assertNotEquals(fabric1211.minecraftAbi(), fabric1214.minecraftAbi());
        assertNotEquals(fabric1211.recipeAbi(), fabric1214.recipeAbi());
        assertNotEquals(fabric1211.menuAbi(), fabric1214.menuAbi());
        assertEquals(new MinecraftAbi.IntegerRangePackFormat(34, 34, 48),
                fabric1211.minecraftAbi().resourcePackFormat());
        assertEquals(new MinecraftAbi.IntegerRangePackFormat(46, 46, 61),
                fabric1214.minecraftAbi().resourcePackFormat());
    }

    @Test
    void duplicateCatalogEntriesFailDeterministically() {
        TargetSpec duplicate = TargetCatalog.standard().require("1.21.4-fabric");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TargetCatalog.of(List.of(duplicate, duplicate)));

        assertEquals("Invalid target catalog: duplicate target IDs: 1.21.4-fabric; "
                + "duplicate target coordinates: 1.21.4/fabric", exception.getMessage());
    }

    @Test
    void incompatibleAbiCombinationCannotEnterCatalog() {
        TargetSpec reference = TargetCatalog.standard().require("1.21.4-fabric");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new TargetSpec(
                reference.id(),
                reference.minecraftVersion(),
                reference.loader(),
                reference.javaVersion(),
                reference.loaderVersion(),
                reference.platformApiVersion(),
                reference.loaderAbi(),
                reference.minecraftAbi(),
                reference.mappingAbi(),
                RecipeAbi.CODEC_1_21_1,
                reference.networkAbi(),
                reference.menuAbi()));

        assertTrue(exception.getMessage().contains(
                "requires recipe ABI CODEC_WITH_PLACEMENT_1_21_4, not CODEC_1_21_1"));
    }
}
