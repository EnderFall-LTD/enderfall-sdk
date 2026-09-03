package uk.co.enderfall.sdk.api.event;

@FunctionalInterface
public interface EventListener<E> {
    void handle(E event) throws Exception;
}
