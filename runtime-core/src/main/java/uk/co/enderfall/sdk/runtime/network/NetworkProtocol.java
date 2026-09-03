package uk.co.enderfall.sdk.runtime.network;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.network.PacketDecodingException;

/** Internal, loader-independent connection negotiation format. */
public final class NetworkProtocol {
    public static final int MAJOR = 0;
    public static final int MINOR = 1;
    public static final int MAXIMUM_PACKET_TYPES = 512;
    public static final int MAXIMUM_MANIFEST_BYTES = 32_000;
    private static final int MAGIC = 0x45465344;

    private NetworkProtocol() {
    }

    public static byte[] encode(Set<PacketCapability> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        if (capabilities.size() > MAXIMUM_PACKET_TYPES) {
            throw new IllegalArgumentException("Packet manifest exceeds " + MAXIMUM_PACKET_TYPES + " entries");
        }
        ByteArrayPacketWriter writer = new ByteArrayPacketWriter(MAXIMUM_MANIFEST_BYTES);
        writer.writeInt(MAGIC);
        writer.writeVarInt(MAJOR);
        writer.writeVarInt(MINOR);
        writer.writeVarInt(capabilities.size());
        capabilities.stream().sorted().forEach(capability -> {
            writer.writeResourceId(capability.id());
            writer.writeVarInt(capability.schemaVersion());
            writer.writeBoolean(capability.required());
        });
        return writer.toByteArray();
    }

    public static Manifest decode(byte[] bytes) {
        ByteArrayPacketReader reader = new ByteArrayPacketReader(bytes, MAXIMUM_MANIFEST_BYTES);
        if (reader.readInt() != MAGIC) {
            throw new PacketDecodingException("Not an EnderFall SDK capability manifest");
        }
        int major = reader.readVarInt();
        int minor = reader.readVarInt();
        int count = reader.readVarInt();
        if (count < 0 || count > MAXIMUM_PACKET_TYPES) {
            throw new PacketDecodingException("Capability count " + count + " exceeds " + MAXIMUM_PACKET_TYPES);
        }
        Set<PacketCapability> capabilities = new HashSet<>();
        for (int index = 0; index < count; index++) {
            PacketCapability capability = new PacketCapability(reader.readResourceId(), reader.readVarInt(),
                    reader.readBoolean());
            if (!capabilities.add(capability)) {
                throw new PacketDecodingException("Duplicate packet capability " + capability.id());
            }
        }
        if (reader.remainingBytes() != 0) {
            throw new PacketDecodingException("Capability manifest has " + reader.remainingBytes()
                    + " unread bytes");
        }
        return new Manifest(major, minor, capabilities);
    }

    public record Manifest(int major, int minor, Set<PacketCapability> capabilities) {
        public Manifest {
            if (major < 0 || minor < 0) {
                throw new PacketDecodingException("Protocol versions cannot be negative");
            }
            capabilities = Set.copyOf(capabilities);
        }
    }

    public record PacketCapability(ResourceId id, int schemaVersion, boolean required)
            implements Comparable<PacketCapability> {
        public PacketCapability {
            Objects.requireNonNull(id, "id");
            if (schemaVersion < 1) {
                throw new PacketDecodingException("Packet schema version must be positive for " + id);
            }
        }

        @Override
        public int compareTo(PacketCapability other) {
            int idComparison = id.compareTo(other.id);
            return idComparison != 0 ? idComparison : Integer.compare(schemaVersion, other.schemaVersion);
        }
    }
}
