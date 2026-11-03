package io.rpcell.api.spi;

/**
 * Registry center extension point.
 */
public interface Registry {

    void register(io.rpcell.api.model.URL url);

    void unregister(io.rpcell.api.model.URL url);

    void subscribe(String serviceName, NotifyListener listener);

    void unsubscribe(String serviceName, NotifyListener listener);
}
