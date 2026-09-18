package uk.co.enderfall.sdk.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import uk.co.enderfall.sdk.api.block.BlockProperty;
import uk.co.enderfall.sdk.api.block.BlockStateManager;
import uk.co.enderfall.sdk.api.block.BlockToolRef;
import uk.co.enderfall.sdk.api.block.BlockToolResult;
import uk.co.enderfall.sdk.api.block.PortableBlockState;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
import uk.co.enderfall.sdk.api.registry.BlockRef;

final class DefaultBlockStateManager implements BlockStateManager {
    private final PlatformAdapter adapter;
    private final DefaultBlockRegistrar blocks;
    DefaultBlockStateManager(PlatformAdapter adapter) { this(adapter, null); }
    DefaultBlockStateManager(PlatformAdapter adapter, DefaultBlockRegistrar blocks) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.blocks = blocks;
    }
    @Override public Optional<PortableBlockState> get(BlockRef block, BlockLocation location) {
        return adapter.blockState(Objects.requireNonNull(block, "block"), Objects.requireNonNull(location, "location"));
    }
    @Override public boolean update(BlockRef block, BlockLocation location, UnaryOperator<PortableBlockState> change) {
        return adapter.updateBlockState(Objects.requireNonNull(block, "block"),
                Objects.requireNonNull(location, "location"), Objects.requireNonNull(change, "change"));
    }

    @Override public Optional<BlockToolResult> selectNextToolProperty(BlockRef block, BlockLocation location,
            BlockToolRef tool, int currentSelection) {
        var properties = toolProperties(block, tool);
        if (properties.isEmpty()) return Optional.empty();
        var state = get(block, location).orElse(null);
        if (state == null) return Optional.empty();
        int selection = (Math.floorMod(currentSelection, properties.size()) + 1) % properties.size();
        return Optional.of(result(tool, selection, properties, state, state, false));
    }

    @Override public Optional<BlockToolResult> cycleToolProperty(BlockRef block, BlockLocation location,
            BlockToolRef tool, int selectedProperty) {
        return cycleToolProperty(block, location, tool, selectedProperty, ignored -> true, ignored -> { });
    }

    @Override public Optional<BlockToolResult> cycleToolProperty(BlockRef block, BlockLocation location,
            BlockToolRef tool, int selectedProperty,
            java.util.function.Predicate<uk.co.enderfall.sdk.api.block.BlockToolChange> policy,
            java.util.function.Consumer<BlockToolResult> committed) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(committed, "committed");
        var properties = toolProperties(block, tool);
        if (properties.isEmpty()) return Optional.empty();
        int selection = Math.floorMod(selectedProperty, properties.size());
        BlockProperty<?> property = properties.get(selection);
        var result = new AtomicReference<BlockToolResult>();
        boolean changed = update(block, location, state -> {
            PortableBlockState replacement = cycle(state, property);
            var proposed = change(tool, selection, properties, property, state, replacement);
            if (!policy.test(proposed)) return state;
            result.set(result(proposed));
            return replacement;
        });
        if (!changed) return Optional.empty();
        var applied = result.get();
        if (applied == null) return Optional.empty();
        committed.accept(applied);
        return Optional.of(applied);
    }

    private java.util.List<BlockProperty<?>> toolProperties(BlockRef block, BlockToolRef tool) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(tool, "tool");
        if (blocks == null) return java.util.List.of();
        return blocks.spec(block.id()).map(spec -> spec.toolProperties(tool)).orElseGet(java.util.List::of);
    }

    private static BlockToolResult result(BlockToolRef tool, int selection,
            java.util.List<BlockProperty<?>> properties, PortableBlockState previous,
            PortableBlockState replacement, boolean changed) {
        BlockProperty<?> property = properties.get(selection);
        return new BlockToolResult(tool, selection, properties.size(), property.name(),
                serialize(previous, property), serialize(replacement, property), changed);
    }

    private static uk.co.enderfall.sdk.api.block.BlockToolChange change(BlockToolRef tool, int selection,
            java.util.List<BlockProperty<?>> properties, BlockProperty<?> property,
            PortableBlockState previous, PortableBlockState replacement) {
        return new uk.co.enderfall.sdk.api.block.BlockToolChange(tool, selection, properties.size(),
                property.name(), serialize(previous, property), serialize(replacement, property),
                previous, replacement);
    }

    private static BlockToolResult result(uk.co.enderfall.sdk.api.block.BlockToolChange change) {
        return new BlockToolResult(change.tool(), change.selectionIndex(), change.propertyCount(),
                change.propertyName(), change.previousValue(), change.value(), true);
    }

    private static <T> PortableBlockState cycle(PortableBlockState state, BlockProperty<T> property) {
        return state.cycle(property);
    }

    private static <T> String serialize(PortableBlockState state, BlockProperty<T> property) {
        return property.serialize(state.get(property));
    }
}
