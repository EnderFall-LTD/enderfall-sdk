package uk.co.enderfall.sdk.gradle.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ModDependenciesDefinitionTest {
    @Test
    void resolvesPortableApiAndTargetRuntimeCoordinatesDeterministically() {
        ModDependenciesDefinition dependencies = new ModDependenciesDefinition();
        dependencies.required("enderui", "2.0.0", dependency -> {
            dependency.setApi("dev.enderui:enderui-api:2.0.0");
            dependency.setTarget("dev.enderui:enderui-{minecraft}-{loader}:2.0.0");
        });

        ModDependencyDefinition dependency = dependencies.all().get(0);
        assertEquals("dev.enderui:enderui-1.21.4-neoforge:2.0.0",
                dependency.resolveTarget(new TargetDefinition("1.21.4", "neoforge", 21, "21.4.157", "")));
        assertEquals("enderui|2.0.0|REQUIRED|BOTH|NONE|false", dependency.metadataDescriptor());
    }

    @Test
    void preservesOptionalClientDevelopmentPolicyAndRejectsDuplicateIds() {
        ModDependenciesDefinition dependencies = new ModDependenciesDefinition();
        dependencies.optional("preview_ui", "1.4.0", dependency -> {
            dependency.setSide("client");
            dependency.setOrdering("after");
            dependency.setExactVersion(true);
            dependency.setDevelopmentRuntime(true);
        });

        ModDependencyDefinition dependency = dependencies.all().get(0);
        assertEquals(ModDependencyDefinition.Side.CLIENT, dependency.getSide());
        assertEquals(ModDependencyDefinition.Ordering.AFTER, dependency.getOrdering());
        assertEquals("preview_ui|1.4.0|OPTIONAL|CLIENT|AFTER|true", dependency.metadataDescriptor());
        assertThrows(IllegalArgumentException.class,
                () -> dependencies.required("preview_ui", "1.4.0"));
    }
}
