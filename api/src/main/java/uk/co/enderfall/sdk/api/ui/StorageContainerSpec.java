package uk.co.enderfall.sdk.api.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uk.co.enderfall.sdk.api.block.BlockProperty;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.blockentity.BlockEntityInt;

/**
 * A block-owned 9-wide storage inventory using vanilla slot synchronization.
 * One to six rows are supported without any consumer client registration.
 */
public final class StorageContainerSpec {
    private final String title;
    private final BlockEntitySpec storage;
    private final BlockProperty<Boolean> openProperty;
    private final ContainerSoundProfile sounds;
    private final int width;
    private final int height;
    private final List<InventorySlotSpec> slots;
    private final boolean playerInventory;
    private final int playerInventoryX;
    private final int playerInventoryY;
    private final List<InventoryQuickMoveRule> quickMoveRules;
    private final List<BlockEntityInt> synchronizedFields;

    private StorageContainerSpec(Builder builder) {
        title = builder.title;
        storage = builder.storage;
        openProperty = builder.openProperty;
        sounds = builder.sounds;
        width = builder.width;
        height = builder.height;
        slots = List.copyOf(builder.slots);
        playerInventory = builder.playerInventory;
        playerInventoryX = builder.playerInventoryX;
        playerInventoryY = builder.playerInventoryY;
        quickMoveRules = List.copyOf(builder.quickMoveRules);
        synchronizedFields = List.copyOf(builder.synchronizedFields);
        if (slots.isEmpty() && (storage.inventorySlots() < 9 || storage.inventorySlots() > 54
                || storage.inventorySlots() % 9 != 0)) {
            throw new IllegalArgumentException("Storage containers require 9, 18, 27, 36, 45, or 54 slots");
        }
        if (!slots.isEmpty() && storage.inventorySlots() < 1) {
            throw new IllegalArgumentException("Authored inventory menus require at least one persistent slot");
        }
        if (openProperty != null) {
            // Identity is intentional for portable block properties.
            // Native registration also verifies the property against the owning block specification.
            if (!openProperty.values().equals(java.util.List.of(false, true))) {
                throw new IllegalArgumentException("Container open state must be a boolean property");
            }
        }
        validateLayout();
        for (BlockEntityInt field : synchronizedFields) {
            if (!field.equals(storage.fields().get(field.name()))) {
                throw new IllegalArgumentException("Synchronized menu field is not declared by this storage: "
                        + field.name());
            }
        }
    }

    public static Builder builder(String title, BlockEntitySpec storage) {
        return new Builder(title, storage);
    }

    public String title() { return title; }
    public BlockEntitySpec storage() { return storage; }
    public int rows() {
        if (customLayout()) throw new IllegalStateException("Authored inventory menus do not use vanilla chest rows");
        return storage.inventorySlots() / 9;
    }
    public Optional<BlockProperty<Boolean>> openProperty() { return Optional.ofNullable(openProperty); }
    public ContainerSoundProfile sounds() { return sounds; }
    /** Width of an authored menu; vanilla chest layouts continue to use their native dimensions. */
    public int width() { return width; }
    /** Height of an authored menu; vanilla chest layouts continue to use their native dimensions. */
    public int height() { return height; }
    public List<InventorySlotSpec> slots() { return slots; }
    public boolean customLayout() { return !slots.isEmpty(); }
    public boolean playerInventory() { return playerInventory; }
    public int playerInventoryX() { return playerInventoryX; }
    public int playerInventoryY() { return playerInventoryY; }
    public List<InventoryQuickMoveRule> quickMoveRules() { return quickMoveRules; }
    public List<BlockEntityInt> synchronizedFields() { return synchronizedFields; }

    private void validateLayout() {
        if (slots.isEmpty()) {
            if (!quickMoveRules.isEmpty()) throw new IllegalArgumentException("Quick-move rules require a custom slot layout");
            return;
        }
        if (slots.size() != storage.inventorySlots()) {
            throw new IllegalArgumentException("A custom layout must expose every persistent inventory slot exactly once");
        }
        HashSet<Integer> indices = new HashSet<>();
        HashSet<String> groups = new HashSet<>();
        for (InventorySlotSpec slot : slots) {
            if (!indices.add(slot.storageIndex()) || slot.storageIndex() >= storage.inventorySlots()) {
                throw new IllegalArgumentException("Duplicate or out-of-range storage slot index: " + slot.storageIndex());
            }
            if (slot.x() + 18 > width || slot.y() + 18 > height) {
                throw new IllegalArgumentException("Inventory slot is outside the authored menu: " + slot.storageIndex());
            }
            groups.add(slot.group());
        }
        if (playerInventory && (playerInventoryX + 162 > width || playerInventoryY + 76 > height)) {
            throw new IllegalArgumentException("Player inventory is outside the authored menu");
        }
        if (!playerInventory && !quickMoveRules.isEmpty()) {
            for (InventoryQuickMoveRule rule : quickMoveRules) {
                if (rule.sourceGroup().startsWith("player_")
                        || rule.targetGroups().stream().anyMatch(group -> group.startsWith("player_"))) {
                    throw new IllegalArgumentException("Quick-move player groups require a bound player inventory");
                }
            }
        }
        HashSet<String> sources = new HashSet<>();
        for (InventoryQuickMoveRule rule : quickMoveRules) {
            if (!sources.add(rule.sourceGroup())) throw new IllegalArgumentException("Duplicate quick-move source: " + rule.sourceGroup());
            if (!knownGroup(rule.sourceGroup(), groups)
                    || rule.targetGroups().stream().anyMatch(group -> !knownGroup(group, groups))) {
                throw new IllegalArgumentException("Quick-move rule references an unknown slot group: " + rule);
            }
        }
    }

    private boolean knownGroup(String group, java.util.Set<String> groups) {
        return groups.contains(group) || playerInventory && (group.equals(InventoryQuickMoveRule.PLAYER_MAIN)
                || group.equals(InventoryQuickMoveRule.PLAYER_HOTBAR));
    }

    public static final class Builder {
        private final String title;
        private final BlockEntitySpec storage;
        private BlockProperty<Boolean> openProperty;
        private ContainerSoundProfile sounds = ContainerSoundProfile.BARREL;
        private int width = 176;
        private int height = 166;
        private final List<InventorySlotSpec> slots = new ArrayList<>();
        private boolean playerInventory;
        private int playerInventoryX = 7;
        private int playerInventoryY = 83;
        private final List<InventoryQuickMoveRule> quickMoveRules = new ArrayList<>();
        private final List<BlockEntityInt> synchronizedFields = new ArrayList<>();

        private Builder(String title, BlockEntitySpec storage) {
            this.title = Objects.requireNonNull(title, "title");
            this.storage = Objects.requireNonNull(storage, "storage");
            if (title.isBlank() || title.length() > 256) {
                throw new IllegalArgumentException("Container title must contain 1-256 characters");
            }
        }

        /** Updates this declared boolean state while at least one player is viewing the container. */
        public Builder openState(BlockProperty<Boolean> property) {
            openProperty = Objects.requireNonNull(property, "property");
            return this;
        }

        public Builder sounds(ContainerSoundProfile profile) {
            sounds = Objects.requireNonNull(profile, "profile");
            return this;
        }

        public Builder silent() {
            sounds = ContainerSoundProfile.NONE;
            return this;
        }

        /** Selects a custom authored menu size and switches registration away from the vanilla chest screen. */
        public Builder size(int width, int height) {
            if (width < 120 || width > 320 || height < 80 || height > 260) {
                throw new IllegalArgumentException("Inventory menu size must be between 120x80 and 320x260");
            }
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder slot(InventorySlotSpec slot) {
            slots.add(Objects.requireNonNull(slot, "slot"));
            return this;
        }

        public Builder slots(InventorySlotSpec... values) {
            for (InventorySlotSpec slot : values) slot(slot);
            return this;
        }

        /** Binds the player's 27 main slots and 9 hotbar slots below the authored machine slots. */
        public Builder playerInventory(int x, int y) {
            if (x < 0 || y < 0) throw new IllegalArgumentException("Player inventory coordinates cannot be negative");
            playerInventory = true;
            playerInventoryX = x;
            playerInventoryY = y;
            return this;
        }

        public Builder quickMove(String sourceGroup, String... targetGroups) {
            quickMoveRules.add(new InventoryQuickMoveRule(sourceGroup, targetGroups));
            return this;
        }

        /** Exposes one saved integer to the open client menu using vanilla menu synchronization. */
        public Builder synchronize(BlockEntityInt field) {
            Objects.requireNonNull(field, "field");
            if (synchronizedFields.contains(field)) {
                throw new IllegalArgumentException("Duplicate synchronized menu field: " + field.name());
            }
            if (synchronizedFields.size() >= 16) {
                throw new IllegalArgumentException("An authored inventory supports at most 16 synchronized fields");
            }
            synchronizedFields.add(field);
            return this;
        }

        public StorageContainerSpec build() {
            return new StorageContainerSpec(this);
        }
    }
}
