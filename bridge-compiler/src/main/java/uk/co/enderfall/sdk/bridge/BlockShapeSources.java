package uk.co.enderfall.sdk.bridge;

/** One native shape implementation shared by ordinary and persistent blocks on every target. */
final class BlockShapeSources {
    private BlockShapeSources() { }
    static String emit(String target) {
        BlockEntityNativePolicy.require(target);
        boolean legacyNeighborSignature = target.startsWith("1.20.1-") || target.startsWith("1.21.1-");
        String neighborHook = legacyNeighborSignature ? """
                    @Override
                    @SuppressWarnings("deprecation")
                    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
                        BlockState nativeState = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
                        return nativeState.getBlock() == this ? portableNeighborUpdate(nativeState, direction, neighborState) : nativeState;
                    }
                """ : """
                    @Override
                    @SuppressWarnings("deprecation")
                    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level,
                            net.minecraft.world.level.ScheduledTickAccess scheduledTicks, BlockPos pos,
                            Direction direction, BlockPos neighborPos, BlockState neighborState,
                            net.minecraft.util.RandomSource random) {
                        BlockState nativeState = super.updateShape(state, level, scheduledTicks, pos,
                                direction, neighborPos, neighborState, random);
                        return nativeState.getBlock() == this ? portableNeighborUpdate(nativeState, direction, neighborState) : nativeState;
                    }
                """;
        return """
                package uk.co.enderfall.sdk.runtime.blockentity.nativebridge;

                import net.minecraft.core.BlockPos;
                import net.minecraft.core.Direction;
                import net.minecraft.world.item.context.BlockPlaceContext;
                import net.minecraft.world.level.block.Rotation;
                import net.minecraft.world.level.block.Mirror;
                import net.minecraft.world.level.block.state.StateDefinition;
                import net.minecraft.world.level.block.state.properties.BlockStateProperties;
                import net.minecraft.world.level.BlockGetter;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.block.state.BlockState;
                import net.minecraft.world.phys.shapes.CollisionContext;
                import net.minecraft.world.phys.shapes.Shapes;
                import net.minecraft.world.phys.shapes.VoxelShape;
                import uk.co.enderfall.sdk.api.block.BlockShape;
                import uk.co.enderfall.sdk.api.registry.BlockSpec;

                /** Native shell only. Shape authoring is entirely portable. */
                public class PortableShapeBlock extends Block {
                    private static final ThreadLocal<BlockSpec> CONSTRUCTION = new ThreadLocal<>();
                    /** Scoped because Block constructs state definitions before subclass fields exist. */
                    public static <T> T construct(BlockSpec spec, java.util.function.Supplier<T> factory) {
                        BlockSpec previous = CONSTRUCTION.get();
                        CONSTRUCTION.set(spec);
                        try { return factory.get(); }
                        finally {
                            if (previous == null) CONSTRUCTION.remove(); else CONSTRUCTION.set(previous);
                        }
                    }
                    private static final class PortableProperty extends net.minecraft.world.level.block.state.properties.Property<String> {
                        private final java.util.List<String> values;
                        private PortableProperty(uk.co.enderfall.sdk.api.block.BlockProperty<?> property) {
                            super(property.name(), String.class);
                            values = encodedValues(property);
                        }
                        private static <T> java.util.List<String> encodedValues(uk.co.enderfall.sdk.api.block.BlockProperty<T> property) {
                            return property.values().stream().map(property::serialize).toList();
                        }
                        @Override public java.util.List<String> getPossibleValues() { return values; }
                        @Override public String getName(String value) { return value; }
                        // Required from 1.21.4 onward; harmless additional method on older targets.
                        public int getInternalIndex(String value) { return values.indexOf(value); }
                        @Override public boolean equals(Object other) {
                            return this == other || other instanceof PortableProperty property
                                    && super.equals(other) && values.equals(property.values);
                        }
                        @Override public java.util.Optional<String> getValue(String value) {
                            return values.contains(value) ? java.util.Optional.of(value) : java.util.Optional.empty();
                        }
                    }
                    private final BlockSpec specification;
                    /** Marker available even while Block's constructor builds its state definition. */
                    public interface Directional { }
                    public interface SixWayDirectional extends Directional { }
                    private static final class SixWayBlock extends PortableShapeBlock implements SixWayDirectional {
                        private SixWayBlock(BlockBehaviour.Properties properties, BlockSpec spec) { super(properties, spec); }
                    }
                    private static final class DirectionalBlock extends PortableShapeBlock implements Directional {
                        private DirectionalBlock(BlockBehaviour.Properties properties, BlockSpec spec) { super(properties, spec); }
                    }
                    public static PortableShapeBlock create(BlockBehaviour.Properties properties, BlockSpec spec) {
                        return construct(spec, () -> {
                            var block = spec.sixWayFacing() ? new SixWayBlock(properties, spec)
                                    : spec.horizontalFacing() ? new DirectionalBlock(properties, spec) : new PortableShapeBlock(properties, spec);
                            block.initializeFacing();
                            return block;
                        });
                    }
                    private final VoxelShape[] outline;
                    private final VoxelShape[] collision;
                    private final java.util.Map<BlockState, VoxelShape> stateShapes;
                    public PortableShapeBlock(BlockBehaviour.Properties properties, BlockSpec spec) {
                        super(prepare(properties, spec));
                        specification = spec;
                        outline = spec.outlineShape().map(PortableShapeBlock::rotations).orElse(null);
                        collision = spec.collisionShape().map(PortableShapeBlock::rotations).orElse(null);
                        var dynamic = new java.util.HashMap<BlockState, VoxelShape>();
                        if (spec.stateShapes().isPresent()) {
                            var shapes = spec.stateShapes().orElseThrow();
                            for (BlockState state : stateDefinition.getPossibleStates()) {
                                var encoded = new java.util.LinkedHashMap<String, String>();
                                for (var property : spec.states().properties()) {
                                    encoded.put(property.name(), state.getValue((PortableProperty) stateDefinition.getProperty(property.name())));
                                }
                                BlockShape shape = shapes.shape(spec.states().parse(encoded));
                                shape = switch (directionIndex(state)) {
                                    case 1 -> shape.rotateY(1);
                                    case 2 -> shape.rotateY(2);
                                    case 3 -> shape.rotateY(3);
                                    case 4 -> shape.rotateX(1);
                                    case 5 -> shape.rotateX(-1);
                                    default -> shape;
                                };
                                dynamic.put(state, convert(shape));
                            }
                        }
                        stateShapes = java.util.Map.copyOf(dynamic);
                    }
                    /** Called by native factories after the complete subclass has been constructed. */
                    public final void initializeFacing() {
                        BlockState state = stateDefinition.any();
                        if (this instanceof Directional) state = state.setValue(facingProperty(), Direction.NORTH);
                        for (var entry : specification.states().defaultState().serializedValues().entrySet()) {
                            var property = (PortableProperty) stateDefinition.getProperty(entry.getKey());
                            state = state.setValue(property, entry.getValue());
                        }
                        registerDefaultState(state);
                    }
                    private net.minecraft.world.level.block.state.properties.EnumProperty<Direction> facingProperty() {
                        return this instanceof SixWayDirectional ? BlockStateProperties.FACING : BlockStateProperties.HORIZONTAL_FACING;
                    }
                    public final uk.co.enderfall.sdk.api.block.PortableBlockState portableState(BlockState state) {
                        if (state.getBlock() != this) throw new IllegalArgumentException("State belongs to another block");
                        var encoded = new java.util.LinkedHashMap<String, String>();
                        for (var property : specification.states().properties()) {
                            var nativeProperty = (PortableProperty) stateDefinition.getProperty(property.name());
                            encoded.put(property.name(), state.getValue(nativeProperty));
                        }
                        return specification.states().parse(encoded);
                    }
                    public final BlockState withPortableState(BlockState nativeState,
                            uk.co.enderfall.sdk.api.block.PortableBlockState portableState) {
                        if (nativeState.getBlock() != this) throw new IllegalArgumentException("State belongs to another block");
                        if (portableState.definition() != specification.states())
                            throw new IllegalArgumentException("State uses another block definition");
                        BlockState result = nativeState;
                        for (var entry : portableState.serializedValues().entrySet()) {
                            var property = (PortableProperty) stateDefinition.getProperty(entry.getKey());
                            result = result.setValue(property, entry.getValue());
                        }
                        return result;
                    }
                    private static VoxelShape[] rotations(BlockShape shape) {
                        return new VoxelShape[] { convert(shape), convert(shape.rotateY(1)), convert(shape.rotateY(2)), convert(shape.rotateY(3)),
                                convert(shape.rotateX(1)), convert(shape.rotateX(-1)) };
                    }
                    private static int directionIndex(BlockState state) {
                        if (!state.hasProperty(BlockStateProperties.FACING) && !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return 0;
                        return switch (state.getValue(state.hasProperty(BlockStateProperties.FACING) ? BlockStateProperties.FACING : BlockStateProperties.HORIZONTAL_FACING)) {
                            case EAST -> 1;
                            case SOUTH -> 2;
                            case WEST -> 3;
                            case UP -> 4;
                            case DOWN -> 5;
                            default -> 0;
                        };
                    }
                    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                        super.createBlockStateDefinition(builder);
                        if (this instanceof Directional) builder.add(facingProperty());
                        BlockSpec spec = CONSTRUCTION.get();
                        if (spec != null) for (var property : spec.states().properties()) builder.add(new PortableProperty(property));
                    }
                    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
                        BlockState placed;
                        if (this instanceof SixWayDirectional) placed = defaultBlockState().setValue(facingProperty(), context.getNearestLookingDirection().getOpposite());
                        else if (this instanceof Directional) placed = defaultBlockState().setValue(facingProperty(), context.getHorizontalDirection().getOpposite());
                        else placed = super.getStateForPlacement(context);
                        if (placed == null || specification.behavior().isEmpty()) return placed;
                        var portableContext = new uk.co.enderfall.sdk.api.block.BlockPlacementContext(
                                portableState(placed), direction(context.getClickedFace()),
                                direction(context.getHorizontalDirection()), direction(context.getNearestLookingDirection()));
                        var portable = java.util.Objects.requireNonNull(
                                specification.behavior().orElseThrow().onPlace(portableContext),
                                "PortableBlock.onPlace returned null");
                        return withPortableState(placed, portable);
                    }
                    private static uk.co.enderfall.sdk.api.block.BlockDirection direction(Direction direction) {
                        return switch (direction) {
                            case DOWN -> uk.co.enderfall.sdk.api.block.BlockDirection.DOWN;
                            case UP -> uk.co.enderfall.sdk.api.block.BlockDirection.UP;
                            case NORTH -> uk.co.enderfall.sdk.api.block.BlockDirection.NORTH;
                            case SOUTH -> uk.co.enderfall.sdk.api.block.BlockDirection.SOUTH;
                            case WEST -> uk.co.enderfall.sdk.api.block.BlockDirection.WEST;
                            case EAST -> uk.co.enderfall.sdk.api.block.BlockDirection.EAST;
                        };
                    }
                    @SuppressWarnings("deprecation") // Legacy FML deprecates the still-required built-in registry field.
                    private BlockState portableNeighborUpdate(BlockState state, Direction direction, BlockState neighborState) {
                        if (specification.behavior().isEmpty()) return state;
                        var neighborId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(neighborState.getBlock());
                        java.util.Optional<uk.co.enderfall.sdk.api.block.PortableBlockState> portableNeighbor =
                                neighborState.getBlock() instanceof PortableShapeBlock portable
                                        ? java.util.Optional.of(portable.portableState(neighborState)) : java.util.Optional.empty();
                        var context = new uk.co.enderfall.sdk.api.block.BlockNeighborContext(
                                portableState(state), direction(direction),
                                uk.co.enderfall.sdk.api.ResourceId.parse(neighborId.toString()),
                                neighborState.getBlock() == this, portableNeighbor);
                        var portable = java.util.Objects.requireNonNull(
                                specification.behavior().orElseThrow().onNeighborUpdate(context),
                                "PortableBlock.onNeighborUpdate returned null");
                        return withPortableState(state, portable);
                    }
                // ${NEIGHBOR_HOOK}
                    @Override @SuppressWarnings("deprecation")
                    public BlockState rotate(BlockState state, Rotation rotation) {
                        return this instanceof Directional ? state.setValue(facingProperty(),
                                rotation.rotate(state.getValue(facingProperty()))) : super.rotate(state, rotation);
                    }
                    @Override @SuppressWarnings("deprecation")
                    public BlockState mirror(BlockState state, Mirror mirror) {
                        return this instanceof Directional ? state.setValue(facingProperty(),
                                mirror.mirror(state.getValue(facingProperty()))) : super.mirror(state, mirror);
                    }
                    private static BlockBehaviour.Properties prepare(BlockBehaviour.Properties properties, BlockSpec spec) {
                        if (spec.outlineShape().isPresent() || spec.collisionShape().isPresent() || spec.stateShapes().isPresent()) properties.noOcclusion();
                        return properties;
                    }
                    private static VoxelShape convert(BlockShape shape) {
                        VoxelShape result = Shapes.empty();
                        for (var box : shape.boxes()) {
                            result = Shapes.or(result, Shapes.box(box.minX() / 16.0, box.minY() / 16.0,
                                    box.minZ() / 16.0, box.maxX() / 16.0, box.maxY() / 16.0, box.maxZ() / 16.0));
                        }
                        return result.optimize();
                    }
                    @Override
                    @SuppressWarnings("deprecation") // 1.20.1 marks the required block override hook deprecated.
                    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
                        if (stateShapes.containsKey(state)) return stateShapes.get(state);
                        return outline == null ? super.getShape(state, world, pos, context) : outline[directionIndex(state)];
                    }
                    @Override
                    @SuppressWarnings("deprecation") // Callers use BlockState; implementations still override this hook.
                    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
                        if (stateShapes.containsKey(state)) return stateShapes.get(state);
                        return collision == null ? super.getCollisionShape(state, world, pos, context) : collision[directionIndex(state)];
                    }
                }
                """.replace("// ${NEIGHBOR_HOOK}", neighborHook.stripTrailing());
    }
}
