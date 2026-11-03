package io.rpcell.registry;

import io.rpcell.api.exception.RpcRegistryException;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.NotifyListener;
import io.rpcell.api.spi.Registry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract registry providing lifecycle template methods and local cache.
 * Subclasses implement doRegister, doSubscribe, doUnsubscribe, doUnregister.
 */
public abstract class AbstractRegistry implements Registry {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRegistry.class);

    protected final RegistryDirectory directory;
    protected final Map<String, List<URL>> localCache = new ConcurrentHashMap<>();
    protected volatile boolean initialized;
    private boolean destroyFailed;

    public AbstractRegistry(RegistryDirectory directory) {
        this.directory = directory;
    }

    @Override
    public final synchronized void register(URL url) {
        checkInitialized();
        doRegister(url);
        LOG.info("Registered service URL: {}", url.toAddress());
    }

    @Override
    public final synchronized void unregister(URL url) {
        checkInitialized();
        doUnregister(url);
        LOG.info("Unregistered service URL: {}", url.toAddress());
    }

    @Override
    public final synchronized void subscribe(String serviceName, NotifyListener listener) {
        checkInitialized();
        doSubscribe(serviceName, listener);
        LOG.info("Subscribed to service: {}", serviceName);
    }

    @Override
    public final synchronized void unsubscribe(String serviceName, NotifyListener listener) {
        checkInitialized();
        doUnsubscribe(serviceName, listener);
        LOG.info("Unsubscribed from service: {}", serviceName);
    }

    /**
     * Initialize the registry. Must be called before any register/subscribe operations.
     */
    public synchronized void init() {
        if (destroyFailed) {
            throw new RpcRegistryException("Previous registry destroy failed; retry destroy before initializing");
        }
        if (initialized) {
            return;
        }
        try {
            doInit();
            initialized = true;
            LOG.info("Registry initialized");
        } catch (Exception e) {
            throw new RpcRegistryException("Failed to initialize registry", e);
        }
    }

    /**
     * Destroy the registry, releasing all resources.
     */
    public synchronized void destroy() {
        if (!initialized && !destroyFailed) {
            return;
        }
        initialized = false;
        destroyFailed = true;
        try {
            doDestroy();
            destroyFailed = false;
            LOG.info("Registry destroyed");
        } catch (Exception e) {
            LOG.warn("Error during registry destroy", e);
        }
    }

    /**
     * Get the RegistryDirectory associated with this registry.
     *
     * @return the RegistryDirectory
     */
    public RegistryDirectory getDirectory() {
        return directory;
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new RpcRegistryException("Registry not initialized");
        }
    }

    protected abstract void doInit();

    protected abstract void doRegister(URL url);

    protected abstract void doUnregister(URL url);

    protected abstract void doSubscribe(String serviceName, NotifyListener listener);

    protected abstract void doUnsubscribe(String serviceName, NotifyListener listener);

    protected abstract void doDestroy();
}
