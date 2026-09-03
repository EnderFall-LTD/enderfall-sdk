# Compatibility contract

The public API is compiled with `--release 17`.

- During `0.x`, patch releases remain source- and binary-compatible. A minor release may
  break API or DSL only with a migration guide.
- From `1.0`, minor and patch releases preserve source and binary compatibility for the
  stable API and settings DSL.
- A target mod already built against an SDK release remains compatible with later SDK
  releases in the same SDK major and exact Minecraft/loader target.
- Experimental symbols and native roots are outside that guarantee.
- A deprecated stable API remains until the next major release.

The `checkApiCompatibility` task compares the current API JAR against an explicitly
provided prior release and fails on source or binary incompatibility. Release review is
still required because compatibility tools cannot determine semantic behavior.
