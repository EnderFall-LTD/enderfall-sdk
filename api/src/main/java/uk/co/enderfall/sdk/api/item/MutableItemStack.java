package uk.co.enderfall.sdk.api.item;

/**
 * Safe, loader-neutral access to the real item stack involved in an interaction.
 * Mutating methods are server-only; client-side views reject writes.
 */
public interface MutableItemStack {
    int count();

    int maxStackSize();

    boolean damageable();

    int damage();

    int maxDamage();

    default int remainingDurability() {
        return Math.max(0, maxDamage() - damage());
    }

    /**
     * Applies deterministic durability damage. Returns {@code true} when the item breaks.
     * This low-level operation deliberately does not roll Unbreaking; portable item behavior
     * decides exactly when a successful action should cost durability.
     */
    boolean damage(int amount);

    /** Repairs up to {@code amount} durability and returns the amount actually repaired. */
    int repair(int amount);

    /** Consumes up to {@code amount} items and returns the number actually consumed. */
    int consume(int amount);
}
