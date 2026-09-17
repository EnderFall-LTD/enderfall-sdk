# Publishing

Publication is intentionally fail-closed. A tagged or manually requested release
does not bypass compatibility, runtime, signing, or credential checks.

## What the release workflow does

The protected `release` environment runs one coordinated workflow:

1. confirm that the requested version exactly matches `sdkVersion`;
2. run `checkAll`, `verifyRuntimeMatrix`, checksum generation, and CycloneDX SBOM
   generation;
3. require every Central, signing, and Plugin Portal secret;
4. publish all Maven publications into an isolated local Maven repository and create
   one reproducible signed archive with `centralBundle`;
5. upload that archive to the Central Publisher API with automatic publication and
   wait until Central reports `PUBLISHED`;
6. publish `uk.co.enderfall.sdk` to the Gradle Plugin Portal;
7. generate a GitHub build-provenance attestation for the produced JARs;
8. create the version tag and prerelease with target runtime JARs, checksums, SBOMs,
   generated release notes, and migration notes.

The Central bundle uses Maven repository layout and contains the coordinated BOM,
API, runtime core, Gradle implementation and marker, and every generated target
runtime. Gradle writes MD5, SHA-1, SHA-256, and SHA-512 checksums; the release signing
key adds detached ASCII-armored signatures. Handwritten reference runtimes have no
working publication tasks and cannot overwrite generated runtime coordinates.

## Release block

`verifyRuntimeMatrix` currently blocks unconditionally because target runtime
acceptance is not complete. Removing that block without replacing it with the full
nine-target automated evidence and manual release-candidate evidence is a release
policy violation.

This means repository, DNS, Central, Pages, and Plugin Portal ownership can be set up
now, but `0.1.0-beta.1` must not be published merely because the build compiles.

## Required contents

Every accepted release must include source and Javadoc JARs, the BOM, API, Gradle
plugin, target runtimes, SHA-256 checksums, CycloneDX SBOMs, GitHub provenance
attestations, release and migration notes, and third-party notices. Published Maven
coordinates are immutable.

## Local dry run

Normal local development uses `publishWorkspace` and never needs secrets. To exercise
the Central archive path, provide a disposable test signing key through the same
environment-variable names and run:

```powershell
.\gradlew.bat centralBundle
```

The archive is written to `build/central`. Creating it does not make any network
request. Only the protected release workflow uploads it.

See [Repository and service setup](repository-and-services.md) for the one-time
GitHub, DNS, Central, Plugin Portal, and secret configuration.
