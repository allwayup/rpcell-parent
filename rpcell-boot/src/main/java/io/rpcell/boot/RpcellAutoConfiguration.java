package io.rpcell.boot;

import io.rpcell.api.annotation.RpcReference;
import io.rpcell.api.annotation.RpcService;
import io.rpcell.api.constant.Attachments;
import io.rpcell.api.constant.Extensions;
import io.rpcell.api.constant.RegistryTypes;
import io.rpcell.api.exception.RpcParamException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.RegistryConfig;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.SerializerConfig;
import io.rpcell.api.model.ServiceDescriptor;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.Filter;
import io.rpcell.api.spi.Invoker;
import io.rpcell.api.spi.Registry;
import io.rpcell.cluster.FailoverCluster;
import io.rpcell.cluster.RandomLoadBalance;
import io.rpcell.cluster.RoundRobinLoadBalance;
import io.rpcell.codec.RpcCellProtocol;
import io.rpcell.common.ServiceDescriptorBuilder;
import io.rpcell.filter.FilterChain;
import io.rpcell.filter.FilterLoader;
import io.rpcell.proxy.RpcProxyFactory;
import io.rpcell.proxy.RpcProxyFactory.CallMetadata;
import io.rpcell.registry.AbstractRegistry;
import io.rpcell.registry.DirectRegistry;
import io.rpcell.registry.RegistryDirectory;
import io.rpcell.registry.nacos.NacosRegistryFactory;
import io.rpcell.serializer.SerializerManager;
import io.rpcell.serializer.msgpack.MessagePackSerializerFactory;
import io.rpcell.server.RpcServerExecutor;
import io.rpcell.spi.ExtensionLoader;
import io.rpcell.transport.ClientInvoker;
import io.rpcell.transport.RpcNettyClient;
import io.rpcell.transport.RpcNettyServer;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.ClassUtils;

/**
 * RPCell auto-configuration: SPI warmup, provider exposure, consumer proxy injection,
 * registry center selection, and startup orchestration.
 */
@AutoConfiguration
@EnableConfigurationProperties(RpcellProperties.class)
public class RpcellAutoConfiguration
        implements ApplicationListener<ContextRefreshedEvent>,
                ApplicationContextAware,
                BeanPostProcessor,
                DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(RpcellAutoConfiguration.class);

    private final RpcellProperties properties;
    private ApplicationContext applicationContext;
    private RpcServerExecutor serverExecutor;
    private RpcNettyServer nettyServer;
    private RpcNettyClient nettyClient;
    private Registry registry;
    private ClientInvoker clientInvoker;
    private final List<RpcReferenceMetadata> referenceFields = new ArrayList<>();
    private final List<ServiceDescriptor> pendingServices = new ArrayList<>();
    private final List<URL> registeredUrls = new ArrayList<>();
    private final List<RegistrySubscription> subscriptions = new ArrayList<>();
    private final ThreadLocal<Class<?>> creatingServiceType = new ThreadLocal<>();
    private final Set<Object> managedServiceInstances =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    public RpcellAutoConfiguration(RpcellProperties properties) {
        this.properties = properties;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    // ── Provider / Consumer annotation scanning ──────────────────────────────

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> creatingType = creatingServiceType.get();
        if (creatingType == null || !creatingType.equals(ClassUtils.getUserClass(bean))) {
            scanProviderBeans(bean);
        }
        scanConsumerFields(bean);
        return bean;
    }

    private void scanProviderBeans(Object bean) {
        for (Class<?> iface : ClassUtils.getAllInterfaces(bean)) {
            if (iface.isAnnotationPresent(RpcService.class)) {
                if (!properties.getProvider().isExport()) {
                    continue;
                }
                try {
                    RpcService serviceAnn = iface.getAnnotation(RpcService.class);
                    if (!serviceAnn.export()) {
                        continue;
                    }
                    Object serviceInstance =
                            serviceAnn.multiInstance() ? createManagedServiceInstance(bean, iface) : bean;
                    ServiceDescriptor descriptor = ServiceDescriptorBuilder.build(iface, serviceInstance);
                    applyProviderDefaults(descriptor, serviceAnn);

                    ServiceDescriptor existing = findPendingByServiceKey(descriptor.toServiceKey());
                    boolean newIsPrimary = ClassUtils.getUserClass(bean).isAnnotationPresent(Primary.class);
                    if (existing != null) {
                        boolean existingIsPrimary = existing.getInstance() != null
                                && ClassUtils.getUserClass(existing.getInstance())
                                        .isAnnotationPresent(Primary.class);
                        if (existingIsPrimary == newIsPrimary) {
                            throw new RpcParamException("Multiple beans for RPC service " + descriptor.getServiceName()
                                    + " without distinct @Primary priority");
                        }
                        if (existingIsPrimary) {
                            LOG.debug(
                                    "Keeping existing @Primary implementation of RPC service {}",
                                    descriptor.getServiceName());
                            continue;
                        }
                        pendingServices.remove(existing);
                        if (serverExecutor != null) {
                            serverExecutor.replaceService(existing, descriptor);
                        }
                        pendingServices.add(descriptor);
                        LOG.info(
                                "RPC service {}: replaced non-@Primary implementation with @Primary implementation",
                                descriptor.getServiceName());
                        continue;
                    }

                    pendingServices.add(descriptor);
                    if (serverExecutor != null) {
                        serverExecutor.registerService(descriptor, true);
                    }
                } catch (Exception e) {
                    LOG.error("Failed to export service: {}", iface.getName(), e);
                    throw new RpcParamException("Failed to export service: " + iface.getName(), e);
                }
            }
        }
    }

    private Object createManagedServiceInstance(Object sourceBean, Class<?> iface) {
        if (applicationContext == null) {
            throw new RpcParamException(
                    "ApplicationContext is required when @RpcService(multiInstance = true) is used");
        }
        Class<?> implementationClass = ClassUtils.getUserClass(sourceBean);
        if (Proxy.isProxyClass(implementationClass)) {
            throw new RpcParamException(
                    "Multi-instance RPC services require a concrete implementation class: " + iface.getName());
        }

        creatingServiceType.set(implementationClass);
        try {
            Object instance = applicationContext.getAutowireCapableBeanFactory().createBean(implementationClass);
            managedServiceInstances.add(instance);
            LOG.debug(
                    "Created independent RPC service instance {} for {}",
                    implementationClass.getName(),
                    iface.getName());
            return instance;
        } catch (Exception e) {
            throw new RpcParamException("Failed to create independent RPC service instance for " + iface.getName(), e);
        } finally {
            creatingServiceType.remove();
        }
    }

    private ServiceDescriptor findPendingByServiceKey(String serviceKey) {
        for (ServiceDescriptor descriptor : pendingServices) {
            if (descriptor.toServiceKey().equals(serviceKey)) {
                return descriptor;
            }
        }
        return null;
    }

    private void scanConsumerFields(Object bean) {
        Object targetBean = AopProxyUtils.getSingletonTarget(bean);
        if (targetBean == null) {
            targetBean = bean;
        }
        for (Class<?> current = targetBean.getClass();
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(RpcReference.class)) {
                    RpcReference ref = field.getAnnotation(RpcReference.class);
                    Class<?> iface = field.getType();
                    if (!iface.isInterface()) {
                        LOG.warn(
                                "@RpcReference field type must be an interface: {} in {}",
                                field.getName(),
                                targetBean.getClass().getName());
                        continue;
                    }
                    RpcService serviceAnn = iface.getAnnotation(RpcService.class);
                    if (serviceAnn == null) {
                        continue;
                    }
                    String serviceName = serviceAnn.value().isEmpty() ? iface.getName() : serviceAnn.value();

                    String effectiveGroup = orElse(
                            ref.group(),
                            serviceAnn.group(),
                            properties.getConsumer().getGroup());
                    String effectiveVersion = orElse(
                            ref.version(),
                            serviceAnn.version(),
                            properties.getConsumer().getVersion());

                    CallMetadata callMeta = buildCallMetadata(ref, effectiveGroup, effectiveVersion);
                    referenceFields.add(new RpcReferenceMetadata(
                            targetBean, field, iface, effectiveGroup, effectiveVersion, serviceName, callMeta));
                }
            }
        }
    }

    // ── Annotation → descriptor / call-metadata bridge ──────────────────────

    private CallMetadata buildCallMetadata(RpcReference ref, String group, String version) {
        RpcellProperties.Consumer consumer = properties.getConsumer();
        return CallMetadata.builder()
                .timeout(ref.timeout() >= 0 ? ref.timeout() : consumer.getTimeout())
                .retries(ref.retries() >= 0 ? ref.retries() : consumer.getRetries())
                .group(orEmpty(group))
                .version(orEmpty(version))
                .loadbalance(orElse(
                        ref.loadbalance(),
                        consumer.getLoadbalance(),
                        properties.getCluster().getLoadbalance()))
                .check(ref.check() || consumer.isCheck())
                .filter(
                        ref.filter().length > 0
                                ? ref.filter()
                                : consumer.getFilter().toArray(new String[0]))
                .build();
    }

    private void applyProviderDefaults(ServiceDescriptor descriptor, RpcService ann) {
        ifEmpty(
                descriptor.getGroup(),
                () -> descriptor.setGroup(orElse(
                        propertyOrDefault(ann.group(), ""),
                        properties.getProvider().getGroup(),
                        null)));
        ifEmpty(
                descriptor.getVersion(),
                () -> descriptor.setVersion(orElse(
                        propertyOrDefault(ann.version(), ""),
                        properties.getProvider().getVersion(),
                        null)));

        if (ann.timeout() >= 0) {
            descriptor.setTimeout(ann.timeout());
        } else {
            descriptor.setTimeout(properties.getProvider().getTimeout());
        }
        if (ann.retries() >= 0) {
            descriptor.setRetries(ann.retries());
        } else {
            descriptor.setRetries(properties.getCluster().getRetries());
        }
        descriptor.setRegister(ann.register() && properties.getProvider().isRegister());
        if (ann.filter().length > 0) {
            descriptor.setFilter(ann.filter());
        } else if (!properties.getProvider().getFilter().isEmpty()) {
            descriptor.setFilter(properties.getProvider().getFilter().toArray(new String[0]));
        }
    }

    // ── Startup orchestration ────────────────────────────────────────────────

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!properties.isEnabled()) {
            LOG.info("RPCell is disabled");
            return;
        }

        if (event.getApplicationContext() != applicationContext) {
            return;
        }

        try {
            verifyConfiguration();
            warmupSpi();
            initServer();
            initRegistry();
            registerPendingServices();
            injectReferences();

            LOG.info(
                    "RPCell framework started successfully (registry type: {})",
                    properties.getRegistry().getType());
        } catch (RuntimeException | Error e) {
            destroy();
            throw e;
        }
    }

    private void verifyConfiguration() {
        String appName = resolveAppName();
        if (appName == null || appName.isEmpty()) {
            throw new RpcParamException("rpcell.application.name is required");
        }

        if (!pendingServices.isEmpty()) {
            int port = properties.getTransport().getPort();
            if (port <= 0 || port > 65535) {
                throw new RpcParamException("rpcell.transport.port must be between 1 and 65535");
            }
        }

        int retries = properties.getCluster().getRetries();
        if (retries < 0) {
            throw new RpcParamException("rpcell.cluster.retries must be >= 0");
        }

        String serialization = properties.getProtocol().getSerialization();
        if (!"msgpack".equals(serialization)) {
            throw new RpcParamException(
                    "rpcell.protocol.serialization currently supports only 'msgpack', got: " + serialization);
        }

        String regType = properties.getRegistry().getType();
        if (regType == null || (!RegistryTypes.NACOS.equals(regType) && !RegistryTypes.DIRECT.equals(regType))) {
            throw new RpcParamException("rpcell.registry.type must be 'nacos' or 'direct', got: " + regType);
        }
    }

    private void warmupSpi() {
        ExtensionLoader.register(Extensions.SERIALIZER_MSGPACK, MessagePackSerializerFactory.class);
        ExtensionLoader.register(Extensions.LOADBALANCE_RANDOM, RandomLoadBalance.class);
        ExtensionLoader.register(Extensions.LOADBALANCE_ROUNDROBIN, RoundRobinLoadBalance.class);

        SerializerConfig config = new SerializerConfig();
        config.setType(properties.getProtocol().getSerialization());
        config.setAllowJdkTypes(properties.getSecurity().getSerialization().isAllowJdk());
        String whitelist = properties.getSecurity().getSerialization().getWhitelist();
        if (whitelist != null && !whitelist.isBlank()) {
            config.setWhitelist(Arrays.stream(whitelist.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList());
        }
        SerializerManager.init(config);
    }

    private void initServer() {
        RpcCellProtocol protocol = new RpcCellProtocol();

        if (!referenceFields.isEmpty()) {
            nettyClient = new RpcNettyClient(
                    protocol,
                    properties.getTransport().getConnectTimeout(),
                    properties.getTransport().getReadTimeout());
            clientInvoker = new ClientInvoker(nettyClient);
            LOG.info("RPC client initialized ({} consumer references)", referenceFields.size());
        }

        if (!pendingServices.isEmpty()) {
            serverExecutor = new RpcServerExecutor(
                    properties.getTransport().getHost(),
                    properties.getTransport().getPort());
            nettyServer = new RpcNettyServer(
                    protocol,
                    properties.getTransport().getHost(),
                    properties.getTransport().getPort(),
                    properties.getTransport().getThreads());

            nettyServer.setRequestHandler(buildProviderRequestHandler());

            try {
                nettyServer.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RpcParamException("Failed to start RPC server", e);
            }
        }
    }

    private Function<Invocation, Result> buildProviderRequestHandler() {
        List<Filter> providerFilters =
                FilterLoader.load(properties.getFilter().getProvider().getNames());
        Map<String, Invoker> providerInvokers = new HashMap<>();
        for (ServiceDescriptor descriptor : pendingServices) {
            List<Filter> serviceFilters = new ArrayList<>(providerFilters);
            serviceFilters.addAll(FilterLoader.load(descriptor.getFilter()));
            providerInvokers.put(
                    descriptor.toServiceKey(), FilterChain.buildFilterChain(serverExecutor::execute, serviceFilters));
        }
        Invoker defaultInvoker = FilterChain.buildFilterChain(serverExecutor::execute, providerFilters);
        return invocation -> {
            ServiceDescriptor descriptor = serverExecutor.findService(invocation);
            Invoker serviceInvoker = descriptor != null ? providerInvokers.get(descriptor.toServiceKey()) : null;
            return (serviceInvoker != null ? serviceInvoker : defaultInvoker).invoke(invocation);
        };
    }

    private void initRegistry() {
        String regType = properties.getRegistry().getType();
        RegistryConfig config = new RegistryConfig();
        config.setType(regType);

        String address =
                RegistryTypes.DIRECT.equals(regType) ? properties.getRegistry().getAddress() : resolveNacosAddress();
        config.setAddress(address);

        String username = resolveNacosUsername();
        config.setUsername(username);

        String password = resolveNacosPassword();
        config.setPassword(password);

        Map<String, String> params = new HashMap<>();
        params.put(
                "namespace",
                resolveProperty(
                        "spring.cloud.nacos.discovery.namespace",
                        properties.getRegistry().getNamespace()));
        params.put(
                "group",
                resolveProperty(
                        "spring.cloud.nacos.discovery.group",
                        properties.getRegistry().getGroup()));
        config.setParams(params);

        if (RegistryTypes.DIRECT.equals(regType)) {
            DirectRegistry.DirectRegistryFactory factory = new DirectRegistry.DirectRegistryFactory();
            registry = factory.create(config);
        } else {
            NacosRegistryFactory factory = new NacosRegistryFactory();
            registry = factory.create(config);
        }
    }

    private String resolveNacosAddress() {
        String springAddr = applicationContext.getEnvironment().getProperty("spring.cloud.nacos.discovery.server-addr");
        if (springAddr != null && !springAddr.isEmpty()) {
            return springAddr;
        }
        return properties.getRegistry().getAddress();
    }

    private String resolveNacosUsername() {
        String springUser = applicationContext.getEnvironment().getProperty("spring.cloud.nacos.discovery.username");
        if (springUser != null && !springUser.isEmpty()) {
            return springUser;
        }
        return properties.getRegistry().getUsername();
    }

    private String resolveNacosPassword() {
        String springPass = applicationContext.getEnvironment().getProperty("spring.cloud.nacos.discovery.password");
        if (springPass != null && !springPass.isEmpty()) {
            return springPass;
        }
        return properties.getRegistry().getPassword();
    }

    private String resolveProperty(String springKey, String fallback) {
        String value = applicationContext.getEnvironment().getProperty(springKey);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return fallback;
    }

    private void injectReferences() {
        List<Filter> consumerFilters =
                FilterLoader.load(properties.getFilter().getConsumer().getNames());
        for (RpcReferenceMetadata meta : referenceFields) {
            try {
                RegistryDirectory serviceDirectory = new RegistryDirectory();
                FailoverCluster serviceCluster = new FailoverCluster(
                        serviceDirectory,
                        new RandomLoadBalance(),
                        clientInvoker,
                        properties.getCluster().getRetries());
                List<Filter> referenceFilters = new ArrayList<>(consumerFilters);
                referenceFilters.addAll(FilterLoader.load(meta.callMetadata.getFilter()));
                Invoker invoker = FilterChain.buildFilterChain(serviceCluster, referenceFilters);

                Object proxy = RpcProxyFactory.createProxy(meta.interfaceClass, invoker, meta.callMetadata);
                meta.field.setAccessible(true);
                meta.field.set(meta.bean, proxy);

                LOG.info(
                        "Injected RPC proxy for {} into {}.{}",
                        meta.interfaceClass.getName(),
                        meta.bean.getClass().getSimpleName(),
                        meta.field.getName());

                if (registry != null) {
                    registry.subscribe(meta.serviceName, serviceDirectory);
                    subscriptions.add(new RegistrySubscription(meta.serviceName, serviceDirectory));
                }
                if (meta.callMetadata.isCheck()) {
                    Invocation checkInvocation = new Invocation();
                    checkInvocation.setInterfaceName(meta.interfaceClass.getName());
                    if (meta.group != null) {
                        checkInvocation.getAttachments().put(Attachments.GROUP, meta.group);
                    }
                    if (meta.version != null) {
                        checkInvocation.getAttachments().put(Attachments.VERSION, meta.version);
                    }
                    if (serviceDirectory.list(checkInvocation).isEmpty()) {
                        throw new RpcParamException("No available provider for " + meta.serviceName);
                    }
                }
            } catch (Exception e) {
                throw new RpcParamException(
                        "Failed to inject @RpcReference for " + meta.field.getName() + " in "
                                + meta.bean.getClass().getName(),
                        e);
            }
        }
    }

    private String resolveAppName() {
        String name = properties.getApplication().getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return applicationContext.getEnvironment().getProperty("spring.application.name");
    }

    /**
     * Register all pending services with the server executor and export URLs to the registry.
     */
    private void registerPendingServices() {
        if (serverExecutor == null) {
            LOG.warn("Server executor not initialized, skipping service registration");
            return;
        }
        for (ServiceDescriptor descriptor : pendingServices) {
            serverExecutor.registerService(descriptor, true);
            if (registry != null && descriptor.isRegister()) {
                URL url = new URL(
                        "rpcell",
                        properties.getTransport().getHost(),
                        properties.getTransport().getPort(),
                        descriptor.getServiceName(),
                        descriptor.getGroup(),
                        descriptor.getVersion());
                registry.register(url);
                registeredUrls.add(url);
                LOG.info(
                        "Exported service {} to registry at {}:{}",
                        descriptor.getServiceName(),
                        properties.getTransport().getHost(),
                        properties.getTransport().getPort());
            }
        }
    }

    @Override
    public synchronized void destroy() {
        if (registry != null) {
            for (RegistrySubscription subscription : subscriptions) {
                try {
                    registry.unsubscribe(subscription.serviceName, subscription.listener);
                } catch (Exception e) {
                    LOG.warn("Failed to unsubscribe from service: {}", subscription.serviceName, e);
                }
            }
            for (URL url : registeredUrls) {
                try {
                    registry.unregister(url);
                } catch (Exception e) {
                    LOG.warn("Failed to unregister service: {}", url.toServiceKey(), e);
                }
            }
            if (registry instanceof AbstractRegistry) {
                ((AbstractRegistry) registry).destroy();
            }
        }
        subscriptions.clear();
        registeredUrls.clear();

        if (nettyServer != null) {
            try {
                nettyServer.shutdown();
            } catch (Exception e) {
                LOG.warn("Failed to shut down RPC server", e);
            }
        }
        if (nettyClient != null) {
            try {
                nettyClient.shutdown();
            } catch (Exception e) {
                LOG.warn("Failed to shut down RPC client", e);
            }
        }
        destroyManagedServiceInstances();
    }

    private void destroyManagedServiceInstances() {
        List<Object> instances;
        synchronized (managedServiceInstances) {
            instances = new ArrayList<>(managedServiceInstances);
            managedServiceInstances.clear();
        }
        if (applicationContext == null) {
            return;
        }
        AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
        for (Object instance : instances) {
            try {
                beanFactory.destroyBean(instance);
            } catch (Exception e) {
                LOG.warn("Failed to destroy independent RPC service instance", e);
            }
        }
    }

    // ── Static helpers ───────────────────────────────────────────────────────

    private static String orEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static String orElse(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isEmpty()) {
            return primary;
        }
        if (secondary != null && !secondary.isEmpty()) {
            return secondary;
        }
        return fallback;
    }

    private static String propertyOrDefault(String value, String fallback) {
        return (value != null && !value.isEmpty()) ? value : fallback;
    }

    private static void ifEmpty(String value, Runnable action) {
        if (value == null || value.isEmpty()) {
            action.run();
        }
    }

    private static class RegistrySubscription {
        final String serviceName;
        final RegistryDirectory listener;

        RegistrySubscription(String serviceName, RegistryDirectory listener) {
            this.serviceName = serviceName;
            this.listener = listener;
        }
    }

    /**
     * Metadata for @RpcReference fields pending injection.
     */
    private static class RpcReferenceMetadata {
        final Object bean;
        final Field field;
        final Class<?> interfaceClass;
        final String group;
        final String version;
        final String serviceName;
        final CallMetadata callMetadata;

        RpcReferenceMetadata(
                Object bean,
                Field field,
                Class<?> interfaceClass,
                String group,
                String version,
                String serviceName,
                CallMetadata callMetadata) {
            this.bean = bean;
            this.field = field;
            this.interfaceClass = interfaceClass;
            this.group = group;
            this.version = version;
            this.serviceName = serviceName;
            this.callMetadata = callMetadata;
        }
    }
}
