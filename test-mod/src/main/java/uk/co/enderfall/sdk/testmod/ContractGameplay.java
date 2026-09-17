package uk.co.enderfall.sdk.testmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.gameplay.InventoryCost;
import uk.co.enderfall.sdk.api.network.NetworkContext;
import uk.co.enderfall.sdk.api.network.PacketCodec;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.network.PacketReader;
import uk.co.enderfall.sdk.api.network.PacketRequirement;
import uk.co.enderfall.sdk.api.network.PacketType;
import uk.co.enderfall.sdk.api.network.PacketWriter;
import uk.co.enderfall.sdk.api.registry.ItemRef;
import uk.co.enderfall.sdk.api.ui.MenuActionContext;
import uk.co.enderfall.sdk.api.ui.MenuRef;
import uk.co.enderfall.sdk.api.ui.WorkbenchCraftContext;
import uk.co.enderfall.sdk.api.ui.WorkbenchRef;

/** Portable server authority for the opt-in real-slot gameplay test. Never imports native types. */
public final class ContractGameplay {
    public static final String COMPLETE_PROPERTY = "uk.co.enderfall.sdk.test.clientSmokeComplete";
    public static final PacketType<String> PACKET = new PacketType<>(
            ResourceId.of("enderfall_sdk_test", "gameplay"), 1, PacketDirection.BIDIRECTIONAL,
            PacketRequirement.REQUIRED, 256, new PacketCodec<>() {
                @Override public void encode(PacketWriter writer, String value) { writer.writeString(value, 128); }
                @Override public String decode(PacketReader reader) { return reader.readString(128); }
            });
    public static volatile boolean connectionReady;
    public static volatile String clientAcknowledgement = "";
    private final ModContext context;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final ItemRef blocks = new ItemRef(ResourceId.of("enderfall_sdk_test", "test_block"));
    private final ItemRef catalysts = new ItemRef(ResourceId.of("enderfall_sdk_test", "catalyst"));
    private final ItemRef hammers = new ItemRef(ResourceId.of("enderfall_sdk_test", "hammer"));
    private MenuRef menu;
    private WorkbenchRef workbench;

    public ContractGameplay(ModContext context) {
        this.context = context;
        context.networking().register(PACKET, this::receive);
    }

    public static boolean enabled() {
        return "true".equalsIgnoreCase(System.getenv("ENDERFALL_GAMEPLAY_SMOKE"));
    }

    public void bind(MenuRef menu, WorkbenchRef workbench) {
        this.menu = menu;
        this.workbench = workbench;
    }

    public void left(UUID player) { sessions.remove(player); }

    public void menuAction(MenuActionContext action) {
        if (!enabled()) return;
        Session session = sessions.get(action.playerId());
        require(session != null, "Menu action before gameplay session");
        require(session.stage == Stage.MENU && session.actions == 0, "Unexpected or repeated menu action");
        require(action.action().equals("confirm") && action.state().values().get("state").equals("synchronized"),
                "Menu action arrived with the wrong server-owned state");
        session.actions++;
        marker("MENU_ACTION", action.playerId());
    }

    public void crafted(WorkbenchCraftContext craft) {
        if (!enabled()) return;
        Session session = sessions.get(craft.playerId());
        require(session != null, "Craft before gameplay session");
        // Count even invalid callbacks: listener isolation must not hide a full-inventory craft.
        session.crafts++;
        require(session.stage == Stage.FIRST || session.stage == Stage.SECOND,
                "Craft outside its expected phase");
        require(session.insufficientChecked, "Craft before insufficient-input check");
        require(craft.workbench().equals(workbench) && craft.recipeId().equals(context.id("contract_assembly"))
                && craft.result().equals(hammers) && craft.resultCount() == 1, "Wrong custom recipe/craft result");
        require(session.crafts == (session.stage == Stage.FIRST ? 1 : 2), "Unexpected duplicate craft callback");
    }

    private void receive(String message, NetworkContext network) {
        if (!enabled()) return;
        if (network.receivedDirection() == PacketDirection.CLIENTBOUND) {
            clientAcknowledgement = message;
            if (message.equals("complete")) {
                context.logger().info("ENDERFALL_GAMEPLAY_CLIENT_COMPLETE {}", context.platform().targetId());
                System.setProperty(COMPLETE_PROPERTY, "true");
            }
            return;
        }
        UUID player = network.playerId().orElseThrow();
        try {
            if (message.equals("open-menu")) {
                require(!sessions.containsKey(player), "Repeated gameplay session");
                sessions.put(player, new Session());
                context.menus().open(player, menu, uk.co.enderfall.sdk.api.ui.MenuState.builder()
                        .value("target", context.platform().targetId()).value("state", "synchronized").build());
                return;
            }
            Session session = sessions.get(player);
            require(session != null, "Gameplay request before menu action");
            switch (message) {
                case "menu-confirmed" -> {
                    require(session.stage == Stage.MENU && session.actions == 1, "Menu not confirmed");
                    require(context.menus().isOpen(player, menu), "Server lost the menu session");
                    context.menus().close(player);
                    // Reset only fixture items, in the harness's disposable world.
                    for (ItemRef item : new ItemRef[] {blocks, catalysts, hammers}) {
                        int count = context.players().count(player, item);
                        if (count > 0) require(context.players().tryConsume(player, InventoryCost.of(item, count)),
                                "Cannot reset fixture inventory");
                    }
                    context.players().give(player, blocks, 4);
                    context.players().give(player, catalysts, 2);
                    session.stage = Stage.FIRST;
                    context.workbenches().open(player, workbench);
                    acknowledge(player, "workbench-open");
                }
                case "insufficient" -> {
                    require(session.stage == Stage.FIRST && session.crafts == 0 && !session.insufficientChecked,
                            "Insufficient-input phase out of order");
                    inventory(player, 3, 0, 0);
                    session.insufficientChecked = true;
                    acknowledge(player, "insufficient");
                }
                case "crafted-once" -> {
                    require(session.stage == Stage.FIRST && session.crafts == 1, "Normal craft callback missing");
                    inventory(player, 2, 0, 1);
                    session.stage = Stage.SECOND;
                    marker("NORMAL_CRAFT", player);
                    acknowledge(player, "crafted-once");
                }
                case "crafted-twice" -> {
                    require(session.stage == Stage.SECOND && session.crafts == 2, "Shift craft callback missing");
                    inventory(player, 0, 0, 2);
                    session.stage = Stage.RETURN;
                    context.players().give(player, blocks, 1);
                    context.players().give(player, catalysts, 1);
                    marker("SHIFT_CRAFT", player);
                    acknowledge(player, "return-items");
                }
                case "fill-inventory" -> {
                    require(session.stage == Stage.SECOND && session.crafts == 1,
                            "Full-inventory setup out of order");
                    inventory(player, 0, 0, 1);
                    // Non-stackable fixture items occupy exactly the 35 remaining slots.
                    for (int index = 0; index < 35; index++) context.players().give(player, hammers, 1);
                    inventory(player, 0, 0, 36);
                    session.stage = Stage.FULL;
                    acknowledge(player, "inventory-full");
                }
                case "full-inventory-attempted" -> {
                    require(session.stage == Stage.FULL && session.crafts == 1,
                            "Full inventory unexpectedly allowed a craft callback");
                    inventory(player, 0, 0, 36);
                    marker("FULL_INVENTORY_REJECTED", player);
                    require(context.players().tryConsume(player, InventoryCost.of(hammers, 35)),
                            "Cannot restore inventory capacity");
                    inventory(player, 0, 0, 1);
                    session.stage = Stage.SECOND;
                    acknowledge(player, "capacity-restored");
                }
                case "return-loaded" -> {
                    require(session.stage == Stage.RETURN && session.crafts == 2, "Unexpected extra craft");
                    inventory(player, 0, 0, 2);
                    session.stage = Stage.CLOSE;
                    acknowledge(player, "return-loaded");
                }
                case "closed" -> {
                    require(session.stage == Stage.CLOSE && session.crafts == 2, "Close phase out of order");
                    inventory(player, 1, 1, 2);
                    marker("RETURNED_INPUTS", player);
                    session.stage = Stage.REOPEN;
                    context.workbenches().open(player, workbench);
                    acknowledge(player, "reopened");
                }
                case "finished" -> {
                    require(session.stage == Stage.REOPEN && session.actions == 1 && session.crafts == 2,
                            "Incomplete gameplay sequence");
                    inventory(player, 1, 1, 2);
                    session.stage = Stage.COMPLETE;
                    context.logger().info("ENDERFALL_GAMEPLAY_SERVER_COMPLETE {}", context.platform().targetId());
                    acknowledge(player, "complete");
                }
                default -> throw new IllegalStateException("Unknown gameplay request " + message);
            }
        } catch (RuntimeException failure) {
            context.logger().error("ENDERFALL_GAMEPLAY_FAILED {} {}", context.platform().targetId(),
                    failure.getMessage());
            network.disconnect("Gameplay assertion failed: " + failure.getMessage());
        }
    }

    private void inventory(UUID player, int blockCount, int catalystCount, int hammerCount) {
        require(context.players().count(player, blocks) == blockCount, "Wrong server block inventory count");
        require(context.players().count(player, catalysts) == catalystCount, "Wrong server catalyst inventory count");
        require(context.players().count(player, hammers) == hammerCount, "Wrong server hammer inventory count");
    }

    private void acknowledge(UUID player, String message) {
        context.networking().sendToPlayer(player, PACKET, message);
    }

    private void marker(String check, UUID player) {
        context.logger().info("ENDERFALL_GAMEPLAY_{} {} {}", check, context.platform().targetId(), player);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Stage { MENU, FIRST, SECOND, FULL, RETURN, CLOSE, REOPEN, COMPLETE }

    private static final class Session {
        private Stage stage = Stage.MENU;
        private int actions;
        private int crafts;
        private boolean insufficientChecked;
    }
}
