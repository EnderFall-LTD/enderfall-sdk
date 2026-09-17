# EnderFall bridge compiler

This module is the first slice of the generated-runtime architecture. It is deliberately
standalone until its generated output has been compared with the existing runtime modules.

## Canonical input layout

The compiler accepts the root of the central `bridge-runtime` project. Its checked-in runtime
implementation has exactly two source roots:

```text
bridge-runtime/
  MANIFEST.sha256           Exact hashes for every canonical file
  src/canonical/java/       Java sources owned by EnderFall
  src/canonical/resources/  transitional parity fixtures and other runtime resources
```

The manifest uses `<64 lowercase hex><two spaces>src/canonical/...`, covers every file exactly
once, and is verified before any output is written. There are no loader- or Minecraft-version
source directories in this layout. Target differences are expressed by the compiler's typed ABI
model and emitters. Java and unowned resource files are currently copied after manifest validation.
For the implemented Fabric targets, `fabric.mod.json` and `pack.mcmeta` are instead rendered from
the reviewed `TargetSpec`, its typed Minecraft resource-pack format, and `ModRuntimeMetadata`.
Their checked-in canonical files remain temporarily as manifest-validated parity fixtures; their
bytes are not used as the emitted bytes.

`1.21.1-fabric` reads this same 1.21.4 canonical tree. Eleven Java files pass through byte for
byte. The platform adapter, workbench menu, and workbench recipe use reviewed ABI-specific patches
whose exact anchors must each occur once. They are emitted as `Fabric1211PlatformAdapter.java`,
`Fabric1211WorkbenchMenu.java`, and `Fabric1211WorkbenchRecipe.java`; no 1.21.1 source copy is read
by the compiler.

The implemented target is bound to the complete reviewed target record, not only the text target
ID. A custom catalog cannot reuse `1.21.4-fabric` with different dependency or ABI metadata and
silently receive an artifact intended for the reviewed coordinate.

## Deterministic Fabric resource format

The two generated files use UTF-8 without a byte-order mark, LF line endings, two-space JSON
indentation, stable field order, and exactly one trailing LF. JSON strings are escaped by the
compiler. Gradle expands the literal `${version}` token later during `processResources`.

The exact generated resource results are:

| Target | Path | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| `1.21.1-fabric` | `fabric.mod.json` | 509 | `90a3bf2b681f6192ff632ad44601ec0d92ba07a7696607ff6e636eb090577da4` |
| `1.21.1-fabric` | `pack.mcmeta` | 131 | `b6b5fd4e53f6393b66d5d10e176cd4f5fa0eff82e02d56350cd10e012d745c37` |
| `1.21.4-fabric` | `fabric.mod.json` | 508 | `db85224b2a90895281de3247722e85529dde547bebd4cd4e2976624f9ad827d0` |
| `1.21.4-fabric` | `pack.mcmeta` | 131 | `380d9004da5646cca7515f7215d6d8f3d3c1d0c75711027ef0bd8399b9f4598e` |

`fabric.mod.json` derives the exact Minecraft, Java, Fabric Loader, and Fabric API dependency
values from `TargetSpec`; mod identity, authorship, license, environment, entrypoint, and pack
description come from `ModRuntimeMetadata`. The target emitter owns Fabric schema version 1.
The 1.21.1 metadata deliberately normalizes the old handwritten reference by declaring the
required Fabric API dependency and removing its stray description punctuation.

## Command line

```text
BridgeCompilerMain \
  --target 1.21.1-fabric \
  --canonical-root /path/to/bridge-runtime \
  --output-sources /path/to/build/generated/sources \
  --output-resources /path/to/build/generated/resources
```

Known targets whose emitter is not implemented fail explicitly. The compiler refuses symlinked
input files, overlapping roots, unsafe target IDs, and duplicate case-insensitive output paths.
An existing planned output is accepted only when it is a regular, non-symlink file whose bytes
exactly match the deterministic plan. All conflicts and unsupported destination entries are found
before any missing file is written. Missing files are staged beside their destination and
published without replacing an existing file; if another compiler wins the race, its file must
match exactly. This makes unchanged reruns safe while keeping stale or unexpected output
fail-closed.

## Target-coordinate verification

`uk.co.enderfall.sdk.bridge.TargetCoordinateMain` lets generated Gradle projects prove that their
configured Minecraft, Java, loader, platform API, and ABI values match the reviewed Java catalog:

```text
TargetCoordinateMain \
  --target 1.21.1-fabric \
  --expect minecraftVersion=1.21.1 \
  --expect javaVersion=21 \
  --expect loader=fabric \
  --expect loaderVersion=0.19.5 \
  --expect platformApiVersion=0.116.17+1.21.1
```

On success it prints every coordinate as fixed-order `key=value` lines. `--expect` is repeatable;
unknown keys, duplicate keys, mismatches, malformed values, and unknown targets exit non-zero
before any coordinate lines are printed. Keys and values are restricted to a newline-safe ASCII
alphabet; an empty value is permitted for targets without a separate platform API artifact.

## Parity and migration gate

`BridgeParityMain` performs read-only SHA-256 comparisons without Gradle task closures, so it can
be called by a configuration-cache-compatible `JavaExec` task:

```text
BridgeParityMain \
  --forbidden-root /path/to/removed/runtime-fabric-1.21.4 \
  --expected /path/to/bridge-runtime/src/canonical/java \
  --actual /path/to/generated/java \
  --expected /path/to/bridge-runtime/src/canonical/resources \
  --actual /path/to/generated/resources \
  --expected-file /path/to/reference-runtime.jar \
  --actual-file /path/to/generated-runtime.jar
```

`--forbidden-root`, directory `--expected`/`--actual`, and `--expected-file`/`--actual-file` are
repeatable. Expected and actual values are paired by their occurrence order. File pairs must both
be regular, non-symlink files and are compared using their byte length and SHA-256 digest.
