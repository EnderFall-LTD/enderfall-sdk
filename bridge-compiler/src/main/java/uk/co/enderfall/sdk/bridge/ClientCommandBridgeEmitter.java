package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MinecraftVersion;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Emits the physical-client Brigadier bridge for portable client commands. */
final class ClientCommandBridgeEmitter {
    private ClientCommandBridgeEmitter() {
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> declarations)
            throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) {
            throw new BridgeGenerationException("Unreviewed client-command target " + target.id());
        }
        boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
        boolean legacy = target.loaderAbi() == LoaderAbi.LEGACY_FML;
        String canonicalPackage = "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge")
                + "/v1_21_4/";
        String canonicalPrefix = fabric ? "Fabric" : "NeoForge";
        if (!declarations.contains(canonicalPackage + canonicalPrefix + "PlatformAdapter.java")) return List.of();
        boolean identifier = target.minecraftVersion() == MinecraftVersion.V26_2;
        String packageSuffix = legacy ? "forge.v1_20_1" : (fabric ? "fabric" : "neoforge") + ".v1_21_4";
        String prefix = legacy ? "LegacyForge" : fabric ? "Fabric" : "NeoForge";
        String eventImports;
        String registration;
        String sourceType;
        String feedback;
        String failure;
        String player;
        if (fabric) {
            eventImports = "import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;\n"
                    + "import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;\n";
            registration = "ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> register(dispatcher, spec));";
            sourceType = "FabricClientCommandSource";
            feedback = "source.sendFeedback(message);";
            failure = "source.sendError(message);";
            player = "source.getPlayer()";
        } else {
            eventImports = legacy
                    ? "import net.minecraftforge.client.event.RegisterClientCommandsEvent;\n"
                            + "import net.minecraftforge.common.MinecraftForge;\n"
                    : "import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;\n"
                            + "import net.neoforged.neoforge.common.NeoForge;\n";
            registration = (legacy ? "MinecraftForge" : "NeoForge")
                    + ".EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> register(event.getDispatcher(), spec));";
            sourceType = "CommandSourceStack";
            feedback = "source.sendSuccess(() -> message, false);";
            failure = "source.sendFailure(message);";
            player = "Minecraft.getInstance().player";
        }
        String className = prefix + "ClientCommandBridge";
        String content = SOURCE.formatted(packageSuffix, eventImports, className, sourceType, registration,
                player, feedback, failure, identifier ? "profile().name()" : "getName()",
                identifier ? "getString()" : "getString()");
        String path = "uk/co/enderfall/sdk/runtime/" + packageSuffix.replace('.', '/') + "/" + className + ".java";
        return List.of(new RuntimeSource(path, path, content.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String SOURCE = """
            package uk.co.enderfall.sdk.runtime.%1$s;

            import com.mojang.brigadier.arguments.ArgumentType;
            import com.mojang.brigadier.arguments.BoolArgumentType;
            import com.mojang.brigadier.arguments.IntegerArgumentType;
            import com.mojang.brigadier.arguments.StringArgumentType;
            import com.mojang.brigadier.builder.ArgumentBuilder;
            import com.mojang.brigadier.builder.LiteralArgumentBuilder;
            import com.mojang.brigadier.builder.RequiredArgumentBuilder;
            import com.mojang.brigadier.context.CommandContext;
            import java.util.ArrayList;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import java.util.UUID;
            import net.minecraft.client.Minecraft;
            import net.minecraft.commands.CommandSourceStack;
            import net.minecraft.network.chat.Component;
            %2$simport uk.co.enderfall.sdk.api.command.CommandArgument;
            import uk.co.enderfall.sdk.api.command.CommandSpec;

            final class %3$s {
                private %3$s() {
                }

                static void register(CommandSpec spec) {
                    %5$s
                }

                private static void register(com.mojang.brigadier.CommandDispatcher<%4$s> dispatcher, CommandSpec spec) {
                    LiteralArgumentBuilder<%4$s> root = LiteralArgumentBuilder.literal(spec.name());
                    List<RequiredArgumentBuilder<%4$s, ?>> nativeArguments = new ArrayList<>();
                    for (CommandArgument<?> portableArgument : spec.arguments()) {
                        RequiredArgumentBuilder<%4$s, ?> child = RequiredArgumentBuilder.argument(
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
                            ArgumentBuilder<%4$s, ?> previous = nativeArguments.isEmpty()
                                    ? root : nativeArguments.get(nativeArguments.size() - 1);
                            previous.executes(command -> execute(spec, command));
                        }
                        nativeArguments.add(child);
                    }
                    if (nativeArguments.isEmpty()) {
                        root.executes(command -> execute(spec, command));
                    } else {
                        nativeArguments.get(nativeArguments.size() - 1).executes(command -> execute(spec, command));
                        for (int index = nativeArguments.size() - 2; index >= 0; index--) {
                            nativeArguments.get(index).then(nativeArguments.get(index + 1));
                        }
                        root.then(nativeArguments.get(0));
                    }
                    dispatcher.register(root);
                }

                private static ArgumentType<?> argumentType(CommandArgument<?> argument) {
                    return switch (argument.type()) {
                        case WORD -> StringArgumentType.word();
                        case STRING -> StringArgumentType.string();
                        case GREEDY_STRING -> StringArgumentType.greedyString();
                        case INTEGER -> IntegerArgumentType.integer();
                        case BOOLEAN -> BoolArgumentType.bool();
                        case PLAYER -> throw new IllegalArgumentException("Player arguments are server-only");
                    };
                }

                private static int execute(CommandSpec spec, CommandContext<%4$s> context) {
                    try {
                        return spec.executor().execute(context(spec, context, false));
                    } catch (Exception exception) {
                        fail(context.getSource(), Component.literal("Client command failed: " + exception.getMessage()));
                        return 0;
                    }
                }

                private static PortableCommandContext context(CommandSpec spec, CommandContext<%4$s> context,
                                                               boolean tolerateMissing) {
                    Map<String, Object> values = new LinkedHashMap<>();
                    for (CommandArgument<?> argument : spec.arguments()) {
                        try {
                            Object value = switch (argument.type()) {
                                case WORD, STRING, GREEDY_STRING -> StringArgumentType.getString(context, argument.name());
                                case INTEGER -> IntegerArgumentType.getInteger(context, argument.name());
                                case BOOLEAN -> BoolArgumentType.getBool(context, argument.name());
                                case PLAYER -> throw new IllegalArgumentException("Player arguments are server-only");
                            };
                            values.put(argument.name(), value);
                        } catch (IllegalArgumentException ignored) {
                            if (!tolerateMissing && !argument.optional()) {
                                throw new IllegalStateException("Missing required command argument " + argument.name(), ignored);
                            }
                        }
                    }
                    return new PortableCommandContext(context.getSource(), Map.copyOf(values));
                }

                private static void feedback(%4$s source, Component message) {
                    %7$s
                }

                private static void fail(%4$s source, Component message) {
                    %8$s
                }

                private record PortableCommandContext(%4$s source, Map<String, Object> arguments)
                        implements uk.co.enderfall.sdk.api.command.CommandContext {
                    @Override public String sourceName() {
                        var player = %6$s;
                        return player == null ? "client" : player.getName().%10$s;
                    }
                    @Override public Optional<UUID> sourcePlayerId() {
                        var player = %6$s;
                        return player == null ? Optional.empty() : Optional.of(player.getUUID());
                    }
                    @Override public void reply(String message) { feedback(source, Component.literal(message)); }
                    @Override public void replyTranslation(String key, Object... arguments) {
                        feedback(source, Component.translatable(key, arguments));
                    }
                }
            }
            """;
}
