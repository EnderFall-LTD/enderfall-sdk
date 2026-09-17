package uk.co.enderfall.sdk.demo.workbench;

import java.util.UUID;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.ui.WorkbenchRef;
import uk.co.enderfall.sdk.api.ui.WorkbenchSpec;
import uk.co.enderfall.sdk.demo.gameplay.DemoGameplay;
import uk.co.enderfall.sdk.demo.recipe.DemoRecipes;

/** Owns the real inventory-backed Resonance Workbench menu registration. */
public final class DemoWorkbench {
    private final ModContext context;
    private final WorkbenchRef reference;

    private DemoWorkbench(ModContext context, WorkbenchRef reference) {
        this.context = context;
        this.reference = reference;
    }

    public static DemoWorkbench register(ModContext context, DemoRecipes recipes, DemoGameplay gameplay) {
        WorkbenchRef reference = context.workbenches().register("resonance_workbench",
                WorkbenchSpec.builder("Resonance Workbench", recipes.resonanceInfusing())
                        .backgroundColor(0xFF1A1426)
                        .build(), gameplay::onWorkbenchCrafted);
        return new DemoWorkbench(context, reference);
    }

    public void open(UUID playerId) {
        context.workbenches().open(playerId, reference);
    }
}
