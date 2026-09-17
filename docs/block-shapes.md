# Portable block shapes

Static shapes work with ordinary blocks and persistent blocks in generated feature
runtimes (`-Penderfall.persistence=true`). Reference/baseline runtimes reject them
explicitly instead of silently creating a full cube.

```java
public final class TableBlock implements PortableBlock {
    private static final BlockShape TOP = BlockShape.box(0, 12, 0, 16, 16, 16);
    private static final BlockShape LEG = BlockShape.box(1, 0, 1, 3, 12, 3);

    @Override
    public void configure(Registration.BlockOptions properties) {
        properties.shape(BlockShape.union(TOP, LEG, LEG.rotateY(1),
            LEG.rotateY(2), LEG.rotateY(3)));
    }
}
```

Register it with `BLOCKS.block("table", TableBlock::new, p -> p.withItem())`.
No Minecraft imports or target branches are needed.

- Coordinates use model pixels, 0..16 on each axis. Bounds must be finite and
  strictly ordered. Protruding boxes outside the block are not supported yet.
- A shape is an immutable union of at most 64 cuboids. Empty shapes are allowed.
- `rotateY(n)` rotates around the block center in clockwise quarter-turns viewed
  from above; negative turns are supported. This authors rotated geometry; it
  does **not** automatically add a facing state or placement behavior.
- `shape(value)` sets outline and collision together.
- `outlineShape(value)` and `collisionShape(value)` override them individually.
  Unspecified shapes defer to Minecraft's base block behavior; an unspecified
  collision shape can therefore follow an explicitly supplied outline.
  Use `collisionShape(BlockShape.empty())` for an explicitly non-solid shape.
- Native cuboid unions are constructed once per block definition, not per query.
- Custom-shaped blocks use conservative non-occluding properties so empty space
  does not hide neighboring faces. Fine-grained occlusion/light shapes are not
  implemented yet.
- Models remain separate resources. The SDK does not infer physics from model JSON.
- Property copying does not copy custom shape behavior, or erase the receiving
  definition's shapes. Apply a shared shape explicitly when wanted.

## Preview check

The persistent preview's existing `workbench` now has a tabletop and four legs,
with matching JSON model and portable shapes. Its ID, inventory schema and menu
remain unchanged. The timed workbench and tank retain their previous geometry.

On a disposable world, check targeting through the gaps, outline around the legs
and tabletop, collision against each piece, item rendering, and opening/storing/
reopening the workbench. Check adjacent opaque blocks for incorrect face culling.
Build and source parity alone do not prove these in-game behaviors.

Opt-in [horizontal facing](directional-blocks.md) now rotates these shapes with
native placement and rotation. Arbitrary state-dependent geometry and
neighbor-driven connections remain pending.
