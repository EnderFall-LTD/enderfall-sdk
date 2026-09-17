package uk.co.enderfall.sdk.demo.command;

import java.util.UUID;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.command.Arguments;
import uk.co.enderfall.sdk.api.command.CommandArgument;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.demo.config.DemoConfig;
import uk.co.enderfall.sdk.demo.gameplay.DemoGameplay;
import uk.co.enderfall.sdk.demo.network.DemoNetworking;

/** All loader-neutral command definitions are registered here. */
public final class DemoCommands {
    private static final CommandArgument<String> MESSAGE = Arguments.greedyString("message");

    private DemoCommands() {
    }

    public static void register(ModContext context, DemoConfig config, DemoNetworking networking,
                                DemoGameplay gameplay) {
        context.commands().register(CommandSpec.builder("enderfall_demo_status")
                .description("Displays the active EnderFall SDK demo target and configuration.")
                .executes(command -> {
                    UUID playerId = command.sourcePlayerId().orElse(null);
                    String gameplayStatus = playerId == null ? "player gameplay unavailable from console"
                            : gameplay.status(playerId);
                    command.reply("EnderFall target=" + context.platform().targetId()
                            + ", enabled=" + config.enabled() + ", " + gameplayStatus);
                    return 1;
                })
                .build());

        context.commands().register(CommandSpec.builder("enderfall_demo_guide")
                .description("Explains the playable resonance progression loop.")
                .executes(command -> {
                    UUID playerId = command.sourcePlayerId().orElse(null);
                    if (playerId == null) {
                        command.reply("This command must be run by a player.");
                        return 0;
                    }
                    gameplay.sendGuide(playerId);
                    return 1;
                })
                .build());

        context.commands().register(CommandSpec.builder("enderfall_demo_reset")
                .description("Resets your temporary resonance charge and statistics.")
                .executes(command -> {
                    UUID playerId = command.sourcePlayerId().orElse(null);
                    if (playerId == null) {
                        command.reply("This command must be run by a player.");
                        return 0;
                    }
                    gameplay.reset(playerId);
                    return 1;
                })
                .build());

        context.commands().register(CommandSpec.builder("enderfall_demo_kit")
                .description("Grants a development kit for testing the complete gameplay loop.")
                .permissionLevel(2)
                .executes(command -> {
                    UUID playerId = command.sourcePlayerId().orElse(null);
                    if (playerId == null) {
                        command.reply("This command must be run by a player.");
                        return 0;
                    }
                    gameplay.giveStarterKit(playerId);
                    return 1;
                })
                .build());

        context.commands().register(CommandSpec.builder("enderfall_demo_pulse")
                .description("Sends a typed server-to-client pulse and receives its acknowledgement.")
                .argument(MESSAGE)
                .executes(command -> {
                    UUID playerId = command.sourcePlayerId().orElse(null);
                    if (playerId == null) {
                        command.reply("This command must be run by a player.");
                        return 0;
                    }
                    if (!config.enabled()) {
                        command.reply("Resonance is disabled in the demo config.");
                        return 0;
                    }
                    boolean sent = networking.sendPulse(playerId, command.argument(MESSAGE), config.strength());
                    command.reply(sent ? "Pulse sent; watch both logs for the acknowledgement."
                            : "The connected client does not support the demo pulse packet yet.");
                    return sent ? 1 : 0;
                })
                .build());
    }
}
