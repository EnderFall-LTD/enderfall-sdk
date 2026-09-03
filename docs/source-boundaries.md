# Source boundaries

| Root | Purpose | Classpath guarantee |
|---|---|---|
| `src/main` | portable common code/resources | EnderFall API only |
| `src/client` | portable client-only code/resources | EnderFall API only |
| `src/datagen` | portable data definitions | EnderFall API only |
| `src/loader/<loader>` | native code shared by one loader | target APIs |
| `src/version/<minecraft>` | native code shared by one game version | target APIs |
| `src/target/<minecraft>-<loader>` | exact-target native code | target APIs |

Portable Java is compiled in an isolated Java 17 source set. A verification task also
rejects Minecraft, Fabric, Forge, NeoForge imports and Stonecutter directives there so
the failure message points directly at the portability breach.

Native roots are escape hatches, not portable code. If two active roots contribute the
same resource path, the build fails instead of selecting an undocumented precedence.
Loader metadata paths are reserved because the plugin owns them.
