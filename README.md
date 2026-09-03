# EnderFall SDK

EnderFall SDK is a Java-first, loader-neutral API and build system for producing
separate Minecraft mod artifacts from one portable source tree.

The project is in pre-release development. The current implementation contains the
Java 17 API contract, shared runtime services, reviewed target catalog, Gradle settings
plugin, portable contract test mod, publishing safeguards, and a separate CC0 starter.
Target-native loader adapters are the remaining critical release gate and must pass
their real client/server matrix before any target is declared playable or stable.

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

`buildAll` proves the Java and Gradle implementation builds. It does not prove Minecraft
launch, dedicated-server isolation, or mixed-loader networking. Those remain explicitly
unverified until target adapters and the automated game harness are present.
