# Testing and releases

Local verification:

```bash
./gradlew checkAll
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

The release gate additionally requires all nine target contract suites, Linux/Xvfb
client and dedicated-server smoke tests, every directed same-version loader pairing,
Windows/Linux fresh-template builds, reproducibility comparison, signature/checksum/SBOM
inspection, and a manual launcher/dedicated-server release-candidate pass.

A green Java/Gradle build is not evidence that Minecraft started, that a dedicated
server avoided client classes, or that mixed loaders exchanged packets. Release reports
record those results separately. Coordinated publication is blocked if any supported
target fails.
