# Architecture

The SDK uses three deliberate boundaries:

1. `enderfall-sdk-api` is compiled with `--release 17` and has no Minecraft or loader
   dependency.
2. `runtime-core` implements rules that must be identical everywhere: lifecycle
   freezing, event isolation, TOML repair, packet framing, capability negotiation,
   diagnostics, and deterministic data output.
3. A target runtime translates those contracts to one exact Minecraft/loader pair.

Consumer target projects are created during settings evaluation. Each target compiles
the portable source set independently with Java 17, compiles native escape-hatch roots
with that target's toolchain, generates loader metadata, and creates a separately named
JAR. Duplicate resources fail before packaging.

Architectury is not a runtime dependency. Adapter implementations use public Fabric,
NeoForge, or Forge hooks. A narrowly scoped target Mixin is allowed only when a public
hook cannot provide the required baseline behavior, and must have a contract test and
an explanatory comment.

## Failure policy

Duplicate IDs, duplicate packet/config declarations, and registration after the
initialization gate closes are startup errors containing the consumer mod ID and target.
Event listeners are isolated: a failing listener is logged and unrelated listeners
continue in priority order. Adapter callbacks must publish on the documented game
thread.
