# Library-mod dependencies

EnderFall consumers can declare another mod as a portable API plus one native runtime
artifact per Minecraft/loader target. Portable source compiles only against the Java 17
API artifact; generated target projects resolve the matching runtime artifact and add the
loader metadata dependency.

```kotlin
enderfallSdk {
    dependencies {
        required("enderui", "2.0.0") {
            api = "uk.co.enderfall:enderui-api:2.0.0"
            target = "uk.co.enderfall:enderui-{minecraft}-{loader}:2.0.0"
        }
    }
}
```

For `1.21.4-fabric`, the example resolves
`uk.co.enderfall:enderui-1.21.4-fabric:2.0.0`; for `26.2-neoforge`, it resolves
`uk.co.enderfall:enderui-26.2-neoforge:2.0.0`. Both placeholders are required when a
target coordinate is supplied so one loader artifact cannot accidentally be used on a
different target.

The generated metadata uses `>=2.0.0` on Fabric and `[2.0.0,)` on Forge/NeoForge.
Set `exactVersion = true` for `=2.0.0` and `[2.0.0]` respectively. Optional dependencies
are emitted as Fabric `suggests` or Forge/NeoForge optional dependencies:

```kotlin
optional("recipe_viewer", "1.4.0") {
    api = "com.example:recipe-viewer-api:1.4.0"
    target = "com.example:recipe-viewer-{minecraft}-{loader}:1.4.0"
    side = "client"
    ordering = "after"
    developmentRuntime = true
}
```

`api` and `target` are independently optional. Omitting `target` creates a metadata/API
dependency without a development runtime artifact. Omitting `api` is suitable when the
portable source does not import that library. A required dependency defaults to being
present in development runs; an optional dependency defaults to compile-only unless
`developmentRuntime` is enabled.

This contract handles consumption and generated loader metadata. Publishing a consumer's
separate API artifact and all target runtime artifacts is a later library-authoring slice;
ordinary target JAR collection continues to work as before.
