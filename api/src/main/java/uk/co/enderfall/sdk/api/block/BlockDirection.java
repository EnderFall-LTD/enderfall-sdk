package uk.co.enderfall.sdk.api.block;

/** Loader-neutral direction used by portable block behavior callbacks. */
public enum BlockDirection {
    DOWN,
    UP,
    NORTH,
    SOUTH,
    WEST,
    EAST;

    public BlockDirection opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public boolean horizontal() {
        return this != DOWN && this != UP;
    }
}
