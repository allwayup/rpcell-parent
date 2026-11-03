package io.rpcell.spi;

import io.rpcell.api.exception.RpcParamException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal extension loader: name-based registration and instantiation.
 * No @Adaptive, Wrapper, or @Activate semantics.
 */
public final class ExtensionLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final Map<String, Class<?>> REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Object> INSTANCE_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Deque<String>> LOADING_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private ExtensionLoader() {}

    /**
     * Register an implementation class by name.
     *
     * @param name the extension name
     * @param impl the implementation class
     */
    public static void register(String name, Class<?> impl) {
        if (name == null || name.isEmpty()) {
            throw new RpcParamException("Extension name must not be empty");
        }
        if (impl == null) {
            throw new RpcParamException("Extension implementation class must not be null");
        }
        synchronized (REGISTRY) {
            REGISTRY.put(name, impl);
            INSTANCE_CACHE.remove(name);
        }
        LOG.debug("Registered SPI extension: {} -> {}", name, impl.getName());
    }

    /**
     * Load and instantiate an extension by name.
     *
     * @param name the extension name
     * @param <T> the expected type
     * @return a new instance of the extension
     */
    @SuppressWarnings("unchecked")
    public static <T> T load(String name) {
        synchronized (REGISTRY) {
            Object cached = INSTANCE_CACHE.get(name);
            if (cached != null) {
                return (T) cached;
            }
        }

        Deque<String> loadingStack = LOADING_STACK.get();
        if (loadingStack.contains(name)) {
            throw new RpcParamException("Circular SPI extension loading: " + loadingStack + " -> " + name);
        }
        loadingStack.addLast(name);
        try {
            while (true) {
                Class<?> impl;
                synchronized (REGISTRY) {
                    Object cached = INSTANCE_CACHE.get(name);
                    if (cached != null) {
                        return (T) cached;
                    }
                    impl = REGISTRY.get(name);
                    if (impl == null) {
                        throw new RpcParamException("No SPI extension registered for name: " + name);
                    }
                }

                Object instance;
                try {
                    instance = impl.getDeclaredConstructor().newInstance();
                    LOG.debug("Instantiated SPI extension: {} -> {}", name, impl.getName());
                } catch (Exception e) {
                    throw new RpcParamException("Failed to instantiate SPI extension: " + name, e);
                }

                synchronized (REGISTRY) {
                    if (!impl.equals(REGISTRY.get(name))) {
                        continue;
                    }
                    Object cached = INSTANCE_CACHE.get(name);
                    if (cached == null) {
                        cached = instance;
                        INSTANCE_CACHE.put(name, cached);
                    }
                    return (T) cached;
                }
            }
        } finally {
            loadingStack.removeLast();
            if (loadingStack.isEmpty()) {
                LOADING_STACK.remove();
            }
        }
    }

    /**
     * Check if an extension is registered.
     *
     * @param name the extension name
     * @return true if registered
     */
    public static boolean contains(String name) {
        return REGISTRY.containsKey(name);
    }

    /**
     * Get a snapshot of all registered extensions.
     *
     * @return an unmodifiable map of name to implementation class
     */
    public static Map<String, Class<?>> registeredExtensions() {
        return Map.copyOf(REGISTRY);
    }

    /**
     * Clear all registered extensions (useful for testing).
     */
    public static void clear() {
        synchronized (REGISTRY) {
            REGISTRY.clear();
            INSTANCE_CACHE.clear();
        }
    }
}
