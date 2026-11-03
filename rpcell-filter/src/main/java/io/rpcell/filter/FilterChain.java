package io.rpcell.filter;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.spi.Filter;
import io.rpcell.api.spi.Invoker;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds an invoker chain by wrapping a target invoker with a list of filters.
 * Filters are applied in the order they appear in the list, so the first filter
 * in the list is the outermost wrapper.
 *
 * <p>Each filter receives the next invoker in the chain and is responsible for
 * calling {@code invoker.invoke(invocation)} to proceed down the chain.
 */
public final class FilterChain {

    private static final Logger LOG = LoggerFactory.getLogger(FilterChain.class);

    private FilterChain() {}

    /**
     * Build a filter chain wrapping the target invoker.
     *
     * @param target the target invoker at the end of the chain
     * @param filters the ordered list of filters (outermost first)
     * @return the wrapped invoker, or the target if filters is empty
     */
    public static Invoker buildFilterChain(Invoker target, List<Filter> filters) {
        if (filters == null || filters.isEmpty()) {
            return target;
        }
        Invoker chain = target;
        for (int i = filters.size() - 1; i >= 0; i--) {
            final Filter filter = filters.get(i);
            final Invoker next = chain;
            chain = new FilterInvoker(filter, next);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Built filter chain with {} filter(s)", filters.size());
        }
        return chain;
    }

    /**
     * Build a filter chain with an empty filter list (identity pass-through).
     *
     * @param target the target invoker
     * @return the target invoker unchanged
     */
    public static Invoker buildFilterChain(Invoker target) {
        return buildFilterChain(target, Collections.emptyList());
    }

    /**
     * Single filter wrapper that delegates to the filter and passes the next invoker.
     */
    private static class FilterInvoker implements Invoker {

        private final Filter filter;
        private final Invoker next;

        FilterInvoker(Filter filter, Invoker next) {
            this.filter = filter;
            this.next = next;
        }

        @Override
        public Result invoke(Invocation invocation) {
            return filter.invoke(next, invocation);
        }
    }
}
