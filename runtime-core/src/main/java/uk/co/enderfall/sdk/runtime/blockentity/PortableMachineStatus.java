package uk.co.enderfall.sdk.runtime.blockentity;

/** Transient server-derived menu status. Codes are explicit, never enum ordinals. */
public enum PortableMachineStatus {
    IDLE(0), PROCESSING(1), OUTPUT_BLOCKED(2), REMAINDER_BLOCKED(3), FAULT(4);

    private final int code;
    PortableMachineStatus(int code) { this.code = code; }
    public int code() { return code; }
    /** Unknown status must not appear to be successful processing. */
    public static PortableMachineStatus fromCode(int code) {
        return switch (code) {
            case 0 -> IDLE;
            case 1 -> PROCESSING;
            case 2 -> OUTPUT_BLOCKED;
            case 3 -> REMAINDER_BLOCKED;
            default -> FAULT;
        };
    }
}
