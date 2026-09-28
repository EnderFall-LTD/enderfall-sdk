# EnderFall SDK

EnderFall SDK lets a mod author write loader-neutral Java once and build a separate
artifact for each selected Minecraft and loader target. Portable code imports only
`uk.co.enderfall.sdk.api`; the SDK owns version mappings, loader metadata, bootstrap
code, and generated runtime bindings.

It deliberately does **not** promise one universal JAR. It also does not promise that
an arbitrary future Minecraft version works before EnderFall's bridge compiler supports
that exact target and it has passed the contract and runtime suites.

## Current implementation status

The `0.1.0-beta.1` source tree contains the Java 17 API, shared runtime core,
deterministic config/network/data services, the settings plugin, a nine-target starter,
working Fabric/Forge/NeoForge reference runtimes, generated bridge-compiler paths for
all nine catalog targets,
unit and Gradle functional tests, and publication
scaffolding. One unchanged portable starter builds all nine target JARs, and every
dedicated-server and client target has reached its lifecycle smoke markers. Every exact
same-loader client/server pair also completes the SDK's three-leg packet round trip.
The protected release workflow now reruns deeper foundation-feature assertions for every
target and audits their exact-revision evidence. A candidate is publishable only after that
workflow, cross-platform reproducibility, publication validation, and the documented manual
release-candidate pass all succeed.

Build success and Minecraft runtime validation are reported separately throughout this
project.
