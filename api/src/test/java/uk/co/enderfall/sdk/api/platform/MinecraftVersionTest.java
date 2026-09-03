package uk.co.enderfall.sdk.api.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinecraftVersionTest {
    @Test
    void comparesOldAndNewVersionSchemesNumerically() {
        assertTrue(new MinecraftVersion("1.21.4").compareTo(new MinecraftVersion("26.2")) < 0);
        assertEquals(0, new MinecraftVersion("1.21").compareTo(new MinecraftVersion("1.21.0")));
    }
}
