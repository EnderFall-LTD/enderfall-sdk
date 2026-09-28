# Publishing

Publication is intentionally fail-closed. A tagged or manually requested release
does not bypass compatibility, runtime, signing, or credential checks.

## What the release workflow does

The protected `release` environment runs one coordinated, fail-closed workflow:

1. confirm that the requested version exactly matches `sdkVersion`;
2. build and test the SDK plus fresh consumers on Windows and Linux, then compare their
   release checksums;
3. launch a dedicated server, standalone client/UI lane, and connected foundation
   gameplay lane for every catalog target in isolated Linux/Xvfb jobs;
4. download those same-workflow artifacts and run `verifyRuntimeMatrix`, which requires
   all nine targets, the exact workflow Git revision, matching portable-source hashes,
   all expected assertion keys, clean exit codes, and the underlying log checkpoints;
5. require explicit confirmation of the manual release-candidate checklist and every
   Central, signing, and Plugin Portal secret;
6. publish all Maven publications into an isolated local Maven repository and create
   one reproducible signed archive with `centralBundle`;
7. upload that archive to the Central Publisher API with automatic publication and
   wait until Central reports `PUBLISHED`;
8. publish `uk.co.enderfall.sdk` to the Gradle Plugin Portal;
9. generate a GitHub build-provenance attestation for the produced JARs;
10. create the version tag and prerelease with target runtime JARs, checksums, SBOMs,
    generated release notes, migration notes, and the audited runtime matrix.

The Central bundle uses Maven repository layout and contains the coordinated BOM,
API, runtime core, Gradle implementation and marker, and every generated target
runtime. The Maven repository contains Gradle's MD5 and SHA-1 companions and detached
ASCII-armored signatures; the GitHub release adds the coordinated SHA-256 manifest.
Handwritten reference runtimes have no
working publication tasks and cannot overwrite generated runtime coordinates.

## Runtime release gate

`verifyRuntimeMatrix` cannot be satisfied by old local reports or a boolean workflow
flag. It requires the evidence directory and full Git SHA supplied by the release
workflow. Evidence for one target cannot substitute for another, a later failure cannot
be hidden by an older pass, and report flags cannot substitute for required log markers.

The manual checkbox is not a waiver. It records that the human checks in
[Release-candidate checklist](release-candidate.md) were performed against the exact
commit being released.

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
