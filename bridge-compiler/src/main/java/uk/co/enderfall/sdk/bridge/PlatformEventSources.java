package uk.co.enderfall.sdk.bridge;

/**
 * Shared event operations with explicit native ABI facets.
 * These are authored compiler templates, not contextual edits of canonical Java.
 * Layout intentionally retains reference source and bytecode parity.
 */
final class PlatformEventSources {
    private PlatformEventSources() { }

    static String emit(PlatformOperation operation, NativePlatformPolicy policy) throws BridgeGenerationException {
        return switch (operation) {
            case INSTALL_EVENTS -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED -> """
                        private void installEvents() {
                            AtomicLong tick = new AtomicLong();
                            ServerLifecycleEvents.SERVER_STARTING.register(value -> {
                                server = value;
                                context.runtimeConfigs().loadServerConfigs();
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STARTING);
                            });
                            ServerLifecycleEvents.SERVER_STARTED.register(value -> publishLifecycle(LifecycleEvent.Stage.SERVER_STARTED));
                            ServerLifecycleEvents.SERVER_STOPPING.register(value -> publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPING));
                            ServerLifecycleEvents.SERVER_STOPPED.register(value -> {
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPED);
                                context.runtimeConfigs().unloadServerConfigs();
                                joiningPlayers.clear();
                                server = null;
                            });
                            ServerTickEvents.START_SERVER_TICK.register(value -> context.runtimeEvents().publish(
                                    SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.START, tick.get())));
                            ServerTickEvents.END_SERVER_TICK.register(value -> context.runtimeEvents().publish(
                                    SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.END, tick.getAndIncrement())));
                            ServerPlayConnectionEvents.JOIN.register((handler, sender, value) -> {
                                java.util.UUID playerId = handler.player.getUUID();
                                // Fabric fires JOIN before the player is guaranteed to be visible through PlayerList.
                                joiningPlayers.put(playerId, handler.player);
                                try {
                                    context.runtimeNetworking().connectionOpened(playerId);
                                    context.runtimeEvents().publish(SdkEvents.PLAYER,
                                            new PlayerEvent(PlayerEvent.Action.JOIN, playerId,
                                                    handler.player.getGameProfile().getName()));
                                } finally {
                                    joiningPlayers.remove(playerId, handler.player);
                                }
                            });
                            ServerPlayConnectionEvents.DISCONNECT.register((handler, value) -> {
                                java.util.UUID playerId = handler.player.getUUID();
                                joiningPlayers.remove(playerId);
                                context.runtimeNetworking().connectionClosed(playerId);
                                context.runtimeEvents().publish(SdkEvents.PLAYER,
                                        new PlayerEvent(PlayerEvent.Action.LEAVE, playerId,
                                                handler.player.getGameProfile().getName()));
                            });
                            UseItemCallback.EVENT.register((player, level, hand) -> {
                                ResourceLocation target = BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem());
                                InteractionResult result = interaction(InteractionEvent.Kind.USE_ITEM,
                                        level.isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        player.getUUID(), target, target,
                                        hand == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        player.isShiftKeyDown(), null);
                                return result == InteractionResult.FAIL
                                        ? InteractionResultHolder.fail(player.getItemInHand(hand))
                                        : result == InteractionResult.SUCCESS
                                                ? InteractionResultHolder.success(player.getItemInHand(hand))
                                                : InteractionResultHolder.pass(player.getItemInHand(hand));
                            });
                            UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> interaction(
                                    InteractionEvent.Kind.USE_BLOCK,
                                    level.isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                    player.getUUID(),
                                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(hitResult.getBlockPos()).getBlock()),
                                    BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()),
                                    hand == net.minecraft.world.InteractionHand.OFF_HAND
                                            ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                    player.isShiftKeyDown(),
                                    new uk.co.enderfall.sdk.api.blockentity.BlockLocation(
                                            ResourceId.parse(level.dimension().location().toString()),
                                            hitResult.getBlockPos().getX(), hitResult.getBlockPos().getY(),
                                            hitResult.getBlockPos().getZ())));
                            if (platformInfo.environment() == Environment.CLIENT) {
                                ${ClientHooks}.installLifecycle(context);
                            }
                        }
                    
                    """;
                case FABRIC_KEYED -> """
                        private void installEvents() {
                            AtomicLong tick = new AtomicLong();
                            ServerLifecycleEvents.SERVER_STARTING.register(value -> {
                                server = value;
                                context.runtimeConfigs().loadServerConfigs();
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STARTING);
                            });
                            ServerLifecycleEvents.SERVER_STARTED.register(value -> publishLifecycle(LifecycleEvent.Stage.SERVER_STARTED));
                            ServerLifecycleEvents.SERVER_STOPPING.register(value -> publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPING));
                            ServerLifecycleEvents.SERVER_STOPPED.register(value -> {
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPED);
                                context.runtimeConfigs().unloadServerConfigs();
                                joiningPlayers.clear();
                                server = null;
                            });
                            ServerTickEvents.START_SERVER_TICK.register(value -> context.runtimeEvents().publish(
                                    SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.START, tick.get())));
                            ServerTickEvents.END_SERVER_TICK.register(value -> context.runtimeEvents().publish(
                                    SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.END, tick.getAndIncrement())));
                            ServerPlayConnectionEvents.JOIN.register((handler, sender, value) -> {
                                java.util.UUID playerId = handler.player.getUUID();
                                // Fabric fires JOIN before the player is guaranteed to be visible through PlayerList.
                                joiningPlayers.put(playerId, handler.player);
                                try {
                                    context.runtimeNetworking().connectionOpened(playerId);
                                    context.runtimeEvents().publish(SdkEvents.PLAYER,
                                            new PlayerEvent(PlayerEvent.Action.JOIN, playerId,
                                                    handler.player.getGameProfile().getName()));
                                } finally {
                                    joiningPlayers.remove(playerId, handler.player);
                                }
                            });
                            ServerPlayConnectionEvents.DISCONNECT.register((handler, value) -> {
                                java.util.UUID playerId = handler.player.getUUID();
                                joiningPlayers.remove(playerId);
                                context.runtimeNetworking().connectionClosed(playerId);
                                context.runtimeEvents().publish(SdkEvents.PLAYER,
                                        new PlayerEvent(PlayerEvent.Action.LEAVE, playerId,
                                                handler.player.getGameProfile().getName()));
                            });
                            UseItemCallback.EVENT.register((player, level, hand) -> {
                                ResourceLocation target = BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem());
                                return interaction(InteractionEvent.Kind.USE_ITEM,
                                        level.isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        player.getUUID(), target, target,
                                        hand == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        player.isShiftKeyDown(), null);
                            });
                            UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
                                ResourceLocation target = BuiltInRegistries.BLOCK.getKey(level.getBlockState(hitResult.getBlockPos()).getBlock());
                                return interaction(InteractionEvent.Kind.USE_BLOCK,
                                        level.isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        player.getUUID(), target,
                                        BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()),
                                        hand == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        player.isShiftKeyDown(),
                                        new uk.co.enderfall.sdk.api.blockentity.BlockLocation(
                                                ResourceId.parse(level.dimension().location().toString()),
                                                hitResult.getBlockPos().getX(), hitResult.getBlockPos().getY(),
                                                hitResult.getBlockPos().getZ()));
                            });
                            if (platformInfo.environment() == Environment.CLIENT) {
                                ${ClientHooks}.installLifecycle(context);
                            }
                        }
                    
                    """;
                case FABRIC_IDENTIFIER -> """
                        private void installEvents() {
                            AtomicLong tick = new AtomicLong();
                            ServerLifecycleEvents.SERVER_STARTING.register(value -> {
                                server = value;
                                context.runtimeConfigs().loadServerConfigs();
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STARTING);
                            });
                            ServerLifecycleEvents.SERVER_STARTED.register(value -> publishLifecycle(LifecycleEvent.Stage.SERVER_STARTED));
                            ServerLifecycleEvents.SERVER_STOPPING.register(value -> publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPING));
                            ServerLifecycleEvents.SERVER_STOPPED.register(value -> {
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPED);
                                context.runtimeConfigs().unloadServerConfigs();
                                joiningPlayers.clear();
                                server = null;
                            });
                            ServerTickEvents.START_SERVER_TICK.register(value -> context.runtimeEvents().publish(
                                    SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.START, tick.get())));
                            ServerTickEvents.END_SERVER_TICK.register(value -> context.runtimeEvents().publish(
                                    SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.END, tick.getAndIncrement())));
                            ServerPlayConnectionEvents.JOIN.register((handler, sender, value) -> {
                                java.util.UUID playerId = handler.player.getUUID();
                                // Fabric fires JOIN before the player is guaranteed to be visible through PlayerList.
                                joiningPlayers.put(playerId, handler.player);
                                try {
                                    context.runtimeNetworking().connectionOpened(playerId);
                                    context.runtimeEvents().publish(SdkEvents.PLAYER,
                                            new PlayerEvent(PlayerEvent.Action.JOIN, playerId,
                                                    handler.player.getGameProfile().name()));
                                } finally {
                                    joiningPlayers.remove(playerId, handler.player);
                                }
                            });
                            ServerPlayConnectionEvents.DISCONNECT.register((handler, value) -> {
                                java.util.UUID playerId = handler.player.getUUID();
                                joiningPlayers.remove(playerId);
                                context.runtimeNetworking().connectionClosed(playerId);
                                context.runtimeEvents().publish(SdkEvents.PLAYER,
                                        new PlayerEvent(PlayerEvent.Action.LEAVE, playerId,
                                                handler.player.getGameProfile().name()));
                            });
                            UseItemCallback.EVENT.register((player, level, hand) -> {
                                Identifier target = BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem());
                                return interaction(InteractionEvent.Kind.USE_ITEM,
                                        level.isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        player.getUUID(), target, target,
                                        hand == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        player.isShiftKeyDown(), null);
                            });
                            UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
                                Identifier target = BuiltInRegistries.BLOCK.getKey(
                                        level.getBlockState(hitResult.getBlockPos()).getBlock());
                                return interaction(InteractionEvent.Kind.USE_BLOCK,
                                        level.isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        player.getUUID(), target,
                                        BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()),
                                        hand == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        player.isShiftKeyDown(),
                                        new uk.co.enderfall.sdk.api.blockentity.BlockLocation(
                                                ResourceId.parse(level.dimension().identifier().toString()),
                                                hitResult.getBlockPos().getX(), hitResult.getBlockPos().getY(),
                                                hitResult.getBlockPos().getZ()));
                            });
                            if (platformInfo.environment() == Environment.CLIENT) {
                                ${ClientHooks}.installLifecycle(context);
                            }
                        }
                    
                    """;
                case LEGACY_FML -> """
                        private void installEvents() {
                            AtomicLong tick = new AtomicLong();
                            MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
                                server = event.getServer();
                                context.runtimeConfigs().loadServerConfigs();
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STARTING);
                            });
                            MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                                    publishLifecycle(LifecycleEvent.Stage.SERVER_STARTED));
                            MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
                                    publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPING));
                            MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPED);
                                context.runtimeConfigs().unloadServerConfigs();
                                server = null;
                            });
                            MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
                                uk.co.enderfall.sdk.api.event.TickEvent.Phase phase = event.phase == TickEvent.Phase.START
                                        ? uk.co.enderfall.sdk.api.event.TickEvent.Phase.START
                                        : uk.co.enderfall.sdk.api.event.TickEvent.Phase.END;
                                long current = tick.get();
                                context.runtimeEvents().publish(SdkEvents.TICK,
                                        new uk.co.enderfall.sdk.api.event.TickEvent(
                                                uk.co.enderfall.sdk.api.event.TickEvent.Side.SERVER, phase, current));
                                if (event.phase == TickEvent.Phase.END) {
                                    tick.incrementAndGet();
                                }
                            });
                            MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
                                context.runtimeNetworking().connectionOpened(event.getEntity().getUUID());
                                publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action.JOIN, event);
                            });
                            MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
                                context.runtimeNetworking().connectionClosed(event.getEntity().getUUID());
                                publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action.LEAVE, event);
                            });
                            MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
                                ResourceLocation target = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
                                InteractionResult result = interaction(InteractionEvent.Kind.USE_ITEM,
                                        event.getLevel().isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        event.getEntity().getUUID(), target, target,
                                        event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        event.getEntity().isShiftKeyDown(), null);
                                if (result != InteractionResult.PASS) {
                                    event.setCancellationResult(result);
                                    event.setCanceled(true);
                                }
                            });
                            MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
                                ResourceLocation target = ForgeRegistries.BLOCKS.getKey(
                                        event.getLevel().getBlockState(event.getPos()).getBlock());
                                InteractionResult result = interaction(InteractionEvent.Kind.USE_BLOCK,
                                        event.getLevel().isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        event.getEntity().getUUID(), target,
                                        ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem()),
                                        event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        event.getEntity().isShiftKeyDown(),
                                        new uk.co.enderfall.sdk.api.blockentity.BlockLocation(
                                                ResourceId.parse(event.getLevel().dimension().location().toString()),
                                                event.getPos().getX(), event.getPos().getY(), event.getPos().getZ()));
                                if (result != InteractionResult.PASS) {
                                    event.setCancellationResult(result);
                                    event.setCanceled(true);
                                }
                            });
                        }
                    
                    """;
                case NEOFORGE -> """
                        private void installEvents() {
                            AtomicLong tick = new AtomicLong();
                            NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
                                server = event.getServer();
                                context.runtimeConfigs().loadServerConfigs();
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STARTING);
                            });
                            NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                                    publishLifecycle(LifecycleEvent.Stage.SERVER_STARTED));
                            NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
                                    publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPING));
                            NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPED);
                                context.runtimeConfigs().unloadServerConfigs();
                                server = null;
                            });
                            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> context.runtimeEvents().publish(
                                    SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.START, tick.get())));
                            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> context.runtimeEvents().publish(
                                    SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.END, tick.getAndIncrement())));
                            NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
                                context.runtimeNetworking().connectionOpened(event.getEntity().getUUID());
                                publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action.JOIN, event);
                            });
                            NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
                                context.runtimeNetworking().connectionClosed(event.getEntity().getUUID());
                                publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action.LEAVE, event);
                            });
                            NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
                                ResourceLocation target = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
                                InteractionResult result = interaction(InteractionEvent.Kind.USE_ITEM,
                                        event.getLevel().isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        event.getEntity().getUUID(), target, target,
                                        event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        event.getEntity().isShiftKeyDown(), null);
                                if (result != InteractionResult.PASS) {
                                    event.setCancellationResult(result);
                                    event.setCanceled(true);
                                }
                            });
                            NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
                                ResourceLocation target = BuiltInRegistries.BLOCK.getKey(
                                        event.getLevel().getBlockState(event.getPos()).getBlock());
                                InteractionResult result = interaction(InteractionEvent.Kind.USE_BLOCK,
                                        event.getLevel().isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        event.getEntity().getUUID(), target,
                                        BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()),
                                        event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        event.getEntity().isShiftKeyDown(),
                                        new uk.co.enderfall.sdk.api.blockentity.BlockLocation(
                                                ResourceId.parse(event.getLevel().dimension().location().toString()),
                                                event.getPos().getX(), event.getPos().getY(), event.getPos().getZ()));
                                if (result != InteractionResult.PASS) {
                                    event.setCancellationResult(result);
                                    event.setCanceled(true);
                                }
                            });
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        private void installEvents() {
                            AtomicLong tick = new AtomicLong();
                            NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
                                server = event.getServer();
                                context.runtimeConfigs().loadServerConfigs();
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STARTING);
                            });
                            NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                                    publishLifecycle(LifecycleEvent.Stage.SERVER_STARTED));
                            NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
                                    publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPING));
                            NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
                                publishLifecycle(LifecycleEvent.Stage.SERVER_STOPPED);
                                context.runtimeConfigs().unloadServerConfigs();
                                server = null;
                            });
                            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> context.runtimeEvents().publish(
                                    SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.START, tick.get())));
                            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> context.runtimeEvents().publish(
                                    SdkEvents.TICK, new TickEvent(TickEvent.Side.SERVER, TickEvent.Phase.END, tick.getAndIncrement())));
                            NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
                                context.runtimeNetworking().connectionOpened(event.getEntity().getUUID());
                                publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action.JOIN, event);
                            });
                            NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
                                context.runtimeNetworking().connectionClosed(event.getEntity().getUUID());
                                publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action.LEAVE, event);
                            });
                            NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
                                Identifier target = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
                                InteractionResult result = interaction(InteractionEvent.Kind.USE_ITEM,
                                        event.getLevel().isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        event.getEntity().getUUID(), target, target,
                                        event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        event.getEntity().isShiftKeyDown(), null);
                                if (result != InteractionResult.PASS) {
                                    event.setCancellationResult(result);
                                    event.setCanceled(true);
                                }
                            });
                            NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
                                Identifier target = BuiltInRegistries.BLOCK.getKey(
                                        event.getLevel().getBlockState(event.getPos()).getBlock());
                                InteractionResult result = interaction(InteractionEvent.Kind.USE_BLOCK,
                                        event.getLevel().isClientSide() ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                        event.getEntity().getUUID(), target,
                                        BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()),
                                        event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND
                                                ? InteractionEvent.Hand.OFF_HAND : InteractionEvent.Hand.MAIN_HAND,
                                        event.getEntity().isShiftKeyDown(),
                                        new uk.co.enderfall.sdk.api.blockentity.BlockLocation(
                                                ResourceId.parse(event.getLevel().dimension().identifier().toString()),
                                                event.getPos().getX(), event.getPos().getY(), event.getPos().getZ()));
                                if (result != InteractionResult.PASS) {
                                    event.setCancellationResult(result);
                                    event.setCanceled(true);
                                }
                            });
                        }
                    
                    """;
            };
            case INTERACTION -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED, FABRIC_KEYED -> """
                        private InteractionResult interaction(InteractionEvent.Kind kind, InteractionEvent.Side side,
                                                              java.util.UUID playerId,
                                                              ResourceLocation target, ResourceLocation heldItem,
                                                              InteractionEvent.Hand hand, boolean sneaking,
                                                              uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
                            InteractionEvent event = new InteractionEvent(kind, side, playerId,
                                    ResourceId.of(target.getNamespace(), target.getPath()), location,
                                    heldItem == null ? null : ResourceId.of(heldItem.getNamespace(), heldItem.getPath()),
                                    hand, sneaking);
                            context.runtimeEvents().publish(SdkEvents.INTERACTION, event);
                            return event.cancelled() ? InteractionResult.FAIL
                                    : event.handled() ? InteractionResult.SUCCESS : InteractionResult.PASS;
                        }
                    
                    """;
                case FABRIC_IDENTIFIER -> """
                        private InteractionResult interaction(InteractionEvent.Kind kind, InteractionEvent.Side side,
                                                              java.util.UUID playerId,
                                                              Identifier target, Identifier heldItem,
                                                              InteractionEvent.Hand hand, boolean sneaking,
                                                              uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
                            InteractionEvent event = new InteractionEvent(kind, side, playerId,
                                    ResourceId.of(target.getNamespace(), target.getPath()), location,
                                    heldItem == null ? null : ResourceId.of(heldItem.getNamespace(), heldItem.getPath()),
                                    hand, sneaking);
                            context.runtimeEvents().publish(SdkEvents.INTERACTION, event);
                            return event.cancelled() ? InteractionResult.FAIL
                                    : event.handled() ? InteractionResult.SUCCESS : InteractionResult.PASS;
                        }
                    
                    """;
                case LEGACY_FML -> """
                        private InteractionResult interaction(InteractionEvent.Kind kind, InteractionEvent.Side side,
                                                              java.util.UUID playerId, ResourceLocation target,
                                                              ResourceLocation heldItem, InteractionEvent.Hand hand,
                                                              boolean sneaking,
                                                              uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
                            if (target == null) {
                                return InteractionResult.PASS;
                            }
                            InteractionEvent event = new InteractionEvent(kind, side, playerId,
                                    ResourceId.of(target.getNamespace(), target.getPath()), location,
                                    heldItem == null ? null : ResourceId.of(heldItem.getNamespace(), heldItem.getPath()),
                                    hand, sneaking);
                            context.runtimeEvents().publish(SdkEvents.INTERACTION, event);
                            return event.cancelled() ? InteractionResult.FAIL
                                    : event.handled() ? InteractionResult.SUCCESS : InteractionResult.PASS;
                        }
                    
                    """;
                case NEOFORGE -> """
                        private InteractionResult interaction(InteractionEvent.Kind kind, InteractionEvent.Side side,
                                                    java.util.UUID playerId,
                                                    ResourceLocation target, ResourceLocation heldItem,
                                                    InteractionEvent.Hand hand, boolean sneaking,
                                                    uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
                            InteractionEvent event = new InteractionEvent(kind, side, playerId,
                                    ResourceId.of(target.getNamespace(), target.getPath()), location,
                                    heldItem == null ? null : ResourceId.of(heldItem.getNamespace(), heldItem.getPath()),
                                    hand, sneaking);
                            context.runtimeEvents().publish(SdkEvents.INTERACTION, event);
                            return event.cancelled() ? InteractionResult.FAIL
                                    : event.handled() ? InteractionResult.SUCCESS : InteractionResult.PASS;
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        private InteractionResult interaction(InteractionEvent.Kind kind, InteractionEvent.Side side,
                                                    java.util.UUID playerId,
                                                    Identifier target, Identifier heldItem,
                                                    InteractionEvent.Hand hand, boolean sneaking,
                                                    uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
                            InteractionEvent event = new InteractionEvent(kind, side, playerId,
                                    ResourceId.of(target.getNamespace(), target.getPath()), location,
                                    heldItem == null ? null : ResourceId.of(heldItem.getNamespace(), heldItem.getPath()),
                                    hand, sneaking);
                            context.runtimeEvents().publish(SdkEvents.INTERACTION, event);
                            return event.cancelled() ? InteractionResult.FAIL
                                    : event.handled() ? InteractionResult.SUCCESS : InteractionResult.PASS;
                        }
                    
                    """;
            };
            case PUBLISH_LIFECYCLE -> """
                        private void publishLifecycle(LifecycleEvent.Stage stage) {
                            context.runtimeEvents().publish(SdkEvents.LIFECYCLE, new LifecycleEvent(stage));
                        }
                    
                    """;
            case PUBLISH_PLAYER -> switch (policy) {
                case LEGACY_FML -> """
                        private void publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action action, PlayerEvent event) {
                            context.runtimeEvents().publish(SdkEvents.PLAYER,
                                    new uk.co.enderfall.sdk.api.event.PlayerEvent(action, event.getEntity().getUUID(),
                                            event.getEntity().getGameProfile().getName()));
                        }
                    
                    """;
                case NEOFORGE -> """
                        private void publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action action,
                                                   PlayerEvent event) {
                            context.runtimeEvents().publish(SdkEvents.PLAYER,
                                    new uk.co.enderfall.sdk.api.event.PlayerEvent(action, event.getEntity().getUUID(),
                                            event.getEntity().getGameProfile().getName()));
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        private void publishPlayer(uk.co.enderfall.sdk.api.event.PlayerEvent.Action action,
                                                   PlayerEvent event) {
                            context.runtimeEvents().publish(SdkEvents.PLAYER,
                                    new uk.co.enderfall.sdk.api.event.PlayerEvent(action, event.getEntity().getUUID(),
                                            event.getEntity().getGameProfile().name()));
                        }
                    
                    """;
                default -> throw new BridgeGenerationException(operation + " is unavailable for " + policy);
            };
            default -> throw new BridgeGenerationException("Operation " + operation + " does not belong to PlatformEventSources");
        };
    }
}
