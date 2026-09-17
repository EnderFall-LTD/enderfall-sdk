package uk.co.enderfall.sdk.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import uk.co.enderfall.sdk.api.block.BlockStateManager;
import uk.co.enderfall.sdk.api.block.PortableBlockState;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
import uk.co.enderfall.sdk.api.registry.BlockRef;

final class DefaultBlockStateManager implements BlockStateManager {
    private final PlatformAdapter adapter;
    DefaultBlockStateManager(PlatformAdapter adapter) { this.adapter = Objects.requireNonNull(adapter, "adapter"); }
    @Override public Optional<PortableBlockState> get(BlockRef block, BlockLocation location) {
        return adapter.blockState(Objects.requireNonNull(block, "block"), Objects.requireNonNull(location, "location"));
    }
    @Override public boolean update(BlockRef block, BlockLocation location, UnaryOperator<PortableBlockState> change) {
        return adapter.updateBlockState(Objects.requireNonNull(block, "block"),
                Objects.requireNonNull(location, "location"), Objects.requireNonNull(change, "change"));
    }
}
