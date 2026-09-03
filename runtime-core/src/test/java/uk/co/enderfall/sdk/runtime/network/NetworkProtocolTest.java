package uk.co.enderfall.sdk.runtime.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.network.PacketDecodingException;

class NetworkProtocolTest {
    @Test
    void manifestsAreDeterministicAndRoundTrip() {
        Set<NetworkProtocol.PacketCapability> first = Set.of(
                new NetworkProtocol.PacketCapability(ResourceId.of("test_mod", "zeta"), 2, false),
                new NetworkProtocol.PacketCapability(ResourceId.of("test_mod", "alpha"), 1, true));
        Set<NetworkProtocol.PacketCapability> second = Set.of(
                new NetworkProtocol.PacketCapability(ResourceId.of("test_mod", "alpha"), 1, true),
                new NetworkProtocol.PacketCapability(ResourceId.of("test_mod", "zeta"), 2, false));

        byte[] encoded = NetworkProtocol.encode(first);
        assertArrayEquals(encoded, NetworkProtocol.encode(second));
        NetworkProtocol.Manifest decoded = NetworkProtocol.decode(encoded);
        assertEquals(NetworkProtocol.MAJOR, decoded.major());
        assertEquals(first, decoded.capabilities());
    }

    @Test
    void rejectsTrailingAndOversizedManifests() {
        byte[] encoded = NetworkProtocol.encode(Set.of());
        byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(PacketDecodingException.class, () -> NetworkProtocol.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> NetworkProtocol.encode(java.util.stream.IntStream.range(0, 513)
                        .mapToObj(index -> new NetworkProtocol.PacketCapability(
                                ResourceId.of("test_mod", "packet_" + index), 1, false))
                        .collect(java.util.stream.Collectors.toSet())));
    }
}
