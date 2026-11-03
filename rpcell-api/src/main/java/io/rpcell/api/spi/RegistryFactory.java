package io.rpcell.api.spi;

import io.rpcell.api.model.RegistryConfig;

/**
 * Registry center factory extension point.
 */
public interface RegistryFactory {

    Registry create(RegistryConfig config);
}
