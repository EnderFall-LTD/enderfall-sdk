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

## Authoring a library mod

A reusable library opts into coordinated publication in the settings DSL:

```kotlin
enderfallSdk {
    mod {
        id = "enderui"
        name = "EnderUI"
        group = "uk.co.enderfall"
        version = "2.0.0"
        entrypoint = "uk.co.enderfall.enderui.EnderUi"
        clientEntrypoint = "uk.co.enderfall.enderui.EnderUiClient"
    }

    library {
        apiArtifact = "enderui-api"
        targetArtifact = "enderui-{minecraft}-{loader}"
    }
}
```

Place the public, loader-neutral Java 17 contract under `src/api/java`. Library
implementation stays in `src/main/java` and `src/client/java`. API sources are part of each
runtime mod JAR so the classes are present in-game, but the separately published API JAR
contains only `src/api`; it cannot accidentally expose implementation, Minecraft or loader
classes. All portable roots may import the API classes normally.

The plugin exposes:

- `publishLibraryWorkspace`, which publishes the API and every selected runtime beneath
  `build/library-repository` for local multi-repository development;
- `publishLibraryToMavenLocal`, which publishes the same coordinated set to Maven local;
- `publishLibrary`, when `repositoryUrl` is configured, which publishes the coordinated set
  to that Maven repository.

For an authenticated release repository, set standard Gradle credentials without committing
them:

```kotlin
library {
    apiArtifact = "enderui-api"
    targetArtifact = "enderui-{minecraft}-{loader}"
    repositoryUrl = "https://maven.example.com/releases"
}
```

```properties
# ~/.gradle/gradle.properties or protected CI variables
enderfallLibraryReleaseUsername=...
enderfallLibraryReleasePassword=...
```

The artifact pattern must contain both placeholders. This prevents publication from silently
overwriting one loader/version with another. `buildAll` and `checkAll` include the API project
when library mode is enabled, while ordinary non-library mods retain their existing tasks and
layout.
