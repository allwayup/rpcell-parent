package io.rpcell.registry;

import io.rpcell.api.exception.RpcRegistryException;
import io.rpcell.api.model.RegistryConfig;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.NotifyListener;
import io.rpcell.api.spi.RegistryFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in direct registry implementation.
 * Reads static address list from configuration; register is no-op; subscribe immediately calls back once.
 * Guarantees the framework can run without any external registry center.
 */
public class DirectRegistry extends AbstractRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(DirectRegistry.class);

    private final List<URL> staticUrls;
    private final Map<String, List<NotifyListener>> listeners = new ConcurrentHashMap<>();

    public DirectRegistry(RegistryDirectory directory, List<URL> staticUrls) {
        super(directory);
        this.staticUrls = new ArrayList<>(staticUrls);
    }

    @Override
    protected void doInit() {
        LOG.info("DirectRegistry initialized with {} static URLs", staticUrls.size());
    }

    @Override
    protected void doRegister(URL url) {
        LOG.debug("DirectRegistry: register no-op for {}", url.toAddress());
    }

    @Override
    protected void doUnregister(URL url) {
        LOG.debug("DirectRegistry: unregister no-op for {}", url.toAddress());
    }

    @Override
    protected void doSubscribe(String serviceName, NotifyListener listener) {
        listeners
                .computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>())
                .add(listener);
        List<URL> matchingUrls = filterUrls(serviceName);
        try {
            listener.notify(matchingUrls);
            LOG.info("DirectRegistry: subscribe {} with {} URLs", serviceName, matchingUrls.size());
        } catch (RuntimeException | Error e) {
            doUnsubscribe(serviceName, listener);
            throw e;
        }
    }

    @Override
    protected void doUnsubscribe(String serviceName, NotifyListener listener) {
        List<NotifyListener> list = listeners.get(serviceName);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(serviceName, list);
            }
        }
    }

    @Override
    protected void doDestroy() {
        listeners.clear();
        LOG.info("DirectRegistry destroyed");
    }

    private List<URL> filterUrls(String serviceName) {
        if ("*".equals(serviceName)) {
            return new ArrayList<>(staticUrls);
        }
        List<URL> result = new ArrayList<>();
        for (URL url : staticUrls) {
            if (url.getServiceName() == null || url.getServiceName().equals(serviceName)) {
                result.add(url);
            }
        }
        return result;
    }

    /**
     * Factory for DirectRegistry.
     */
    public static class DirectRegistryFactory implements RegistryFactory {

        @Override
        public io.rpcell.api.spi.Registry create(RegistryConfig config) {
            String address = config.getAddress();
            if (address == null || address.isEmpty()) {
                throw new RpcRegistryException("rpcell.registry.address is required for direct registry mode");
            }

            List<URL> urls = new ArrayList<>();
            String[] parts = address.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String host;
                int port;
                if (trimmed.startsWith("[")) {
                    int bracketClose = trimmed.indexOf(']');
                    if (bracketClose < 0
                            || bracketClose == trimmed.length() - 1
                            || trimmed.charAt(bracketClose + 1) != ':') {
                        throw new RpcRegistryException(
                                "Invalid IPv6 address format: " + trimmed + " (expected [host]:port)");
                    }
                    host = trimmed.substring(1, bracketClose).trim();
                    if (host.isEmpty()) {
                        throw new RpcRegistryException("Host must not be empty in address: " + trimmed);
                    }
                    String portStr = trimmed.substring(bracketClose + 2).trim();
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        throw new RpcRegistryException("Invalid port in address: " + trimmed);
                    }
                } else {
                    String[] hp = trimmed.split(":");
                    if (hp.length != 2) {
                        throw new RpcRegistryException(
                                "Invalid address format: " + trimmed + " (expected host:port or [host]:port)");
                    }
                    host = hp[0].trim();
                    if (host.isEmpty()) {
                        throw new RpcRegistryException("Host must not be empty in address: " + trimmed);
                    }
                    try {
                        port = Integer.parseInt(hp[1].trim());
                    } catch (NumberFormatException e) {
                        throw new RpcRegistryException("Invalid port in address: " + trimmed);
                    }
                }
                if (port < 1 || port > 65535) {
                    throw new RpcRegistryException("Port must be between 1 and 65535 in address: " + trimmed);
                }
                urls.add(new URL("rpcell", host, port, null, null, null));
            }
            if (urls.isEmpty()) {
                throw new RpcRegistryException("No valid addresses found in registry config");
            }

            RegistryDirectory directory = new RegistryDirectory();
            DirectRegistry registry = new DirectRegistry(directory, urls);
            registry.init();
            return registry;
        }
    }
}
