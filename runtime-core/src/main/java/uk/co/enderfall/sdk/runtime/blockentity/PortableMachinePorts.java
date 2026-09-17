package uk.co.enderfall.sdk.runtime.blockentity;

import java.util.Objects;

/** Fixed world-facing positional ports for experimental timed workbenches. */
public final class PortableMachinePorts {
    public enum Face { UP, NORTH, EAST, SOUTH, WEST, DOWN }
    private final int inputs;

    public PortableMachinePorts(int inputs) {
        if (inputs < 1 || inputs > 5) throw new IllegalArgumentException("Machine ports require 1-5 inputs");
        this.inputs = inputs;
    }

    /** New array on every call; callers cannot change the routing policy. */
    public int[] slots(Face face) {
        int slot = switch (Objects.requireNonNull(face, "face")) {
            case UP -> 0;
            case NORTH -> 1;
            case EAST -> 2;
            case SOUTH -> 3;
            case WEST -> 4;
            case DOWN -> inputs;
        };
        return face == Face.DOWN || slot < inputs ? new int[] {slot} : new int[0];
    }

    public boolean canInsert(int slot, Face face) {
        if (face == null || face == Face.DOWN || slot < 0 || slot >= inputs) return false;
        int[] exposed = slots(face);
        return exposed.length == 1 && exposed[0] == slot;
    }

    public boolean canExtract(int slot, Face face) { return face == Face.DOWN && slot == inputs; }
    public boolean isInput(int slot) { return slot >= 0 && slot < inputs; }
    public boolean isOutput(int slot) { return slot == inputs; }
}
