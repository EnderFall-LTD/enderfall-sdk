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
    public void onUse(ModContext context, InteractionEvent event) {
        MutableItemData data = event.itemData().orElseThrow();
        int uses = data.getOrDefault(USES, 0) + 1;
        data.set(USES, uses);
        context.players().actionBar(event.playerId(), "Wrench uses: " + uses);
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

The current interaction view is sufficient for item-use state such as counters and
tool modes. Secure editing of an arbitrary player inventory slot, needed by the letter
screen, is the remaining durable-data API slice.
