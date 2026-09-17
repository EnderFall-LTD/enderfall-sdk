# Testing and releases

Local verification:

```bash
./gradlew checkAll
./gradlew checkContractTargets
./gradlew serverSmoke
./gradlew clientSmoke
./gradlew sameLoaderSmoke
./gradlew releaseChecksums cyclonedxBom
./gradlew checkApiCompatibility -Penderfall.previousApi=/path/to/previous-api.jar
```

`test-mod` is both a normal API compile fixture and a standalone nine-target consumer
build. Run `./gradlew checkContractTargets` to publish the current SDK into an isolated
workspace repository and build that full-feature mod unchanged for every target. Unit suites
cover identifiers/builders, registration freezing, event priority and fault isolation,
strict codecs and manifests, TOML repair/backups, and deterministic data paths. Gradle
TestKit creates a fresh consumer, builds two targets, and inspects both JAR metadata
formats.

`serverSmoke` launches every generated dedicated server sequentially, waits for the
portable `SERVER_STARTED` lifecycle marker, sends `stop`, and requires a clean zero-code
exit. It creates EULA and offline/ephemeral-port settings only inside ignored test run
directories. Per-target logs and a JSON summary are written under
`build/reports/runtime-smoke`. Use
`-Penderfall.smokeTarget=1.21.4-fabric` to exercise one catalog target.

`clientSmoke` launches every generated client sequentially, waits for portable lifecycle
and client-tick assertions after the initial resource reload, requests a native graceful
shutdown, and requires a clean zero-code exit. It uses `--refresh-dependencies` for the
nested consumer build because workspace publications deliberately reuse the coordinated
development version. Per-target logs and `client.json` are written under the same report
directory. Use `-Penderfall.smokeTarget=<target>` to exercise one catalog target.

`generatedBridgeServerSmoke` and `generatedBridgeClientSmoke` exercise only targets
implemented by the central bridge compiler. They resolve SDK artifacts from
`build/generated-bridge-repository`, where handwritten runtime publications are absent,
and use `build/generated-bridge-gradle-user-home` to isolate Loom caches from IDE imports.
Preparation and game-launch timeouts are separate. Their reports are written under
`build/reports/generated-bridge-runtime-smoke`.
When requested together, generated server/client smoke lanes run in that order
to avoid concurrent native preparation and launches for the same consumer target.
Modern NeoForge and legacy Forge-family targets use the root `prepareClient` or `prepareServer`
proxy, which materializes only the selected consumer target and finishes native assets
and run artifacts before the timed game phase. All nine catalog targets, including
1.20.1 Forge and NeoForge, have passed both generated-only
process-smoke lanes. These are lifecycle/resource checks, not interactive feature or
connection tests.

For connected generated-runtime menu actions and workbench slot crafting, run
`generatedBridgeGameplaySmoke`; see the [scenario and evidence gates](gameplay-testing.md).
`verifyGameplayMatrix -Penderfall.gameplayReports=<oldest.json>,<newer.json>,...`
audits explicitly ordered reports and their actual logs for every catalog target.
It audits historical focused-scenario evidence, not release readiness or the latest
edited binaries; newer failures supersede older passes.

`sameLoaderSmoke` launches a dedicated server and a separate client for the same exact
Minecraft/loader target, auto-connects over loopback, requires a three-leg SDK packet
round trip, opens and renders the synchronized contract screen, and then shuts both
processes down cleanly. It never pairs different loaders.
Per-target logs and `connection.json` are written under the runtime-smoke report directory.
The full nine-target matrix passed locally on Windows on 2026-09-04; CI runs the same
task as one isolated Linux/Xvfb job per target.

The release gate additionally requires all nine target contract suites, Linux/Xvfb
client and dedicated-server smoke tests, every same-loader external connection test,
Windows/Linux fresh-template builds, reproducibility comparison, signature/checksum/SBOM
inspection, and a manual launcher/dedicated-server release-candidate pass.

A green Java/Gradle build is not evidence that Minecraft started or that a real client
exchanged packets with a dedicated server. The process-level server report, client report,
and same-loader connection report are recorded separately. Coordinated publication is
blocked if any supported target fails.
