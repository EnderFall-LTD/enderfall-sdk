# Target catalog

Every value is exact. A `+` inside an exact Fabric build version is literal; releases
reject wildcard selectors such as `1.+`, `latest.*`, ranges, and `-SNAPSHOT` versions.

| Minecraft | Loader | Java | Loader version | Platform API | Milestone |
|---|---|---:|---|---|---|
| 1.21.4 | Fabric | 21 | 0.19.5 | 0.119.4+1.21.4 | 0.1 beta |
| 1.21.4 | NeoForge | 21 | 21.4.157 | bundled | 0.1 beta |
| 26.2 | Fabric | 25 | 0.19.5 | 0.159.0+26.2 | 0.1 beta |
| 26.2 | NeoForge | 25 | 26.2.0.75 | bundled | 0.1 beta |
| 1.21.1 | Fabric | 21 | 0.19.5 | 0.116.17+1.21.1 | 0.2 beta |
| 1.21.1 | NeoForge | 21 | 21.1.249 | bundled | 0.2 beta |
| 1.20.1 | Fabric | 17 | 0.19.5 | 0.92.12+1.20.1 | 0.2 beta |
| 1.20.1 | Forge | 17 | 47.4.23 | bundled | 0.2 beta |
| 1.20.1 | NeoForge | 17 | 47.1.106 | bundled | 0.2 beta |

The catalog is not the same thing as runtime acceptance. Each target moves through
`catalogued`, `compiles`, `client smoke`, `server smoke`, and `mixed-loader accepted`.
Only the final state may be advertised as supported.

## Current validation snapshot

Validated locally on Windows on 2026-09-03:

| Target group | Unchanged portable build | Dedicated server ready | Client smoke | Mixed-loader |
|---|---|---|---|---|
| 1.20.1 Fabric/Forge/NeoForge | pass | pass | pending | pending |
| 1.21.1 Fabric/NeoForge | pass | pass | pending | pending |
| 1.21.4 Fabric/NeoForge | pass | pass | pending | pending |
| 26.2 Fabric/NeoForge | pass | pass | pending | pending |

The nine output JARs carried the same portable-source SHA-256 value. Server-ready means
the generated consumer loaded and Minecraft reached `Done`; it does not claim that every
foundation feature or any network pairing has been exercised in-game.

The newest two Minecraft release lines are active. Older lines receive six months'
notice before maintenance status and retain compile/smoke lanes and permanent artifacts.
