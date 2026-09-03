# Delivery roadmap

## Implemented in the source baseline

- [x] Java 17 loader-neutral API and typed foundation specifications.
- [x] Shared registration gate, event isolation, TOML repair, bounded codecs,
  capability manifest, deterministic data output, and diagnostics.
- [x] Nine-entry exact target catalog and Java 17/21/25 toolchain selection.
- [x] Settings plugin, generated loader metadata, aggregate tasks, source-boundary
  checks, duplicate-path failures, portable-source hashes, and deterministic JAR names.
- [x] JUnit and Gradle TestKit suites plus a portable full-feature contract fixture.
- [x] Minimal CC0 starter, one-shot identity task, wrappers, and dev container.
- [x] Maven/BOM/plugin publication scaffolding, signing hooks, SBOMs, checksums,
  API comparison, pinned actions, and fail-closed release workflow.

## Required before 0.1.0-beta.1 can be released

- [ ] Implement direct 1.21.4 Fabric and NeoForge adapters.
- [ ] Implement direct 26.2 Fabric and NeoForge adapters.
- [ ] Generate and compile loader bootstrap classes through Loom/ModDevGradle.
- [ ] Package one real `enderfall_sdk` runtime mod per initial target.
- [ ] Wire `runClient`, `runServer`, and `generateData` to actual loader tasks.
- [ ] Run registration, resources, commands, events, config, networking, and
  dedicated-server client-isolation smoke tests for all four targets.
- [ ] Run every directed Fabric/NeoForge client-server pairing per Minecraft version.

## Required before 0.2.0-beta.1

- [ ] Add the 1.21.1 Fabric/NeoForge adapters.
- [ ] Add 1.20.1 Fabric/Forge/NeoForge adapters using ModDevGradle legacy support for
  both Forge-family builds.
- [ ] Run all directed 1.20.1 Fabric/Forge/NeoForge pairings.

`verifyRuntimeMatrix` remains an unconditional failure until these checks are replaced
by machine-readable runtime evidence. This prevents an incomplete build pipeline from
being published accidentally.
