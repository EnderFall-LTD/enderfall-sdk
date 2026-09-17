# Networking

The portable layer uses vanilla custom-payload IDs and the same codecs on every loader.
It never uses Java serialization or makes a loader-specific channel protocol part of a
consumer mod's contract.

Primitive numbers use network byte order. Lengths are deterministic VarInts, strings
are strict UTF-8, and strings/collections/byte arrays are bounded before allocation.
The uniform payload ceiling is 32,000 bytes; a packet may declare a smaller limit.
Decoders must consume the complete buffer, match packet direction, and reject malformed
input with a clear disconnect.

Before consumer packets are sent, peers exchange a deterministic manifest containing
SDK protocol major/minor and packet ID/schema/required flags. A protocol-major mismatch
or missing required packet disconnects. Optional packets are used only when the remote
manifest advertises the exact schema.

A multiplayer connection must use the same Minecraft version, loader, EnderFall SDK
target, and compatible mod set on both sides. Cross-loader connections and cross-version
protocol translation are deliberately out of scope: EnderFall builds each loader target
from the same portable source, but does not make different loader runtimes interoperable.
Every catalog target now passes the real automated same-loader client/server suite.
Formal stable status still follows the SDK release policy and the remaining full
foundation-feature and release-candidate gates.
