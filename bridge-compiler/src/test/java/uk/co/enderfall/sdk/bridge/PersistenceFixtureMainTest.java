package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class PersistenceFixtureMainTest {
    @TempDir Path directory;

    @Test void adaptsOnlyLauncherAndResourceFormatsAcrossAllTargets() throws Exception {
        for (String target : TargetCatalog.standard().targetIds()) {
            var policy = BlockEntityNativePolicy.require(target);
            Path output = directory.resolve(target);
            PersistenceFixtureMain.main(new String[] {target, "../examples/persistent-preview", output.toString()});
            String machinePath = "data/enderfall_persistent_preview/enderfall_machine/washed_crystal.json";
            assertEquals(Files.readString(Path.of("../examples/persistent-preview/src/main/resources/" + machinePath)),
                    Files.readString(output.resolve("resources/" + machinePath)), target);
            String glue = Files.readString(output.resolve("java/uk/co/enderfall/sdk/preview/GeneratedPersistenceEntrypoint.java"));
            assertTrue(glue.contains("ConsumerBootstrap.initialize("), target);
            assertFalse(glue.contains("PersistentPreviewBootstrap"), target);
            Path recipeDirectory = output.resolve("resources/data/enderfall_persistent_preview/"
                    + (policy.legacy() ? "recipes" : "recipe"));
            for (String name : java.util.List.of("assembly.json", "assembly_lamp.json", "assembly_observer.json")) {
                String recipe = Files.readString(recipeDirectory.resolve(name));
                assertEquals(!policy.modernRecipes(), recipe.contains("\"ingredient\":{\"item\""), target + ":" + name);
                assertEquals(policy.legacy(), recipe.contains("\"result\":{\"item\""), target + ":" + name);
            }
            assertTrue(Files.readString(recipeDirectory.resolve("assembly_lamp.json"))
                    .contains("minecraft:redstone_lamp"), target);
            assertTrue(Files.readString(recipeDirectory.resolve("assembly_observer.json"))
                    .contains("minecraft:observer"), target);
            assertEquals(!policy.modernRecipes(), Files.exists(output.resolve("resources/assets/enderfall_persistent_preview/models/item/workbench.json")), target);
            assertEquals(!policy.modernRecipes(), Files.exists(output.resolve("resources/assets/enderfall_persistent_preview/models/item/storage_cabinet.json")), target);
            assertEquals(policy.modernRecipes(), Files.exists(output.resolve("resources/assets/enderfall_persistent_preview/items/storage_cabinet.json")), target);
            assertTrue(Files.exists(output.resolve("resources/assets/enderfall_persistent_preview/blockstates/axis_test.json")), target);
            assertTrue(Files.exists(output.resolve("resources/assets/enderfall_persistent_preview/models/item/axis_test.json")), target);
            assertEquals(policy.modernRecipes(), Files.exists(output.resolve("resources/assets/enderfall_persistent_preview/items/axis_test.json")), target);
            assertTrue(Files.exists(output.resolve("resources/data/enderfall_persistent_preview/"
                    + (policy.legacy() ? "loot_tables" : "loot_table") + "/blocks/axis_test.json")), target);
            assertTrue(Files.exists(output.resolve("resources/data/enderfall_persistent_preview/"
                    + (policy.legacy() ? "loot_tables" : "loot_table")
                    + "/chests/storage_cabinet_test.json")), target);
            String metadata = Files.readString(output.resolve("resources/" + (policy.fabric() ? "fabric.mod.json"
                    : policy.legacy() ? "META-INF/mods.toml" : "META-INF/neoforge.mods.toml")));
            assertTrue(metadata.contains("enderfall_sdk"), target);
        }
    }
}
