package uk.co.enderfall.sdk.harness;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Independent evidence gates: a network round trip or an open-menu log cannot satisfy gameplay acceptance. */
final class GameplayEvidence {
    private final Map<String, CountDownLatch> checks = new LinkedHashMap<>();
    private final Map<String, CountDownLatch> server = new LinkedHashMap<>();
    private final Map<String, CountDownLatch> client = new LinkedHashMap<>();

    GameplayEvidence(String target) {
        add(server, target, "menuAction", "MENU_ACTION");
        add(client, target, "menuStateConfirmed", "MENU_STATE_CONFIRMED");
        add(client, target, "insufficientRejected", "INSUFFICIENT_REJECTED");
        add(server, target, "normalCraft", "NORMAL_CRAFT");
        add(server, target, "fullInventoryRejected", "FULL_INVENTORY_REJECTED");
        add(client, target, "fullInventoryUnchanged", "FULL_INVENTORY_UNCHANGED");
        add(server, target, "shiftCraft", "SHIFT_CRAFT");
        add(server, target, "returnedInputs", "RETURNED_INPUTS");
        add(client, target, "reopenedEmpty", "REOPEN_EMPTY");
        add(server, target, "serverGameplayComplete", "SERVER_COMPLETE");
        add(client, target, "clientGameplayComplete", "CLIENT_COMPLETE");
    }

    private void add(Map<String, CountDownLatch> side, String target, String name, String marker) {
        CountDownLatch latch = new CountDownLatch(1);
        checks.put(name, latch);
        side.put("ENDERFALL_GAMEPLAY_" + marker + " " + target, latch);
    }

    Map<String, CountDownLatch> serverMarkers() { return Map.copyOf(server); }
    Map<String, CountDownLatch> clientMarkers() { return Map.copyOf(client); }
    CountDownLatch clientComplete() { return checks.get("clientGameplayComplete"); }
    boolean passed() { return checks.values().stream().allMatch(latch -> latch.getCount() == 0); }

    Map<String, Boolean> snapshot() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        checks.forEach((name, latch) -> values.put(name, latch.getCount() == 0));
        return Map.copyOf(values);
    }
}
