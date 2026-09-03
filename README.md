# EnderFall SDK

EnderFall SDK is a Java-first, loader-neutral API and build system for producing
separate Minecraft mod artifacts from one portable source tree.

The project is in pre-release development. The current implementation contains the
Java 17 API contract, shared runtime services, reviewed target catalog, Gradle settings
plugin, direct Fabric/Forge/NeoForge adapters for all nine catalog targets, a portable
contract test mod, publishing safeguards, and a separate CC0 starter. All nine artifacts
compile from one unchanged portable source tree and all nine dedicated-server targets
have reached Minecraft's ready state. Client and same-version mixed-loader tests remain
release gates, so networking is not yet declared stable.

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
- `releaseChecksums` — reproducible JAR build plus SHA-256 manifest.
- `cyclonedxBom` — CycloneDX JSON and XML SBOMs.
- `checkApiCompatibility -Penderfall.previousApi=<jar>` — japicmp release gate.
- `publishWorkspace` — local Maven repository used to test the sibling template.

The API is published as `uk.co.enderfall.sdk:enderfall-sdk-api`, the shared runtime as
`uk.co.enderfall.sdk:enderfall-sdk-runtime-core`, and coordinated versions through
`uk.co.enderfall.sdk:enderfall-sdk-bom`.

## Compatibility promise

Portable code compiles only against `enderfall-sdk-api`. Native Minecraft and
loader types are intentionally unavailable to that source set. A build produces
one artifact per selected Minecraft/loader target, not one universal JAR.

See `docs/architecture.md` and `docs/compatibility.md` for the detailed contract.

## Validation status

`buildAll` proves the Java and Gradle implementation builds. Dedicated-server smoke
results are tracked separately in `docs/targets.md`; they do not prove client behavior or
mixed-loader networking. Coordinated publication remains blocked until the automated
client/server matrix and manual release-candidate pass are complete.
