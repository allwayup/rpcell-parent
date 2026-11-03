package io.rpcell.api.spi;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.URL;
import java.util.List;

/**
 * Cluster fault-tolerance strategy extension point.
 */
public interface Cluster {

    Result invoke(Invocation invocation, List<URL> urls);
}
