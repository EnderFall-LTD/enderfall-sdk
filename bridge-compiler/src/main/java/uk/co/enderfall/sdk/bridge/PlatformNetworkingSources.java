package uk.co.enderfall.sdk.bridge;

/**
 * Shared networking operations with explicit native ABI facets.
 * These are authored compiler templates, not contextual edits of canonical Java.
 * Layout intentionally retains reference source and bytecode parity.
 */
final class PlatformNetworkingSources {
    private PlatformNetworkingSources() { }

    static String emit(PlatformOperation operation, NativePlatformPolicy policy) throws BridgeGenerationException {
        return switch (operation) {
            case REGISTER_PAYLOAD -> switch (policy) {
                case FABRIC_LEGACY -> """
                        @Override
                        public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                                    PayloadReceiver receiver) {
                            ResourceLocation channel = location(id);
                            if (direction == PacketDirection.SERVERBOUND || direction == PacketDirection.BIDIRECTIONAL) {
                                if (!ServerPlayNetworking.registerGlobalReceiver(channel, (server, player, handler, buffer, sender) -> {
                                    byte[] bytes = readPayload(id, maximumBytes, buffer);
                                    server.execute(() -> receiver.receive(bytes, PacketDirection.SERVERBOUND,
                                            Optional.of(player.getUUID()),
                                            reason -> player.connection.disconnect(Component.literal(reason))));
                                })) {
                                    throw new IllegalStateException("Duplicate serverbound payload " + id);
                                }
                            }
                            if ((direction == PacketDirection.CLIENTBOUND || direction == PacketDirection.BIDIRECTIONAL)
                                    && platformInfo.environment() == Environment.CLIENT) {
                                ${ClientHooks}.registerReceiver(channel, maximumBytes, receiver);
                            }
                            payloads.put(id, new PayloadBinding(channel, maximumBytes));
                        }
                    
                    """;
                case FABRIC_UNKEYED -> """
                        @Override
                        public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                                    PayloadReceiver receiver) {
                            CustomPacketPayload.Type<${RawPayload}> type = new CustomPacketPayload.Type<>(location(id));
                            var codec = ${RawPayload}.codec(type, maximumBytes);
                            if (direction == PacketDirection.SERVERBOUND || direction == PacketDirection.BIDIRECTIONAL) {
                                PayloadTypeRegistry.playC2S().register(type, codec);
                                if (!ServerPlayNetworking.registerGlobalReceiver(type, (payload, networkContext) -> receiver.receive(
                                        payload.bytes(), PacketDirection.SERVERBOUND,
                                        Optional.of(networkContext.player().getUUID()),
                                        reason -> networkContext.player().connection.disconnect(Component.literal(reason))))) {
                                    throw new IllegalStateException("Duplicate serverbound payload " + id);
                                }
                            }
                            if (direction == PacketDirection.CLIENTBOUND || direction == PacketDirection.BIDIRECTIONAL) {
                                PayloadTypeRegistry.playS2C().register(type, codec);
                                if (platformInfo.environment() == Environment.CLIENT) {
                                    ${ClientHooks}.registerReceiver(type, receiver);
                                }
                            }
                            payloads.put(id, new PayloadBinding(type, maximumBytes));
                        }
                    
                    """;
                case FABRIC_KEYED -> """
                        @Override
                        public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes, PayloadReceiver receiver) {
                            CustomPacketPayload.Type<${RawPayload}> type = new CustomPacketPayload.Type<>(location(id));
                            var codec = ${RawPayload}.codec(type, maximumBytes);
                            if (direction == PacketDirection.SERVERBOUND || direction == PacketDirection.BIDIRECTIONAL) {
                                PayloadTypeRegistry.playC2S().register(type, codec);
                                if (!ServerPlayNetworking.registerGlobalReceiver(type, (payload, networkContext) -> receiver.receive(
                                        payload.bytes(), PacketDirection.SERVERBOUND,
                                        Optional.of(networkContext.player().getUUID()),
                                        reason -> networkContext.player().connection.disconnect(Component.literal(reason))))) {
                                    throw new IllegalStateException("Duplicate serverbound payload " + id);
                                }
                            }
                            if (direction == PacketDirection.CLIENTBOUND || direction == PacketDirection.BIDIRECTIONAL) {
                                PayloadTypeRegistry.playS2C().register(type, codec);
                                if (platformInfo.environment() == Environment.CLIENT) {
                                    ${ClientHooks}.registerReceiver(type, receiver);
                                }
                            }
                            payloads.put(id, new PayloadBinding(type, maximumBytes));
                        }
                    
                    """;
                case FABRIC_IDENTIFIER -> """
                        @Override
                        public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                                    PayloadReceiver receiver) {
                            CustomPacketPayload.Type<${RawPayload}> type = new CustomPacketPayload.Type<>(identifier(id));
                            var codec = ${RawPayload}.codec(type, maximumBytes);
                            if (direction == PacketDirection.SERVERBOUND || direction == PacketDirection.BIDIRECTIONAL) {
                                PayloadTypeRegistry.serverboundPlay().register(type, codec);
                                if (!ServerPlayNetworking.registerGlobalReceiver(type, (payload, networkContext) -> receiver.receive(
                                        payload.bytes(), PacketDirection.SERVERBOUND,
                                        Optional.of(networkContext.player().getUUID()),
                                        reason -> networkContext.player().connection.disconnect(Component.literal(reason))))) {
                                    throw new IllegalStateException("Duplicate serverbound payload " + id);
                                }
                            }
                            if (direction == PacketDirection.CLIENTBOUND || direction == PacketDirection.BIDIRECTIONAL) {
                                PayloadTypeRegistry.clientboundPlay().register(type, codec);
                                if (platformInfo.environment() == Environment.CLIENT) {
                                    ${ClientHooks}.registerReceiver(type, receiver);
                                }
                            }
                            payloads.put(id, new PayloadBinding(type, maximumBytes));
                        }
                    
                    """;
                case LEGACY_FML -> """
                        @Override
                        public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                                    PayloadReceiver receiver) {
                            ResourceLocation location = location(id);
                            Predicate<String> accepted = NetworkRegistry.acceptMissingOr(CHANNEL_VERSION);
                            SimpleChannel channel = NetworkRegistry.ChannelBuilder.named(location)
                                    .networkProtocolVersion(() -> CHANNEL_VERSION)
                                    .clientAcceptedVersions(accepted)
                                    .serverAcceptedVersions(accepted)
                                    .simpleChannel();
                            PayloadBinding binding = new PayloadBinding(location, direction, maximumBytes, receiver, channel);
                            if (payloads.putIfAbsent(id, binding) != null) {
                                throw new IllegalStateException("[" + modId + "] Duplicate payload " + id);
                            }
                            channel.registerMessage(0, byte[].class,
                                    (payload, buffer) -> buffer.writeByteArray(payload),
                                    buffer -> buffer.readByteArray(maximumBytes),
                                    (payload, source) -> receive(binding, payload, source));
                        }
                    
                    """;
                case NEOFORGE -> """
                        @Override
                        public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                                    PayloadReceiver receiver) {
                            CustomPacketPayload.Type<${RawPayload}> type = new CustomPacketPayload.Type<>(location(id));
                            if (payloads.putIfAbsent(id, new PayloadBinding(type, direction, maximumBytes, receiver)) != null) {
                                throw new IllegalStateException("[" + modId + "] Duplicate payload " + id);
                            }
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                                    PayloadReceiver receiver) {
                            CustomPacketPayload.Type<${RawPayload}> type = new CustomPacketPayload.Type<>(identifier(id));
                            if (payloads.putIfAbsent(id, new PayloadBinding(type, direction, maximumBytes, receiver)) != null) {
                                throw new IllegalStateException("[" + modId + "] Duplicate payload " + id);
                            }
                        }
                    
                    """;
            };
            case SEND_TO_SERVER -> switch (policy) {
                case FABRIC_LEGACY -> """
                        @Override
                        public void sendToServer(ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
                                throw new IllegalStateException("Cannot send to a server from a dedicated server process");
                            }
                            ${ClientHooks}.sendToServer(binding.channel(), payload);
                        }
                    
                    """;
                case FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        @Override
                        public void sendToServer(ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
                                throw new IllegalStateException("Cannot send to a server from a dedicated server process");
                            }
                            ${ClientHooks}.sendToServer(new ${RawPayload}(binding.type(), payload));
                        }
                    
                    """;
                case LEGACY_FML -> """
                        @Override
                        public void sendToServer(ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            if (FMLEnvironment.dist != Dist.CLIENT) {
                                throw new IllegalStateException("Cannot send to a server from a dedicated server process");
                            }
                            ${ClientHooks}.sendToServer(binding.channel(), payload);
                        }
                    
                    """;
                case NEOFORGE -> """
                        @Override
                        public void sendToServer(ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            if (FMLEnvironment.dist != Dist.CLIENT) {
                                throw new IllegalStateException("Cannot send to a server from a dedicated server process");
                            }
                            ${ClientHooks}.sendToServer(new ${RawPayload}(binding.type(), payload));
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void sendToServer(ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            if (FMLEnvironment.getDist() != Dist.CLIENT) {
                                throw new IllegalStateException("Cannot send to a server from a dedicated server process");
                            }
                            ${ClientHooks}.sendToServer(new ${RawPayload}(binding.type(), payload));
                        }
                    
                    """;
            };
            case SEND_TO_PLAYER -> switch (policy) {
                case FABRIC_LEGACY -> """
                        @Override
                        public void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            ServerPlayer player = joiningPlayers.get(playerId);
                            if (player == null) {
                                player = requireServer().getPlayerList().getPlayer(playerId);
                            }
                            if (player == null) {
                                throw new IllegalArgumentException("Unknown player " + playerId);
                            }
                            FriendlyByteBuf buffer = PacketByteBufs.create();
                            buffer.writeBytes(payload);
                            ServerPlayNetworking.send(player, binding.channel(), buffer);
                        }
                    
                    """;
                case FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        @Override
                        public void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            ServerPlayer player = joiningPlayers.get(playerId);
                            if (player == null) {
                                player = requireServer().getPlayerList().getPlayer(playerId);
                            }
                            if (player == null) {
                                throw new IllegalArgumentException("Unknown player " + playerId);
                            }
                            ServerPlayNetworking.send(player, new ${RawPayload}(binding.type(), payload));
                        }
                    
                    """;
                case LEGACY_FML -> """
                        @Override
                        public void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            ServerPlayer player = requireServer().getPlayerList().getPlayer(playerId);
                            if (player == null) {
                                throw new IllegalArgumentException("Unknown player " + playerId);
                            }
                            player.connection.send(binding.channel().toVanillaPacket(payload, NetworkDirection.PLAY_TO_CLIENT));
                        }
                    
                    """;
                case NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            ServerPlayer player = requireServer().getPlayerList().getPlayer(playerId);
                            if (player == null) {
                                throw new IllegalArgumentException("Unknown player " + playerId);
                            }
                            PacketDistributor.sendToPlayer(player, new ${RawPayload}(binding.type(), payload));
                        }
                    
                    """;
            };
            case SEND_TO_ALL -> switch (policy) {
                case FABRIC_LEGACY -> """
                        @Override
                        public void sendToAll(ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            for (ServerPlayer player : requireServer().getPlayerList().getPlayers()) {
                                FriendlyByteBuf buffer = PacketByteBufs.create();
                                buffer.writeBytes(payload);
                                ServerPlayNetworking.send(player, binding.channel(), buffer);
                            }
                        }
                    
                    """;
                case FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        @Override
                        public void sendToAll(ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            for (ServerPlayer player : requireServer().getPlayerList().getPlayers()) {
                                ServerPlayNetworking.send(player, new ${RawPayload}(binding.type(), payload));
                            }
                        }
                    
                    """;
                case LEGACY_FML -> """
                        @Override
                        public void sendToAll(ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            for (ServerPlayer player : requireServer().getPlayerList().getPlayers()) {
                                player.connection.send(binding.channel().toVanillaPacket(payload, NetworkDirection.PLAY_TO_CLIENT));
                            }
                        }
                    
                    """;
                case NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void sendToAll(ResourceId id, byte[] payload) {
                            PayloadBinding binding = requirePayload(id, payload.length);
                            requireServer();
                            PacketDistributor.sendToAllPlayers(new ${RawPayload}(binding.type(), payload));
                        }
                    
                    """;
            };
            case REGISTER_PAYLOAD_HANDLERS -> switch (policy) {
                case NEOFORGE -> """
                        private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
                            PayloadRegistrar registrar = event.registrar("0.1").optional();
                            for (PayloadBinding binding : payloads.values()) {
                                var codec = ${RawPayload}.codec(binding.type(), binding.maximumBytes());
                                switch (binding.direction()) {
                                    case SERVERBOUND -> registrar.playToServer(binding.type(), codec,
                                            (payload, networkContext) -> receive(binding, payload, networkContext));
                                    case CLIENTBOUND -> registrar.playToClient(binding.type(), codec,
                                            (payload, networkContext) -> receive(binding, payload, networkContext));
                                    case BIDIRECTIONAL -> registrar.playBidirectional(binding.type(), codec,
                                            (payload, networkContext) -> receive(binding, payload, networkContext));
                                }
                            }
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
                            PayloadRegistrar registrar = event.registrar("0.1").optional();
                            for (PayloadBinding binding : payloads.values()) {
                                var codec = ${RawPayload}.codec(binding.type(), binding.maximumBytes());
                                switch (binding.direction()) {
                                    case SERVERBOUND -> registrar.playToServer(binding.type(), codec,
                                            (payload, networkContext) -> receive(binding, payload, networkContext));
                                    case CLIENTBOUND -> registrar.playToClient(binding.type(), codec,
                                            (payload, networkContext) -> receive(binding, payload, networkContext));
                                    case BIDIRECTIONAL -> registrar.playBidirectional(binding.type(), codec,
                                            (payload, networkContext) -> receive(binding, payload, networkContext),
                                            (payload, networkContext) -> receive(binding, payload, networkContext));
                                }
                            }
                        }
                    
                    """;
                default -> throw new BridgeGenerationException(operation + " is unavailable for " + policy);
            };
            case RECEIVE -> switch (policy) {
                case LEGACY_FML -> """
                        private void receive(PayloadBinding binding, byte[] payload,
                                             Supplier<NetworkEvent.Context> source) {
                            int length = payload.length;
                            NetworkEvent.Context networkContext = source.get();
                            if (length > binding.maximumBytes()) {
                                networkContext.getNetworkManager().disconnect(Component.literal(
                                        "Payload " + binding.location() + " exceeds " + binding.maximumBytes() + " bytes"));
                                networkContext.setPacketHandled(true);
                                return;
                            }
                            Optional<java.util.UUID> playerId = Optional.ofNullable(networkContext.getSender())
                                    .map(ServerPlayer::getUUID);
                            PacketDirection direction = playerId.isPresent()
                                    ? PacketDirection.SERVERBOUND : PacketDirection.CLIENTBOUND;
                            networkContext.enqueueWork(() -> binding.receiver().receive(payload, direction, playerId,
                                    reason -> networkContext.getNetworkManager().disconnect(Component.literal(reason))));
                            networkContext.setPacketHandled(true);
                        }
                    
                    """;
                case NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        private static void receive(PayloadBinding binding, ${RawPayload} payload,
                                                    IPayloadContext networkContext) {
                            PacketDirection direction = networkContext.flow() == PacketFlow.SERVERBOUND
                                    ? PacketDirection.SERVERBOUND : PacketDirection.CLIENTBOUND;
                            Optional<java.util.UUID> playerId = networkContext.player() instanceof ServerPlayer player
                                    ? Optional.of(player.getUUID()) : Optional.empty();
                            binding.receiver().receive(payload.bytes(), direction, playerId,
                                    reason -> networkContext.disconnect(Component.literal(reason)));
                        }
                    
                    """;
                default -> throw new BridgeGenerationException(operation + " is unavailable for " + policy);
            };
            case READ_PAYLOAD -> switch (policy) {
                case FABRIC_LEGACY -> """
                        private static byte[] readPayload(ResourceId id, int maximumBytes, FriendlyByteBuf buffer) {
                            int length = buffer.readableBytes();
                            if (length > maximumBytes) {
                                throw new IllegalArgumentException("Payload " + id + " exceeds " + maximumBytes + " bytes");
                            }
                            byte[] bytes = new byte[length];
                            buffer.readBytes(bytes);
                            return bytes;
                        }
                    
                    """;
                default -> throw new BridgeGenerationException(operation + " is unavailable for " + policy);
            };
            case REQUIRE_PAYLOAD -> """
                        private PayloadBinding requirePayload(ResourceId id, int length) {
                            PayloadBinding binding = payloads.get(id);
                            if (binding == null) {
                                throw new IllegalStateException("[" + modId + "] Unknown payload " + id);
                            }
                            if (length > binding.maximumBytes()) {
                                throw new IllegalArgumentException("Payload " + id + " exceeds " + binding.maximumBytes() + " bytes");
                            }
                            return binding;
                        }
                    
                    """;
            case PAYLOAD_BINDING -> switch (policy) {
                case FABRIC_LEGACY -> """
                        private record PayloadBinding(ResourceLocation channel, int maximumBytes) {
                        }
                    """;
                case FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        private record PayloadBinding(CustomPacketPayload.Type<${RawPayload}> type, int maximumBytes) {
                        }
                    """;
                case LEGACY_FML -> """
                        private record PayloadBinding(ResourceLocation location, PacketDirection direction,
                                                      int maximumBytes, PayloadReceiver receiver,
                                                      SimpleChannel channel) {
                        }
                    """;
                case NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        private record PayloadBinding(CustomPacketPayload.Type<${RawPayload}> type,
                                                      PacketDirection direction, int maximumBytes,
                                                      PayloadReceiver receiver) {
                        }
                    """;
            };
            default -> throw new BridgeGenerationException("Operation " + operation + " does not belong to PlatformNetworkingSources");
        };
    }
}
