package uk.co.enderfall.sdk.bridge;

/** Central vanilla-bucket interaction; no loader-specific implementation copies. */
final class FluidBucketSources {
    private FluidBucketSources() { }

    static String helper() {
        return """
                        private boolean handleFluidBucket(Level level, BlockPos pos, Player player,
                                net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
                            ItemStack held = player.getItemInHand(hand);
                            boolean empty = held.is(net.minecraft.world.item.Items.BUCKET);
                            boolean water = held.is(net.minecraft.world.item.Items.WATER_BUCKET);
                            boolean lava = held.is(net.minecraft.world.item.Items.LAVA_BUCKET);
                            if (binding.spec.tanks().isEmpty() || (!empty && !water && !lava)) return false;
                            // Consume recognized interactions even when rejected: never place fluid in the world instead.
                            if (level.isClientSide || player.isSpectator()) return true;
                            if (!(level.getBlockEntity(pos) instanceof StoredBlockEntity owner)) return true;
                            long bucket = uk.co.enderfall.sdk.api.fluid.FluidVolume.BUCKET;
                            for (var spec : binding.spec.tanks().values()) {
                                var tank = owner.fluidPort(spec, hit.getDirection());
                                net.minecraft.world.item.Item result;
                                if (!empty) {
                                    var volume = new uk.co.enderfall.sdk.api.fluid.FluidVolume(
                                            ResourceId.parse(water ? "minecraft:water" : "minecraft:lava"), bucket);
                                    if (tank.fill(volume, true) != bucket) continue;
                                    tank.fill(volume, false);
                                    result = net.minecraft.world.item.Items.BUCKET;
                                } else {
                                    var contents = tank.contents();
                                    if (contents.isEmpty() || contents.get().amount() < bucket) continue;
                                    String id = contents.get().fluid().toString();
                                    if (!id.equals("minecraft:water") && !id.equals("minecraft:lava")) continue;
                                    if (tank.drain(contents.get().fluid(), bucket, true) != bucket) continue;
                                    tank.drain(contents.get().fluid(), bucket, false);
                                    result = id.equals("minecraft:water") ? net.minecraft.world.item.Items.WATER_BUCKET
                                            : net.minecraft.world.item.Items.LAVA_BUCKET;
                                }
                                // Vanilla exchanges one container, retaining the stack and adding/dropping
                                // the result as needed; creative handling also stays with Minecraft.
                                player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(
                                        held, player, new ItemStack(result)));
                                showFluidStatus(owner, player, empty ? "Removed one bucket" : "Added one bucket");
                                return true;
                            }
                            showFluidStatus(owner, player, "No transfer: check fluid, space and face access");
                            return true;
                        }

                        private void showFluidStatus(StoredBlockEntity owner, Player player, String action) {
                            StringBuilder text = new StringBuilder(action);
                            for (var spec : binding.spec.tanks().values()) {
                                var tank = owner.fluidTank(spec);
                                var state = uk.co.enderfall.sdk.api.ui.MenuState.builder().tank("tank", tank).build();
                                text.append(" | ").append(spec.name()).append(": ")
                                        .append(state.value("tank.fluid").isEmpty() ? "empty" : state.value("tank.fluid"))
                                        .append(" ").append(state.value("tank.percent")).append("% (")
                                        .append(state.value("tank.amount")).append("/")
                                        .append(state.value("tank.capacity")).append(" units)");
                            }
                            // Server-owned snapshot; never infer success from the creative held item.
                            String message = text.length() > 512 ? text.substring(0, 509) + "..." : text.toString();
                            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(message), true);
                            }
                        }
                """;
    }

    static String itemHook(BlockEntityNativePolicy policy) {
        if (policy.legacy()) return "";
        String type = policy.target().startsWith("1.21.1-") ? "ItemInteractionResult" : "InteractionResult";
        return """
                        @Override
                        protected net.minecraft.world.%s useItemOn(ItemStack stack, BlockState state,
                                Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
                                net.minecraft.world.phys.BlockHitResult hit) {
                            if (handleFluidBucket(level, pos, player, hand, hit)) return net.minecraft.world.%s.SUCCESS;
                            return super.useItemOn(stack, state, level, pos, player, hand, hit);
                        }
                """.formatted(type, type);
    }
}
