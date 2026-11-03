package io.rpcell.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation providing the finest-grained override for RPC behavior.
 * <p>
 * When present, {@code RpcMethod} values override both the interface-level {@link RpcService}
 * and the global {@code rpcell.*} configuration.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcMethod {

    /** Whether the method is exposed for RPC invocation. */
    boolean exposed() default true;

    int timeout() default -1;

    int retries() default -1;
}
