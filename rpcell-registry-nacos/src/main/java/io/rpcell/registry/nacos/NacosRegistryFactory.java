package io.rpcell.registry.nacos;

import io.rpcell.api.model.RegistryConfig;
import io.rpcell.api.spi.Registry;
import io.rpcell.api.spi.RegistryFactory;
import io.rpcell.registry.RegistryDirectory;

/**
 * Factory for creating NacosRegistry instances.
 */
public class NacosRegistryFactory implements RegistryFactory {

    @Override
    public Registry create(RegistryConfig config) {
        NacosConfig nacosConfig = new NacosConfig();
        nacosConfig.setServerAddr(config.getAddress());
        nacosConfig.setUsername(config.getUsername());
        nacosConfig.setPassword(config.getPassword());

        if (config.getParams() != null) {
            nacosConfig.setNamespace(config.getParams().get("namespace"));
            nacosConfig.setGroup(config.getParams().get("group"));
        }

        RegistryDirectory directory = new RegistryDirectory();
        NacosRegistry registry = new NacosRegistry(directory, nacosConfig);
        registry.init();
        return registry;
    }
}
