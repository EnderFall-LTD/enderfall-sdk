package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class ClientCommandBridgeEmitterTest {
    private static final Set<String> FABRIC_DECLARATIONS = Set.of(
            "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricPlatformAdapter.java");
    private static final Set<String> NEOFORGE_DECLARATIONS = Set.of(
            "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/NeoForgePlatformAdapter.java");

    @Test
    void emitsAClientOnlyBridgeForEveryReviewedTarget() throws Exception {
        for (var target : TargetCatalog.standard().targets()) {
            Set<String> declarations = target.loader().id().equals("fabric")
                    ? FABRIC_DECLARATIONS : NEOFORGE_DECLARATIONS;
            var output = ClientCommandBridgeEmitter.emitIfPresent(target, declarations);
            assertEquals(1, output.size(), target.id());
            String source = new String(output.get(0).content(), StandardCharsets.UTF_8);
            assertTrue(source.contains("static void register(CommandSpec spec)"), target.id());
            assertTrue(source.contains("case PLAYER -> throw"), target.id());
            if (target.loader().id().equals("fabric")) {
                assertTrue(source.contains("ClientCommandRegistrationCallback.EVENT.register"), target.id());
                assertTrue(source.contains("FabricClientCommandSource"), target.id());
            } else if (target.minecraftVersion().id().equals("1.20.1")) {
                assertTrue(source.contains("MinecraftForge.EVENT_BUS.addListener"), target.id());
            } else {
                assertTrue(source.contains("NeoForge.EVENT_BUS.addListener"), target.id());
            }
        }
    }

    @Test
    void omitsBridgeWhenThePlatformServiceIsNotPartOfTheRuntime() throws Exception {
        assertTrue(ClientCommandBridgeEmitter.emitIfPresent(
                TargetCatalog.standard().require("1.21.4-fabric"), Set.of()).isEmpty());
    }
}
