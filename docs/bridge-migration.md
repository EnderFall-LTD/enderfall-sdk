# Bridge runtime migration

The bridge compiler is replacing checked-in target adapter Java with generated
native shells. Consumer mods keep one portable source tree; each selected target
still receives its own compiled runtime and mod JAR.

!!! info "Current status"
    Generation and build parity now cover all nine catalog targets. The two 1.20.1
    Forge-family runtimes share one legacy rule set and emit identical Java, with
    separate metadata. All nine targets have completed generated-only client and
    dedicated-server lifecycle smoke tests. A focused connected gameplay scenario
    now has passing evidence on all nine targets using identical portable sources.
    Two consecutive observed/unobserved Forge repeats also passed. The earlier
    intermittent login timeout remains unexplained; launch reliability and broader
    foundation acceptance remain separate open gates.

Recorded Windows validation includes successful `verifyBridgeCoverage`, API,
shared-runtime, Gradle plugin functional tests, and gameplay harness tests.
The follow-up harness run now passes 39 tests (four additional regression tests),
bringing the recorded total to 154 passes and one Windows symlink test skipped
(the compiler accounts for 69 passes and that skip). The follow-up changed test
tooling only; it did not rerun every runtime lane or change the SDK binaries.
The final follow-up invocation passed both `verifyBridgeCoverage` for all nine
targets and `verifyGameplayMatrix` with all seven explicitly ordered reports.
Its unchanged test tasks reused their successful up-to-date results.
On 2026-09-08, generated Forge 1.20.1 passed server/client lifecycle checks in
169,080/63,223 ms; generated NeoForge 1.20.1 passed in 131,278/44,430 ms.
These timings exclude dependency preparation. Target logs remain under
`build/reports/generated-bridge-runtime-smoke/{server,client}`; aggregate JSON reports
describe only the most recent invocation, not the entire historical matrix.
Earlier Fabric 1.21.1/1.21.4 client and Fabric 1.21.4 server success evidence is
retained under `build/reports/generated-bridge-runtime-smoke-isolated`.

Both new legacy FML targets passed source/class/resource parity and byte identity
for both development and reobfuscated runtime JARs. Their shared twelve-source
output and distinct metadata are independently hash-locked by regression tests.

## Connected gameplay evidence (2026-09-08)

The [generated-runtime gameplay harness](gameplay-testing.md) drives a real client
against a separate same-target dedicated server. It requires nine independent
assertions: server menu action, displayed confirmed state, insufficient-input
rejection, normal craft, shift craft, returned inputs, empty reopened slots, and
server/client completion. The three-leg SDK packet exchange and clean process
exits are required in addition to those assertions.

| Generated target | Focused gameplay result | Duration including dependency preparation |
| --- | --- | ---: |
| 1.20.1 Fabric | Passed all nine assertions; both exits 0 | 1,285,831 ms |
| 1.20.1 Forge | Passed all nine assertions on unobserved repeat; both exits 0 | 546,952 ms |
| 1.20.1 NeoForge | Passed all nine assertions on retry; both exits 0 | 232,766 ms |
| 1.21.1 Fabric | Passed all nine assertions; both exits 0 | 600,664 ms |
| 1.21.1 NeoForge | Passed all nine assertions; both exits 0 | 257,293 ms |
| 1.21.4 Fabric | Passed all nine assertions; both exits 0 | 436,766 ms |
| 1.21.4 NeoForge | Passed all nine assertions; both exits 0 | 276,689 ms |
| 26.2 Fabric | Passed all nine assertions; both exits 0 | 266,095 ms |
| 26.2 NeoForge | Passed all nine assertions; both exits 0 | 300,820 ms |

All nine passing targets used portable-source SHA-256
`bc8d53c045e0f3ec829afb87a1cdd20a9874360ba670af415d4937392d5ec515`,
identical between client and server. The Fabric/modern NeoForge report is retained at
`build/reports/generated-bridge-gameplay/connection-20260908-representative-initial.json`.
That initial report also records the failed Forge preparation; it is not an
all-green matrix report. The passing Forge retry is archived as
`connection-20260908-forge-retry.json`; the next six-target batch is archived as
`connection-20260908-remaining-initial.json`. That batch records five passes and
the legacy NeoForge connection failure. Per-target game logs remain under the
`connection` subdirectory, with the failed legacy NeoForge logs separately retained
under `legacy-neoforge-initial-20260908` before retrying.

`connection-20260908-legacy-retry.json` records the successful NeoForge retry
and failed Forge follow-up. The latter's game and debug logs are retained under
`forge-followup-failed-20260908`. The passing Forge-only diagnostic retry is
archived as `connection-20260908-forge-diagnostic-retry.json`. It used the same
SDK build and portable fixture, with temporary network/Mojang debug logging enabled.
No Forge login fix is claimed: the intermittent stall still needs diagnosis and
repeat-run reliability testing. The temporary logging options were not retained.

The first Forge retry only changed the native test driver's 1.20.1 item-ID lookup.
The legacy connector now waits for a real pong and Forge status data instead of
the favicon-change callback. Both generated and retained reference legacy builds
pass source/class and development/reobfuscated JAR parity after that change.
NeoForge passed its in-game retry. Forge's follow-up stalled during the native
login handshake, before the SDK packet scenario; its later passing diagnostic
retry supersedes that failure, rather than relying on the earlier 358,878 ms pass.
Portable fixture sources remain unchanged.

Two subsequent named Forge runs passed all nine assertions and clean exits without
changing the SDK runtime or portable sources:

- `runs/forge-observe-1/connection.json`: 807,224 ms with the first read-only channel
  observer. Its logs show HANDSHAKING, LOGIN, then PLAY. This first observer did not
  yet sample NIO read-interest fields.
- `runs/forge-unobserved-1/connection.json`: 546,952 ms with the observer explicitly
  disabled. The enclosing Gradle invocation also exited 0. The extended NIO observer
  compiled here but was not enabled; its added samples are not yet live-validated.

These are two consecutive gameplay passes, not a root-cause diagnosis or login fix.
Named runs preserve per-attempt preparation/game logs; loader debug logs were also
copied alongside these two runs. The first combined command had a test-compilation
failure caused by editing while compilation was in progress; its independent game
task passed, and a separate clean harness test rerun passed all 39 tests. Each named
run includes a `run-notes.md` recording that distinction.

Use `verifyGameplayMatrix` with the five archives above in the order listed, followed
by the two named run reports. The last supplied attempt for each target is
authoritative. The resulting
`build/reports/generated-bridge-gameplay/matrix.json` audits all nine targets,
the source hashes, individual gameplay checks, clean exits, and real log markers.
This combines explicitly selected historical evidence; it does not attest newly
edited binaries or erase intermittent failures. See [gameplay testing](gameplay-testing.md).

The first fixture attempt exposed an existing player-inventory limitation:
vanilla/other-mod `ItemRef` lookups are not resolved by that service. The focused
scenario now uses registered fixture items and does not claim to fix that gap.
These tests do not constitute the full in-game contract/removal gate below.

## Measured reference inventory

The current handwritten Fabric, Forge, and NeoForge reference runtimes contain
66 Java files (about 8,246 lines) and 20 metadata/resource files (about 260
lines). These measurements exclude `runtime-core`, canonical bridge input,
generated output, and build directories.

| Semantic subsystem | Java files | Approximate lines | Generation boundary |
| --- | ---: | ---: | --- |
| Bootstrap and platform identity | 10 | 318 | Loader entrypoint, mod bus, class loader, environment, and mod lookup templates |
| Registration, events, and client hooks | 12 | 4,128 | Shared operations composed with typed registry, event, and networking ABI fragments |
| Commands | 5 | 627 | Shared command tree and executor with a loader/version registration hook |
| Typed payload classes | 2 | 73 | Shared bounded codec shape; legacy networking remains a distinct ABI |
| Menus, screens, workbench, and recipes | 37 | 3,100 | Shared behavior with loader registry-handle and Minecraft recipe/menu ABI facets |
| **Total** | **66** | **8,246** | |

The existing builds already demonstrate how much target code can be shared
exactly:

| Target | Java inherited unchanged | Checked-in replacement files |
| --- | ---: | ---: |
| Fabric 1.20.1 | 5 of the 1.21.4 Fabric classes | 6 files replacing or combining 9 classes |
| Fabric 1.21.1 | 11 of 14 | 3 |
| Fabric 26.2 | 8 of 14 | 6 |
| NeoForge 1.20.1 | All 12 Forge 1.20.1 classes | 0 |
| NeoForge 1.21.1 | 12 of 14 | 2 |
| NeoForge 26.2 | 3 of 14 | 9 files replacing or combining 11 classes |

This is exact source-directory reuse, rather than separately stored files that
happen to match. The generator should retain that property by emitting shared
templates and narrowly scoped ABI variations instead of copying whole adapters.

## Why native shells remain

Loader entrypoints and metadata are different on Fabric and the Forge family.
Registrations must run through the target loader lifecycle, while recipes,
payloads, menus, and screens must implement or extend classes from the selected
Minecraft version. Those native types are linked and verified by the JVM, so a
target-specific compiled shell and a separate target JAR are unavoidable.

Handwritten per-target adapters are not unavoidable. EnderFall owns the known
differences through the reviewed target catalog and typed facets for loader,
Minecraft, mappings, recipes, networking, and menus. The compiler can therefore
generate the native shell into an isolated build directory and compile it against
the exact target classpath. Consumer authors never maintain that shell.

The large platform adapters should be decomposed by responsibility before their
generation rules grow: shared player/gameplay operations, loader registration,
event wiring, and network transport. Unrestricted search-and-replace is not an
acceptable compatibility mechanism.

## Shared binding emitter follow-up

Recipe and workbench registration records now come from one
`WorkbenchBindingEmitter` field model across all nine targets. The model selects
direct values, suppliers, or legacy registry objects and preserves the reviewed
combined-file layouts. Four binding-specific source-patch blocks were removed.
Neither the canonical manifest nor any existing golden source hashes changed.

`./gradlew :bridge-compiler:test :integration-harness:test verifyBridgeCoverage`
passed after this refactor: 77 compiler tests and 39 harness tests passed, with
one existing Windows symlink skip. Source/class/resource/artifact parity passed
on all nine targets. Together with the unchanged earlier API/runtime/plugin
results, the recorded aggregate is now 162 passing tests and one skip.
No fresh client/server runs were performed for this byte-preserving refactor;
the gameplay evidence above remains a separate historical result.

This was the first declaration-level slice, not full semantic recipe/menu
generation. The next serializer slice is described below. The reference runtime
folders stay in place.

## Shared 1.21 recipe/serializer follow-up

`CodecWorkbenchRecipeEmitter` now constructs the complete positional recipe,
JSON codec, stream codec, counted-ingredient entry, matching logic, and defensive
copies for 1.21.1 and 1.21.4 on both Fabric and NeoForge. It uses one shared
implementation with typed ABI selection; it does not patch canonical recipe text.
The two old 1.21.1 recipe patch lists were removed, and the 1.21.4 baselines now
come through the same emitter. Existing source hashes and golden fixtures were
not changed. Anchor-rejection tests now exercise the still-patched menu sources.

The compiler and harness suites passed: 85 compiler tests and 39 harness tests,
with one existing Windows symlink skip. All nine target source/class/resource/JAR
parity gates passed. Eight new generator tests cover target selection, missing
declarations, registry-resolution timing, ABI methods, defensive copies, and the
emitted count/size guards. Those are generator/source-contract checks, not fresh
runtime fuzz tests. The recorded aggregate, including unchanged earlier module
results, is 170 passes and one skip.

No new game clients or servers were launched for this byte-preserving refactor.
The 1.20.1 and 26.2 recipe implementations, all slot-menu generation, and screens
remain on their previous generation paths. Consumer sources, public APIs, recipe
data, and wire formats remain unchanged.

## Shared legacy recipe emitter follow-up

`LegacyWorkbenchRecipeEmitter` now emits the complete 1.20.1 Fabric, Forge, and
NeoForge recipe/serializer classes. Shared matching, JSON structure, and wire
ordering replace the two legacy recipe source-patch lists. Explicit registry
policies preserve direct references versus handles and existing JSON exception
behavior. Forge and NeoForge continue emitting identical Java. Canonical feature
paths remain manifest-checked, but their recipe text is no longer a template for
these targets. No golden hashes were updated.

Validation: `:bridge-compiler:test :integration-harness:test verifyBridgeCoverage`
passed in 2m 18s. Compiler tests passed 93 with one Windows symlink skip; harness
tests passed 39. All nine source/class/resource and artifact parity lanes passed.
The eight new tests check emitted source contracts, not live codec fuzzing. No
clients or servers were launched during this refactor. The 26.2 recipes, slot menus,
and screens remain on their previous generation paths; reference runtimes stay
in place. Consumer code, public APIs, data, and wire formats are unchanged.

## Shared 26.2 recipe emitter follow-up

`RecordWorkbenchRecipeEmitter` now emits both 26.2 recipe implementations. The
Fabric and NeoForge recipe patch lists were removed. Shared matching, template
results, JSON codecs, and wire ordering retain the existing native differences:
Fabric resolves its supplier lazily, while NeoForge uses direct codec factories
and includes its immutable recipe-input record in the same generated file. All
nine targets now use shared recipe ABI-family emitters. No source goldens,
consumer APIs, recipe data, or runtime wire formats changed.

Validation: `:bridge-compiler:test :integration-harness:test verifyBridgeCoverage`
passed in 1m 53s. Compiler tests passed 101 with one Windows symlink skip; harness
tests passed 39. All nine source/class/resource and artifact parity lanes passed.
Eight new generator/source-contract tests cover target selection, partial
declarations, binding timing, input ownership, bounds, matching, and wire order.
These are not live fuzz or gameplay tests; no clients or servers were launched.
Reference runtimes remain, and slot menus and screens still await migration.

## Shared 1.21.4 slot-menu follow-up

`WorkbenchMenuEmitter` now emits both 1.21.4 Fabric/NeoForge slot containers from
one implementation with explicit registry access policy. Their canonical menu
files remain manifest-checked declarations, not output templates. Input/result
slots, shift-click routing, server-side result updates, recipe revalidation,
counted consumption, craft callbacks, and close cleanup retain their exact source
and compiled output. No existing golden hashes were changed.

Validation: `:bridge-compiler:test verifyBridgeCoverage` passed in 2m 15s. Compiler
tests passed 107 with one Windows symlink skip, including six new menu-emission
contract tests. All nine source/class/resource and artifact parity lanes passed.
The integration harness was not rerun in this step. No clients or servers were
launched; emitted-source tests do not independently prove live synchronization.
Other menu ABIs and all screens remain on their previous generation paths, and
reference runtime directories remain in place.

## Shared 1.21.1 slot-menu follow-up

The existing `WorkbenchMenuEmitter` now also emits both 1.21.1 menus. A reviewed
menu ABI policy selects recipe-manager versus server recipe-access lookup,
assembly registry access, and recipe-holder ID handling. All four 1.21.x menus
share the same slot, shift-click, cleanup, and crafting implementation. Both
1.21.1 menu patch lists were removed; source and artifact goldens are unchanged.

Validation: `:bridge-compiler:test verifyBridgeCoverage` passed in 1m 13s, with
109 compiler tests passing and one Windows symlink skip. All nine source/class/
resource and artifact parity lanes passed. Menu contract tests now cover all four
1.21.x targets. Two new tests replace the canonical menu text with a declaration
and regenerate its test manifest, proving that reviewed text no longer controls
emission. Unreviewed menu edits still fail the manifest check before output is
written. The previous menu-anchor tests now assert that integrity boundary.

No integration-harness or in-game runs were performed in this step. The 1.20.1
and 26.2 menu paths and all screens still await migration. Reference runtimes
remain in place; this is output-preserving compiler work, not new gameplay.

## Remaining menus and workbench screens follow-up

`ContainerWorkbenchMenuEmitter` now covers all three 1.20.1 slot containers and
both 26.2 containers. Shared slot and crafting flow retains direct-recipe versus
recipe-holder access, registration handles, registry lookups, and native method
layout. `WorkbenchScreenEmitter` emits all nine workbench screens from shared
geometry and labels with explicit rendering policies. All legacy/26.2 menu and
workbench-screen patch lists were removed. All nine recipe, slot-menu, and
workbench-screen implementations now use shared emitters. This does not include
the separate label/button portable-screen implementation.

Validation: `:bridge-compiler:test :integration-harness:test verifyBridgeCoverage`
passed in 1m 12s. Compiler tests passed 118 with one Windows symlink skip; harness
tests passed 39. All nine source/class/resource and artifact parity lanes passed
without golden updates. Five new menu tests and four screen tests cover target
selection, dependency declarations, recipe assembly, consumption order, registry
access, geometry, labels, blend hooks, and immediate/extracted rendering.

These are generator/source-contract and build-parity checks, not fresh gameplay
or visual tests. No game clients or servers were launched. Consumer APIs, recipe
data, native behavior, and resource-pack files are unchanged. Reference runtimes
remain pending the established removal gates.

## Shared portable label/button screen follow-up

`PortableMenuScreenEmitter` now emits all nine label/button screens from one
implementation. Typed rendering policies preserve immediate versus extracted
rendering, text calls, and client screen access. Shared code preserves button
actions, state resolution, session filtering, main-thread dispatch, and local
close notification without echoing server-initiated closure. The legacy FML and
26.2 screen patch lists were removed; existing source and artifact goldens are
unchanged. Canonical screen paths select the feature but their Java text is no
longer used as a template.

Validation: `:bridge-compiler:test :integration-harness:test verifyBridgeCoverage`
passed in 1m 1s, including all nine source/class/resource and artifact parity
lanes. A subsequent `:bridge-compiler:test` run passed in 38s after adding six
focused emitter tests. Final results: 124 compiler passes, one Windows symlink
skip, and 39 harness passes. Tests cover deterministic selection, rendering API
pairing, session filtering, dispatch, close guards, and button/state behavior.
No new game clients were launched; these source-contract tests do not independently
prove live interaction or visuals. Reference runtimes and resource packs remain
untouched. Remaining native services still use transitional generation paths.

## Shared platform information follow-up

`PlatformInfoEmitter` now emits platform queries for all nine targets from shared
logic and explicit loader policies. It preserves Fabric metadata formatting, FML
mod-container queries, client/server detection, the 26.2 environment accessor,
and legacy Forge/NeoForge identity from the runtime marker resource. The legacy
and 26.2 platform-info patch lists were removed. Canonical platform-info paths
remain manifest checked but no longer act as Java templates. Source and artifact
goldens were not changed.

Validation: `:bridge-compiler:test :integration-harness:test verifyBridgeCoverage`
passed in 1m 48s. Compiler tests passed 128 with one Windows symlink skip; harness
tests passed 39. All nine source/class/resource and artifact parity lanes passed.
Four new generator tests cover deterministic feature selection, environment/mod
queries, legacy identity/failure messages, and unreviewed target rejection.
No new clients or servers were launched. These are compiler and parity checks,
not fresh runtime validation. Reference runtimes and resource packs are untouched.

## Shared consumer bootstrap follow-up

On 2026-09-12, `ConsumerBootstrapEmitter` replaced copied consumer bootstraps and
the legacy FML bootstrap patch with shared initialization logic across all nine
targets. Explicit loader policies preserve event-bus access, class-loader
selection, method signatures, binary names, context lookup, and the original
initialize / duplicate-check / attach ordering. This does not change initialization
semantics or migrate loader-owned SDK startup and native registration services.
Canonical bootstrap paths still require their adapter declarations and remain
manifest checked, but their Java text is no longer an output template.

Validation: `:bridge-compiler:test :integration-harness:test verifyBridgeCoverage`
passed in 1m 35s. Compiler tests passed 133 with one Windows symlink skip; harness
tests passed 39. All nine source/class/resource and artifact parity lanes passed
without changing goldens. Five new generator tests cover deterministic emission,
feature dependencies, native initialization policies, ordering, and rejection of
unreviewed targets. No fresh game clients or servers were launched. These are
build/source-contract checks, not live startup validation. Reference runtimes and
unrelated resource-pack files remain untouched.

## Shared loader-owned entrypoint follow-up

`RuntimeEntrypointEmitter` now generates the SDK marker entrypoints for all nine
targets, preserving Fabric initialization hooks, modern/legacy FML annotations,
public constructors, binary names, and deliberately empty marker behavior.
Consumer bootstraps continue to own isolated mod contexts. The legacy marker
patch was removed; retained canonical declarations still undergo manifest checks.
No source or artifact goldens were changed.

Validation: `:bridge-compiler:test :integration-harness:test verifyBridgeCoverage`
passed in 2m 23s, including a temporary wait for VS Code's Java extension to release
a Loom cache lock. No IDE process was stopped. Compiler tests passed 136 with one
Windows symlink skip; harness tests passed 39. All nine source/class/resource and
artifact parity lanes passed. Three new emitter tests cover deterministic feature
selection, native marker hooks without consumer initialization, and unreviewed
target rejection. No fresh in-game startup tests were run. Client/lifecycle hooks,
registration, commands, and other platform services remain on transitional paths.

## Modern client-hook follow-up

The latest completed slice is the shared modern NeoForge client-hook emitter:
`ClientHooksEmitter` now covers 1.21.1, 1.21.4, and 26.2, preserving lifecycle
callbacks, tick ordering, screen registration, session forwarding, and native
payload sending. The 26.2 client-hook patch is removed. The existing negative
source-anchor test now exercises the still-patched command bridge instead.
Canonical manifests and source/artifact goldens are unchanged.

Validation: `:bridge-compiler:test :integration-harness:test verifyBridgeCoverage`
passed in 1m 40s. Compiler tests passed 140 with one Windows symlink skip; harness
tests passed 39. All nine source/class/resource and artifact parity lanes passed.
Four new emitter tests cover deterministic target selection, partial dependencies,
native hook source contracts, and unreviewed targets. No fresh clients or servers
were launched; live callback execution is not established by these tests.
Fabric and legacy FML client hooks remain to migrate alongside registration,
commands, and other native services.

## Shared client, command, payload, and input follow-up (2026-09-12)

All nine client-hook implementations now use shared emitters, including all four
Fabric targets and both legacy FML targets. The legacy emitters retain bounded
channel allocation, client-thread receive dispatch, remote-channel guards, and
the existing opt-in NeoForge ping-before-connect test sequence. All nine command
bridges now share argument conversion, suggestions, execution, and replies with
explicit registration and permission policies. Six modern targets share one
bounded raw-payload codec. Five standalone recipe inputs share one emitter;
NeoForge 26.2 keeps its input inside the existing shared recipe implementation.

`SharedRuntimeSources` composes the emitters. Its new coverage test scans both
declaration profiles across every target and rejects duplicate output/declaration
paths, emitted declarations that should be omitted, and fallback source outside
the remaining platform-service class. Registration, server events, inventory
operations, and platform-owned transport still need semantic migration there.
The source-anchor negative tests now exercise those remaining platform patches.
No canonical manifests, consumer sources, or reference goldens were changed.

Validation: `:bridge-compiler:test :integration-harness:test verifyBridgeCoverage`
passed in 1m 11s: 154 compiler passes, one Windows symlink skip, and 39 harness
passes. All nine source/class/resource and artifact parity lanes passed. An earlier
check caught an extra standalone NeoForge 26.2 recipe-input file; the emitter now
omits it, a regression assertion prevents emitted/omitted overlap, and the known
source digest is restored. That extra generated build file was removed; reference
sources were not removed. Fresh gameplay validation is tracked separately below.

## Shared platform-service completion (2026-09-12)

`PlatformServiceEmitter` now assembles the last native service implementation from
named state, registration, networking, gameplay, and event operations. Reviewed
`NativePlatformPolicy` facets select the actual loader/Minecraft ABI, while common
inventory operations, item properties, payload bounds, and lifecycle publication
share one implementation. Native binary names and member layout are preserved.
All Java declarations on all nine generated targets are now emitted or explicitly
omitted/combined; no source-copy exception remains for platform services.

The six obsolete contextual patch/transformer classes and their patch-only test
class were removed. They are superseded by the operation emitters, not retained as
an alternate path. Reference runtime directories, canonical manifests, public API,
consumer sources, and golden hashes remain unchanged. Declaration/manifest-negative
tests replace obsolete source-anchor tests. A new declaration-only integration test
substitutes comments for every canonical Java body in a temporary fixture and
requires the same output digest for every target; it passes without reference code.
Generation also rejects every unhandled source declaration before writing output,
even when its manifest hash is valid. There is no implicit Java-copy fallback.

Validation: `:bridge-compiler:test :integration-harness:test verifyBridgeCoverage
--offline` passed in 40s with 156 compiler passes, one Windows symlink skip, and
39 harness passes. All nine source/class/resource and artifact parity lanes passed.
Source-contract assertions preserve the actual legacy Fabric workbench binding and
modern NeoForge payload-registration behavior rather than assuming identical hooks.
After adding the no-copy guard, the focused compiler/harness suite passed again
with 157 compiler passes, one Windows symlink skip, and 39 harness passes. The
declaration-only nine-target digest test continues to pass with unchanged output.
The API, runtime-core, and Gradle-plugin tests were then rerun (not merely accepted
from cache): 17, 21, and 8 passes respectively, including plugin functional tests.
Across these five modules, that is 242 passes and one Windows symlink skip.

The normal `publishWorkspace` workflow now publishes generated runtimes. Reference
runtime Maven tasks are disabled and have no publication repositories, preventing
duplicate coordinates during local or later coordinated publication. Generated
Maven publications depend on `verifyBridgeCoverage`. A local publication dry run
passed and selected all nine generated runtime publications; reference projects
appear only as parity dependencies. No external release was performed.

The structured demo built all nine JARs through the generated workspace publications;
each contains portable-source hash
`df2d830cba29eee395440c1967090356e0d07ba23b787748dad182e0de0dec12`.
The starter template also passed `buildAll` for all nine targets in 3m22s with hash
`312ffb42725ad2b6d5ab340350d92bb92ffb2e00f25bbb9da58a995b4fd9e7e9` in each JAR.
An independent contract-project copy at
`build/consumer-verification/contract-20260912` passed `buildAll` in 5m52s on the
download-enabled retry, producing all nine JARs with portable-source hash
`bc8d53c045e0f3ec829afb87a1cdd20a9874360ba670af415d4937392d5ec515`.
All nine published runtime JARs in `build/repository` also match the corresponding
JARs in the isolated gameplay repository byte-for-byte.
These are build checks, not normal-launcher installation tests.

The combined nested demo/contract check exposed an embedded Kotlin compiler
concurrency failure; the two root fixture tasks now run in order. A later normal-cache
retry was blocked by an IDE-owned Loom lock and was cancelled without stopping the
IDE. Standalone consumer checks used the build-local Gradle user home instead.
Their initial offline attempts lacked Forge's pinned AutoRenamingTool 2.0.4;
retrying with dependency downloads enabled required no source or version changes.

Fresh gameplay validation passed all nine same-loader targets in a single run under
the non-overwriting label `shared-native-20260912` (1h5m49s including preparation).
The report is
`build/reports/generated-bridge-gameplay/runs/shared-native-20260912/connection.json`.
Every target has all nine gameplay checks true, successful bidirectional packet
exchange, and zero client/server exit codes. All use the same contract portable
source hash recorded above. No target needed a retry in this run.
The independent `:integration-harness:verifyGameplayMatrix` audit passed in 27s,
rereading the report and all selected logs and recording their hashes in
`build/reports/generated-bridge-gameplay/matrix.json`.

The assertions cover synchronized menu actions/state, rejection of insufficient
ingredients, normal crafting, shift-crafting without extra output, returning
unused inputs, and reopening an empty workbench. This is not full foundation-feature
acceptance, pixel-level visual review, or production-launcher installation proof.
The historical intermittent legacy login stall did not recur here; its underlying
cause and long-term reliability remain open.

## Catalog-created generated runtime projects (2026-09-12)

Settings now reads `gradle/runtime-projects.properties` and creates all nine
generated project descriptors using four shared build-family scripts. The nine
target-owned entry scripts were removed; their plugin and target-selection behavior
is retained centrally. Existing project IDs, artifact names, build output paths,
canonical manifests, reference implementations, and golden hashes remain unchanged.

Root generation, parity, and both workspace-publication tasks discover generated
projects rather than duplicating the target list. `RuntimeProjectCatalogTest`
checks exact agreement with the reviewed Java target catalog, loader/mapping ABI
selection, shared script presence, and absence of per-target entry scripts. Routing
and script files are declared test inputs so subsequent edits invalidate cached checks.

Validation: `:bridge-compiler:test verifyBridgeCoverage publishWorkspace --offline`
passed in 4m24s, including all nine source/class/resource/artifact parity lanes and
all nine generated-runtime local publications. The compiler suite passed 158 tests
with one Windows symlink skip. An earlier overlapping test invocation collided on
its report files; the single combined rerun above completed successfully. IDE-owned
Loom cache contention/recovery delayed the retry but required no IDE process changes.
The republished runtime JARs match the prior gameplay-tested binaries byte-for-byte.

This is build-structure work only: no portable or native runtime code changed and
no new live gameplay run is claimed for this step. The prior focused nine-target
gameplay evidence remains recorded above; broader runtime acceptance is still pending.

## Full-inventory gameplay expansion (2026-09-12)

The portable contract server and shared native test driver now exercise a full
36-slot player inventory before the second craft. Repeated shift-clicks must keep
the ingredients, output, cursor, and inventory unchanged and invoke no craft
callback. Freeing inventory space must then allow the same recipe to craft normally.
Unexpected callbacks are counted before validation so isolated listener exceptions
cannot hide an invalid craft. No SDK runtime implementation or API changed.

The evidence gate now requires eleven independent assertions rather than nine,
including separate client and server full-inventory checkpoints. All 40 harness
regression tests passed, including rejection of historical reports that lack these
new checks. Earlier nine-check results retain their historical meaning; they are
not evidence for this expanded scenario. Live attempts are retained under
`build/reports/generated-bridge-gameplay/runs/full-inventory-20260912`.

The live run passed on 1.20.1 Forge and 26.2 NeoForge in 14m3s including preparation
and publication/parity prerequisites. Every expanded assertion passed, the client
and server both exited with code zero, and no runtime fix was needed. Both targets
used portable-source hash
`1253baf11073a38a82bf486d8a07bc674dea7188ed2f90964731f43c95093bb7`.
The remaining seven targets subsequently passed in 42m16s, including preparation
and publication/parity prerequisites, with no retries or runtime changes. Their
report and separate logs are retained under
`build/reports/generated-bridge-gameplay/runs/full-inventory-remaining-20260912`.
All nine used the same portable-source hash above, passed all eleven assertions and
bidirectional connection checks, and exited cleanly on both client and server.

The earlier two-target audit correctly failed for seven missing targets. The new
independent audit explicitly combined the two named reports, reread all selected
logs, and passed all nine targets in 11s. The current
`build/reports/generated-bridge-gameplay/matrix.json` records the complete expanded
focused matrix with report/log hashes. It does not establish full-inventory close/drop,
partial-stack capacity, disconnect/reconnect, or the broader release acceptance gates.

## Next migration work

1. Diagnose the intermittent legacy login stall and establish repeatable launches.
   Extend inventory coverage to full-inventory close/drop, partial-stack capacity,
   and disconnect/reconnect, preserving the completed eleven-check evidence.
   Extend the completed focused matrix to the remaining foundation and edge-case
   assertions. Keep it separate from startup evidence and resolve the documented
   player-inventory lookup limitation.
2. Retire reference source trees only after their complete in-game parity gates pass.
   Generated project entry stubs have been replaced by catalog-created descriptors
   and shared build-family scripts; reference fixture builds remain separate.
3. Replace retained canonical Java baselines with an explicit feature manifest
   once parity-fixture retirement is approved; Java bodies no longer drive emission.

## Evidence and removal gates

Evidence must be reported by layer. A later layer cannot be inferred from an
earlier green result.

1. **Generation integrity:** validate the canonical manifest, reject unsafe or
   duplicate paths, generate twice, and prove deterministic source/resources.
2. **Build parity:** compile with the pinned loader, Minecraft, mappings, and Java
   toolchain; compare effective class names, metadata, remapped JAR contents, and
   reproducibility with the handwritten reference. This proves build output only.
3. **Process smoke evidence:** launch the dedicated server and client separately,
   observe lifecycle markers and clean shutdown, and retain their logs. This proves
   startup and basic class isolation, not complete gameplay behavior.
4. **In-game contract evidence:** verify registration, commands, events, configs,
   recipes, workbench slots and crafting, menu synchronization, and packet round
   trips on the actual target. Record these results separately from build parity.
5. **Reference removal:** remove a handwritten target only after both its build
   parity and target-specific in-game contract gates pass. Failure of either gate
   leaves the reference implementation in place.

No unimplemented target should be described as generated, supported by the new
compiler path, or runtime-verified merely because it exists in the target catalog.
