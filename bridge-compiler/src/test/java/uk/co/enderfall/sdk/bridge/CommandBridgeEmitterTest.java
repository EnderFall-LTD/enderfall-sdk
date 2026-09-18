package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class CommandBridgeEmitterTest {
    private static final Set<String> PATHS = Set.of("uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricCommandBridge.java",
            "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/NeoForgeCommandBridge.java");

    @Test void emitsEveryTargetDeterministicallyOnlyWhenSelected() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            var output = CommandBridgeEmitter.emitIfPresent(target, PATHS);
            assertEquals(1, output.size());
            assertTrue(CommandBridgeEmitter.emitIfPresent(target, Set.of()).isEmpty());
            assertArrayEquals(output.get(0).content(), CommandBridgeEmitter.emitIfPresent(target, PATHS).get(0).content());
        }
        assertEquals(source("1.20.1-forge"), source("1.20.1-neoforge"));
    }

    @Test void retainsNativeRegistrationAndPermissionPolicies() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = source(id);
            assertTrue(source.contains(id.endsWith("fabric") ? "CommandRegistrationCallback.EVENT.register"
                    : id.startsWith("1.20.1") ? "MinecraftForge.EVENT_BUS.addListener" : "NeoForge.EVENT_BUS.addListener"));
            if (id.startsWith("26.2")) {
                for (String permission : new String[] {"MODERATOR", "GAMEMASTER", "ADMIN", "OWNER"})
                    assertTrue(source.contains("Permissions.COMMANDS_" + permission));
                assertTrue(source.contains("case 0 -> true"));
                assertTrue(source.contains(".getGameProfile().name()"));
                assertFalse(source.contains("source.hasPermission(spec.permissionLevel())"));
            } else {
                assertTrue(source.contains("source.hasPermission(spec.permissionLevel())"));
                assertTrue(source.contains(".getGameProfile().getName()"));
            }
        }
    }

    @Test void preservesArgumentsSuggestionsFailuresAndPortableReplies() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = source(id);
            for (String expression : new String[] {"StringArgumentType.word()", "StringArgumentType.string()",
                    "StringArgumentType.greedyString()", "IntegerArgumentType.integer()", "BoolArgumentType.bool()", "EntityArgument.player()",
                    "portableArgument.optional()", "spec.suggestions().suggest", "CompletableFuture.failedFuture(exception)",
                    "spec.executor().execute", "Missing required command argument", "Component.translatable(translationKey, arguments)",
                    "Optional.of(player.getUUID())", "Map.copyOf(values)",
                    "nativeArguments.get(nativeArguments.size() - 1)", "root.then(nativeArguments.get(0))"})
                assertTrue(source.contains(expression), expression);
            int leafExecutor = source.lastIndexOf("nativeArguments.get(nativeArguments.size() - 1)");
            int rootAttachment = source.indexOf("root.then(nativeArguments.get(0));");
            assertTrue(leafExecutor >= 0 && leafExecutor < rootAttachment, id);
            assertFalse(source.contains("current.then(child)"), id);
        }
    }

    @Test void rejectsUnreviewedTarget() {
        var base = TargetCatalog.standard().require("1.21.4-fabric");
        var changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> CommandBridgeEmitter.emitIfPresent(changed, PATHS));
    }

    private static String source(String id) throws BridgeGenerationException {
        return new String(CommandBridgeEmitter.emitIfPresent(TargetCatalog.standard().require(id), PATHS).get(0).content(), StandardCharsets.UTF_8);
    }
}
