package uk.co.enderfall.sdk.demo.config;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.config.ConfigHandle;
import uk.co.enderfall.sdk.api.config.ConfigKey;
import uk.co.enderfall.sdk.api.config.ConfigScope;
import uk.co.enderfall.sdk.api.config.ConfigSpec;

/** Typed configuration and its keys are kept in one small service object. */
public record DemoConfig(ConfigHandle handle, ConfigKey<Boolean> resonanceEnabled,
                         ConfigKey<Integer> pulseStrength, ConfigKey<Integer> maximumChargeKey,
                         ConfigKey<Integer> crystalChargeGainKey, ConfigKey<Integer> rodChargeCostKey,
                         ConfigKey<Double> rodHealingKey, ConfigKey<Integer> rodCooldownSecondsKey,
                         ConfigKey<Integer> ritualChargeBonusKey) {
    public static DemoConfig register(ModContext context) {
        ConfigSpec.Builder builder = ConfigSpec.builder();
        ConfigKey<Boolean> enabled = builder.booleanValue("resonance.enabled", true,
                "Allows the resonance demo interactions and packet command.");
        ConfigKey<Integer> strength = builder.integer("resonance.pulse_strength", 6, 1, 15,
                "Strength displayed by the portable resonance demo.");
        ConfigKey<Integer> maximumCharge = builder.integer("gameplay.maximum_charge", 100, 10, 1_000,
                "Maximum resonance held by one player during a server session.");
        ConfigKey<Integer> crystalChargeGain = builder.integer("gameplay.crystal_charge_gain", 8, 1, 100,
                "Resonance gained by absorbing one Void Crystal.");
        ConfigKey<Integer> rodChargeCost = builder.integer("gameplay.rod_charge_cost", 12, 1, 1_000,
                "Resonance spent when the Resonance Rod ability succeeds.");
        ConfigKey<Double> rodHealing = builder.decimal("gameplay.rod_healing", 6.0D, 0.0D, 100.0D,
                "Health restored by the Resonance Rod ability.");
        ConfigKey<Integer> rodCooldownSeconds = builder.integer("gameplay.rod_cooldown_seconds", 4, 0, 60,
                "Cooldown in seconds between successful Resonance Rod uses.");
        ConfigKey<Integer> ritualChargeBonus = builder.integer("gameplay.ritual_charge_bonus", 25, 0, 1_000,
                "Resonance granted when a workbench ritual succeeds.");
        ConfigHandle handle = context.configs().register("demo", ConfigScope.COMMON, builder.build());
        return new DemoConfig(handle, enabled, strength, maximumCharge, crystalChargeGain, rodChargeCost,
                rodHealing, rodCooldownSeconds, ritualChargeBonus);
    }

    public boolean enabled() {
        return handle.get(resonanceEnabled);
    }

    public int strength() {
        return handle.get(pulseStrength);
    }

    public int maximumCharge() {
        return handle.get(maximumChargeKey);
    }

    public int crystalChargeGain() {
        return handle.get(crystalChargeGainKey);
    }

    public int rodChargeCost() {
        return handle.get(rodChargeCostKey);
    }

    public double rodHealing() {
        return handle.get(rodHealingKey);
    }

    public int rodCooldownSeconds() {
        return handle.get(rodCooldownSecondsKey);
    }

    public int ritualChargeBonus() {
        return handle.get(ritualChargeBonusKey);
    }
}
