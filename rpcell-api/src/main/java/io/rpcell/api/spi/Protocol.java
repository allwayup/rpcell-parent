package io.rpcell.api.spi;

import io.rpcell.api.model.ServiceDescriptor;

/**
 * Protocol extension point.
 */
public interface Protocol {

    void export(ServiceDescriptor descriptor, Object instance);

    void refer(String serviceName);

    Object codecHandler();
}
