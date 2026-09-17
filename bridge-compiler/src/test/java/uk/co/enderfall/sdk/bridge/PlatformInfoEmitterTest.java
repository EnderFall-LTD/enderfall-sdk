package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class PlatformInfoEmitterTest {
    @Test void emitsEveryReviewedTargetDeterministicallyOnlyWhenDeclared() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            assertTrue(PlatformInfoEmitter.emitIfPresent(target, Set.of()).isEmpty());
            var first = PlatformInfoEmitter.emitIfPresent(target, paths(id));
            assertEquals(1, first.size());
            assertArrayEquals(first.get(0).content(), PlatformInfoEmitter.emitIfPresent(target, paths(id)).get(0).content());
        }
        assertEquals(text("1.20.1-forge"), text("1.20.1-neoforge"));
    }

    @Test void preservesLoaderEnvironmentAndModQueries() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            boolean fabric = id.endsWith("-fabric");
            assertTrue(source.contains(fabric ? "loader.getEnvironmentType() == EnvType.CLIENT" : id.startsWith("26.2")
                    ? "FMLEnvironment.getDist() == Dist.CLIENT" : "FMLEnvironment.dist == Dist.CLIENT"));
            assertTrue(source.contains(fabric ? "loader.isModLoaded(modId)" : "ModList.get().isLoaded(modId)"));
            assertTrue(source.contains(fabric ? "loader.getModContainer(modId)" : "ModList.get().getModContainerById(modId)"));
            assertTrue(source.contains("Environment.DEDICATED_SERVER"));
        }
    }

    @Test void preservesLegacyMarkerIdentityAndFailureMessages() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            boolean legacy = id.equals("1.20.1-forge") || id.equals("1.20.1-neoforge");
            assertEquals(legacy, source.contains("META-INF/enderfall-sdk-loader"));
            if (legacy) {
                assertTrue(source.contains("new MinecraftVersion(\"1.20.1\")"));
                assertTrue(source.contains("EnderFall loader marker is missing"));
                assertTrue(source.contains("Cannot read EnderFall loader marker"));
                assertTrue(source.contains(".trim().toUpperCase(java.util.Locale.ROOT)"));
            } else assertTrue(source.contains("Minecraft mod container is unavailable"));
        }
    }

    @Test void rejectsUnreviewedCoordinates() {
        TargetSpec base = TargetCatalog.standard().require("26.2-neoforge");
        TargetSpec changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> PlatformInfoEmitter.emitIfPresent(changed, paths(base.id())));
    }

    private static Set<String> paths(String id) {
        boolean fabric = id.endsWith("-fabric");
        return Set.of("uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/"
                + (fabric ? "Fabric" : "NeoForge") + "PlatformInfo.java");
    }
    private static String text(String id) throws BridgeGenerationException {
        return new String(PlatformInfoEmitter.emitIfPresent(TargetCatalog.standard().require(id), paths(id)).get(0).content(), StandardCharsets.UTF_8);
    }
}
