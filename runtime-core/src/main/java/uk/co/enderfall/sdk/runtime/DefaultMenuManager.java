package uk.co.enderfall.sdk.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.event.PlayerEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.logging.ModLogger;
import uk.co.enderfall.sdk.api.network.PacketDecodingException;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.ui.MenuActionContext;
import uk.co.enderfall.sdk.api.ui.MenuActionHandler;
import uk.co.enderfall.sdk.api.ui.MenuManager;
import uk.co.enderfall.sdk.api.ui.MenuRef;
import uk.co.enderfall.sdk.api.ui.MenuSpec;
import uk.co.enderfall.sdk.api.ui.MenuState;
import uk.co.enderfall.sdk.api.ui.MenuStateSource;
import uk.co.enderfall.sdk.api.event.TickEvent;
import uk.co.enderfall.sdk.runtime.network.ByteArrayPacketReader;
import uk.co.enderfall.sdk.runtime.network.ByteArrayPacketWriter;

/** Runtime-owned synchronized menu registration, session validation, and wire protocol. */
public final class DefaultMenuManager implements MenuManager {
    static final int PACKET_LIMIT = 16_384;
    private static final int PROTOCOL_VERSION = 1;
    private static final int OPEN = 1;
    private static final int UPDATE = 2;
    private static final int CLOSE = 3;
    private static final int ACTION = 4;
    private static final int CLIENT_CLOSED = 5;
    private static final int MAXIMUM_STATE_VALUE_BYTES = 2_048;

    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    private final ModLogger logger;
    private final ResourceId payloadId;
    private final Map<ResourceId, Registration> registrations = new LinkedHashMap<>();
    private final Map<UUID, ServerSession> serverSessions = new ConcurrentHashMap<>();
    private final AtomicLong nextSession = new AtomicLong(1L);
    private volatile long clientSession;

    public DefaultMenuManager(String modId, String target, PlatformAdapter adapter,
                              RegistrationGateAccess gate, ModLogger logger, DefaultEventBus events) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.target = Objects.requireNonNull(target, "target");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.logger = Objects.requireNonNull(logger, "logger");
        payloadId = ResourceId.of(modId, "enderfall_sdk/ui");
        adapter.registerPayload(payloadId, PacketDirection.BIDIRECTIONAL, PACKET_LIMIT, this::receive);
        events.subscribe(SdkEvents.PLAYER, event -> {
            if (event.action() == PlayerEvent.Action.LEAVE) {
                serverSessions.remove(event.playerId());
            }
        });
        events.subscribe(SdkEvents.TICK, event -> {
            if (event.side() == TickEvent.Side.SERVER && event.phase() == TickEvent.Phase.END) {
                refreshLiveSessions();
            }
        });
        events.subscribe(SdkEvents.LIFECYCLE, event -> {
            if (event.stage() == uk.co.enderfall.sdk.api.event.LifecycleEvent.Stage.SERVER_STOPPING) {
                serverSessions.clear();
            }
        });
    }

    @Override
    public synchronized MenuRef register(ResourceId id, MenuSpec spec, MenuActionHandler handler) {
        gate.requireOpen(modId, target);
        if (!Objects.requireNonNull(spec, "spec").gauges().isEmpty() && !adapter.supportsMenuGauges()) {
            throw new UnsupportedOperationException("[" + modId + " on " + target + "] Menu gauges are unavailable");
        }
        Objects.requireNonNull(id, "id");
        if (!id.namespace().equals(modId)) {
            throw new IllegalArgumentException("[" + modId + "] Menu ID must use the consumer namespace: " + id);
        }
        Registration registration = new Registration(new MenuRef(id), Objects.requireNonNull(spec, "spec"),
                Objects.requireNonNull(handler, "handler"));
        if (registrations.putIfAbsent(id, registration) != null) {
            throw new IllegalStateException("[" + modId + "] Duplicate menu " + id + " on " + target);
        }
        return registration.ref();
    }

    @Override
    public MenuRef register(String path, MenuSpec spec, MenuActionHandler handler) {
        return register(ResourceId.of(modId, path), spec, handler);
    }

    @Override
    public void open(UUID playerId, MenuRef menu, MenuState initialState) {
        Objects.requireNonNull(playerId, "playerId");
        Registration registration = requireRegistration(menu);
        Objects.requireNonNull(initialState, "initialState");
        long sessionId = nextPositiveSession();
        byte[] payload = encodeOpen(sessionId, menu, initialState);
        serverSessions.put(playerId, new ServerSession(sessionId, registration, initialState, null));
        adapter.sendToPlayer(playerId, payloadId, payload);
    }

    @Override
    public void bindTank(MenuRef menu, uk.co.enderfall.sdk.api.registry.BlockRef block,
                         uk.co.enderfall.sdk.api.fluid.FluidTankSpec tank) {
        gate.requireOpen(modId, target);
        requireRegistration(menu);
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(tank, "tank");
        if (!block.id().namespace().equals(modId)) {
            throw new IllegalArgumentException("[" + modId + "] Tank block must belong to the consumer");
        }
        adapter.bindTankMenu(block, tank, (player, source) -> openLive(player, menu, 5, source));
    }

    @Override
    public void openLive(UUID playerId, MenuRef menu, int intervalTicks, MenuStateSource source) {
        Objects.requireNonNull(playerId, "playerId");
        Registration registration = requireRegistration(menu);
        Objects.requireNonNull(source, "source");
        if (intervalTicks < 1 || intervalTicks > 1200) {
            throw new IllegalArgumentException("Live menu interval must be 1-1200 ticks");
        }
        Optional<MenuState> initial = Objects.requireNonNull(source.snapshot(), "snapshot");
        if (initial.isEmpty()) return;
        long id = nextPositiveSession();
        byte[] payload = encodeOpen(id, menu, initial.get());
        serverSessions.put(playerId, new ServerSession(id, registration, initial.get(),
                new LiveBinding(source, intervalTicks)));
        adapter.sendToPlayer(playerId, payloadId, payload);
    }

    private void refreshLiveSessions() {
        serverSessions.forEach((player, session) -> {
            LiveBinding binding = session.binding();
            if (binding == null || --binding.remaining > 0) return;
            binding.remaining = binding.interval;
            try {
                Optional<MenuState> snapshot = Objects.requireNonNull(binding.source.snapshot(), "snapshot");
                if (snapshot.isEmpty()) {
                    if (serverSessions.remove(player, session)) {
                        adapter.sendToPlayer(player, payloadId, encodeSession(CLOSE, session.id()));
                    }
                } else if (!snapshot.get().values().equals(session.state().values())) {
                    byte[] payload = encodeState(UPDATE, session.id(), snapshot.get());
                    if (serverSessions.replace(player, session, session.withState(snapshot.get()))) {
                        adapter.sendToPlayer(player, payloadId, payload);
                    }
                }
            } catch (RuntimeException failure) {
                // Do not log snapshot values or exception messages, which may contain private state.
                logger.error("[{} on {}] Live menu {} failed ({})", modId, target,
                        session.registration().ref().id(), failure.getClass().getSimpleName());
                if (serverSessions.remove(player, session)) {
                    try {
                        adapter.sendToPlayer(player, payloadId, encodeSession(CLOSE, session.id()));
                    } catch (RuntimeException transportFailure) {
                        logger.error("[{} on {}] Could not send live menu close", modId, target);
                    }
                }
            }
        });
    }

    @Override
    public void update(UUID playerId, MenuState state) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(state, "state");
        ServerSession session = serverSessions.computeIfPresent(playerId,
                (ignored, current) -> current.withState(state));
        if (session == null) {
            throw new IllegalStateException("[" + modId + "] Player has no active menu on " + target);
        }
        adapter.sendToPlayer(playerId, payloadId, encodeState(UPDATE, session.id(), state));
    }

    @Override
    public void close(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ServerSession session = serverSessions.remove(playerId);
        if (session != null) {
            adapter.sendToPlayer(playerId, payloadId, encodeSession(CLOSE, session.id()));
        }
    }

    @Override
    public boolean isOpen(UUID playerId, MenuRef menu) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(menu, "menu");
        ServerSession session = serverSessions.get(playerId);
        return session != null && session.registration().ref().equals(menu);
    }

    private void receive(byte[] payload, PacketDirection direction, Optional<UUID> playerId,
                         DisconnectHandler disconnect) {
        try {
            ByteArrayPacketReader reader = new ByteArrayPacketReader(payload, PACKET_LIMIT);
            int protocol = reader.readVarInt();
            if (protocol != PROTOCOL_VERSION) {
                throw new PacketDecodingException("Unsupported EnderFall menu protocol " + protocol);
            }
            int kind = reader.readByte() & 0xFF;
            if (direction == PacketDirection.CLIENTBOUND) {
                receiveClientbound(kind, reader);
            } else if (direction == PacketDirection.SERVERBOUND && playerId.isPresent()) {
                receiveServerbound(kind, playerId.get(), reader);
            } else {
                throw new PacketDecodingException("Invalid synchronized menu packet direction");
            }
            if (reader.remainingBytes() != 0) {
                throw new PacketDecodingException("Trailing bytes in synchronized menu packet");
            }
        } catch (IllegalArgumentException exception) {
            disconnect.disconnect("[" + modId + "] Invalid synchronized menu packet: " + exception.getMessage());
        } catch (RuntimeException exception) {
            StackTraceElement location = exception.getStackTrace().length == 0
                    ? null : exception.getStackTrace()[0];
            logger.error("Synchronized menu failure on {} via {} at {}: {}", target,
                    adapter.getClass().getName(), location, exception.getMessage());
        }
    }

    private void receiveClientbound(int kind, ByteArrayPacketReader reader) {
        long sessionId = reader.readLong();
        if (kind == OPEN) {
            MenuRef menu = new MenuRef(reader.readResourceId());
            Registration registration = requireRegistration(menu);
            MenuState state = readState(reader);
            clientSession = sessionId;
            PortableMenuView view = new PortableMenuView(sessionId, menu, registration.spec(), state);
            adapter.showMenu(view, action -> sendAction(sessionId, action), () -> sendClosed(sessionId));
            if ("true".equalsIgnoreCase(System.getenv("ENDERFALL_CONNECTION_SMOKE"))) {
                logger.info("ENDERFALL_MENU_CLIENT_OPEN {} {}", target, menu.id());
            }
        } else if (kind == UPDATE) {
            MenuState state = readState(reader);
            if (clientSession == sessionId) {
                adapter.updateMenu(sessionId, state);
            }
        } else if (kind == CLOSE) {
            if (clientSession == sessionId) {
                clientSession = 0L;
                adapter.closeMenu(sessionId);
            }
        } else {
            throw new PacketDecodingException("Client received invalid menu message " + kind);
        }
    }

    private void receiveServerbound(int kind, UUID playerId, ByteArrayPacketReader reader) {
        long sessionId = reader.readLong();
        ServerSession session = serverSessions.get(playerId);
        if (session == null || session.id() != sessionId) {
            throw new PacketDecodingException("Menu action does not match the active session");
        }
        if (kind == CLIENT_CLOSED) {
            serverSessions.remove(playerId, session);
            return;
        }
        if (kind != ACTION) {
            throw new PacketDecodingException("Server received invalid menu message " + kind);
        }
        String action = reader.readString(64);
        if (!session.registration().spec().supportsAction(action)) {
            throw new PacketDecodingException("Unknown menu action " + action);
        }
        session.registration().handler().handle(new ActionContext(playerId, session, action));
    }

    private void sendAction(long sessionId, String action) {
        ByteArrayPacketWriter writer = header(ACTION);
        writer.writeLong(sessionId);
        writer.writeString(action, 64);
        adapter.sendToServer(payloadId, writer.toByteArray());
    }

    private void sendClosed(long sessionId) {
        if (clientSession == sessionId) {
            clientSession = 0L;
            adapter.sendToServer(payloadId, encodeSession(CLIENT_CLOSED, sessionId));
        }
    }

    private byte[] encodeOpen(long sessionId, MenuRef menu, MenuState state) {
        ByteArrayPacketWriter writer = header(OPEN);
        writer.writeLong(sessionId);
        writer.writeResourceId(menu.id());
        writeState(writer, state);
        return writer.toByteArray();
    }

    private byte[] encodeState(int kind, long sessionId, MenuState state) {
        ByteArrayPacketWriter writer = header(kind);
        writer.writeLong(sessionId);
        writeState(writer, state);
        return writer.toByteArray();
    }

    private byte[] encodeSession(int kind, long sessionId) {
        ByteArrayPacketWriter writer = header(kind);
        writer.writeLong(sessionId);
        return writer.toByteArray();
    }

    private static ByteArrayPacketWriter header(int kind) {
        ByteArrayPacketWriter writer = new ByteArrayPacketWriter(PACKET_LIMIT);
        writer.writeVarInt(PROTOCOL_VERSION);
        writer.writeByte(kind);
        return writer;
    }

    private static void writeState(ByteArrayPacketWriter writer, MenuState state) {
        var entries = new ArrayList<>(state.values().entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        writer.writeVarInt(entries.size());
        for (Map.Entry<String, String> entry : entries) {
            writer.writeString(entry.getKey(), 64);
            writer.writeString(entry.getValue(), MAXIMUM_STATE_VALUE_BYTES);
        }
    }

    private static MenuState readState(ByteArrayPacketReader reader) {
        int size = reader.readVarInt();
        if (size < 0 || size > MenuState.MAXIMUM_ENTRIES) {
            throw new PacketDecodingException("Menu state entry count is invalid: " + size);
        }
        MenuState.Builder state = MenuState.builder();
        for (int index = 0; index < size; index++) {
            state.value(reader.readString(64), reader.readString(MAXIMUM_STATE_VALUE_BYTES));
        }
        return state.build();
    }

    private synchronized Registration requireRegistration(MenuRef menu) {
        Objects.requireNonNull(menu, "menu");
        Registration registration = registrations.get(menu.id());
        if (registration == null) {
            throw new IllegalArgumentException("[" + modId + "] Unknown menu " + menu.id() + " on " + target);
        }
        return registration;
    }

    private long nextPositiveSession() {
        long id = nextSession.getAndUpdate(value -> value == Long.MAX_VALUE ? 1L : value + 1L);
        return Math.max(1L, id);
    }

    private record Registration(MenuRef ref, MenuSpec spec, MenuActionHandler handler) {
    }

    private static final class LiveBinding {
        private final MenuStateSource source;
        private final int interval;
        private int remaining;

        private LiveBinding(MenuStateSource source, int interval) {
            this.source = source;
            this.interval = interval;
            remaining = interval;
        }
    }

    private record ServerSession(long id, Registration registration, MenuState state, LiveBinding binding) {
        ServerSession withState(MenuState replacement) {
            return new ServerSession(id, registration, replacement, binding);
        }
    }

    private final class ActionContext implements MenuActionContext {
        private final UUID playerId;
        private final long sessionId;
        private final Registration registration;
        private final String action;
        private MenuState state;
        private boolean closed;

        private ActionContext(UUID playerId, ServerSession session, String action) {
            this.playerId = playerId;
            sessionId = session.id();
            registration = session.registration();
            state = session.state();
            this.action = action;
        }

        @Override public UUID playerId() { return playerId; }
        @Override public MenuRef menu() { return registration.ref(); }
        @Override public String action() { return action; }
        @Override public MenuState state() { return state; }

        @Override
        public void update(MenuState replacement) {
            if (closed) {
                throw new IllegalStateException("Menu action already closed its session");
            }
            Objects.requireNonNull(replacement, "state");
            ServerSession current = serverSessions.get(playerId);
            if (current == null || current.id() != sessionId) {
                throw new IllegalStateException("Menu session is no longer active");
            }
            state = replacement;
            serverSessions.put(playerId, current.withState(replacement));
            adapter.sendToPlayer(playerId, payloadId, encodeState(UPDATE, sessionId, replacement));
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                ServerSession current = serverSessions.get(playerId);
                if (current != null && current.id() == sessionId) {
                    serverSessions.remove(playerId, current);
                    adapter.sendToPlayer(playerId, payloadId, encodeSession(CLOSE, sessionId));
                }
            }
        }
    }
}
