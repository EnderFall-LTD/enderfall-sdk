package uk.co.enderfall.sdk.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import uk.co.enderfall.sdk.api.data.DataGenerationContext;
import uk.co.enderfall.sdk.api.data.DataGenerationManager;
import uk.co.enderfall.sdk.api.data.DataGenerator;

public final class DefaultDataGenerationManager implements DataGenerationManager {
    private final List<DataGenerator> generators = new ArrayList<>();
    private final RegistrationGate gate;
    private final String modId;
    private final String target;

    DefaultDataGenerationManager(RegistrationGate gate, String modId, String target) {
        this.gate = gate;
        this.modId = modId;
        this.target = target;
    }

    @Override
    public void register(DataGenerator generator) {
        gate.requireOpen(modId, target);
        generators.add(Objects.requireNonNull(generator, "generator"));
    }

    public void generate(DataGenerationContext context) {
        generators.forEach(generator -> generator.generate(context));
    }

    public int generatorCount() {
        return generators.size();
    }
}
