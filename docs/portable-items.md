# Portable item definitions

Class-based portable items keep item behavior in recognizable item classes while the
SDK owns loader registration and event wiring. A definition is ordinary Java 17 and
does not extend a Minecraft or loader class:

```java
public final class WrenchItem implements PortableItem {
    @Override
    public void configure(ItemSpec.Builder properties) {
        properties.maxStackSize(1).durability(256);
    }

    @Override
    public void onUse(ModContext context, InteractionEvent event) {
        context.players().actionBar(event.playerId(), "The portable wrench was used");
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

The current completed slice routes unhandled server-side right-click-item events on
all supported targets. It does not yet expose the used hand or mutable stack. Portable
use-on-block context, shift state, tooltips and per-stack durable data are the next item
contracts; until they land, those behaviors are not claimed portable.
