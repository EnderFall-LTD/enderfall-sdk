package uk.co.enderfall.sdk.api.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MenuGaugeTest {
    @Test void fillIsBoundedAndMalformedStateIsEmpty() {
        var gauge = new MenuGauge("tank.percent", 10, 30, 20, 102, 0xFF4488FF);
        assertEquals(50, gauge.filledPixels(MenuState.builder().value("tank.percent", 50).build()));
        assertEquals(100, gauge.filledPixels(MenuState.builder().value("tank.percent", 999).build()));
        assertEquals(0, gauge.filledPixels(MenuState.builder().value("tank.percent", -5).build()));
        assertEquals(0, gauge.filledPixels(MenuState.builder().value("tank.percent", "bad").build()));
        assertEquals(0, gauge.filledPixels(MenuState.empty()));
    }
    @Test void layoutRejectsInvalidGaugesAndCopiesDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> new MenuGauge("bad key", 0, 0, 10, 10, 0));
        var gauge = new MenuGauge("tank.percent", 10, 30, 20, 80, 0);
        var builder = MenuSpec.builder("Tank").gauge(gauge);
        var built = builder.build();
        builder.gauge(gauge);
        assertEquals(1, built.gauges().size());
        assertThrows(UnsupportedOperationException.class, () -> built.gauges().clear());
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.builder("Tank").size(120, 80).gauge(gauge).build());
    }
}
