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
deterministic config/network/data services, the settings plugin, a nine-target starter,
direct Fabric/Forge/NeoForge adapters, unit and Gradle functional tests, and publication
scaffolding. One unchanged portable starter builds all nine target JARs, and every
dedicated-server target has reached Minecraft's ready state. Automated client and
same-version mixed-loader lanes remain release gates, so this is still a development
baseline rather than a release-ready SDK.

Build success and Minecraft runtime validation are reported separately throughout this
project.
