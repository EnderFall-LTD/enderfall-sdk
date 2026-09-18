package uk.co.enderfall.sdk.bridge;

/**
 * Shared gameplay operations with explicit native ABI facets.
 * These are authored compiler templates, not contextual edits of canonical Java.
 * Layout intentionally retains reference source and bytecode parity.
 */
final class PlatformGameplaySources {
    private PlatformGameplaySources() { }

    static String emit(PlatformOperation operation, NativePlatformPolicy policy) throws BridgeGenerationException {
        return switch (operation) {
            case CONNECTED_PLAYERS -> """
                        @Override
                        public java.util.Collection<java.util.UUID> connectedPlayers() {
                            MinecraftServer current = server;
                            return current == null ? java.util.List.of() : current.getPlayerList().getPlayers().stream()
                                    .map(ServerPlayer::getUUID).toList();
                        }
                    
                    """;
            case PLAYER_SNAPSHOT -> """
                        @Override
                        public Optional<PlayerSnapshot> playerSnapshot(java.util.UUID playerId) {
                            ServerPlayer player = onlinePlayer(playerId);
                            return player == null ? Optional.empty() : Optional.of(new PlayerSnapshot(
                                    playerId, player.getHealth(), player.getMaxHealth()));
                        }
                    
                    """;
            case COUNT_PLAYER_ITEM -> """
                        @Override
                        public int countPlayerItem(java.util.UUID playerId, ResourceId itemId) {
                            return requireOnlinePlayer(playerId).getInventory().countItem(requireItem(itemId));
                        }
                    
                    """;
            case CONSUME_PLAYER_ITEMS -> """
                        @Override
                        public boolean consumePlayerItems(java.util.UUID playerId, Map<ResourceId, Integer> requirements) {
                            ServerPlayer player = requireOnlinePlayer(playerId);
                            for (Map.Entry<ResourceId, Integer> requirement : requirements.entrySet()) {
                                if (player.getInventory().countItem(requireItem(requirement.getKey())) < requirement.getValue()) {
                                    return false;
                                }
                            }
                            for (Map.Entry<ResourceId, Integer> requirement : requirements.entrySet()) {
                                Item item = requireItem(requirement.getKey());
                                int remaining = requirement.getValue();
                                for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                                    ItemStack stack = player.getInventory().getItem(slot);
                                    if (stack.is(item)) {
                                        int removed = Math.min(remaining, stack.getCount());
                                        stack.shrink(removed);
                                        remaining -= removed;
                                    }
                                }
                            }
                            player.getInventory().setChanged();
                            player.containerMenu.broadcastChanges();
                            return true;
                        }
                    
                    """;
            case GIVE_PLAYER_ITEM -> """
                        @Override
                        public void givePlayerItem(java.util.UUID playerId, ResourceId itemId, int amount) {
                            ServerPlayer player = requireOnlinePlayer(playerId);
                            ItemStack stack = new ItemStack(requireItem(itemId), amount);
                            player.getInventory().add(stack);
                            if (!stack.isEmpty()) {
                                player.drop(stack, false);
                            }
                            player.containerMenu.broadcastChanges();
                        }
                    """;
            case PLAYER_ITEM_DATA -> """
                        @Override
                        public <T> Optional<T> playerItemData(java.util.UUID playerId,
                                uk.co.enderfall.sdk.api.gameplay.PlayerInventorySlot slot,
                                ResourceId expectedItemId, uk.co.enderfall.sdk.api.item.ItemDataKey<T> key) {
                            MinecraftServer current = requireServer();
                            if (!current.isSameThread()) {
                                throw new IllegalStateException("Player item data must be read on the server thread");
                            }
                            ServerPlayer player = onlinePlayer(playerId);
                            if (player == null) return Optional.empty();
                            ItemStack stack = playerItemStack(player, slot);
                            if (!stack.is(requireItem(expectedItemId))) return Optional.empty();
                            uk.co.enderfall.sdk.api.item.MutableItemData data =
                                    uk.co.enderfall.sdk.runtime.item.nativebridge.PortableSdkItem.data(stack, false);
                            return data == null ? Optional.empty() : data.get(key);
                        }

                    """;
            case UPDATE_PLAYER_ITEM_DATA -> """
                        @Override
                        public boolean updatePlayerItemData(java.util.UUID playerId,
                                uk.co.enderfall.sdk.api.gameplay.PlayerInventorySlot slot,
                                ResourceId expectedItemId,
                                java.util.function.Consumer<uk.co.enderfall.sdk.api.item.MutableItemData> update) {
                            MinecraftServer current = requireServer();
                            if (!current.isSameThread()) {
                                throw new IllegalStateException("Player item data must be changed on the server thread");
                            }
                            ServerPlayer player = onlinePlayer(playerId);
                            if (player == null) return false;
                            ItemStack stack = playerItemStack(player, slot);
                            if (!stack.is(requireItem(expectedItemId))) return false;
                            uk.co.enderfall.sdk.api.item.MutableItemData data =
                                    uk.co.enderfall.sdk.runtime.item.nativebridge.PortableSdkItem.data(stack, true);
                            if (data == null) return false;
                            update.accept(data);
                            player.getInventory().setChanged();
                            player.containerMenu.broadcastChanges();
                            return true;
                        }

                    """;
            case SEND_PLAYER_MESSAGE -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED, FABRIC_KEYED, LEGACY_FML, NEOFORGE -> """
                        @Override
                        public void sendPlayerMessage(java.util.UUID playerId, String message, boolean actionBar) {
                            requireOnlinePlayer(playerId).displayClientMessage(Component.literal(message), actionBar);
                        }
                    
                    """;
                case FABRIC_IDENTIFIER, NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void sendPlayerMessage(java.util.UUID playerId, String message, boolean actionBar) {
                            ServerPlayer player = requireOnlinePlayer(playerId);
                            if (actionBar) {
                                player.sendOverlayMessage(Component.literal(message));
                            } else {
                                player.sendSystemMessage(Component.literal(message));
                            }
                        }
                    
                    """;
            };
            case HEAL_PLAYER -> """
                        @Override
                        public void healPlayer(java.util.UUID playerId, double amount) {
                            requireOnlinePlayer(playerId).heal((float) amount);
                        }
                    
                    """;
            case ADD_PLAYER_EXPERIENCE -> """
                        @Override
                        public void addPlayerExperience(java.util.UUID playerId, int points) {
                            requireOnlinePlayer(playerId).giveExperiencePoints(points);
                        }
                    
                    """;
            case SHOW_MENU -> """
                        @Override
                        public void showMenuWithInputs(uk.co.enderfall.sdk.runtime.PortableMenuView view,
                                java.util.function.Consumer<uk.co.enderfall.sdk.runtime.PortableMenuSubmission> actionSender,
                                Runnable closeSender) {
                            ${ClientHooks}.showMenu(view, actionSender, closeSender);
                        }
                    
                    """;
            case UPDATE_MENU -> """
                        @Override
                        public void updateMenu(long sessionId, uk.co.enderfall.sdk.api.ui.MenuState state) {
                            ${ClientHooks}.updateMenu(sessionId, state);
                        }
                    
                    """;
            case CLOSE_MENU -> """
                        @Override
                        public void closeMenu(long sessionId) {
                            ${ClientHooks}.closeMenu(sessionId);
                        }
                    
                    """;
            case REQUIRE_ITEM -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        private Item requireItem(ResourceId id) {
                            Item item = items.get(id);
                            if (item == null) {
                                throw new IllegalStateException("[" + modId + "] Creative tab references unknown item " + id);
                            }
                            return item;
                        }
                    
                    """;
                case LEGACY_FML, NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        private Item requireItem(ResourceId id) {
                            Supplier<? extends Item> item = items.get(id);
                            if (item == null) {
                                throw new IllegalStateException("[" + modId + "] Creative tab references unknown item " + id);
                            }
                            return item.get();
                        }
                    
                    """;
            };
            case REQUIRE_SERVER -> """
                        private MinecraftServer requireServer() {
                            MinecraftServer current = server;
                            if (current == null) {
                                throw new IllegalStateException("No Minecraft server is running");
                            }
                            return current;
                        }
                    
                    """;
            case ONLINE_PLAYER -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        private ServerPlayer onlinePlayer(java.util.UUID playerId) {
                            ServerPlayer joining = joiningPlayers.get(playerId);
                            MinecraftServer current = server;
                            return joining != null ? joining
                                    : current == null ? null : current.getPlayerList().getPlayer(playerId);
                        }
                    
                    """;
                case LEGACY_FML, NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        private ServerPlayer onlinePlayer(java.util.UUID playerId) {
                            MinecraftServer current = server;
                            return current == null ? null : current.getPlayerList().getPlayer(playerId);
                        }
                    
                    """;
            };
            case REQUIRE_ONLINE_PLAYER -> """
                        private ServerPlayer requireOnlinePlayer(java.util.UUID playerId) {
                            ServerPlayer player = onlinePlayer(playerId);
                            if (player == null) {
                                throw new IllegalArgumentException("Unknown player " + playerId);
                            }
                            return player;
                        }
                    
                    """;
            case PLAYER_ITEM_STACK -> """
                        private ItemStack playerItemStack(ServerPlayer player,
                                uk.co.enderfall.sdk.api.gameplay.PlayerInventorySlot slot) {
                            java.util.Objects.requireNonNull(slot, "slot");
                            if (slot.area() == uk.co.enderfall.sdk.api.gameplay.PlayerInventorySlot.Area.OFF_HAND) {
                                return player.getOffhandItem();
                            }
                            int nativeIndex = slot.area()
                                    == uk.co.enderfall.sdk.api.gameplay.PlayerInventorySlot.Area.HOTBAR
                                    ? slot.index() : slot.index() + 9;
                            return player.getInventory().getItem(nativeIndex);
                        }

                    """;
            case LOCATION -> switch (policy) {
                case FABRIC_LEGACY -> """
                        private static ResourceLocation location(ResourceId id) {
                            return new ResourceLocation(id.namespace(), id.path());
                        }
                    
                    """;
                case FABRIC_UNKEYED, FABRIC_KEYED, NEOFORGE -> """
                        private static ResourceLocation location(ResourceId id) {
                            return ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
                        }
                    
                    """;
                case LEGACY_FML -> """
                        private static ResourceLocation location(ResourceId id) {
                            ResourceLocation location = ResourceLocation.tryBuild(id.namespace(), id.path());
                            if (location == null) {
                                throw new IllegalArgumentException("Invalid resource ID " + id);
                            }
                            return location;
                        }
                    
                    """;
                default -> throw new BridgeGenerationException(operation + " is unavailable for " + policy);
            };
            case IDENTIFIER -> switch (policy) {
                case FABRIC_IDENTIFIER, NEOFORGE_IDENTIFIER -> """
                        private static Identifier identifier(ResourceId id) {
                            return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
                        }
                    
                    """;
                default -> throw new BridgeGenerationException(operation + " is unavailable for " + policy);
            };
            default -> throw new BridgeGenerationException("Operation " + operation + " does not belong to PlatformGameplaySources");
        };
    }
}
