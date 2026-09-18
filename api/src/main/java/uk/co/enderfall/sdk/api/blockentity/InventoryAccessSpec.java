package uk.co.enderfall.sdk.api.blockentity;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import uk.co.enderfall.sdk.api.block.BlockDirection;

/**
 * Loader-neutral sided automation rules for a block-entity inventory.
 * Undeclared faces expose no slots. Player menus are not restricted by these rules.
 */
public final class InventoryAccessSpec {
    private final int inventorySlots;
    private final Map<BlockDirection, FaceAccess> faces;

    private InventoryAccessSpec(int inventorySlots, Builder builder) {
        if (inventorySlots <= 0 || inventorySlots > BlockEntitySpec.MAXIMUM_SLOTS) {
            throw new IllegalArgumentException("Inventory access requires between 1 and "
                    + BlockEntitySpec.MAXIMUM_SLOTS + " inventory slots");
        }
        this.inventorySlots = inventorySlots;
        var resolved = new EnumMap<BlockDirection, FaceAccess>(BlockDirection.class);
        builder.faces.forEach((direction, access) -> resolved.put(direction, access.resolve(inventorySlots)));
        faces = Map.copyOf(resolved);
    }

    static InventoryAccessSpec configured(int inventorySlots, Consumer<Builder> configure) {
        var builder = new Builder();
        configure.accept(builder);
        return new InventoryAccessSpec(inventorySlots, builder);
    }

    /** Creates the conventional container policy: every slot can be inserted/extracted on every face. */
    public static InventoryAccessSpec allFaces(int inventorySlots) {
        return configured(inventorySlots, builder -> builder.allFaces(InventoryAccessMode.BOTH));
    }

    public int inventorySlots() { return inventorySlots; }

    /** Returns the slots visible to automation on this face, in stable ascending order. */
    public int[] slots(BlockDirection face) {
        var access = faces.get(Objects.requireNonNull(face, "face"));
        return access == null ? new int[0] : access.visibleSlots().clone();
    }

    public boolean canInsert(BlockDirection face, int slot) {
        var access = faces.get(Objects.requireNonNull(face, "face"));
        return access != null && access.canInsert(slot);
    }

    public boolean canExtract(BlockDirection face, int slot) {
        var access = faces.get(Objects.requireNonNull(face, "face"));
        return access != null && access.canExtract(slot);
    }

    public boolean canInsertFromAnyFace(int slot) {
        return faces.entrySet().stream().anyMatch(entry -> canInsert(entry.getKey(), slot));
    }

    public boolean canExtractFromAnyFace(int slot) {
        return faces.entrySet().stream().anyMatch(entry -> canExtract(entry.getKey(), slot));
    }

    private record FaceAccess(int[] insertSlots, int[] extractSlots, int[] visibleSlots) {
        private FaceAccess {
            insertSlots = insertSlots.clone();
            extractSlots = extractSlots.clone();
            visibleSlots = visibleSlots.clone();
        }

        private boolean canInsert(int slot) { return Arrays.binarySearch(insertSlots, slot) >= 0; }
        private boolean canExtract(int slot) { return Arrays.binarySearch(extractSlots, slot) >= 0; }
    }

    private record SlotSelection(boolean allSlots, int[] slots) {
        private static final SlotSelection NONE = new SlotSelection(false, new int[0]);
        private SlotSelection { slots = slots.clone(); }

        private int[] resolve(int inventorySlots) {
            int[] resolved = allSlots ? IntStream.range(0, inventorySlots).toArray() : slots.clone();
            for (int slot : resolved) {
                if (slot < 0 || slot >= inventorySlots) {
                    throw new IllegalArgumentException("Inventory automation slot is outside the inventory: " + slot);
                }
            }
            return resolved;
        }
    }

    private static final class PendingAccess {
        private SlotSelection insert = SlotSelection.NONE;
        private SlotSelection extract = SlotSelection.NONE;

        private void replace(InventoryAccessMode mode, SlotSelection selection) {
            insert = mode.allowsInsert() ? selection : SlotSelection.NONE;
            extract = mode.allowsExtract() ? selection : SlotSelection.NONE;
        }

        private FaceAccess resolve(int inventorySlots) {
            int[] insertSlots = insert.resolve(inventorySlots);
            int[] extractSlots = extract.resolve(inventorySlots);
            int[] visible = IntStream.concat(Arrays.stream(insertSlots), Arrays.stream(extractSlots))
                    .distinct().sorted().toArray();
            return new FaceAccess(insertSlots, extractSlots, visible);
        }
    }

    public static final class Builder {
        private final Map<BlockDirection, PendingAccess> faces = new EnumMap<>(BlockDirection.class);

        private Builder() { }

        /** Exposes every inventory slot on one face using the selected operation mode. */
        public Builder face(BlockDirection face, InventoryAccessMode mode) {
            return replace(face, mode, new SlotSelection(true, new int[0]));
        }

        /** Exposes only the listed slots on one face using the selected operation mode. */
        public Builder face(BlockDirection face, InventoryAccessMode mode, int... slots) {
            Objects.requireNonNull(slots, "slots");
            if (slots.length == 0) {
                throw new IllegalArgumentException("Use face(face, mode) to expose every slot");
            }
            int[] normalized = Arrays.stream(slots).distinct().sorted().toArray();
            if (normalized.length != slots.length) {
                throw new IllegalArgumentException("Inventory automation slots must not be duplicated");
            }
            return replace(face, mode, new SlotSelection(false, normalized));
        }

        /** Applies an all-slot rule to all six faces. Individual face calls may override it. */
        public Builder allFaces(InventoryAccessMode mode) {
            for (var face : BlockDirection.values()) face(face, mode);
            return this;
        }

        /** Applies an all-slot rule to north, south, east, and west. */
        public Builder horizontalFaces(InventoryAccessMode mode) {
            for (var face : BlockDirection.values()) {
                if (face.horizontal()) face(face, mode);
            }
            return this;
        }

        public Builder insert(BlockDirection face) {
            return insert(face, new SlotSelection(true, new int[0]));
        }

        public Builder insert(BlockDirection face, int firstSlot, int... additionalSlots) {
            return insert(face, selection(firstSlot, additionalSlots));
        }

        public Builder extract(BlockDirection face) {
            return extract(face, new SlotSelection(true, new int[0]));
        }

        public Builder extract(BlockDirection face, int firstSlot, int... additionalSlots) {
            return extract(face, selection(firstSlot, additionalSlots));
        }

        public Builder both(BlockDirection face) {
            return face(face, InventoryAccessMode.BOTH);
        }

        public Builder both(BlockDirection face, int firstSlot, int... additionalSlots) {
            return replace(face, InventoryAccessMode.BOTH, selection(firstSlot, additionalSlots));
        }

        private static SlotSelection selection(int first, int[] additional) {
            Objects.requireNonNull(additional, "additionalSlots");
            int[] slots = new int[additional.length + 1];
            slots[0] = first;
            System.arraycopy(additional, 0, slots, 1, additional.length);
            int[] normalized = Arrays.stream(slots).distinct().sorted().toArray();
            if (normalized.length != slots.length) {
                throw new IllegalArgumentException("Inventory automation slots must not be duplicated");
            }
            return new SlotSelection(false, normalized);
        }

        private Builder replace(BlockDirection face, InventoryAccessMode mode, SlotSelection selection) {
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(mode, "mode");
            if (mode == InventoryAccessMode.NONE) {
                faces.remove(face);
            } else {
                faces.computeIfAbsent(face, ignored -> new PendingAccess()).replace(mode, selection);
            }
            return this;
        }

        private Builder insert(BlockDirection face, SlotSelection selection) {
            Objects.requireNonNull(face, "face");
            faces.computeIfAbsent(face, ignored -> new PendingAccess()).insert = selection;
            return this;
        }

        private Builder extract(BlockDirection face, SlotSelection selection) {
            Objects.requireNonNull(face, "face");
            faces.computeIfAbsent(face, ignored -> new PendingAccess()).extract = selection;
            return this;
        }
    }
}
