package uk.co.enderfall.sdk.api.ui;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** One authored slot in a block-owned portable inventory menu. */
public final class InventorySlotSpec {
    private final int storageIndex;
    private final int x;
    private final int y;
    private final InventorySlotRole role;
    private final String group;
    private final Set<ResourceId> acceptedItems;
    private final int maximumCount;

    private InventorySlotSpec(int storageIndex, int x, int y, InventorySlotRole role,
            String group, Collection<ResourceId> acceptedItems, int maximumCount) {
        if (storageIndex < 0 || storageIndex > 255) {
            throw new IllegalArgumentException("Storage slot index must be between 0 and 255");
        }
        if (x < 0 || x > 319 || y < 0 || y > 259) {
            throw new IllegalArgumentException("Inventory slot coordinates are outside the portable menu bounds");
        }
        if (maximumCount < 1 || maximumCount > 64) {
            throw new IllegalArgumentException("Slot maximum count must be between 1 and 64");
        }
        this.storageIndex = storageIndex;
        this.x = x;
        this.y = y;
        this.role = Objects.requireNonNull(role, "role");
        this.group = requireGroup(group);
        LinkedHashSet<ResourceId> filter = new LinkedHashSet<>();
        for (ResourceId item : Objects.requireNonNull(acceptedItems, "acceptedItems")) {
            filter.add(Objects.requireNonNull(item, "accepted item"));
        }
        if (filter.size() > 256) throw new IllegalArgumentException("A slot filter supports at most 256 items");
        if (role == InventorySlotRole.OUTPUT && !filter.isEmpty()) {
            throw new IllegalArgumentException("Output slots cannot declare an insertion filter");
        }
        this.acceptedItems = Set.copyOf(filter);
        this.maximumCount = maximumCount;
    }

    public static InventorySlotSpec storage(int storageIndex, int x, int y, String group) {
        return new InventorySlotSpec(storageIndex, x, y, InventorySlotRole.STORAGE,
                group, Set.of(), 64);
    }

    public static InventorySlotSpec input(int storageIndex, int x, int y, String group) {
        return new InventorySlotSpec(storageIndex, x, y, InventorySlotRole.INPUT,
                group, Set.of(), 64);
    }

    public static InventorySlotSpec input(int storageIndex, int x, int y, String group,
            Collection<ItemRef> acceptedItems) {
        Objects.requireNonNull(acceptedItems, "acceptedItems");
        return accepting(storageIndex, x, y, group,
                acceptedItems.stream().map(ItemRef::id).toList());
    }

    public static InventorySlotSpec accepting(int storageIndex, int x, int y, String group,
            Collection<ResourceId> acceptedItems) {
        return new InventorySlotSpec(storageIndex, x, y, InventorySlotRole.INPUT,
                group, acceptedItems, 64);
    }

    public static InventorySlotSpec output(int storageIndex, int x, int y, String group) {
        return new InventorySlotSpec(storageIndex, x, y, InventorySlotRole.OUTPUT,
                group, Set.of(), 64);
    }

    public InventorySlotSpec maximumCount(int value) {
        return new InventorySlotSpec(storageIndex, x, y, role, group, acceptedItems, value);
    }

    public int storageIndex() { return storageIndex; }
    public int x() { return x; }
    public int y() { return y; }
    public InventorySlotRole role() { return role; }
    public String group() { return group; }
    public Set<ResourceId> acceptedItems() { return acceptedItems; }
    public int maximumCount() { return maximumCount; }

    private static String requireGroup(String value) {
        Objects.requireNonNull(value, "group");
        if (!value.matches("[a-z][a-z0-9_]{0,31}")) {
            throw new IllegalArgumentException("Slot group must match [a-z][a-z0-9_]{0,31}: " + value);
        }
        if (value.equals(InventoryQuickMoveRule.PLAYER_MAIN)
                || value.equals(InventoryQuickMoveRule.PLAYER_HOTBAR)) {
            throw new IllegalArgumentException("Slot group uses a reserved player-inventory name: " + value);
        }
        return value;
    }
}
