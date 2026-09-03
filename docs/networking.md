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

Mixed-loader compatibility means clients and servers on the **same Minecraft version**.
Cross-version protocol translation is out of scope. Networking is not called stable
until every directed loader pairing passes the real automated client/server suite.
