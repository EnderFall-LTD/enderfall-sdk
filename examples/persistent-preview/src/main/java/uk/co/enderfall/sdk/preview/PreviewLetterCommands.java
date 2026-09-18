package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.command.Arguments;
import uk.co.enderfall.sdk.api.command.CommandArgument;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.gameplay.PlayerInventorySlot;

/** Exercises the same serverbound slot-update shape used by Furnection's letter screen. */
public final class PreviewLetterCommands {
    private static final CommandArgument<Integer> SLOT = Arguments.integer("slot");
    private static final CommandArgument<String> TEXT = Arguments.greedyString("text");

    private PreviewLetterCommands() { }

    public static void register(ModContext context) {
        context.commands().register(CommandSpec.builder("enderfall_letter")
                .description("Signs a portable letter in carried slot 0-35; use -1 for off-hand.")
                .argument(SLOT)
                .argument(TEXT)
                .executes(command -> {
                    var playerId = command.sourcePlayerId();
                    if (playerId.isEmpty()) {
                        command.reply("Run this command as a player.");
                        return 0;
                    }
                    String text = command.argument(TEXT);
                    if (text.codePointCount(0, text.length()) > 288) {
                        command.reply("Letter text cannot exceed 288 characters.");
                        return 0;
                    }
                    final PlayerInventorySlot slot;
                    try {
                        int value = command.argument(SLOT);
                        slot = value == -1 ? PlayerInventorySlot.offHand()
                                : PlayerInventorySlot.carried(value);
                    } catch (IllegalArgumentException invalid) {
                        command.reply(invalid.getMessage());
                        return 0;
                    }
                    boolean updated = context.players().updateItemData(playerId.get(), slot,
                            PreviewItems.PORTABLE_LETTER, data -> {
                                data.set(PreviewLetterItem.TEXT, text);
                                data.set(PreviewLetterItem.AUTHOR, command.sourceName());
                            });
                    if (!updated) {
                        command.reply("That slot no longer contains the portable letter.");
                        return 0;
                    }
                    String savedAuthor = context.players().itemData(playerId.get(), slot,
                            PreviewItems.PORTABLE_LETTER, PreviewLetterItem.AUTHOR).orElse("missing");
                    String savedText = context.players().itemData(playerId.get(), slot,
                            PreviewItems.PORTABLE_LETTER, PreviewLetterItem.TEXT).orElse("missing");
                    command.reply("Saved and read back letter by " + savedAuthor + ": " + savedText);
                    return 1;
                }).build());
    }
}
