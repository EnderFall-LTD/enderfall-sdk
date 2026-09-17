# Changelog

## 0.1.0-beta.1 - unreleased

- Added `verifyGameplayMatrix` to audit explicitly ordered gameplay reports against
  all nine catalog targets, matching portable-source hashes, and actual log checkpoints.
  Newer failed attempts cannot be masked by older passes; report/log hashes are retained.
- Moved Fabric client asset downloads into gameplay-harness preparation so they no
  longer consume the connection/gameplay timeout on subsequent runs.
- Corrected the legacy NeoForge smoke connector to wait for the status pong and
  Forge handshake data, rather than a callback that only fires for a changed server icon.
- Added `generatedBridgeGameplaySmoke`: connected native-button/state assertions,
  real normal/shift-click custom-recipe crafting, insufficient-input checks, input
  return and empty reopen checks, with independent server/client evidence gates.
  The native test driver uses one shared template; portable fixture code is unchanged
  between targets. This is additional test tooling, not complete gameplay acceptance.

- Introduced the loader-neutral Java 17 API and shared runtime contracts.
- Added strict TOML repair, bounded packet codecs and capability manifests.
- Added the settings plugin, isolated portable/native source roots, generated metadata,
  deterministic target artifact names, aggregate tasks, and diagnostics.
- Added the portable contract mod, unit/functional tests, starter repository, and local
  publication/release-verification scaffolding.
- Added direct runtime adapters for all nine catalog targets and automated client and
  dedicated-server startup/shutdown harnesses with JSON evidence.
- Added the exact same-loader connection matrix with a three-leg SDK packet round trip;
  cross-loader connections are deliberately unsupported.
- Made combined SDK checks and the consumer `enderfallDoctor` task safe for Gradle's
  configuration cache, with functional cache-reuse coverage.
- Added the first generated-runtime pipeline: a typed nine-target ABI catalog,
  deterministic bridge compiler, generated-only Fabric 1.20.1/1.21.1/1.21.4/26.2 and
  Forge 1.20.1 and NeoForge 1.20.1/1.21.1/1.21.4/26.2 targets,
  fail-closed source transformations, and strict source, class, resource, target
  coordinate, and final-JAR parity gates.
- Added reviewed legacy Fabric and modern NeoForge 26.2 compiler rules, including
  legacy channel transport, recipe serialization, and native menu/screen bindings.
  Missing/ambiguous source anchors and invalid UTF-8 fail generation explicitly.
- Added one shared legacy FML source/resource emitter and build script for both
  1.20.1 Forge-family targets, with exact Java, class, resource, development-JAR,
  and reobfuscated-JAR parity against the working references.
- Added an isolated generated-runtime Maven repository and dedicated client/server
  smoke tasks. All nine generated targets now pass real lifecycle, tick,
  resource-loading, dedicated-server
  startup, and clean-shutdown checks without resolving handwritten runtime artifacts.
- Added selected-target `prepareClient` and `prepareServer` consumer tasks so modern
  NeoForge and legacy Forge-family assets/run artifacts are prepared outside the timed game-launch
  phase without materializing unsupported targets.
- Serialize generated server/client smoke tasks when requested together, avoiding
  concurrent native preparation and game launches against the same consumer project.
- Allow comma-separated target selections in standalone client/server smoke tasks.

This beta is not a runtime release until the remaining in-game foundation assertions and
manual release-candidate checks pass.
