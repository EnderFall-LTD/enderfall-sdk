package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;

/** Declarative definition for a real, inventory-backed custom workbench menu. */
public final class WorkbenchSpec {
    private final String title;
    private final java.util.List<String> machineInputs;
    private final java.util.List<String> machineOutputs;
    private final boolean machine;
    private final WorkbenchRecipeTypeRef recipeType;
    private final int backgroundColor;
    private final int processingTicks;
    private final uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec storage;

    private WorkbenchSpec(Builder builder) {
        title = builder.title;
        machineInputs = builder.machineInputs;
        machineOutputs = builder.machineOutputs;
        machine = builder.machine;
        recipeType = builder.recipeType;
        backgroundColor = builder.backgroundColor;
        processingTicks = builder.processingTicks;
        storage = builder.storage;
    }

    public static Builder builder(String title, WorkbenchRecipeTypeRef recipeType) {
        return new Builder(title, recipeType);
    }

    public String title() {
        return title;
    }
    public boolean machineRecipes() { return machine; }
    public java.util.List<String> fluidInputTanks() { return machineInputs; }
    public java.util.List<String> fluidOutputTanks() { return machineOutputs; }

    public WorkbenchRecipeTypeRef recipeType() {
        return recipeType;
    }

    public int backgroundColor() {
        return backgroundColor;
    }

    /** Zero for instant crafting; otherwise the loaded-server-tick duration of each batch. */
    public int processingTicks() { return processingTicks; }

    /** Empty for the existing temporary, menu-owned workbench. */
    public java.util.Optional<uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec> storage() {
        return java.util.Optional.ofNullable(storage);
    }

    public static final class Builder {
        private final String title;
        private java.util.List<String> machineInputs = java.util.List.of();
        private java.util.List<String> machineOutputs = java.util.List.of();
        private boolean machine;
        /** Combined recipes use {@code data/<namespace>/enderfall_machine/*.json}.
         * Requires 1-5 positional item inputs plus one item output; tanks must be distinct.
         */
        public Builder persistentMachine(uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec value,
                java.util.List<String> inputTanks, java.util.List<String> outputTanks) {
            persistentTimed(value, 1);
            var inputs = java.util.List.copyOf(inputTanks);
            var outputs = java.util.List.copyOf(outputTanks);
            var all = new java.util.HashSet<String>();
            if (inputs.size() > 16 || outputs.size() > 16) throw new IllegalArgumentException("Too many machine tanks");
            for (String name : java.util.stream.Stream.concat(inputs.stream(), outputs.stream()).toList()) {
                if (!value.tanks().containsKey(name) || !all.add(name)) throw new IllegalArgumentException("Undeclared or repeated machine tank: " + name);
            }
            machineInputs = inputs; machineOutputs = outputs; machine = true;
            return this;
        }
        private final WorkbenchRecipeTypeRef recipeType;
        private int backgroundColor = 0xFF1A1426;
        private int processingTicks;
        private uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec storage;

        private Builder(String title, WorkbenchRecipeTypeRef recipeType) {
            this.title = Objects.requireNonNull(title, "title");
            this.recipeType = Objects.requireNonNull(recipeType, "recipeType");
            if (title.isBlank() || title.length() > 256) {
                throw new IllegalArgumentException("Workbench title must contain 1-256 characters");
            }
        }

        public Builder backgroundColor(int argb) {
            backgroundColor = argb;
            return this;
        }

        public WorkbenchSpec build() {
            return new WorkbenchSpec(this);
        }

        /** Binds persisted inputs to an already registered persistent block; output remains derived. */
        @uk.co.enderfall.sdk.api.annotation.Experimental("Persistent block-entity integration is under development")
        public Builder persistent(uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec value) {
            machine = false; machineInputs = java.util.List.of(); machineOutputs = java.util.List.of();
            Objects.requireNonNull(value, "storage");
            if (value.inventorySlots() != recipeType.inputSlots()) {
                throw new IllegalArgumentException("Persistent inventory must match the recipe input slot count");
            }
            storage = value;
            processingTicks = 0;
            return this;
        }

        /**
         * Binds inputs plus one stored output slot to a timed machine. Processing happens
         * without a player, so the player-owned WorkbenchCraftHandler is not invoked.
         */
        @uk.co.enderfall.sdk.api.annotation.Experimental("Available only in the generated Fabric 1.21.4 preview")
        public Builder persistentTimed(uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec value, int ticks) {
            machine = false; machineInputs = java.util.List.of(); machineOutputs = java.util.List.of();
            Objects.requireNonNull(value, "storage");
            if (ticks < 1 || ticks > 1_728_000) throw new IllegalArgumentException("Processing ticks must be 1..1728000");
            if (value.inventorySlots() != recipeType.inputSlots() + 1) {
                throw new IllegalArgumentException("Timed inventory requires inputs plus one stored output slot");
            }
            storage = value;
            processingTicks = ticks;
            return this;
        }
    }
}
