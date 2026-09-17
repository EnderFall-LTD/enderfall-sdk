package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class RawPayloadEmitterTest {
    private static final Set<String> PATHS = Set.of("uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricRawPayload.java",
            "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/NeoForgeRawPayload.java");

    @Test void emitsSixModernTargetsDeterministicallyAndOmitsLegacyChannels() throws Exception {
        int emitted = 0;
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            var output = RawPayloadEmitter.emitIfPresent(target, PATHS);
            assertEquals(id.startsWith("1.20.1") ? 0 : 1, output.size());
            assertTrue(RawPayloadEmitter.emitIfPresent(target, Set.of()).isEmpty());
            if (output.isEmpty()) continue;
            emitted++;
            assertArrayEquals(output.get(0).content(), RawPayloadEmitter.emitIfPresent(target, PATHS).get(0).content());
            String source = new String(output.get(0).content(), StandardCharsets.UTF_8);
            assertTrue(source.contains("bytes = Arrays.copyOf(bytes, bytes.length)"));
            assertTrue(source.contains("return Arrays.copyOf(bytes, bytes.length)"));
            assertTrue(source.indexOf("length > maximumBytes") < source.indexOf("new byte[length]"));
            assertTrue(source.indexOf("payload.bytes.length > maximumBytes") < source.indexOf("buffer.writeBytes(payload.bytes)"));
        }
        assertEquals(6, emitted);
    }

    @Test void rejectsUnreviewedTarget() {
        var base = TargetCatalog.standard().require("1.21.4-fabric");
        var changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> RawPayloadEmitter.emitIfPresent(changed, PATHS));
    }
}
