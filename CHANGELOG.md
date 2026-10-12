# Changelog

## 0.1.0-beta.1 - unreleased

- Fixed the settings plugin registering Maven Central from `apply()`, which prepended it
  ahead of every repository a consumer declares afterwards. The contract test mod, the demo
  mod and every smoke lane therefore resolved the published SDK from Central instead of the
  locally published workspace build, so the nine-target matrix was validating previously
  released artifacts rather than the working tree. Maven Central is now an idempotent
  fallback registered after consumer repositories.
- Fixed the shared runtime disconnecting a player when a consumer's packet handler or menu
  action handler threw. Handler dispatch is now isolated and logged, matching the documented
  event-listener policy, and a failing menu handler no longer sends the mod's exception
  message -- which can echo submitted input -- as the disconnect reason. Malformed packets
  still disconnect.
- Fixed TOML repair rewriting unknown sections as `key = value`. An array of tables became
  `items = [[]]` plus stray headers, destroying unrelated settings and producing a file the
  next launch rejected and regenerated from defaults; a single `[[items]]` entry still parsed
  but moved its keys to the document root. A repaired document is now written only if every key
  the user wrote reads back with the same value (an empty table header is still not retained);
  otherwise the values are still repaired in memory and the file is left untouched, without a
  backup that would otherwise repeat on every launch.
- Fixed closed event subscriptions accumulating forever on their event. `Subscription.close`
  now detaches the registration instead of only muting it, so a long-running server no longer
  retains every block entity, menu and player session its listeners captured.
- Fixed registration IDs being burned when native registration failed. Item, block, creative
  tab and command registrars now release their reservation, matching the client command,
  screen and reload registrars. Workbenches and storage containers are now recorded only once
  native registration succeeds, so a rejected one can be retried and can never be opened.
- Fixed `MenuState` capping synchronized entries at 64 while `MenuSpec` accepts two eight-row
  selection lists needing 106, so a legally declared menu could not be opened. Because a
  0.1.0-beta.1 peer rejects states above 64 entries, the menu wire protocol is now version 3:
  a mixed client and server fail on the first menu packet with "Unsupported EnderFall menu
  protocol" instead of only when a menu happens to carry more than 64 entries.
- Fixed a menu update too large for the 16 KiB menu packet still replacing the server's copy of
  the state. The client kept showing the old page, so the player's next row click resolved to
  an entry from a page they never saw. Such an update now throws before anything changes, with
  a message naming the mod and the packet limit, and an update the transport fails to deliver is
  withdrawn from the server's copy.
- Fixed non-deterministic iteration order in `BlockEntitySpec.animations()` and
  `ConfigHandle.snapshot()`, both of which copied ordered collections with unordered factories.
- Fixed builders recording state before validating it, so a rejected declaration left the
  builder corrupted and a corrected retry failed with a misleading error. This affects
  `ConfigSpec`, `WorkbenchSpec` and `MenuSpec`.
- Fixed `PacketCodecs.string`/`bytes` accepting byte bounds that were only rejected later
  inside loader networking, and `StorageContainerSpec` rejecting any container group whose
  name merely started with `player_`.
- Fixed `MinecraftVersion` accepting version segments too large to parse, which then threw
  from `compareTo`.
- Fixed `DefaultConfigManager` reserving a config name before checking the environment, so a
  client config rejected on a dedicated server reported a duplicate on every later attempt.
- Added a release note documenting dependency repository precedence for consumers.

- Added a fail-closed release workflow that verifies fresh Windows/Linux consumer
  builds, reproducible checksums, the starter template, all nine dedicated-server,
  client/UI, and connected-gameplay targets, signed Maven Central contents, Plugin
  Portal metadata, SBOMs, provenance, and the protected manual acceptance sign-off.
- Expanded connected gameplay acceptance to 17 independent checks covering portable
  registration, dependency detection, config loading, listener isolation, player
  actions, live command execution, synchronized menus, networking, normal and
  shift-click crafting, rejection paths, input return, and persistent menu state.
- Added `verifyReleaseMatrix`, which binds every runtime report and required log
  checkpoint to the exact release commit and rejects missing, duplicate, stale, or
  inconsistent target evidence.
- Fixed intermittent 1.20.1 Forge development-client login stalls by using the same
  generated Forge status-ping negotiation already required by legacy NeoForge instead
  of vanilla Quick Play; two consecutive Forge gameplay runs passed after the change.
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

Publication remains blocked until the automated workflow and the documented manual
release-candidate checks pass against the exact commit selected for release.
