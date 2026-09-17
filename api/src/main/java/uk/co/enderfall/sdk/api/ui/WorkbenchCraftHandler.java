package uk.co.enderfall.sdk.api.ui;

/** Handles successful, server-authoritative workbench crafts. */
@FunctionalInterface
public interface WorkbenchCraftHandler {
    void crafted(WorkbenchCraftContext context);
}
