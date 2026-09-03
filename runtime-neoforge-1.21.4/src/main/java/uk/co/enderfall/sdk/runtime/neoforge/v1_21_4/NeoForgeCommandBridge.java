package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import uk.co.enderfall.sdk.api.command.CommandArgument;
import uk.co.enderfall.sdk.api.command.CommandSpec;

final class NeoForgeCommandBridge {
    private NeoForgeCommandBridge() {
    }

    static void register(CommandSpec spec) {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(spec.name())
                    .requires(source -> source.hasPermission(spec.permissionLevel()));
            ArgumentBuilder<CommandSourceStack, ?> current = root;
            for (CommandArgument<?> portableArgument : spec.arguments()) {
                RequiredArgumentBuilder<CommandSourceStack, ?> child = Commands.argument(
                        portableArgument.name(), argumentType(portableArgument));
                child.suggests((context, builder) -> {
                    try {
                        PortableCommandContext portable = context(spec, context, true);
                        for (String suggestion : spec.suggestions().suggest(portable, builder.getRemaining())) {
                            builder.suggest(suggestion);
                        }
                        return builder.buildFuture();
                    } catch (Exception exception) {
                        return java.util.concurrent.CompletableFuture.failedFuture(exception);
                    }
                });
                if (portableArgument.optional()) {
                    current.executes(command -> execute(spec, command));
                }
                current.then(child);
                current = child;
            }
            current.executes(command -> execute(spec, command));
            event.getDispatcher().register(root);
        });
    }

    private static ArgumentType<?> argumentType(CommandArgument<?> argument) {
        return switch (argument.type()) {
            case WORD -> StringArgumentType.word();
            case STRING -> StringArgumentType.string();
            case GREEDY_STRING -> StringArgumentType.greedyString();
            case INTEGER -> IntegerArgumentType.integer();
            case BOOLEAN -> BoolArgumentType.bool();
            case PLAYER -> EntityArgument.player();
        };
    }

    private static int execute(CommandSpec spec, CommandContext<CommandSourceStack> context) {
        try {
            return spec.executor().execute(context(spec, context, false));
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("Command failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static PortableCommandContext context(CommandSpec spec, CommandContext<CommandSourceStack> context,
                                                   boolean tolerateMissing) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (CommandArgument<?> argument : spec.arguments()) {
            try {
                Object value = switch (argument.type()) {
                    case WORD, STRING, GREEDY_STRING -> StringArgumentType.getString(context, argument.name());
                    case INTEGER -> IntegerArgumentType.getInteger(context, argument.name());
                    case BOOLEAN -> BoolArgumentType.getBool(context, argument.name());
                    case PLAYER -> EntityArgument.getPlayer(context, argument.name()).getGameProfile().getName();
                };
                values.put(argument.name(), value);
            } catch (IllegalArgumentException | com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
                if (!tolerateMissing && !argument.optional()) {
                    throw new IllegalStateException("Missing required command argument " + argument.name(), ignored);
                }
            }
        }
        return new PortableCommandContext(context.getSource(), Map.copyOf(values));
    }

    private record PortableCommandContext(CommandSourceStack source, Map<String, Object> arguments)
            implements uk.co.enderfall.sdk.api.command.CommandContext {
        @Override
        public String sourceName() {
            return source.getTextName();
        }

        @Override
        public Optional<UUID> sourcePlayerId() {
            ServerPlayer player = source.getPlayer();
            return player == null ? Optional.empty() : Optional.of(player.getUUID());
        }

        @Override
        public void reply(String message) {
            source.sendSuccess(() -> Component.literal(message), false);
        }

        @Override
        public void replyTranslation(String translationKey, Object... arguments) {
            source.sendSuccess(() -> Component.translatable(translationKey, arguments), false);
        }
    }
}
