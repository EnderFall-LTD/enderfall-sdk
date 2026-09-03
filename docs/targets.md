# Target catalog

Every value is exact. Releases reject `+`, `latest.*`, and `-SNAPSHOT` selectors.

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

The newest two Minecraft release lines are active. Older lines receive six months'
notice before maintenance status and retain compile/smoke lanes and permanent artifacts.
