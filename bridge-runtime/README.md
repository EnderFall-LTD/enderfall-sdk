# EnderFall canonical bridge runtime

This directory holds reviewed feature declarations and retained parity baselines.
The executable source of truth is the shared operation emitters in `bridge-compiler`.
Generated target projects consume their output, never handwritten
`runtime-<loader>-<minecraft>` source trees.

## Current migration stage

`src/canonical` is a byte-for-byte baseline of the working Minecraft 1.21.4
Fabric runtime. It deliberately preserves the existing class and package names
while the generation pipeline is proved against the reference runtime.

The compiler also derives Fabric 1.20.1, 1.21.1, and 26.2 from these declarations.
Shared semantic emitters produce every selected Java class, including the platform
services. All Java declarations must be emitted or explicitly omitted/combined;
unhandled declarations fail before output is written. A regression test substitutes
declaration comments for every canonical
Java implementation and requires identical generated output for all nine targets.

`src/neoforge` is the centrally reviewed modern NeoForge profile. It emits
NeoForge 1.21.4, 1.21.1, and 26.2 through the same shared emitters with native
registration and transport policies. The full manifest
covers both central profiles; target generation never reads the old runtime folders.
The same NeoForge baseline now also produces both 1.20.1 Forge-family runtimes via
one shared legacy FML rule set: identical twelve-file Java output, separate legacy
loader metadata and identity markers. No third baseline profile was added.

Recipe and workbench binding declarations now come from the compiler's shared
`WorkbenchBindingEmitter`, rather than copied records or target-specific source
patches. The retained manifest entries select that feature and remain integrity
checked; their Java text is no longer an emission template. Output is kept
byte-identical while the rest of the migration continues.

`CodecWorkbenchRecipeEmitter` now emits complete recipe/serializer classes for
1.21.1 and 1.21.4 Fabric/NeoForge from one shared implementation. Those targets use
the recipe/binding/input paths as feature declarations, not Java text templates.
`LegacyWorkbenchRecipeEmitter` emits all three 1.20.1 recipe/serializer classes
from shared matching, JSON, and wire-format logic, with explicit direct-reference
versus registry-handle policies. `RecordWorkbenchRecipeEmitter` now emits both
26.2 recipe implementations using shared template-result and codec logic, retaining
Fabric's lazy binding resolution and NeoForge's direct factories. All nine recipe
implementations now use shared ABI-family emitters, not canonical recipe text
patches. `WorkbenchMenuEmitter` now emits all four 1.21.1/1.21.4 slot menus from one shared
implementation with explicit registry and recipe-lookup policies.
`ContainerWorkbenchMenuEmitter` covers the remaining 1.20.1 and 26.2 slot menus
using direct-recipe versus recipe-holder policies. `WorkbenchScreenEmitter`
emits all nine workbench screens from shared geometry/labels and explicit
immediate versus extracted-render-state hooks. Recipe, slot-menu, and workbench
screen output no longer depends on canonical Java text patches.
`PortableMenuScreenEmitter` also emits all nine label/button screens from one
implementation, preserving main-thread dispatch, session filtering, state updates,
and close-notification guards. `PlatformInfoEmitter` emits platform identity,
environment detection, and mod-presence/version queries for all nine targets,
including the legacy FML loader marker. `ConsumerBootstrapEmitter` emits consumer
entrypoint initialization for all nine targets, preserving event-bus acquisition,
class-loader selection, duplicate checks, and context attachment.
`RuntimeEntrypointEmitter` emits all nine loader-owned SDK marker entrypoints,
preserving Fabric's initializer and FML's mod annotation without initializing
consumer contexts there. `ClientHooksEmitter` emits the shared client lifecycle,
tick, screen-registration, and payload-send hooks for modern NeoForge 1.21.1,
1.21.4, and 26.2. `FabricClientHooksEmitter` covers all four Fabric targets;
`LegacyClientHooksEmitter` covers both legacy FML targets, including their existing
channel guards and test-only ping-before-connect sequence. `CommandBridgeEmitter`
shares commands across all nine targets. `RawPayloadEmitter` and
`WorkbenchInputEmitter` share modern payload and recipe-input records. NeoForge
26.2 keeps its input record inside the shared recipe emitter; legacy targets
retain their channel/container ABIs.

`SharedRuntimeSources` composes these emitters. Its coverage regression test
requires every canonical Java declaration to be emitted or explicitly omitted,
with no platform-service exception. `PlatformServiceEmitter` assembles named state,
registration, networking, gameplay, and event operations using `NativePlatformPolicy`.
Common inventory and lifecycle operations are shared; native API differences remain
explicit SDK-owned facets. The contextual Java patch machinery has been removed.

Reference source retirement and catalog-created SDK target projects remain separate
migration stages. Keep the baselines until their broader in-game acceptance gates
pass; source/artifact parity alone does not authorize removing them.

Each generated parity project writes only beneath its own `build/generated`
directory. No generated Java or resources are committed.

`MANIFEST.sha256` records the exact canonical baseline. Bridge verification must
fail when the reference, canonical input, generated output, or compiled class
set differs unexpectedly.
