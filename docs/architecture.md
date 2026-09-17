# Architecture

The SDK uses four deliberate boundaries:

1. `enderfall-sdk-api` is compiled with `--release 17` and has no Minecraft or loader
   dependency.
2. `runtime-core` implements rules that must be identical everywhere: lifecycle
   freezing, event isolation, TOML repair, packet framing, capability negotiation,
   diagnostics, and deterministic data output.
3. `bridge-compiler` owns the reviewed Minecraft and loader ABI catalog and emits
   the target-native Java and metadata needed by each supported combination.
4. Generated target projects compile that output against one exact
   Minecraft/loader classpath and toolchain.

Consumer target projects are created during settings evaluation. Portable consumer
bytecode is isolated from Minecraft and loader dependencies. The bridge compiler
generates loader metadata and native shells into build output, and each generated
target creates a separately named JAR. Duplicate resources fail before packaging.

Architectury is not a runtime dependency. Generated bindings use public Fabric,
NeoForge, or Forge hooks. A narrowly scoped generated target Mixin is allowed only when
a public hook cannot provide the required baseline behavior, and must have a contract
test and an explanatory reason in the corresponding bridge ABI facet.

The current handwritten runtime projects remain temporary reference implementations
while generated replacements pass differential compilation and in-game acceptance.
All native classes on the generated path now come from shared operation emitters;
canonical Java text is retained for integrity/parity checks, not used as an output
template. Version/loader differences live in explicit SDK-owned native policies.
See [Bridge compiler architecture](bridge-compiler.md).

## Failure policy

Duplicate IDs, duplicate packet/config declarations, and registration after the
initialization gate closes are startup errors containing the consumer mod ID and target.
Event listeners are isolated: a failing listener is logged and unrelated listeners
continue in priority order. Generated bridge callbacks must publish on the documented
game thread.
