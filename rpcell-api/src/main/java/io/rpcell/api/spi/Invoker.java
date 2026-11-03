package io.rpcell.api.spi;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;

/**
 * Common downstream abstraction shared by the proxy layer and cluster layer.
 */
public interface Invoker {

    Result invoke(Invocation invocation);
}
