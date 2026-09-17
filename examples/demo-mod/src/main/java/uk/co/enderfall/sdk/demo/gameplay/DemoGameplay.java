package uk.co.enderfall.sdk.demo.gameplay;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.PlayerEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.event.TickEvent;
import uk.co.enderfall.sdk.api.gameplay.InventoryCost;
import uk.co.enderfall.sdk.api.gameplay.PlayerManager;
import uk.co.enderfall.sdk.api.gameplay.PlayerSnapshot;
import uk.co.enderfall.sdk.api.registry.ItemRef;
import uk.co.enderfall.sdk.api.ui.WorkbenchCraftContext;
import uk.co.enderfall.sdk.demo.config.DemoConfig;
import uk.co.enderfall.sdk.demo.content.DemoBlocks;
import uk.co.enderfall.sdk.demo.content.DemoItems;
import uk.co.enderfall.sdk.demo.recipe.DemoRecipes;
import uk.co.enderfall.sdk.demo.workbench.DemoWorkbench;

/** Server-authoritative resonance progression built only from portable SDK services. */
public final class DemoGameplay {
    private static final int CORE_EXPERIENCE = 12;
    private static final int ROD_EXPERIENCE = 3;

    private final ModContext context;
    private final DemoConfig config;
    private final DemoItems items;
    private final DemoBlocks blocks;
    private final PlayerManager players;
    private final ItemRef alloyBlockItem;
    private final Map<UUID, PlayerProgress> progress = new ConcurrentHashMap<>();
    private final AtomicLong serverTick = new AtomicLong();
    private DemoWorkbench workbench;

    private DemoGameplay(ModContext context, DemoConfig config, DemoItems items, DemoBlocks blocks) {
        this.context = context;
        this.config = config;
        this.items = items;
        this.blocks = blocks;
        players = context.players();
        alloyBlockItem = new ItemRef(blocks.enderAlloy().id());
    }

    public static DemoGameplay register(ModContext context, DemoConfig config,
                                        DemoItems items, DemoBlocks blocks) {
        DemoGameplay gameplay = new DemoGameplay(context, config, items, blocks);
        context.events().subscribe(SdkEvents.INTERACTION, gameplay::onInteraction);
        context.events().subscribe(SdkEvents.PLAYER, gameplay::onPlayer);
        context.events().subscribe(SdkEvents.TICK, gameplay::onTick);
        context.events().subscribe(SdkEvents.LIFECYCLE, gameplay::onLifecycle);
        return gameplay;
    }

    public void attachWorkbench(DemoWorkbench value) {
        if (workbench != null) {
            throw new IllegalStateException("Demo workbench has already been attached");
        }
        workbench = java.util.Objects.requireNonNull(value, "value");
    }

    public String status(UUID playerId) {
        PlayerProgress state = state(playerId);
        PlayerSnapshot snapshot = players.find(playerId).orElse(null);
        String health = snapshot == null ? "offline"
                : format(snapshot.health()) + '/' + format(snapshot.maximumHealth());
        return "resonance=" + state.charge + '/' + config.maximumCharge()
                + ", health=" + health
                + ", cores=" + players.count(playerId, items.resonanceCore())
                + ", rituals=" + state.ritualsCompleted
                + ", rod uses=" + state.rodUses;
    }

    public void reset(UUID playerId) {
        progress.remove(playerId);
        players.message(playerId, "Your temporary demo resonance progress has been reset.");
        showCharge(playerId, state(playerId));
    }

    public void giveStarterKit(UUID playerId) {
        players.give(playerId, items.voidCrystal(), Math.max(DemoRecipes.CRYSTAL_COST, 8));
        players.give(playerId, items.resonanceRod(), 1);
        players.give(playerId, alloyBlockItem, 2);
        players.give(playerId, new ItemRef(uk.co.enderfall.sdk.api.ResourceId.of("minecraft", "echo_shard")), 2);
        players.give(playerId, new ItemRef(blocks.resonanceLamp().id()), 1);
        players.give(playerId, new ItemRef(blocks.resonanceWorkbench().id()), 1);
        players.message(playerId, "Demo kit granted. Place the workbench, then right-click it for an infusion recipe.");
    }

    public void sendGuide(UUID playerId) {
        players.message(playerId, "EnderFall resonance guide:");
        players.message(playerId, "1. Right-click a Void Crystal to absorb it as resonance.");
        players.message(playerId, "2. Right-click the Resonance Workbench to open its real container menu.");
        players.message(playerId, "3. Put " + DemoRecipes.CRYSTAL_COST
                + " Void Crystals, 1 alloy block, and 1 Echo Shard into its three ordered slots.");
        players.message(playerId, "4. Take the Resonance Core from the result slot; shift-click is supported.");
        players.message(playerId, "5. Right-click a core for full charge, or use the rod to spend charge and heal.");
    }

    private void onInteraction(InteractionEvent event) {
        if (event.side() != InteractionEvent.Side.SERVER || !isDemoTarget(event)) {
            return;
        }
        if (!config.enabled()) {
            players.actionBar(event.playerId(), "Resonance gameplay is disabled in the demo config.");
            event.handle();
            return;
        }

        context.logger().info("Demo gameplay interaction {} with {} by {}",
                event.kind(), event.target(), event.playerId());
        if (event.kind() == InteractionEvent.Kind.USE_ITEM) {
            if (event.target().equals(items.voidCrystal().id())) {
                absorbCrystal(event.playerId());
                event.handle();
            } else if (event.target().equals(items.resonanceRod().id())) {
                useRod(event.playerId());
                event.handle();
            } else if (event.target().equals(items.resonanceCore().id())) {
                activateCore(event.playerId());
                event.handle();
            }
        } else if (event.target().equals(blocks.resonanceWorkbench().id())) {
            openWorkbench(event.playerId());
            event.handle();
        } else if (event.target().equals(blocks.resonanceLamp().id())) {
            PlayerProgress state = state(event.playerId());
            players.actionBar(event.playerId(), "The lamp hums. Resonance " + state.charge
                    + '/' + config.maximumCharge());
            event.handle();
        }
    }

    private void absorbCrystal(UUID playerId) {
        PlayerProgress state = state(playerId);
        if (state.charge >= config.maximumCharge()) {
            players.actionBar(playerId, "Resonance is already full.");
            return;
        }
        if (!players.tryConsume(playerId, InventoryCost.of(items.voidCrystal(), 1))) {
            players.actionBar(playerId, "No Void Crystal was available to absorb.");
            return;
        }
        int previous = state.charge;
        state.charge = Math.min(config.maximumCharge(), state.charge + config.crystalChargeGain());
        players.heal(playerId, 1.0D);
        players.addExperience(playerId, 1);
        players.actionBar(playerId, "Resonance " + state.charge + '/' + config.maximumCharge()
                + " (+" + (state.charge - previous) + ")");
    }

    private void useRod(UUID playerId) {
        PlayerProgress state = state(playerId);
        long now = serverTick.get();
        if (now < state.rodReadyTick) {
            long remainingTicks = state.rodReadyTick - now;
            players.actionBar(playerId, "Resonance Rod cooldown: " + ((remainingTicks + 19) / 20) + "s");
            return;
        }
        if (state.charge < config.rodChargeCost()) {
            players.actionBar(playerId, "The rod needs " + config.rodChargeCost()
                    + " resonance; you have " + state.charge + '.');
            return;
        }

        state.charge -= config.rodChargeCost();
        state.rodUses++;
        state.rodReadyTick = now + config.rodCooldownSeconds() * 20L;
        players.heal(playerId, config.rodHealing());
        players.addExperience(playerId, ROD_EXPERIENCE);
        players.message(playerId, "The Resonance Rod restored " + format(config.rodHealing())
                + " health and awarded " + ROD_EXPERIENCE + " experience.");
        showCharge(playerId, state);
    }

    private void openWorkbench(UUID playerId) {
        if (workbench == null) {
            throw new IllegalStateException("Demo workbench was not attached during initialization");
        }
        workbench.open(playerId);
    }

    public void onWorkbenchCrafted(WorkbenchCraftContext craft) {
        UUID playerId = craft.playerId();
        PlayerProgress state = state(playerId);
        state.ritualsCompleted++;
        state.charge = Math.min(config.maximumCharge(), state.charge + config.ritualChargeBonus());
        players.addExperience(playerId, CORE_EXPERIENCE);
        players.message(playerId, "Custom recipe " + craft.recipeId() + " completed: "
                + craft.resultCount() + " Resonance Core crafted and " + CORE_EXPERIENCE
                + " experience awarded.");
        showCharge(playerId, state);
    }

    private void activateCore(UUID playerId) {
        PlayerProgress state = state(playerId);
        PlayerSnapshot snapshot = players.find(playerId).orElse(null);
        boolean fullHealth = snapshot != null && snapshot.missingHealth() < 0.01D;
        if (state.charge >= config.maximumCharge() && fullHealth) {
            players.actionBar(playerId, "The Resonance Core is ready, but you are already fully restored.");
            return;
        }
        if (!players.tryConsume(playerId, InventoryCost.of(items.resonanceCore(), 1))) {
            players.actionBar(playerId, "No Resonance Core was available to activate.");
            return;
        }

        state.charge = config.maximumCharge();
        players.heal(playerId, snapshot == null ? 100.0D : snapshot.maximumHealth());
        players.addExperience(playerId, CORE_EXPERIENCE);
        players.message(playerId, "Resonance Core activated: health restored and resonance fully charged.");
        showCharge(playerId, state);
    }

    private void onPlayer(PlayerEvent event) {
        if (event.action() == PlayerEvent.Action.JOIN) {
            state(event.playerId());
            players.message(event.playerId(), "EnderFall SDK Demo is active. Run /enderfall_demo_guide to begin.");
        }
    }

    private void onTick(TickEvent event) {
        if (event.side() == TickEvent.Side.SERVER && event.phase() == TickEvent.Phase.END) {
            serverTick.set(event.tick());
        }
    }

    private void onLifecycle(LifecycleEvent event) {
        if (event.stage() == LifecycleEvent.Stage.SERVER_STOPPED) {
            progress.clear();
            serverTick.set(0L);
        }
    }

    private boolean isDemoTarget(InteractionEvent event) {
        return event.target().equals(items.voidCrystal().id())
                || event.target().equals(items.resonanceRod().id())
                || event.target().equals(items.resonanceCore().id())
                || event.target().equals(blocks.resonanceWorkbench().id())
                || event.target().equals(blocks.resonanceLamp().id());
    }

    private PlayerProgress state(UUID playerId) {
        return progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
    }

    private void showCharge(UUID playerId, PlayerProgress state) {
        players.actionBar(playerId, "Resonance " + state.charge + '/' + config.maximumCharge());
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format("%.1f", value);
    }

    private static final class PlayerProgress {
        private int charge;
        private int ritualsCompleted;
        private int rodUses;
        private long rodReadyTick;
    }
}
