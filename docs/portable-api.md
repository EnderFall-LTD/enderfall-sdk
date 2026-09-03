# Portable API

Common entrypoints implement `EnderfallMod`; optional client entrypoints implement
`EnderfallClientMod`.

```java
public final class ExampleMod implements EnderfallMod {
    @Override
    public void initialize(ModContext context) {
        context.items().register("example_item", ItemSpec.builder().build());
    }
}
```

The stable foundation covers lifecycle/environment information, basic items and blocks,
creative tabs, tick/server/player/interaction events, commands, typed configuration,
play-phase packets, portable recipes/tags/translations/loot/models, structured logging,
and dependency checks.

Entities, block entities, menus, screens, rendering, complex world generation, custom
data components, and broad native wrappers are intentionally outside the 1.0 foundation.
Use a native source root for those until a later portable module defines a contract.

APIs marked `@Experimental` are outside compatibility guarantees. APIs marked
`@CapabilityGated` have identical source signatures on every target, but callers must
check `context.capabilities()` where an old game genuinely cannot implement the feature.
