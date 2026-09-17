package uk.co.enderfall.sdk.runtime.fluid;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;

class FluidTankCodecTest {
    private PortableFluidTank filled() {
        var tank = new PortableFluidTank(100);
        tank.fill(new FluidVolume(ResourceId.parse("minecraft:water"), 80), false);
        return tank;
    }

    @Test void deterministicRoundTripAndDirtyAcknowledgement() {
        var source = filled();
        assertTrue(source.dirty());
        byte[] saved = source.save();
        assertArrayEquals(saved, source.save());
        source.markPersisted(source.revision());
        assertFalse(source.dirty());
        var target = new PortableFluidTank(100);
        target.restore(saved);
        assertEquals(source.contents(), target.contents());
        source.drain(ResourceId.parse("minecraft:water"), 1, false);
        assertThrows(IllegalStateException.class, () -> source.markPersisted(1));
        assertTrue(source.dirty());
        target.restore(new PortableFluidTank(100).save());
        assertTrue(target.contents().isEmpty());
    }

    @Test void everyTruncationAndTrailingDataFailWithoutMutation() {
        var tank = filled();
        byte[] valid = tank.save();
        var before = tank.contents();
        long revision = tank.revision();
        for (int length = 0; length < valid.length; length++) {
            byte[] truncated = Arrays.copyOf(valid, length);
            assertThrows(IllegalArgumentException.class, () -> tank.restore(truncated));
        }
        assertThrows(IllegalArgumentException.class, () -> tank.restore(Arrays.copyOf(valid, valid.length + 1)));
        assertEquals(before, tank.contents());
        assertEquals(revision, tank.revision());
    }

    @Test void rejectsWrongVersionCapacityLengthsAndMalformedUtf8() {
        var tank = filled();
        byte[] bytes = tank.save();
        bytes[4] = 2;
        assertThrows(IllegalArgumentException.class, () -> tank.restore(bytes));
        bytes[4] = 1;
        ByteBuffer.wrap(bytes).putLong(5, 101);
        assertThrows(IllegalArgumentException.class, () -> tank.restore(bytes));
        ByteBuffer.wrap(bytes).putLong(5, -1);
        assertThrows(IllegalArgumentException.class, () -> tank.restore(bytes));
        ByteBuffer.wrap(bytes).putLong(5, 80);
        bytes[15] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> tank.restore(bytes));
        ByteBuffer.wrap(bytes).putShort(13, (short) 65535);
        assertThrows(IllegalArgumentException.class, () -> tank.restore(bytes));
        assertEquals(80, tank.contents().orElseThrow().amount());
        assertThrows(IllegalArgumentException.class, () -> tank.fill(new FluidVolume(
                new ResourceId("test", "x".repeat(1024)), 1), false));
    }
}
