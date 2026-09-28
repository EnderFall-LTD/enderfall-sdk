# Release-candidate checklist

Complete this checklist against the exact commit supplied to the protected `release`
workflow. The workflow checkbox records completion; it does not skip or weaken any
automated gate.

## Automated evidence

- Confirm the Windows and Linux SDK, contract-mod, demo-mod, checksum, and SBOM jobs pass.
- Confirm all nine dedicated-server, standalone-client/UI, and connected foundation
  gameplay jobs pass without retries that hide a later failure.
- Confirm `release-matrix` reports the expected full Git SHA, nine unique targets, one
  portable-source hash, and no failures.
- Confirm Windows and Linux `SHA256SUMS` files are identical.
- Run `publication-check` and confirm both Plugin Portal validation and signed Central
  bundle validation pass.

## Manual launcher and server pass

- Install the generated runtime and contract/demo mod into ordinary launcher instances
  for the oldest supported ABI (`1.20.1`) and newest supported ABI (`26.2`), using
  different loaders between the two checks.
- Start independent dedicated servers, join each one, perform the contract command,
  open the synchronized menu and inventory workbench, craft normally and with shift-click,
  reconnect, and verify clean shutdown without errors or client classes on the server.
- Open a standard portable screen and authored inventory screen. At GUI scales 2, 3,
  and 4, verify pointer input, keyboard focus and typing, scrolling, clipping, slots,
  tooltips, item/entity previews, sprites, nine-slicing, and resource reload.
- Verify generated recipes, tags, translations, models, block states, and loot resources
  load without missing-resource warnings.
- Exercise config creation plus invalid-value repair in a disposable instance and verify
  the backup, retained valid/unknown settings, regenerated comments, and absence of
  sensitive values in logs.

## Publication review

- Confirm `sdkVersion` and `gradle-plugin/gradle.properties` match the requested version.
- Review release and migration notes, `NOTICE`, `THIRD-PARTY-NOTICES.md`, SBOMs, checksums,
  runtime matrix, and all target filenames.
- Confirm the protected `release` environment contains all six documented secrets and
  that Central namespace and Plugin Portal ownership remain valid.
- Confirm no artifact with this version already exists. Maven Central versions are
  immutable.
