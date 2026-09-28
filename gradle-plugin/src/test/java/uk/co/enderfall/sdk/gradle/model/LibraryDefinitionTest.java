package uk.co.enderfall.sdk.gradle.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LibraryDefinitionTest {
    @Test
    void defaultsFollowTheModIdentityAndSelectedTarget() {
        ModDefinition mod = new ModDefinition();
        mod.setId("enderui");
        LibraryDefinition library = new LibraryDefinition();
        TargetDefinition target = new TargetDefinition("1.21.4", "fabric", 21, "0.16.14", "");

        assertEquals("enderui-api", library.resolvedApiArtifact(mod));
        assertEquals("enderui-1.21.4-fabric", library.resolvedTargetArtifact(mod, target));
    }

    @Test
    void customArtifactPatternsResolveBothTargetAxes() {
        ModDefinition mod = new ModDefinition();
        LibraryDefinition library = new LibraryDefinition();
        library.setApiArtifact("portable-ui-api");
        library.setTargetArtifact("portable-ui-mc{minecraft}-{loader}");
        TargetDefinition target = new TargetDefinition("26.2", "neoforge", 25, "26.2.0.75", "");

        assertEquals("portable-ui-api", library.resolvedApiArtifact(mod));
        assertEquals("portable-ui-mc26.2-neoforge", library.resolvedTargetArtifact(mod, target));
    }
}
