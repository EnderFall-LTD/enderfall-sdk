package uk.co.enderfall.sdk.api.data;

@FunctionalInterface
public interface DataGenerator {
    void generate(DataGenerationContext context);
}
