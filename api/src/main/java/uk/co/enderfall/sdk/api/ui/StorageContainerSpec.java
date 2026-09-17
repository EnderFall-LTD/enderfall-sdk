package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;
import java.util.Optional;
import uk.co.enderfall.sdk.api.block.BlockProperty;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;

/**
 * A block-owned 9-wide storage inventory using vanilla slot synchronization.
 * One to six rows are supported without any consumer client registration.
 */
public final class StorageContainerSpec {
    private final String title;
    private final BlockEntitySpec storage;
    private final BlockProperty<Boolean> openProperty;
    private final ContainerSoundProfile sounds;

    private StorageContainerSpec(Builder builder) {
        title = builder.title;
        storage = builder.storage;
        openProperty = builder.openProperty;
        sounds = builder.sounds;
        if (storage.inventorySlots() < 9 || storage.inventorySlots() > 54
                || storage.inventorySlots() % 9 != 0) {
            throw new IllegalArgumentException("Storage containers require 9, 18, 27, 36, 45, or 54 slots");
        }
        if (openProperty != null) {
            // Identity is intentional for portable block properties.
            // Native registration also verifies the property against the owning block specification.
            if (!openProperty.values().equals(java.util.List.of(false, true))) {
                throw new IllegalArgumentException("Container open state must be a boolean property");
            }
        }
    }

    public static Builder builder(String title, BlockEntitySpec storage) {
        return new Builder(title, storage);
    }

    public String title() { return title; }
    public BlockEntitySpec storage() { return storage; }
    public int rows() { return storage.inventorySlots() / 9; }
    public Optional<BlockProperty<Boolean>> openProperty() { return Optional.ofNullable(openProperty); }
    public ContainerSoundProfile sounds() { return sounds; }

    public static final class Builder {
        private final String title;
        private final BlockEntitySpec storage;
        private BlockProperty<Boolean> openProperty;
        private ContainerSoundProfile sounds = ContainerSoundProfile.BARREL;

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

        public StorageContainerSpec build() {
            return new StorageContainerSpec(this);
        }
    }
}
