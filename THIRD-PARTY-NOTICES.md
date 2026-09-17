# Third-party notices

| Component | Version | License | Use |
|---|---:|---|---|
| TomlJ | 1.1.1 | Apache-2.0 | Relocated runtime TOML parser |
| ANTLR 4 runtime | 4.11.1 | BSD-3-Clause | Relocated parser runtime used by TomlJ |
| JUnit Jupiter | 5.12.2 | EPL-2.0 | Tests only |
| Apache Groovy (including JSON) | 4.0.32, supplied by Gradle 9.7.1 | Apache-2.0 | Integration report auditing only; not a mod runtime dependency |
| Foojay resolver convention | 1.0.0 | Apache-2.0 | Java toolchain provisioning |
| Shadow Gradle plugin | 9.6.1 | Apache-2.0 | Build-time relocation |
| CycloneDX Gradle plugin | 3.4.1 | Apache-2.0 | Release SBOM generation |
| japicmp | 0.26.1 | LGPL-3.0-only | Release-time API comparison |

Minecraft, Fabric, Forge, and NeoForge are external platforms and are not redistributed
by the API module. Their notices must be included by each eventual target runtime where
required.

The integration harness uses Groovy from the pinned Gradle distribution. It includes
software developed by The Apache Software Foundation (https://www.apache.org/).
See the Gradle distribution's LICENSE and NOTICE files for its bundled software.
