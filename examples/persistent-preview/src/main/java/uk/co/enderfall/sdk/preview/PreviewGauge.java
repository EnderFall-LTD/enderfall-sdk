package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.ui.*;

/** Server-synchronized visual test, deliberately independent of real tank contents. */
public final class PreviewGauge {
    private PreviewGauge() { }

    public static void register(ModContext context) {
        var spec = MenuSpec.builder("Gauge rendering test")
                .gauge(new MenuGauge("tank.percent", 20, 32, 24, 78, 0xFF4488FF))
                .label(MenuLabel.text("Test level: {tank.percent}%", 55, 42))
                .label(MenuLabel.text("Not connected to a world tank", 12, 116))
                .button(MenuButton.of("empty", "0%", 55, 70, 45))
                .button(MenuButton.of("half", "50%", 105, 70, 45))
                .button(MenuButton.of("full", "100%", 155, 70, 50)).build();
        final MenuRef menu;
        try {
            menu = context.menus().register("gauge_test", spec, action -> action.update(state(switch (action.action()) {
                case "empty" -> 0;
                case "half" -> 50;
                case "full" -> 100;
                default -> throw new IllegalArgumentException("Unknown gauge action");
            })));
        } catch (UnsupportedOperationException unsupported) {
            context.logger().info("Gauge test unavailable in this runtime profile.");
            return;
        }
        context.commands().register(CommandSpec.builder("enderfall_gauge")
                .description("Opens a synthetic server-synchronized gauge test.")
                .executes(command -> {
                    var player = command.sourcePlayerId();
                    if (player.isEmpty()) { command.reply("Run this command as a player."); return 0; }
                    context.menus().open(player.get(), menu, state(50));
                    return 1;
                }).build());
        var tankMenu = context.menus().register("tank_contents", MenuSpec.builder("Tank contents")
                .size(300, 150)
                .gauge(new MenuGauge("tank.percent", 16, 32, 24, 96, 0xFF4488FF))
                .label(MenuLabel.text("{tank.fluid}", 52, 38))
                .label(MenuLabel.text("{tank.percent}% full", 52, 58))
                .label(MenuLabel.text("{tank.amount} / {tank.capacity} units", 52, 78))
                .label(MenuLabel.text("81,000 units = one bucket", 52, 104))
                .build(), ignored -> { });
        context.menus().bindTank(tankMenu,
                new uk.co.enderfall.sdk.api.registry.BlockRef(context.id("fluid_tank")),
                PreviewFluids.RESERVOIR);
    }

    private static MenuState state(int percent) {
        return MenuState.builder().value("tank.percent", percent).build();
    }
}
