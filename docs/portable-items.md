# Portable item definitions

Class-based portable items keep item behavior in recognizable item classes while the
SDK owns loader registration and event wiring. A definition is ordinary Java 17 and
does not extend a Minecraft or loader class:

```java
public final class WrenchItem implements PortableItem {
    private static final ItemDataKey<Integer> USES = ItemDataKey.integer(
            ResourceId.parse("example:uses"), 0, 1_000_000);

    @Override
    public void configure(ItemSpec.Builder properties) {
        properties.maxStackSize(1)
                .durability(256)
                .data(USES)
                .tooltip("tooltip.example.wrench.summary")
                .shiftHint("tooltip.example.hold_shift")
                .shiftTooltip("tooltip.example.wrench.details");
    }

    @Override
    public void onUseOnBlock(ModContext context, InteractionEvent event) {
        MutableItemData data = event.itemData().orElseThrow();
        int uses = data.getOrDefault(USES, 0) + 1;
        data.set(USES, uses);
        MutableItemStack stack = event.itemStack().orElseThrow();
        if (!event.creativeMode()) {
            stack.damage(1);
        }
        context.players().actionBar(event.playerId(),
                "Wrench uses: " + uses + ", durability: " + stack.remainingDurability());
        event.handle();
    }
}
```

Register it without a terminal `build()` call:

```java
private static final Registration.Items ITEMS = Registration.items(MOD_ID);
public static final ItemRef WRENCH = ITEMS.item("wrench", WrenchItem::new);

public static void register(ModContext context) {
    Registration.register(context, ITEMS);
}
```

The factory runs once during registration preflight. `configure` runs before optional
registration-site overrides, so a registry class can deliberately adjust a reusable
definition:

```java
ITEMS.item("reinforced_wrench", WrenchItem::new,
        properties -> properties.durability(512));
```

The SDK routes unhandled server-side right-click-item and use-on-block events on all
supported targets. `onUseOnBlock` receives the exact block
location, held item, main/off-hand identity and crouching state through the interaction
event. It runs before the target portable block callback; leaving the event untouched
allows block and native fallback behavior to continue.

`itemStack()` is a safe view of the real held stack. It exposes count, stack limit,
damage, maximum and remaining durability, plus server-only `damage`, `repair`, and
`consume` operations. Negative mutations are rejected, repairs clamp at zero damage,
consumption clamps at the current count, and durability damage reports whether the item
broke. `creativeMode()` lets a definition follow vanilla-style no-cost creative use.
These deterministic operations do not roll Unbreaking automatically; call them only
after the portable action has actually succeeded.

Tooltip lines can be literal or translated, coloured, and shown always, while Shift is
held, while Shift is not held, or only with advanced tooltips. The short builder methods
above cover the common translated Shift pattern; use `tooltip(TooltipLine)` for full
control.

Portable stack data supports bounded strings, booleans, integers, longs and doubles.
Every key must be declared with `properties.data(key)`. Undeclared or differently
bounded keys fail instead of silently writing arbitrary data. Interaction callbacks
receive the actual held stack and may mutate it only on the server. The bridge stores
the data under an SDK-owned compound in legacy item NBT on 1.20.1 and vanilla
`CUSTOM_DATA` on component-era targets. Minecraft therefore copies, saves, drops and
synchronizes it as part of the normal item stack.

For serverbound editors, never trust a native inventory index and mutate it directly.
Use a logical `PlayerInventorySlot`, the expected `ItemRef`, and the player manager:

```java
PlayerInventorySlot slot = PlayerInventorySlot.carried(packet.slot());
boolean updated = context.players().updateItemData(playerId, slot, LETTER, data -> {
    data.set(LETTER_TEXT, packet.text());
    data.set(LETTER_AUTHOR, playerName);
});
```

Slots are expressed as hotbar 0-8, main inventory 0-26, or off-hand; armor is not
silently addressable. The native bridge rechecks the online player, server thread,
actual slot item and expected registered item before exposing its declared keys. A
stale packet therefore returns `false` rather than writing to whatever item replaced
the editor's original stack. `itemData(...)` provides the matching typed read path.

Portable menus can now own the editable screen as well. Declare bounded fields directly
in the menu layout; no client entrypoint, packet class, native widget, or loader-specific
screen registration is needed:

```java
MenuRef editor = context.menus().register("letter_editor",
        MenuSpec.builder("Letter")
                .size(230, 190)
                .textInput(MenuTextInput.multiline(
                        "letter.text", "Write...", 15, 35, 200, 100, 288, 16))
                .button(MenuButton.of("save", "Save", 15, 150, 60))
                .build(), action -> {
                    context.players().updateItemData(action.playerId(), slotFrom(action.state()),
                            LETTER, data -> data.set(LETTER_TEXT, action.input("letter.text")));
                    action.close();
                });
```

Drafts remain on the client until a declared action is pressed. The runtime sends every
declared field together, then validates the active session, action, exact field set,
Unicode code-point limit, UTF-8 byte limit, single/multiline policy and line count before
calling consumer code. Slot identity still belongs in server-owned `MenuState`; never
accept it from an editable field. The persistent preview's `/enderfall_letter_edit`
command demonstrates Save, Sign, Cancel, reopening existing text and stale-slot rejection.
Live copy/drop/reconnect acceptance remains a manual gameplay check.
