package io.rpcell.api.spi;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;

/**
 * Interceptor filter for RPC invocation, modeled after Dubbo Filter.
 * Filters wrap an {@link Invoker} to intercept before/after the actual call,
 * enabling cross-cutting concerns like authentication, logging, monitoring, and rate limiting.
 *
 * <p>Usage: implement this interface, register via {@code ExtensionLoader.register("filter:myfilter", MyFilter.class)},
 * and configure filter names in {@code rpcell.filter.provider.names} / {@code rpcell.filter.consumer.names}.
 */
@FunctionalInterface
public interface Filter {

    /**
     * Invoke the next invoker in the filter chain.
     *
     * @param invoker the downstream invoker (next filter or actual endpoint)
     * @param invocation the invocation data carrier
     * @return the RPC result
     */
    Result invoke(Invoker invoker, Invocation invocation);
}
