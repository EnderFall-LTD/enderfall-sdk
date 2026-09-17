# EnderFall SDK

EnderFall SDK is a Java-first, loader-neutral API and build system for producing
separate Minecraft mod artifacts from one portable source tree.

Portable block-entity development includes shared inventory snapshots, saved-state
definitions, and a compiled Fabric 1.21.4 native persistence development slice. It is
not packaged into release runtimes. An [isolated development demo](examples/persistent-preview/README.md)
now launches on that target; live save/reload validation is still pending.
See [block-entity implementation status](docs/block-entities.md).

The project is in pre-release development. The current implementation contains the
Java 17 API contract, shared runtime services, reviewed target catalog, Gradle settings
plugin, working Fabric/Forge/NeoForge reference runtimes for all nine catalog targets,
generated-runtime bridge compiler paths for all nine targets, a portable
contract test mod, process-level integration harness, publishing safeguards, and a
separate CC0 starter. All nine artifacts compile from one unchanged portable source tree.
The reference-runtime dedicated-server, client-lifecycle, and exact same-loader packet
matrices have passed all nine targets. The expanded eleven-check generated-runtime
menu/workbench scenario, including full-inventory craft rejection and recovery,
also passed all nine targets with matching portable sources and clean exits.
An intermittent legacy login issue is still tracked in
[the migration status](docs/bridge-migration.md).
Deeper foundation-feature assertions and the manual
release-candidate pass remain release gates, so this is still a pre-release baseline.

The generated-runtime compiler now emits every native Java class from shared
operations and reviewed ABI policies, with no canonical Java copying or contextual
patching needed for the nine supported targets. The old runtime folders remain
reference fixtures pending broader acceptance and retirement, with their Maven
publication disabled. Local starter/demo publishing uses the generated runtimes;
consumers still write one portable source tree. See [the bridge architecture](docs/bridge-compiler.md).
Generated SDK projects are created from `gradle/runtime-projects.properties` and
shared build-family scripts; no per-target generated-runtime build entry scripts
need maintaining. Existing project names and build output paths remain unchanged.

For reference-free development, add `'-Penderfall.referenceRuntimes=false'` to a
Gradle command such as `generateAllBridges` or `compileGeneratedRuntimes`.
This excludes the handwritten runtime projects, not just their compilation tasks.
Reference parity tasks are omitted in this mode and generated runtime publishing
is blocked. The default still includes references until retirement is complete;
the switch does not delete their files, build outputs or saved worlds.

## Coordinates

- Maven group: `uk.co.enderfall.sdk`
- Gradle plugin: `uk.co.enderfall.sdk`
- Runtime mod ID: `enderfall_sdk`
- Documentation: <https://sdk.enderfall.co.uk>

## Build

Run `./gradlew buildAll` on Linux/macOS or `gradlew.bat buildAll` on Windows.
The wrapper only needs one Java 17-or-newer installation; target toolchains are
provisioned independently.

Useful verification tasks are:

- `checkAll` — unit tests, plugin functional tests, and pinned-dependency checks.
- `generateAllBridges` — generates every target currently implemented by the bridge compiler.
- `verifyBridgeCoverage` — verifies the typed ABI catalog and generated/reference parity.
- `verifyGameplayMatrix -Penderfall.gameplayReports=<oldest.json>,<newer.json>,...` —
  audits all nine targets from explicit gameplay reports and their logs; a newer failure
  cannot be hidden by an older pass. See [gameplay testing](docs/gameplay-testing.md).
- `generatedBridgeGameplaySmoke` — exercises real connected menu actions, workbench
  slot crafting, and input return against generated runtimes; see [gameplay tests](docs/gameplay-testing.md).
- `generatedBridgeServerSmoke` — launches contract servers from an isolated repository
  containing the centrally generated runtimes instead of handwritten references.
- `generatedBridgeClientSmoke` — launches contract clients from that generated-only
  repository and waits for lifecycle/tick readiness.
- `checkContractTargets` — unchanged portable test-mod build for all nine targets.
- `checkDemoMod` — structured visual demo build for all nine targets.
- `serverSmoke` — real dedicated-server startup and clean shutdown for all nine targets.
- `clientSmoke` — real client startup, resources, ticks, and clean shutdown for all nine targets.
- `sameLoaderSmoke` — a real client/server packet round trip on each identical loader/version target.
- `serverSmoke -Penderfall.smokeTarget=1.21.4-fabric` — one server target only.
- `clientSmoke -Penderfall.smokeTarget=1.21.4-fabric` — one client target only.
- `generatedBridgeServerSmoke -Penderfall.smokeTarget=1.21.4-fabric` — one generated
  runtime server target only.
- `generatedBridgeClientSmoke -Penderfall.smokeTarget=1.21.4-fabric` — one generated
  runtime client target only.
- `releaseChecksums` — reproducible JAR build plus SHA-256 manifest.
- `cyclonedxBom` — CycloneDX JSON and XML SBOMs.
- `checkApiCompatibility -Penderfall.previousApi=<jar>` — japicmp release gate.
- `publishWorkspace` — local Maven repository used to test the sibling template.
- `centralBundle` — isolated, signed Maven Central archive; requires in-memory test or release signing credentials and never uploads by itself.

The API is published as `uk.co.enderfall.sdk:enderfall-sdk-api`, the shared runtime as
`uk.co.enderfall.sdk:enderfall-sdk-runtime-core`, and coordinated versions through
`uk.co.enderfall.sdk:enderfall-sdk-bom`.

## Compatibility promise

Portable code compiles only against `enderfall-sdk-api`. Native Minecraft and
loader types are intentionally unavailable to that source set. A build produces
one artifact per selected Minecraft/loader target, not one universal JAR.

Target-native bindings are being migrated from handwritten runtime projects into a
central bridge compiler. The existing projects remain temporary reference
implementations until generated replacements pass compile and in-game parity gates.

See `docs/architecture.md`, `docs/bridge-compiler.md`, and `docs/compatibility.md` for
the detailed contract.

Public repository, Pages, DNS, Maven Central, and Gradle Plugin Portal ownership are
documented in [the service setup checklist](docs/repository-and-services.md). Release
publication remains blocked until the runtime acceptance gate is replaced with complete
evidence.

The full showcase lives in `examples/demo-mod`. It keeps item, block, creative-tab,
configuration, networking, command, event, gameplay, and data registration in separate
classes. Its playable resonance loop uses atomic inventory costs, item rewards, healing,
experience, action-bar feedback, cooldowns, and server-authoritative interactions. The
workbench also uses the experimental inventory-menu API: one portable recipe definition,
real synchronized slots, target-native serializers/codecs, server-side matching and input
consumption, and native rendering on every target. This sits
alongside original textures and hand-authored multi-element models. See
`docs/feature-status.md` for the exact implemented and proposed feature boundaries.

## Validation status

`buildAll` proves the Java and Gradle implementation builds. `serverSmoke` and
`clientSmoke` write separate machine-readable evidence under
`build/reports/runtime-smoke`; `sameLoaderSmoke` records external client/server network
evidence without claiming cross-loader compatibility. Coordinated publication remains
blocked until the deeper feature matrix and manual
release-candidate pass are complete.
