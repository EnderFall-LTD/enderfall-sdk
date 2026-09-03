package uk.co.enderfall.sdk.runtime;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import uk.co.enderfall.sdk.api.EnderfallClientMod;
import uk.co.enderfall.sdk.api.EnderfallMod;
import uk.co.enderfall.sdk.api.platform.Environment;

/** Instantiates generated-metadata entrypoints without exposing loader APIs to consumer code. */
public final class RuntimeModBootstrap {
    private RuntimeModBootstrap() {
    }

    public static RuntimeModContext initialize(String modId, String commonEntrypoint, String clientEntrypoint,
                                               PlatformAdapter adapter, ClassLoader classLoader) {
        Objects.requireNonNull(commonEntrypoint, "commonEntrypoint");
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(classLoader, "classLoader");
        RuntimeModContext context = new RuntimeModContext(modId, adapter);
        try {
            EnderfallMod common = instantiate(commonEntrypoint, EnderfallMod.class, classLoader);
            common.initialize(context);
            if (adapter.platformInfo().environment() == Environment.CLIENT
                    && clientEntrypoint != null && !clientEntrypoint.isBlank()) {
                EnderfallClientMod client = instantiate(clientEntrypoint, EnderfallClientMod.class, classLoader);
                client.initialize(context);
            }
            context.freezeRegistrations();
            return context;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("[" + modId + "] Initialization failed on "
                    + adapter.platformInfo().targetId(), exception);
        }
    }

    private static <T> T instantiate(String className, Class<T> expectedType, ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        Class<?> loaded = Class.forName(className, true, classLoader);
        if (!expectedType.isAssignableFrom(loaded)) {
            throw new IllegalArgumentException(className + " must implement " + expectedType.getName());
        }
        Object instance = loaded.getDeclaredConstructor().newInstance();
        return expectedType.cast(instance);
    }
}
