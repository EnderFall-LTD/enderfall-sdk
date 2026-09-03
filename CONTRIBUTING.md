# Contributing

Run `./gradlew checkAll` before opening a change. Stable API additions need tests,
Javadocs, and an explanation of how every accepted target implements the contract.
Changes to the target catalog require exact versions and a successful adapter matrix.

Do not place loader or Minecraft imports in portable sources. Keep target work in the
narrowest applicable loader, version, or exact-target root. Report compile evidence and
real game-launch evidence separately.
