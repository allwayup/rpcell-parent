package io.rpcell.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark an interface as an RPC service, declaring service metadata,
 * behavioral configuration, and method visibility rules.
 * Must be declared on the interface, not the implementation class.
 *
 * <p>Attribute priority (highest to lowest):
 * <ol>
 *   <li>Method-level {@link RpcMethod}</li>
 *   <li>Interface-level {@link RpcService}</li>
 *   <li>Global {@code rpcell.*} configuration</li>
 * </ol>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcService {

    /** Service name. Defaults to the fully-qualified interface name. */
    String value() default "";

    /** Service group for namespace isolation. */
    String group() default "";

    /** Service version for gray release / multi-version routing. */
    String version() default "";

    /** Methods to expose (whitelist). Empty = all. */
    String[] include() default {};

    /** Methods to hide (blacklist). Empty = none. */
    String[] exclude() default {};

    int timeout() default -1;

    int retries() default -1;

    /** Whether exported services should be registered. */
    boolean register() default true;

    /** Whether the service should be exported as a provider. */
    boolean export() default true;

    /** If true, create an independent instance for each RPC interface instead of reusing the Spring bean. */
    boolean multiInstance() default false;

    String[] filter() default {};
}
