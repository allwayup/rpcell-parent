package io.rpcell.api.spi;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.URL;
import java.util.List;

/**
 * Load balance strategy extension point.
 */
public interface LoadBalance {

    URL select(List<URL> urls, Invocation invocation);
}
