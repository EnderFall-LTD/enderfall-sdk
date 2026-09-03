package uk.co.enderfall.sdk.gradle.model;

import java.util.Objects;
import org.gradle.api.Action;

public class EnderfallSdkExtension {
    private final ModDefinition mod = new ModDefinition();
    private final TargetsDefinition targets = new TargetsDefinition();
    private String developmentTarget = "26.2-fabric";

    public void mod(Action<? super ModDefinition> action) {
        action.execute(mod);
    }

    public void targets(Action<? super TargetsDefinition> action) {
        action.execute(targets);
    }

    public ModDefinition modDefinition() {
        return mod;
    }

    public TargetsDefinition targetsDefinition() {
        return targets;
    }

    public String getDevelopmentTarget() {
        return developmentTarget;
    }

    public void setDevelopmentTarget(String developmentTarget) {
        this.developmentTarget = Objects.requireNonNull(developmentTarget, "developmentTarget");
    }
}
