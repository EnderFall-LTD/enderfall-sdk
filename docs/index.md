# EnderFall SDK

EnderFall SDK lets a mod author write loader-neutral Java once and build a separate
artifact for each selected Minecraft and loader target. Portable code imports only
`uk.co.enderfall.sdk.api`; the SDK owns version mappings, loader metadata, bootstrap
code, and runtime adapters.

It deliberately does **not** promise one universal JAR. It also does not promise that
an arbitrary future Minecraft version works before an EnderFall adapter for that exact
target has passed the contract and runtime suites.

## Current implementation status

The `0.1.0-beta.1` source tree contains the Java 17 API, shared runtime core,
deterministic config/network/data services, the settings plugin, a four-target starter
build, unit and Gradle functional tests, and publication scaffolding. Target-native
Fabric/NeoForge/Forge adapters and real game-launch/mixed-loader lanes are release
gates still under implementation. Generated consumer JARs are therefore build-pipeline
fixtures, not yet release-ready playable mods.

Build success and Minecraft runtime validation are reported separately throughout this
project.
