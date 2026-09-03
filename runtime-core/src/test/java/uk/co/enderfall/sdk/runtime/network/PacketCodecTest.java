package uk.co.enderfall.sdk.runtime.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.network.PacketDecodingException;

class PacketCodecTest {
    @Test
    void roundTripsNetworkOrderValues() {
        UUID uuid = UUID.fromString("d094e16b-cb90-44c0-a708-e64e0affd609");
        ByteArrayPacketWriter writer = new ByteArrayPacketWriter(512);
        writer.writeVarInt(300);
        writer.writeInt(0x01020304);
        writer.writeString("EnderFall ✓", 64);
        writer.writeUuid(uuid);
        writer.writeResourceId(ResourceId.of("test_mod", "echo"));

        byte[] bytes = writer.toByteArray();
        assertArrayEquals(new byte[] {(byte) 0xac, 0x02, 0x01, 0x02, 0x03, 0x04},
                java.util.Arrays.copyOf(bytes, 6));

        ByteArrayPacketReader reader = new ByteArrayPacketReader(bytes, 512);
        assertEquals(300, reader.readVarInt());
        assertEquals(0x01020304, reader.readInt());
        assertEquals("EnderFall ✓", reader.readString(64));
        assertEquals(uuid, reader.readUuid());
        assertEquals(ResourceId.of("test_mod", "echo"), reader.readResourceId());
        assertEquals(0, reader.remainingBytes());
    }

    @Test
    void rejectsMalformedUtf8AndOversizedLengths() {
        assertThrows(PacketDecodingException.class,
                () -> new ByteArrayPacketReader(new byte[] {2, (byte) 0xc3, 0x28}, 32).readString(8));
        assertThrows(PacketDecodingException.class,
                () -> new ByteArrayPacketReader(new byte[] {9}, 32).readBytes(8));
        assertThrows(PacketDecodingException.class,
                () -> new ByteArrayPacketReader(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80,
                        (byte) 0x80, (byte) 0x80, 0x00}, 32).readVarInt());
        assertThrows(PacketDecodingException.class,
                () -> new ByteArrayPacketReader(new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff,
                        (byte) 0xff, 0x10}, 32).readVarInt());
    }
}
