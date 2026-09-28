# EnderFall SDK

EnderFall SDK is a Java-first, loader-neutral API and build system for producing
separate Minecraft mod artifacts from one portable source tree.

Portable block-entity development includes shared inventory snapshots, saved-state
definitions, and generated native persistence slices for all nine targets. These remain
opt-in development artifacts and are not packaged into release runtimes. The
[isolated development demo](examples/persistent-preview/README.md) exercises that work;
the remaining live acceptance boundaries are tracked explicitly.
See [block-entity implementation status](docs/block-entities.md).

The project is in pre-release development. The current implementation contains the
Java 17 API contract, shared runtime services, reviewed target catalog, Gradle settings
plugin, working Fabric/Forge/NeoForge reference runtimes for all nine catalog targets,
generated-runtime bridge compiler paths for all nine targets, a portable
contract test mod, process-level integration harness, publishing safeguards, and a
separate CC0 starter. All nine artifacts compile from one unchanged portable source tree.
The reference-runtime dedicated-server, client-lifecycle, and exact same-loader packet
matrices have passed all nine targets. Release candidates now run a 17-check generated
runtime foundation scenario for every target, covering registration, configuration,
event isolation, commands, player actions, networking, menus, crafting and rejection
paths from one matching portable source tree. The exact-revision automated matrix and
the manual release-candidate pass remain mandatory gates, so this is still a pre-release
baseline.

The generated-runtime compiler now emits every native Java class from shared
operations and reviewed ABI policies, with no canonical Java copying or contextual
patching needed for the nine supported targets. The old runtime folders remain opt-in
historical fixtures, with their Maven publication disabled; normal builds and releases
no longer configure them. Local starter/demo publishing uses the generated runtimes,
and consumers still write one portable source tree. See [the bridge architecture](docs/bridge-compiler.md).
Generated SDK projects are created from `gradle/runtime-projects.properties` and
shared build-family scripts; no per-target generated-runtime build entry scripts
need maintaining. Existing project names and build output paths remain unchanged.

Normal development and release builds are reference-free. To inspect the retired
handwritten fixtures, add `'-Penderfall.referenceRuntimes=true'` to a Gradle command.
That opt-in mode exists for historical investigation only: the fixtures are stale and
their old byte-parity checks are not release evidence. The switch does not delete their
files, build outputs or saved worlds.

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
documented in [the service setup checklist](docs/repository-and-services.md). The protected
release workflow now collects and audits complete, exact-revision runtime evidence before
it can reach any publication step.

The full showcase lives in `examples/demo-mod`. It keeps item, block, creative-tab,
configuration, networking, command, event, gameplay, and data registration in separate
classes. Its playable resonance loop uses atomic inventory costs, item rewards, healing,
experience, action-bar feedback, cooldowns, and server-authoritative interactions. The
workbench also uses the experimental inventory-menu API: portable recipe definitions,
real synchronized slots, target-native serializers/codecs, server-side matching and input
consumption, and an opt-in synchronized recipe browser with protected result previews on
every target. Mods do not provide loader-specific screens or packets for it. This sits
alongside original textures and hand-authored multi-element models. See
`docs/feature-status.md` for the exact implemented and proposed feature boundaries.

## Validation status

`buildAll` proves the Java and Gradle implementation builds. `serverSmoke` and
`clientSmoke` write separate machine-readable evidence under
`build/reports/runtime-smoke`; `sameLoaderSmoke` records external client/server network
evidence without claiming cross-loader compatibility. `verifyRuntimeMatrix` binds the
fresh server, client/UI, and connected-gameplay evidence to the exact release commit.
Coordinated publication remains blocked until that matrix and the manual
release-candidate pass are complete.
