package uk.co.enderfall.sdk.testmod;

import java.util.List;
import java.util.Map;
import uk.co.enderfall.sdk.api.EnderfallMod;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.command.Arguments;
import uk.co.enderfall.sdk.api.command.CommandArgument;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.config.ConfigHandle;
import uk.co.enderfall.sdk.api.config.ConfigKey;
import uk.co.enderfall.sdk.api.config.ConfigScope;
import uk.co.enderfall.sdk.api.config.ConfigSpec;
import uk.co.enderfall.sdk.api.config.ConfigType;
import uk.co.enderfall.sdk.api.config.ConfigValidator;
import uk.co.enderfall.sdk.api.data.Ingredient;
import uk.co.enderfall.sdk.api.data.ModelSpec;
import uk.co.enderfall.sdk.api.data.RecipeResult;
import uk.co.enderfall.sdk.api.data.ShapedRecipeSpec;
import uk.co.enderfall.sdk.api.data.ShapelessRecipeSpec;
import uk.co.enderfall.sdk.api.data.TagSpec;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.network.PacketCodec;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.network.PacketRequirement;
import uk.co.enderfall.sdk.api.network.PacketType;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemRef;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

/** The same portable fixture is compiled unchanged for every supported runtime target. */
public final class ContractTestMod implements EnderfallMod {
    private static final CommandArgument<String> WORD = Arguments.word("word");
    private static final CommandArgument<String> MESSAGE = Arguments.string("message");
    private static final CommandArgument<Integer> COUNT = Arguments.integer("count");
    private static final CommandArgument<Boolean> ENABLED = Arguments.bool("enabled");
    private static final CommandArgument<String> PLAYER = Arguments.optional(Arguments.player("player"));
    private static final CommandArgument<String> GREEDY = Arguments.greedyString("message");

    @Override
    public void initialize(ModContext context) {
        ItemRef hammer = context.items().register("hammer",
                ItemSpec.builder().maxStackSize(1).durability(512).build());
        BlockRef testBlock = context.blocks().registerWithItem("test_block",
                BlockSpec.builder().strength(2.0F, 6.0F).requiresTool().build(), ItemSpec.builder().build());
        context.creativeTabs().register("main", CreativeTabSpec.builder("tab.enderfall_sdk_test.main", hammer)
                .entry(hammer)
                .entry(new ItemRef(testBlock.id()))
                .build());

        ConfigSpec.Builder configBuilder = ConfigSpec.builder();
        ConfigKey<Boolean> enabled = configBuilder.booleanValue("feature.enabled", true,
                "Enables the contract fixture.");
        ConfigKey<Integer> maximum = configBuilder.integer("feature.maximum", 8, 1, 64,
                "Maximum echo count.");
        configBuilder.longValue("feature.timeout_ticks", 200L, 1L, 72_000L,
                "Long-valued timeout used by the contract fixture.");
        configBuilder.decimal("feature.multiplier", 1.0D, 0.0D, 10.0D,
                "Finite decimal used by the contract fixture.");
        configBuilder.string("feature.greeting", "hello", "Portable string setting.");
        configBuilder.enumeration("feature.mode", Mode.NORMAL, "Portable enum setting.");
        configBuilder.list("feature.labels", ConfigType.STRING, List.of("alpha", "beta"), 8,
                "Bounded portable list setting.");
        configBuilder.value("feature.private_token", ConfigType.STRING, "change-me",
                "Sensitive values must never appear in logs.",
                ConfigValidator.matching(value -> !value.isBlank(), "must not be blank"), true);
        ConfigHandle config = context.configs().register("features", ConfigScope.COMMON, configBuilder.build());
        ConfigSpec.Builder serverConfigBuilder = ConfigSpec.builder();
        serverConfigBuilder.booleanValue("enabled", true, "World-specific fixture toggle.");
        context.configs().register("world", ConfigScope.SERVER, serverConfigBuilder.build());

        PacketType<Echo> echo = new PacketType<>(context.id("echo"), 1, PacketDirection.BIDIRECTIONAL,
                PacketRequirement.REQUIRED, 1024, new EchoCodec());
        context.networking().register(echo,
                (message, network) -> context.logger().info("Echo {} x{}", message.message(), message.count()));

        context.events().subscribe(SdkEvents.TICK,
                event -> {
                    if (event.side() == uk.co.enderfall.sdk.api.event.TickEvent.Side.SERVER
                            && event.tick() == 1 && config.get(enabled)) {
                        context.logger().debug("Contract fixture active with maximum {}", config.get(maximum));
                    }
                });
        context.events().subscribe(SdkEvents.LIFECYCLE,
                event -> context.logger().info("Lifecycle {} on {}", event.stage(), context.platform().targetId()));
        context.events().subscribe(SdkEvents.PLAYER, event -> context.logger().info(
                "Player {} {} (network ready: {})", event.playerName(), event.action(),
                context.networking().remoteSupports(event.playerId(), echo)));
        context.events().subscribe(SdkEvents.INTERACTION, event -> context.logger().debug(
                "Interaction {} with {}", event.kind(), event.target()));

        if (!context.platform().isModLoaded("enderfall_sdk")) {
            throw new IllegalStateException("Required EnderFall SDK runtime was not reported as loaded");
        }

        context.commands().register(CommandSpec.builder("enderfall_contract")
                .description("Exercises the loader-neutral command bridge.")
                .permissionLevel(2)
                .argument(WORD)
                .argument(MESSAGE)
                .argument(COUNT)
                .argument(ENABLED)
                .argument(PLAYER)
                .suggests((command, remaining) -> List.of("alpha", "beta"))
                .executes(command -> {
                    command.reply("word=" + command.argument(WORD)
                            + ", message=" + command.argument(MESSAGE)
                            + ", count=" + command.argument(COUNT)
                            + ", enabled=" + command.argument(ENABLED)
                            + ", player=" + command.arguments().getOrDefault(PLAYER.name(), "none"));
                    return 1;
                })
                .build());
        context.commands().register(CommandSpec.builder("enderfall_contract_say")
                .description("Exercises the portable greedy-string argument and translated reply.")
                .argument(GREEDY)
                .executes(command -> {
                    command.replyTranslation("command.enderfall_sdk_test.say", command.argument(GREEDY));
                    return 1;
                })
                .build());

        context.dataGeneration().register(data -> {
            data.shapedRecipe(context.id("hammer"), new ShapedRecipeSpec(List.of("II", " S"),
                    Map.of('I', Ingredient.item(context.id("test_block")),
                            'S', Ingredient.item(uk.co.enderfall.sdk.api.ResourceId.of("minecraft", "stick"))),
                    new RecipeResult(hammer.id(), 1)));
            data.shapelessRecipe(context.id("test_block_from_hammer"), new ShapelessRecipeSpec(
                    List.of(Ingredient.item(hammer.id())), new RecipeResult(testBlock.id(), 1)));
            data.itemModel(hammer.id(), new ModelSpec(ModelSpec.Kind.HANDHELD_ITEM,
                    context.id("item/hammer")));
            data.blockModel(testBlock.id(), new ModelSpec(ModelSpec.Kind.CUBE_ALL_BLOCK,
                    context.id("block/test_block")));
            data.selfDrop(testBlock.id());
            data.tag(context.id("contract_items"), new TagSpec(TagSpec.Registry.ITEMS,
                    List.of(hammer.id()), false));
            data.translation("en_gb", "item.enderfall_sdk_test.hammer", "Contract Hammer");
            data.translation("en_gb", "block.enderfall_sdk_test.test_block", "Contract Block");
            data.translation("en_gb", "tab.enderfall_sdk_test.main", "EnderFall Contract");
            data.translation("en_gb", "command.enderfall_sdk_test.say", "%s");
        });
    }

    private enum Mode {
        NORMAL,
        STRICT
    }

    public record Echo(String message, int count) {
    }

    private static final class EchoCodec implements PacketCodec<Echo> {
        @Override
        public void encode(uk.co.enderfall.sdk.api.network.PacketWriter writer, Echo value) {
            writer.writeString(value.message(), 512);
            writer.writeVarInt(value.count());
        }

        @Override
        public Echo decode(uk.co.enderfall.sdk.api.network.PacketReader reader) {
            return new Echo(reader.readString(512), reader.readVarInt());
        }
    }
}
