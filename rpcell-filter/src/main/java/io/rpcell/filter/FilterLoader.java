package io.rpcell.filter;

import io.rpcell.api.spi.Filter;
import io.rpcell.spi.ExtensionLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads Filter instances by name from the SPI ExtensionLoader.
 * Mirrors the pattern used by {@code rpcell-serializer} and {@code rpcell-registry}
 * for loading their respective plugin implementations.
 *
 * <p>Usage:
 * <pre>{@code
 * List<Filter> filters = FilterLoader.loadByName("filter:auth", "filter:seata");
 * Invoker chain = FilterChain.buildFilterChain(target, filters);
 * }</pre>
 */
public final class FilterLoader {

    private static final Logger LOG = LoggerFactory.getLogger(FilterLoader.class);

    private FilterLoader() {}

    /**
     * Load filters by their SPI names.
     *
     * @param filterNames the names of filters registered in the ExtensionLoader
     * @return the loaded filter instances in order; empty list if names is null or empty
     */
    private static final String FILTER_PREFIX = "filter:";

    public static List<Filter> load(List<String> filterNames) {
        if (filterNames == null || filterNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<Filter> filters = new ArrayList<>();
        for (String name : filterNames) {
            try {
                String lookupKey = name.startsWith(FILTER_PREFIX) ? name : FILTER_PREFIX + name;
                Filter filter = ExtensionLoader.load(lookupKey);
                filters.add(filter);
                LOG.info("Loaded filter: {}", name);
            } catch (Exception e) {
                throw new io.rpcell.api.exception.RpcParamException("Failed to load filter: " + name, e);
            }
        }
        return filters;
    }

    /**
     * Load filters by varargs SPI names.
     *
     * @param names the names of filters registered in the ExtensionLoader
     * @return the loaded filter instances in order
     */
    public static List<Filter> load(String... names) {
        if (names == null || names.length == 0) {
            return Collections.emptyList();
        }
        return load(List.of(names));
    }
}
