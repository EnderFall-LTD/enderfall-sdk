package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.command.Arguments;
import uk.co.enderfall.sdk.api.command.CommandArgument;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.gameplay.PlayerInventorySlot;
import uk.co.enderfall.sdk.api.ui.MenuButton;
import uk.co.enderfall.sdk.api.ui.MenuLabel;
import uk.co.enderfall.sdk.api.ui.MenuSpec;
import uk.co.enderfall.sdk.api.ui.MenuState;
import uk.co.enderfall.sdk.api.ui.MenuTextInput;

/** Complete server-authorized letter editor using only the portable screen API. */
public final class PreviewLetterEditor {
    private static final CommandArgument<Integer> SLOT = Arguments.integer("slot");

    private PreviewLetterEditor() { }

    public static void register(ModContext context) {
        var editor = context.menus().register("letter_editor", MenuSpec.builder("Portable Letter")
                .size(230, 190)
                .label(MenuLabel.text("Author: {letter.author}", 15, 24))
                .textInput(MenuTextInput.multiline(
                        "letter.text", "Write your letter...", 15, 35, 200, 100, 288, 16))
                .button(MenuButton.of("save", "Save", 15, 150, 60))
                .button(MenuButton.of("sign", "Sign", 85, 150, 60))
                .button(MenuButton.of("cancel", "Cancel", 155, 150, 60))
                .build(), action -> {
                    if (action.action().equals("cancel")) {
                        action.close();
                        return;
                    }
                    PlayerInventorySlot slot = decodeSlot(action.state().value("letter.slot"));
                    boolean sign = action.action().equals("sign");
                    boolean updated = context.players().updateItemData(action.playerId(), slot,
                            PreviewItems.PORTABLE_LETTER, data -> {
                                data.set(PreviewLetterItem.TEXT, action.input("letter.text"));
                                if (sign) {
                                    data.set(PreviewLetterItem.AUTHOR,
                                            action.state().value("letter.editor"));
                                }
                            });
                    if (updated) {
                        context.players().message(action.playerId(),
                                sign ? "Letter saved and signed." : "Letter saved.");
                    } else {
                        context.players().message(action.playerId(),
                                "The selected slot no longer contains that letter.");
                    }
                    action.close();
                });

        context.commands().register(CommandSpec.builder("enderfall_letter_edit")
                .description("Opens the portable letter editor for slot 0-35; use -1 for off-hand.")
                .argument(SLOT)
                .executes(command -> {
                    var player = command.sourcePlayerId();
                    if (player.isEmpty()) {
                        command.reply("Run this command as a player.");
                        return 0;
                    }
                    final PlayerInventorySlot slot;
                    try {
                        slot = decodeSlot(Integer.toString(command.argument(SLOT)));
                    } catch (IllegalArgumentException invalid) {
                        command.reply(invalid.getMessage());
                        return 0;
                    }
                    // The no-op mutation performs an atomic expected-item check on the server thread.
                    if (!context.players().updateItemData(player.get(), slot,
                            PreviewItems.PORTABLE_LETTER, ignored -> { })) {
                        command.reply("That slot does not contain a portable letter.");
                        return 0;
                    }
                    String text = context.players().itemData(player.get(), slot,
                            PreviewItems.PORTABLE_LETTER, PreviewLetterItem.TEXT).orElse("");
                    String author = context.players().itemData(player.get(), slot,
                            PreviewItems.PORTABLE_LETTER, PreviewLetterItem.AUTHOR).orElse("Unsigned");
                    context.menus().open(player.get(), editor, MenuState.builder()
                            .value("letter.slot", encodeSlot(slot))
                            .value("letter.text", text)
                            .value("letter.author", author)
                            .value("letter.editor", command.sourceName())
                            .build());
                    return 1;
                }).build());
    }

    private static String encodeSlot(PlayerInventorySlot slot) {
        return slot.area() == PlayerInventorySlot.Area.OFF_HAND
                ? "-1"
                : Integer.toString(slot.area() == PlayerInventorySlot.Area.HOTBAR
                        ? slot.index() : slot.index() + 9);
    }

    private static PlayerInventorySlot decodeSlot(String encoded) {
        final int value;
        try {
            value = Integer.parseInt(encoded);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Letter slot must be -1 or a number from 0 to 35");
        }
        return value == -1 ? PlayerInventorySlot.offHand() : PlayerInventorySlot.carried(value);
    }
}
