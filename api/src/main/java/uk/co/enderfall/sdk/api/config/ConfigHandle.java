package uk.co.enderfall.sdk.api.config;

import java.nio.file.Path;
import java.util.Map;

public interface ConfigHandle {
    Path path();

    <T> T get(ConfigKey<T> key);

    Map<String, Object> snapshot();
}
