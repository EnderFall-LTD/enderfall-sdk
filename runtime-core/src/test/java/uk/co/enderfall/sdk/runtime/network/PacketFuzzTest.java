package uk.co.enderfall.sdk.runtime.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.network.PacketDecodingException;

class PacketFuzzTest {
    @Test
    void boundedStringDecoderSurvivesDeterministicMalformedCorpus() {
        Random random = new Random(0x454e44455246414cL);
        int rejected = 0;
        for (int sample = 0; sample < 10_000; sample++) {
            byte[] bytes = new byte[random.nextInt(130)];
            random.nextBytes(bytes);
            try {
                new ByteArrayPacketReader(bytes, 128).readString(64);
            } catch (PacketDecodingException expected) {
                rejected++;
            }
        }
        assertTrue(rejected > 9_000, "The malformed corpus should be overwhelmingly rejected");
    }
}
