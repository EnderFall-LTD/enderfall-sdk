package uk.co.enderfall.sdk.api.config;

public interface ConfigManager {
    ConfigHandle register(String name, ConfigScope scope, ConfigSpec spec);
}
