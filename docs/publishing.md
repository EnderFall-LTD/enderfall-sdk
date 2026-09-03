# Publishing setup

Publication is intentionally fail-closed. The protected GitHub `release` environment
must hold Maven Central, Gradle Plugin Portal, and in-memory PGP credentials. Workflows
receive only `contents: write`, `id-token: write`, and `attestations: write` permissions,
and every third-party action is pinned to a full commit SHA.

Before the first Central release, register `uk.co.enderfall.sdk` and publish the DNS TXT
challenge under `enderfall.co.uk` to prove control of the reversed-domain namespace.
Never commit signing keys or publishing tokens.

The release workflow checks the coordinated version, then calls `verifyRuntimeMatrix`.
That task currently blocks unconditionally because target runtime acceptance is not yet
complete. Removing that block without replacing it with the nine-target automated and
manual evidence is a release-policy violation.

Every accepted release must contain source/Javadoc JARs, the BOM, API, Gradle plugin and
target runtimes, SHA-256 checksums, CycloneDX SBOMs, GitHub provenance attestations,
release/migration notes, and third-party notices. Published artifacts are immutable.
