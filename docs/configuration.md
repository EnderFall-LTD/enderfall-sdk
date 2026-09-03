# Configuration

`ConfigSpec` supports booleans, integers, longs, finite doubles, strings, enums, bounded
lists, comments, defaults, custom validators, and sensitive keys. Config handles are
immutable after startup; live reload is outside 1.0.

- `COMMON` and `CLIENT` files use the game config directory.
- `SERVER` files use the world's `serverconfig` directory.
- Valid unknown entries are retained.
- Invalid known entries alone are replaced with defaults.
- A malformed file is regenerated from defaults.
- Every repair first creates a UTC-timestamped `.bak` copy.
- Failure to back up or atomically replace a file aborts startup.
- Values marked sensitive are never included in warnings.

The runtime parses TOML 1.0 with a relocated TomlJ 1.1.1 and emits stable, ordered TOML
itself. Relocation prevents a consumer mod's TomlJ version from colliding with the SDK.
