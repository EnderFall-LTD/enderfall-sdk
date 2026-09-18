package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MinecraftVersion;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared command registration, argument conversion, suggestions, and execution. */
final class CommandBridgeEmitter {
    private CommandBridgeEmitter() { }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed command target " + target.id());
        boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
        boolean legacy = target.loaderAbi() == LoaderAbi.LEGACY_FML;
        boolean modernPermissions = target.minecraftVersion() == MinecraftVersion.V26_2;
        String inputRoot = "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/";
        String canonical = inputRoot + (fabric ? "Fabric" : "NeoForge") + "CommandBridge.java";
        if (!paths.contains(canonical)) return List.of();
        String packageSuffix = legacy ? "forge.v1_20_1" : (fabric ? "fabric" : "neoforge") + ".v1_21_4";
        String prefix = legacy ? "LegacyForge" : fabric ? "Fabric" : "NeoForge";
        String nativeImports = modernPermissions ? "import net.minecraft.server.permissions.Permissions;\n" : "";
        if (!fabric) nativeImports += legacy
                ? "import net.minecraftforge.common.MinecraftForge;\nimport net.minecraftforge.event.RegisterCommandsEvent;\n"
                : "import net.neoforged.neoforge.common.NeoForge;\nimport net.neoforged.neoforge.event.RegisterCommandsEvent;\n";
        String loop = "            for (CommandArgument<?> portableArgument : spec.arguments()) {";
        String content = COMMAND.formatted(packageSuffix, prefix,
                fabric ? "import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;\n" : "",
                nativeImports,
                fabric ? "CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {"
                        : (legacy ? "MinecraftForge" : "NeoForge") + ".EVENT_BUS.addListener((RegisterCommandsEvent event) -> {",
                modernPermissions ? "hasPermission(source, spec.permissionLevel())" : "source.hasPermission(spec.permissionLevel())",
                loop, fabric ? "dispatcher" : "event.getDispatcher()",
                modernPermissions ? PERMISSIONS + "\n" : "", modernPermissions ? "name" : "getName",
                (legacy ? LEGACY_CONTEXT : MODERN_CONTEXT).stripTrailing());
        String outputRoot = "uk/co/enderfall/sdk/runtime/" + packageSuffix.replace('.', '/') + "/";
        return List.of(new RuntimeSource(canonical, outputRoot + prefix + (modernPermissions ? "26" : "") + "CommandBridge.java",
                content.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String PERMISSIONS = """
                private static boolean hasPermission(CommandSourceStack source, int level) {
                    return switch (level) {
                        case 0 -> true;
                        case 1 -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
                        case 2 -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
                        case 3 -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
                        default -> source.permissions().hasPermission(Permissions.COMMANDS_OWNER);
                    };
                }
            """;
    private static final String MODERN_CONTEXT = """
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
            """;
    private static final String LEGACY_CONTEXT = """
                private record PortableCommandContext(CommandSourceStack source, Map<String, Object> arguments)
                        implements uk.co.enderfall.sdk.api.command.CommandContext {
                    @Override public String sourceName() { return source.getTextName(); }
                    @Override public Optional<UUID> sourcePlayerId() {
                        ServerPlayer player = source.getPlayer();
                        return player == null ? Optional.empty() : Optional.of(player.getUUID());
                    }
                    @Override public void reply(String message) {
                        source.sendSuccess(() -> Component.literal(message), false);
                    }
                    @Override public void replyTranslation(String translationKey, Object... arguments) {
                        source.sendSuccess(() -> Component.translatable(translationKey, arguments), false);
                    }
                }
            }
            """;
    private static final String COMMAND = """
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
            %3$simport net.minecraft.commands.CommandSourceStack;
            import net.minecraft.commands.Commands;
            import net.minecraft.commands.arguments.EntityArgument;
            import net.minecraft.network.chat.Component;
            import net.minecraft.server.level.ServerPlayer;
            %4$simport uk.co.enderfall.sdk.api.command.CommandArgument;
            import uk.co.enderfall.sdk.api.command.CommandSpec;
            
            final class %2$sCommandBridge {
                private %2$sCommandBridge() {
                }
            
                static void register(CommandSpec spec) {
                    %5$s
                        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(spec.name())
                                .requires(source -> %6$s);
                        List<RequiredArgumentBuilder<CommandSourceStack, ?>> nativeArguments = new ArrayList<>();
            %7$s
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
                                ArgumentBuilder<CommandSourceStack, ?> previous = nativeArguments.isEmpty()
                                        ? root : nativeArguments.get(nativeArguments.size() - 1);
                                previous.executes(command -> execute(spec, command));
                            }
                            nativeArguments.add(child);
                        }
                        if (nativeArguments.isEmpty()) {
                            root.executes(command -> execute(spec, command));
                        } else {
                            nativeArguments.get(nativeArguments.size() - 1)
                                    .executes(command -> execute(spec, command));
                            for (int index = nativeArguments.size() - 2; index >= 0; index--) {
                                nativeArguments.get(index).then(nativeArguments.get(index + 1));
                            }
                            root.then(nativeArguments.get(0));
                        }
                        %8$s.register(root);
                    });
                }
            
            %9$s    private static ArgumentType<?> argumentType(CommandArgument<?> argument) {
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
                                case PLAYER -> EntityArgument.getPlayer(context, argument.name()).getGameProfile().%10$s();
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
            
            %11$s
            """;
}
