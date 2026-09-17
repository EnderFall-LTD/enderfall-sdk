package uk.co.enderfall.sdk.runtime.blockentity;

/**
 * Internal native-stack boundary, not a consumer API. Implementations must preserve all
 * item components/NBT, enforce native stack limits, and reject undecodable item data.
 * Never reduce a stack to an item ID and count. Empty stacks have an explicit encoding.
 * Encoding must be deterministic for unchanged data; decoding must consume the entire
 * input and reject malformed or unknown data instead of substituting an empty stack.
 * All operations run on the owning game thread with the world's registry context.
 */
public interface BlockEntityStackCodec<S> {
    S empty();
    S copy(S stack);
    byte[] encode(S stack);
    S decode(byte[] encoded);
}
