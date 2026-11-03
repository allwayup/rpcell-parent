package io.rpcell.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a field as an RPC service reference; the framework will inject a proxy at startup.
 *
 * <p>Attribute priority (highest to lowest):
 * <ol>
 *   <li>{@link RpcReference} annotation on the field</li>
 *   <li>{@link RpcService} annotation on the interface</li>
 *   <li>Global {@code rpcell.consumer.*} configuration</li>
 * </ol>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcReference {

    /** Service group. Empty = inherit from {@link RpcService#group()}. */
    String group() default "";

    /** Service version. Empty = inherit from {@link RpcService#version()}. */
    String version() default "";

    int timeout() default -1;

    int retries() default -1;

    String loadbalance() default "";

    boolean check() default false;

    String[] filter() default {};
}
