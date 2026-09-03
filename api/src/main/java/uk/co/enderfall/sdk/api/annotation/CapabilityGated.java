package uk.co.enderfall.sdk.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import uk.co.enderfall.sdk.api.platform.Capability;

/** Documents a portable API whose implementation depends on a target capability. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CapabilityGated {
    Capability value();
}
