package io.rpcell.registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.rpcell.api.exception.RpcRegistryException;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.NotifyListener;
import io.rpcell.registry.AbstractRegistry;
import io.rpcell.registry.RegistryDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos registry implementation extending AbstractRegistry.
 * Adapts Nacos NamingService to the RPCell Registry SPI.
 */
public class NacosRegistry extends AbstractRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRegistry.class);

    private final NacosConfig config;
    private NamingService namingService;
    private final Map<String, Map<NotifyListener, Subscription>> nacosListeners = new ConcurrentHashMap<>();
    private final Map<String, Map<NotifyListener, CopyOnWriteArrayList<Subscription>>> retiredListeners =
            new ConcurrentHashMap<>();

    public NacosRegistry(RegistryDirectory directory, NacosConfig config) {
        super(directory);
        this.config = config;
    }

    @Override
    protected void doInit() {
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", config.getServerAddr());
            if (config.getNamespace() != null && !config.getNamespace().isEmpty()) {
                properties.setProperty("namespace", config.getNamespace());
            }
            if (config.getUsername() != null && !config.getUsername().isEmpty()) {
                properties.setProperty("username", config.getUsername());
            }
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                properties.setProperty("password", config.getPassword());
            }
            this.namingService = NamingFactory.createNamingService(properties);
            LOG.info("NacosRegistry initialized: serverAddr={}", config.getServerAddr());
        } catch (NacosException e) {
            throw new RpcRegistryException("Failed to create Nacos naming service", e);
        }
    }

    @Override
    protected void doRegister(URL url) {
        try {
            String serviceName = buildServiceName(url);
            Instance instance = new Instance();
            instance.setIp(url.getHost());
            instance.setPort(url.getPort());
            instance.setHealthy(true);
            instance.setEnabled(true);
            instance.setServiceName(serviceName);
            if (url.getGroup() != null) {
                instance.setClusterName(url.getGroup());
            }
            if (url.getVersion() != null) {
                instance.getMetadata().put("version", url.getVersion());
            }
            namingService.registerInstance(
                    serviceName, config.getGroup() != null ? config.getGroup() : "DEFAULT_GROUP", instance);
            LOG.info("NacosRegistry registered: {} -> {}:{}", serviceName, url.getHost(), url.getPort());
        } catch (NacosException e) {
            throw new RpcRegistryException("Failed to register service: " + url.toServiceKey(), e);
        }
    }

    @Override
    protected void doUnregister(URL url) {
        try {
            String serviceName = buildServiceName(url);
            String group = config.getGroup() != null ? config.getGroup() : "DEFAULT_GROUP";
            if (url.getGroup() != null) {
                namingService.deregisterInstance(serviceName, group, url.getHost(), url.getPort(), url.getGroup());
            } else {
                namingService.deregisterInstance(serviceName, group, url.getHost(), url.getPort());
            }
            LOG.info("NacosRegistry unregistered: {} -> {}:{}", serviceName, url.getHost(), url.getPort());
        } catch (NacosException e) {
            throw new RpcRegistryException("Failed to unregister service: " + url.toServiceKey(), e);
        }
    }

    @Override
    protected void doSubscribe(String serviceName, NotifyListener listener) {
        Subscription subscription = new Subscription(serviceName, listener);
        AtomicReference<Subscription> previous = new AtomicReference<>();
        nacosListeners.compute(serviceName, (key, serviceListeners) -> {
            Map<NotifyListener, Subscription> current =
                    serviceListeners != null ? serviceListeners : new ConcurrentHashMap<>();
            previous.set(current.put(listener, subscription));
            return current;
        });
        if (previous.get() != null) {
            unsubscribe(serviceName, listener, previous.get());
        }

        try {
            String group = config.getGroup() != null ? config.getGroup() : "DEFAULT_GROUP";
            namingService.subscribe(serviceName, group, subscription);
            if (!isSubscriptionActive(serviceName, listener, subscription)) {
                unsubscribe(serviceName, listener, subscription);
                return;
            }

            List<Instance> instances = namingService.getAllInstances(serviceName, group);
            if (!isSubscriptionActive(serviceName, listener, subscription)) {
                return;
            }
            notifyInstances(serviceName, listener, instances);
            LOG.info("NacosRegistry subscribed: {} (group={}), {} URLs", serviceName, group, instances.size());
        } catch (NacosException e) {
            rollbackSubscription(serviceName, listener, subscription);
            throw new RpcRegistryException("Failed to subscribe to service: " + serviceName, e);
        } catch (RuntimeException | Error e) {
            rollbackSubscription(serviceName, listener, subscription);
            throw e;
        }
    }

    private boolean isSubscriptionActive(String serviceName, NotifyListener listener, Subscription subscription) {
        Map<NotifyListener, Subscription> serviceListeners = nacosListeners.get(serviceName);
        return serviceListeners != null && serviceListeners.get(listener) == subscription;
    }

    private void notifyInstances(String serviceName, NotifyListener listener, List<Instance> instances) {
        List<URL> urls = new ArrayList<>();
        for (Instance instance : instances) {
            if (instance.isHealthy() && instance.isEnabled()) {
                urls.add(new URL(
                        "rpcell",
                        instance.getIp(),
                        instance.getPort(),
                        serviceName,
                        instance.getClusterName(),
                        instance.getMetadata() != null ? instance.getMetadata().get("version") : null));
            }
        }
        listener.notify(urls);
        LOG.debug("NacosRegistry notified: {} -> {} URLs", serviceName, urls.size());
    }

    private void rollbackSubscription(String serviceName, NotifyListener listener, Subscription subscription) {
        removeSubscription(serviceName, listener, subscription);
        unsubscribe(serviceName, listener, subscription);
    }

    @Override
    protected void doUnsubscribe(String serviceName, NotifyListener listener) {
        List<Subscription> retired = detachRetiredListeners(serviceName, listener);
        Subscription current = removeSubscription(serviceName, listener, null);
        if (retired != null) {
            for (Subscription subscription : retired) {
                unsubscribe(serviceName, listener, subscription);
            }
        }
        if (current != null) {
            unsubscribe(serviceName, listener, current);
        }
    }

    private Subscription removeSubscription(
            String serviceName, NotifyListener listener, Subscription expectedSubscription) {
        AtomicReference<Subscription> removed = new AtomicReference<>();
        nacosListeners.computeIfPresent(serviceName, (key, serviceListeners) -> {
            Subscription current = serviceListeners.get(listener);
            if (current != null && (expectedSubscription == null || current == expectedSubscription)) {
                serviceListeners.remove(listener);
                removed.set(current);
            }
            return serviceListeners.isEmpty() ? null : serviceListeners;
        });
        return removed.get();
    }

    private void unsubscribe(String serviceName, NotifyListener listener, Subscription subscription) {
        long attempt;
        synchronized (subscription) {
            attempt = ++subscription.unsubscribeAttempts;
        }
        try {
            namingService.unsubscribe(
                    serviceName, config.getGroup() != null ? config.getGroup() : "DEFAULT_GROUP", subscription);
            synchronized (subscription) {
                if (attempt > subscription.completedUnsubscribeAttempt) {
                    subscription.completedUnsubscribeAttempt = attempt;
                    removeRetiredListener(serviceName, listener, subscription);
                }
            }
        } catch (NacosException e) {
            synchronized (subscription) {
                if (attempt > subscription.completedUnsubscribeAttempt) {
                    subscription.completedUnsubscribeAttempt = attempt;
                    retainRetiredListener(serviceName, listener, subscription);
                }
            }
            LOG.warn("Failed to unsubscribe from service: {}", serviceName, e);
        }
    }

    private void retainRetiredListener(String serviceName, NotifyListener listener, Subscription subscription) {
        retiredListeners.compute(serviceName, (key, serviceListeners) -> {
            Map<NotifyListener, CopyOnWriteArrayList<Subscription>> current =
                    serviceListeners != null ? serviceListeners : new ConcurrentHashMap<>();
            current.computeIfAbsent(listener, ignored -> new CopyOnWriteArrayList<>())
                    .addIfAbsent(subscription);
            return current;
        });
    }

    private List<Subscription> detachRetiredListeners(String serviceName, NotifyListener listener) {
        AtomicReference<List<Subscription>> retired = new AtomicReference<>();
        retiredListeners.computeIfPresent(serviceName, (key, serviceListeners) -> {
            retired.set(serviceListeners.remove(listener));
            return serviceListeners.isEmpty() ? null : serviceListeners;
        });
        return retired.get();
    }

    private void removeRetiredListener(String serviceName, NotifyListener listener, Subscription subscription) {
        retiredListeners.computeIfPresent(serviceName, (key, serviceListeners) -> {
            CopyOnWriteArrayList<Subscription> subscriptions = serviceListeners.get(listener);
            if (subscriptions != null) {
                subscriptions.remove(subscription);
                if (subscriptions.isEmpty()) {
                    serviceListeners.remove(listener);
                }
            }
            return serviceListeners.isEmpty() ? null : serviceListeners;
        });
    }

    private final class Subscription implements EventListener {
        private final String serviceName;
        private final NotifyListener listener;
        private long unsubscribeAttempts;
        private long completedUnsubscribeAttempt;

        private Subscription(String serviceName, NotifyListener listener) {
            this.serviceName = serviceName;
            this.listener = listener;
        }

        @Override
        public void onEvent(com.alibaba.nacos.api.naming.listener.Event event) {
            if (!isSubscriptionActive(serviceName, listener, this) || !(event instanceof NamingEvent)) {
                return;
            }
            notifyInstances(serviceName, listener, ((NamingEvent) event).getInstances());
        }
    }

    @Override
    protected void doDestroy() {
        nacosListeners.clear();
        retiredListeners.clear();
        if (namingService != null) {
            try {
                namingService.shutDown();
            } catch (NacosException e) {
                throw new RpcRegistryException("Failed to shut down Nacos naming service", e);
            }
        }
        LOG.info("NacosRegistry destroyed");
    }

    private String buildServiceName(URL url) {
        return url.getServiceName() != null ? url.getServiceName() : "rpcell-service";
    }
}
