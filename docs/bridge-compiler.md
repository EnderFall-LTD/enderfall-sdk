# Bridge compiler architecture

EnderFall mods have one portable source tree. The SDK compiles that source once
against the EnderFall API and generates the native loader and Minecraft bindings
needed by every selected supported target.

```text
portable mod bytecode
        |
        v
runtime-core and portable feature models
        |
        v
bridge compiler + reviewed target ABI catalog
        |
        v
generated target Java/resources
        |
        v
isolated Loom or ModDevGradle compilation
        |
        v
one JAR for each selected target
```

## Source-of-truth rule

Target-native Java and metadata are generated build output. A supported target
must not gain a new handwritten `runtime-<loader>-<minecraft>` source tree.
Target differences belong in typed, reusable ABI facets in the bridge compiler.

Generation fails before writing output if any source declaration is neither
handled by a shared emitter nor explicitly omitted/combined. Updating the canonical
manifest alone cannot introduce a source-copy fallback.

The facets describe independent compatibility boundaries such as loader
bootstrap, registry lifecycle, Minecraft identifiers, recipes, menus, payloads,
mappings, and Java toolchains. A target catalog entry composes those facets.
When a new target reuses existing ABIs, it should require only a catalog entry.
When a native contract changes, one reusable facet is added or updated.

## Generated native shells

Some generated classes must implement or extend Minecraft and loader types,
including loader entrypoints, recipes, recipe inputs, menus, screens, and custom
payloads. They contain minimal binding code and delegate portable behaviour to
`runtime-core`.

These generated shells are not part of the public EnderFall API and are never
written by consumer mod authors.

### Shared recipe/menu binding declarations

`WorkbenchBindingEmitter` is the first shared declaration emitter. It constructs
recipe and workbench registration records for all nine targets from one field
model and a typed registry-handle policy:

| Registry family | Generated handle |
| --- | --- |
| Fabric | Direct recipe, serializer, and menu values |
| Modern NeoForge | `Supplier<T>` |
| Legacy Forge/NeoForge | `RegistryObject<T>` |

The target plan also describes combined-file layouts and the recipe identifier
required by NeoForge 26.2. A shared formatter preserves existing file names,
whitespace, and record component order so source/class golden files stay unchanged.
The emitter does not read or rewrite canonical Java text. During this transition,
the two manifest-listed binding paths select the feature: neither means no
workbench bindings; a partial pair fails before any generated files are written.
The canonical manifest remains validated, including the retained baseline records.

The old binding-specific source patches have been removed. This was the first
semantic-generator slice; the platform-service section below describes completion
of the remaining source migration. Reference runtime retirement is a separate gate.
No consumer sources or public APIs change.

### Shared 1.20.1 legacy recipes

`LegacyWorkbenchRecipeEmitter` emits the complete container recipe and nested
JSON/network serializer for Fabric, Forge, and NeoForge 1.20.1. Shared matching,
count bounds, defensive copies, and wire ordering are defined once. Explicit
registry policies retain Fabric's direct references and Forge-family handles,
including their existing JSON error behavior. Forge and NeoForge emit identical
Java. Recipe and binding paths select the feature; canonical Java text is not
read as a template. Partial declarations and unreviewed targets fail closed.

Existing golden hashes and all nine build-parity lanes remain unchanged.

### Shared 1.21 codec recipes

`CodecWorkbenchRecipeEmitter` emits the complete positional recipe and its JSON/wire
serializer for Fabric and NeoForge on 1.21.1 and 1.21.4. One shared implementation
constructs the fields, defensive copies, positional matching, counted ingredient
codec, exact-slot-count decoder guard, and encoder. Typed recipe ABI selection
chooses the 1.21.1 result/dimension methods or 1.21.4 placement/category methods.
Loader policy preserves Fabric's deferred serializer binding and NeoForge's
registry-supplier access; formatting preserves the existing source/class goldens.

These four recipes no longer depend on canonical source text or contextual source
replacement. The manifest-listed recipe, binding, and input declarations select
the feature; a partial feature fails closed. Integrity checks still cover the
retained canonical files even after their recipe implementations have migrated.

### Shared 26.2 template-result recipes

`RecordWorkbenchRecipeEmitter` emits both 26.2 recipe implementations from shared
matching, result-template, JSON-codec, and stream-codec logic. Fabric retains its
supplier-based record serializer, resolving bindings only at JSON construction or
network decode. NeoForge retains its direct codec factories and combined immutable
recipe-input declaration. Registry access, recipe groups, native method layout,
count bounds, and wire ordering remain unchanged. Complete manifest-listed feature
declarations are required; no canonical recipe text is used as an output template.

All nine recipes now use shared ABI-family emitters. This step changes the compiler,
not the portable API, recipe format, runtime wire format, or gameplay behavior.

### Shared 1.21.x slot menus

`WorkbenchMenuEmitter` emits the Fabric and NeoForge 1.21.1/1.21.4 slot containers from
one implementation. The registry access policy distinguishes direct references
from deferred handles; native method layout is retained for source/class parity.
Input and result slots, shift-click routing, server-side recipe lookup and result
broadcasts, craft revalidation, counted consumption, and close cleanup are shared.
Menu declarations require their recipe, input, and binding declarations. Recipes
without a menu remain valid. Canonical menu files stay manifest-checked but their
text is no longer used to emit these four menus. The 1.21.1 policy selects the
older recipe-manager lookup, registry access, and direct recipe-holder ID. Its
menu-specific source patches are removed. This does not add new menu behavior or
prove live sync.

### Remaining slot menus and workbench screens

`ContainerWorkbenchMenuEmitter` shares slot construction, quick-move routing,
close cleanup, result updates, ingredient consumption, and craft notification
across all three 1.20.1 targets and both 26.2 targets. Policies preserve direct
recipes versus recipe holders, explicit versus registered menu types, registry
lookup, and template-result assembly. All nine slot menus now use shared emitters.

`WorkbenchScreenEmitter` emits all nine workbench screens with shared slot/arrow
geometry, colors, and labels. Native policies retain 1.20.1 background rendering,
the Forge-family blend hook, and 26.2 extracted render state and text calls.
Screen and menu declarations remain manifest checked. The old menu and workbench
screen patch lists are removed; output goldens remain independent and unchanged.
This does not prove fresh visual/gameplay behavior. Reference runtime trees remain in place.

### Shared label/button portable screens

`PortableMenuScreenEmitter` emits the existing synchronized label/button screen
for all nine targets from one implementation. Typed immediate/extracted rendering
policies select graphics, text, render hooks, and client screen access together.
Shared code retains widget creation, action dispatch, state replacement, button
text updates, main-thread execution, session-ID filtering, and exactly-once local
close notification with server-close echo suppression. The manifest-listed screen
path selects the feature; its Java text is no longer an emission template.
Legacy FML and 26.2 portable-screen patch lists are removed. This is generator
migration, not new UI functionality or fresh visual/interaction validation.

### Shared platform information

`PlatformInfoEmitter` emits loader identity, Minecraft version, environment, and
installed-mod queries for all nine targets. Explicit loader policies select
Fabric metadata versus FML mod-container queries, legacy FML loader-marker
identity, and the changed 26.2 environment accessor. Marker failures, version
formatting, and native method layout are preserved. The canonical platform-info
path selects this feature; its Java text is not rewritten. The subsequent shared
emitters below cover lifecycle, commands, networking, and registration too.
This does not change runtime feature support.

### Shared consumer entrypoint bootstraps

`ConsumerBootstrapEmitter` emits all nine consumer entrypoint bridges from shared
initialization logic. Fabric retains the thread context class loader and its
context lookup method. Modern NeoForge retains the supplied event bus and class
loader. Legacy FML retains event-bus acquisition through its loading context and
the supplied class loader. Initialization, duplicate detection, and adapter
attachment remain in the original order; signatures and binary names are unchanged.
The canonical bootstrap declaration requires its platform-adapter declaration,
but its Java text is no longer an output template. Loader-owned SDK startup and
platform services are handled by their own shared emitters below.

### Shared loader-owned runtime entrypoints

`RuntimeEntrypointEmitter` emits all nine SDK marker entrypoints. It preserves
Fabric's `ModInitializer` contract, modern/legacy FML's `@Mod` annotation, and
the existing public constructors and binary names. These classes deliberately
do not initialize consumer contexts; that remains the consumer bootstrap's job.
The legacy entrypoint patch is removed. Canonical declarations remain integrity
checked, and inherited source descriptions are retained for output parity.
Platform registration/services are covered by the shared operation emitter below.

### Shared modern NeoForge client hooks

`ClientHooksEmitter` emits client hooks for NeoForge 1.21.1, 1.21.4, and 26.2.
One implementation preserves client start/stop publication, pre/post tick ordering,
screen registration, smoke-test shutdown, and portable screen session forwarding.
The 26.2 policy selects its workbench types and `ClientPacketDistributor`; 1.21.x
uses `PacketDistributor`. Output retains the existing source and binary names.
The former 26.2 client-hook text patch is removed. Selecting hooks requires their
binding, workbench screen, raw payload, and portable screen declarations; partial
feature selections fail generation. These source-contract checks do not prove live callbacks.

`FabricClientHooksEmitter` now covers all four Fabric targets with one lifecycle
implementation and modern payload versus legacy bounded-channel transport facets.
`LegacyClientHooksEmitter` shares both 1.20.1 Forge-family hooks, preserving event
phase ordering, remote-channel guards, and the opt-in NeoForge smoke connector.
No client-hook implementation uses canonical Java text patches anymore.

### Shared commands, payloads, and recipe inputs

`CommandBridgeEmitter` emits all nine command bridges. Registration callbacks,
numeric versus 26.2 permission checks, and game-profile access are explicit native
policies; argument conversion, suggestions, execution, and replies are shared.
Formatting policies preserve the reference bytecode/source layout during migration.
`RawPayloadEmitter` shares defensive byte copies and pre-allocation length checks
across the six modern targets. `WorkbenchInputEmitter` shares the five standalone
immutable-list recipe input wrappers; NeoForge 26.2 keeps its input in the shared
recipe emitter. Neither emits a modern payload/input type on legacy targets.

`SharedRuntimeSources` is the central emitter composition. A regression test scans
both declaration profiles for every target and rejects duplicate outputs, emitted
declarations that should be omitted, or any unmigrated Java declaration.
Canonical declarations still undergo manifest validation; their Java text no longer
drives native output.

### Shared platform operations

`PlatformServiceEmitter` assembles a native service class from named operations:

| Operation family | Compiler source | Responsibilities |
| --- | --- | --- |
| State and initialization | `PlatformStateSources` | Imports, fields, construction, context attachment, config directories |
| Registration | `PlatformRegistrationSources` | Items, blocks, creative tabs, recipes, workbenches, native property mapping |
| Networking | `PlatformNetworkingSources` | Channel/payload registration, sending, receiving, bounds and native directions |
| Gameplay | `PlatformGameplaySources` | Player inventory/actions, session forwarding, server/player lookup |
| Events | `PlatformEventSources` | Lifecycle, ticks, join/leave, interactions and cancellation mapping |

`NativePlatformPolicy` selects reviewed registry/event/identifier/transport facets
from the target's loader and Minecraft ABIs. Shared operations are defined once;
genuine native differences remain explicit operation variants inside the SDK.
`PlatformOperation` provides a typed operation list, and `RuntimeSourceLayout`
records declarations that are omitted or combined. Missing required declarations,
unreviewed targets, unavailable operations, and unresolved template names fail
generation. Native binary names and member layout remain unchanged for parity.

There is no runtime source-text patching stage. The old `SourcePatch`, Fabric and
NeoForge source transformers, and legacy/26.2 patch rule classes have been removed.
`DeclarationOnlyGenerationTest` replaces every Java implementation in a temporary
canonical fixture with a declaration comment, rebuilds its fixture manifest, and
requires identical source/resource digests on all nine targets. The real canonical
manifest and reference goldens are not rewritten. This proves implementation
independence, not support for unknown future Minecraft APIs.

## Portability boundary

The portability guarantee covers stable features expressed through EnderFall
API types. Arbitrary source that imports Minecraft, Fabric, Forge, or NeoForge
types cannot be converted reliably and is outside that guarantee.

## Migration gate

The existing runtime projects remain reference implementations until generated
targets pass all of the following:

1. canonical and generated source/resource integrity checks;
2. compilation against the exact pinned target classpath and Java toolchain;
3. compiled class and packaged metadata parity where applicable;
4. unit and adapter contract tests;
5. dedicated-server and client smoke tests; and
6. in-game registration, menu, recipe, networking, and interaction tests.

Only then may the corresponding handwritten runtime project be removed.

All nine reviewed catalog targets now have implemented generation paths, including
`1.20.1-forge` and `1.20.1-neoforge`. Run
`verifyBridgeCoverage` from the repository root to test the compiler, validate
Gradle coordinates against the reviewed catalog, compare generated trees, and
build the remapped artifacts. Fabric 1.21.4 requires whole-JAR byte identity.
Fabric 1.21.1 requires exact source/class/pack and non-metadata JAR-entry
identity; its generated `fabric.mod.json` deliberately adds the Fabric API runtime
dependency missing from the old reference. Passing these gates proves build-output
parity. The 26.2 output uses Java 25 and official unobfuscated names and requires
whole-JAR byte identity with its working reference. The NeoForge 1.21.1 and 1.21.4
outputs are compiled with ModDevGradle and require exact source, class, resource,
coordinate, and whole shaded-JAR byte identity. Every native Java declaration is
emitted or explicitly omitted where the target uses a different native ABI.
NeoForge 26.2 and Fabric 1.20.1 also require exact source, class, resource, and
whole-JAR parity. The implementation no longer depends on source transformation
anchors. Manifest checks, explicit declaration dependencies, and unchanged golden
outputs guard the shared semantic implementation instead.

The two 1.20.1 Forge-family targets derive identical twelve-file Java output from
shared emitters using one legacy FML policy and the central NeoForge declarations. Their three resources
include legacy `mods.toml`, a loader identity marker, and pack metadata. Both use
ModDevGradle legacy support and Java 17 through one shared build script. Parity
checks cover the development shaded JAR and the final reobfuscated JAR separately.
No additional checked-in target-native source profile is needed.

## Generated-only runtime smoke tests

### Catalog-created internal build projects

`gradle/runtime-projects.properties` routes each reviewed target to one shared
build family under `gradle/targets`: remapped Fabric, unobfuscated Fabric, modern
NeoForge, or legacy FML. Settings creates the project descriptors directly; there
are no target-owned `build.gradle` entry scripts. Empty project directories are
created on a fresh checkout to satisfy Gradle's directory requirements. Existing
`runtime-generated-<loader>-<version>/build` paths and project IDs remain stable.

Generation, parity, and both workspace-publication tasks discover these projects
instead of maintaining separate target lists. A compiler regression test requires
the routing catalog to match `TargetCatalog` exactly, verifies loader/mapping ABI
families, and rejects reintroduced per-target entry scripts. Catalog and family
script changes are declared test inputs, so cached tests cannot hide routing drift.
This routing catalog does not admit new Minecraft support by itself; native ABI,
coordinate, artifact, and runtime acceptance checks still apply.

`publishWorkspace` now publishes generated runtimes to `build/repository` for the
starter, contract mod, and demo. Reference runtime projects have no publishing
repositories, Maven publication tasks are disabled, and they cannot overwrite the
same coordinates. Generated Maven publications require `verifyBridgeCoverage`.
Reference projects remain available for compilation/parity checks, not publication.
External release gates and credentials are unchanged; local publication is not a release.

`publishGeneratedBridgeWorkspace` publishes common SDK artifacts and the generated
Fabric 1.20.1/1.21.1/1.21.4/26.2, Forge 1.20.1, and NeoForge
1.20.1/1.21.1/1.21.4/26.2 runtimes to
`build/generated-bridge-repository`. It deliberately does not publish the corresponding
handwritten runtime artifacts. The consumer smoke tests therefore fail instead of
silently falling back to a reference implementation.

For the legacy Forge-family targets this local repository preserves the reference
development-named publication used by the consumer harness. The reobfuscated JARs
under each target's `build/libs` are checked separately; a development launch is
not evidence that installation through a normal production launcher has passed.

Run `generatedBridgeServerSmoke` and `generatedBridgeClientSmoke` from the repository
root. Select one target with `-Penderfall.smokeTarget=1.21.4-neoforge`, or a comma-separated
list such as `-Penderfall.smokeTarget=1.20.1-forge,1.20.1-neoforge`. Quote the complete
property argument in PowerShell. These tasks use a
build-local Gradle user home so IDE imports cannot hold mapped Minecraft JARs open.
Dependency preparation is logged and timed separately from the game launch. Modern
NeoForge and legacy Forge-family targets run the selected-target `prepareClient` or `prepareServer` proxy
before the timed launch so asset and native run preparation cannot consume the game
timeout.

When both generated smoke tasks are selected together, the server lane finishes
before the client lane starts to avoid overlapping native preparation and launches.

All nine catalog targets have passed these generated-only client and dedicated-server
checks, including both 1.20.1 Forge-family targets. This proves resource loading,
lifecycle/tick readiness, dedicated-server client-class isolation, and clean shutdown.
Startup checks alone do not prove interactive menu/recipe behavior or client/server
networking round trips. The separate [gameplay harness](gameplay-testing.md) records
focused end-to-end assertions and the remaining acceptance boundaries.
