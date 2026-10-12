# Quick start

Create a repository from `EnderFall-LTD/enderfall-sdk-template`, then initialize its identity:

```bash
./gradlew initializeMod \
  -PmodId=my_mod \
  -PmodName="My Mod" \
  -PmodPackage=com.example.mymod \
  -PmodAuthor="Your Name"
```

The task is intentionally one-shot. It validates the mod ID and Java package, updates
the settings DSL and source package, and removes the `.enderfall-template` marker.

Build all selected targets:

```bash
./gradlew buildAll
```

Artifacts are collected in `build/releases` using
`<mod-id>-<mod-version>+mc<minecraft>-<loader>.jar`.

## Dependency repositories

Repositories are resolved in the order they are declared. The SDK plugin adds Maven
Central as a **last fallback**, so any repository you declare yourself wins:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("../build/repository") } // a locally published SDK
        mavenCentral()                            // the SDK adds this too; yours keeps priority
    }
}
```

This matters when a version of the SDK exists both locally and on Maven Central. A
repository declared above Central always supplies the artifact, so a local workspace
or development build cannot be silently shadowed by the released one. Loader
repositories (Fabric, NeoForged) are group-filtered and never serve SDK coordinates.

Use `./gradlew enderfallDoctor` to see the exact catalog versions and selected
development target. Select another development target with, for example:

```bash
./gradlew runClient -Penderfall.target=1.21.4-fabric
./gradlew runServer -Penderfall.target=1.20.1-forge
./gradlew generateData -Penderfall.target=26.2-neoforge
```

On PowerShell, quote properties containing the target value, for example
`.\gradlew.bat runServer '-Penderfall.target=1.20.1-forge'`. The selected target must be
present in the settings DSL. These development runs are not a substitute for the
release matrix recorded in `targets.md`.
