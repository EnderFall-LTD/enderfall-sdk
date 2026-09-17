package uk.co.enderfall.sdk.api.ui;

@FunctionalInterface
public interface MenuActionHandler {
    void handle(MenuActionContext context);
}
