package uk.co.enderfall.sdk.runtime.blockentity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.fluid.FluidTankSpec;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;
import uk.co.enderfall.sdk.api.registry.BlockRef;

class RenderTankSnapshotTest {
    private static final BlockRef BLOCK = new BlockRef(ResourceId.of("test", "tank"));
    private static final BlockEntitySpec SPEC = BlockEntitySpec.builder(BLOCK)
            .tank(new FluidTankSpec("public_tank", 81000)).tank(new FluidTankSpec("private_tank", 81000))
            .renderTank("public_tank").build();
    @Test void onlyCapturesDeclaredTankAndRoundTripsEmptyReplacement() {
        var snapshot = RenderTankSnapshot.capture(SPEC, name -> {
            assertEquals("public_tank", name);
            return Optional.of(new FluidVolume(ResourceId.of("minecraft", "water"), 40500));
        });
        var decoded = RenderTankSnapshot.decode(snapshot.encode(), SPEC);
        assertEquals(40500, decoded.contents("public_tank").orElseThrow().amount());
        assertArrayEquals(snapshot.encode(), decoded.encode());
        assertThrows(IllegalArgumentException.class, () -> decoded.contents("private_tank"));
        var empty = RenderTankSnapshot.decode(RenderTankSnapshot.capture(SPEC, name -> Optional.empty()).encode(), SPEC);
        assertTrue(empty.contents("public_tank").isEmpty());
    }
    @Test void rejectsWrongDeclarationsAndMalformedPayloads() {
        assertThrows(IllegalArgumentException.class, () -> BlockEntitySpec.builder(BLOCK).renderTank("missing").build());
        assertThrows(IllegalArgumentException.class, () -> BlockEntitySpec.builder(BLOCK).renderTank("a").renderTank("a"));
        assertThrows(UnsupportedOperationException.class, () -> SPEC.renderTanks().clear());
        assertThrows(IllegalArgumentException.class, () -> RenderTankSnapshot.capture(SPEC,
                name -> Optional.of(new FluidVolume(ResourceId.of("minecraft", "water"), 81001))));
        byte[] valid = RenderTankSnapshot.capture(SPEC, name -> Optional.empty()).encode();
        for (int i = 0; i < valid.length; i++) {
            byte[] truncated = Arrays.copyOf(valid, i);
            assertThrows(IllegalArgumentException.class, () -> RenderTankSnapshot.decode(truncated, SPEC));
        }
        assertThrows(IllegalArgumentException.class, () -> RenderTankSnapshot.decode(Arrays.copyOf(valid, valid.length+1), SPEC));
        byte[] wrongName = valid.clone(); wrongName[7] = 'x';
        assertThrows(IllegalArgumentException.class, () -> RenderTankSnapshot.decode(wrongName, SPEC));
        assertThrows(IllegalArgumentException.class, () -> RenderTankSnapshot.decode(new byte[18001], SPEC));
    }
}
