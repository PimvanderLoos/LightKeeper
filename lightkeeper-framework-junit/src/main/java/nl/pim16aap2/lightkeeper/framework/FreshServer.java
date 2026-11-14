package nl.pim16aap2.lightkeeper.framework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation that forces a fresh server lifecycle.
 * Each annotated method gets a new process. Discovered runtimes retain the same private class session,
 * including its world and plugin data; this annotation does not reset worlds between methods.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface FreshServer
{
}
