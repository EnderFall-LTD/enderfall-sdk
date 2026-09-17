package uk.co.enderfall.sdk.api.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.block.BlockProperty;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.registry.BlockRef;

class StorageContainerSpecTest {
    private static BlockEntitySpec storage(int slots) {
        return BlockEntitySpec.builder(new BlockRef(ResourceId.of("test", "cabinet")))
                .inventorySlots(slots).build();
    }

    @Test void supportsEveryVanillaNineWideStorageSize() {
        for (int rows = 1; rows <= 6; rows++) {
            var spec = StorageContainerSpec.builder("Cabinet", storage(rows * 9)).build();
            assertEquals(rows, spec.rows());
            assertEquals(ContainerSoundProfile.BARREL, spec.sounds());
        }
    }

    @Test void rejectsLayoutsThatCannotUseThePortableVanillaScreen() {
        for (int slots : new int[] {0, 1, 8, 10, 53, 55, 63}) {
            assertThrows(IllegalArgumentException.class,
                    () -> StorageContainerSpec.builder("Cabinet", storage(slots)).build());
        }
    }

    @Test void declaresOptionalOpenStateAndCustomSounds() {
        var open = BlockProperty.bool("open");
        var sounds = ContainerSoundProfile.of(ResourceId.of("test", "cabinet.open"),
                ResourceId.of("test", "cabinet.close"), 0.75F, 1.2F);
        var spec = StorageContainerSpec.builder("Cabinet", storage(27))
                .openState(open).sounds(sounds).build();
        assertEquals(open, spec.openProperty().orElseThrow());
        assertEquals(sounds, spec.sounds());
        assertFalse(StorageContainerSpec.builder("Silent", storage(9)).silent().build().sounds().enabled());
        assertThrows(IllegalArgumentException.class, () -> new ContainerSoundProfile(
                ResourceId.of("test", "open"), null, 1, 1));
    }
}
