package io.rpcell.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.rpcell.api.annotation.RpcReference;
import io.rpcell.api.annotation.RpcService;
import io.rpcell.api.constant.Attachments;
import io.rpcell.api.exception.RpcParamException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.Filter;
import io.rpcell.api.spi.Invoker;
import io.rpcell.api.spi.NotifyListener;
import io.rpcell.api.spi.Registry;
import io.rpcell.registry.AbstractRegistry;
import io.rpcell.registry.RegistryDirectory;
import io.rpcell.server.RpcServerExecutor;
import io.rpcell.spi.ExtensionLoader;
import io.rpcell.transport.ClientInvoker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;

class RpcellAutoConfigurationTest {

    @Test
    void shouldIsolateRegistryDirectoriesPerService() throws Exception {
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());
        ConsumerBean consumerBean = new ConsumerBean();
        autoConfiguration.postProcessAfterInitialization(consumerBean, "consumerBean");

        RecordingRegistry registry = new RecordingRegistry();
        RecordingClientInvoker clientInvoker = new RecordingClientInvoker();
        setField(autoConfiguration, "registry", registry);
        setField(autoConfiguration, "clientInvoker", clientInvoker);
        Method injectReferences = RpcellAutoConfiguration.class.getDeclaredMethod("injectReferences");
        injectReferences.setAccessible(true);
        injectReferences.invoke(autoConfiguration);

        assertNotNull(consumerBean.alphaService);
        assertNotNull(consumerBean.betaService);

        registry.notifyUrls("alpha", new URL("rpcell", "10.0.0.1", 20881, "alpha", null, null));
        registry.notifyUrls("beta", new URL("rpcell", "10.0.0.2", 20882, "beta", null, null));

        assertEquals("ok", consumerBean.alphaService.call());
        assertEquals("ok", consumerBean.betaService.call());
        assertEquals(List.of("10.0.0.1:20881", "10.0.0.2:20882"), clientInvoker.addresses);
    }

    @Test
    void shouldApplyConsumerDefaultsAndRouteByGroupAndVersion() throws Exception {
        RpcellProperties properties = new RpcellProperties();
        properties.getConsumer().setTimeout(1234);
        properties.getConsumer().setRetries(4);
        properties.getConsumer().setGroup("g1");
        properties.getConsumer().setVersion("v1");
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(properties);
        ConsumerBean consumerBean = new ConsumerBean();
        autoConfiguration.postProcessAfterInitialization(consumerBean, "consumerBean");
        RecordingRegistry registry = new RecordingRegistry();
        RecordingClientInvoker clientInvoker = new RecordingClientInvoker();
        setField(autoConfiguration, "registry", registry);
        setField(autoConfiguration, "clientInvoker", clientInvoker);
        Method injectReferences = RpcellAutoConfiguration.class.getDeclaredMethod("injectReferences");
        injectReferences.setAccessible(true);
        injectReferences.invoke(autoConfiguration);
        registry.notifyUrls(
                "alpha",
                new URL("rpcell", "10.0.0.1", 20881, "alpha", "g1", "v1"),
                new URL("rpcell", "10.0.0.2", 20882, "alpha", "g2", "v1"));

        assertEquals("ok", consumerBean.alphaService.call());
        assertEquals(List.of("10.0.0.1:20881"), clientInvoker.addresses);
        assertEquals("1234", clientInvoker.lastInvocation.getAttachments().get(Attachments.TIMEOUT));
        assertEquals("4", clientInvoker.lastInvocation.getAttachments().get(Attachments.RETRIES));
        assertEquals("g1", clientInvoker.lastInvocation.getAttachments().get(Attachments.GROUP));
        assertEquals("v1", clientInvoker.lastInvocation.getAttachments().get(Attachments.VERSION));
    }

    @Test
    void shouldFailWhenReferenceSubscriptionFails() throws Exception {
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());
        autoConfiguration.postProcessAfterInitialization(new ConsumerBean(), "consumerBean");
        RecordingRegistry registry = new RecordingRegistry() {
            @Override
            public void subscribe(String serviceName, NotifyListener listener) {
                throw new RpcParamException("subscribe failed");
            }
        };
        setField(autoConfiguration, "registry", registry);
        setField(autoConfiguration, "clientInvoker", new RecordingClientInvoker());
        Method injectReferences = RpcellAutoConfiguration.class.getDeclaredMethod("injectReferences");
        injectReferences.setAccessible(true);

        Exception error = assertThrows(Exception.class, () -> injectReferences.invoke(autoConfiguration));
        assertSame(RpcParamException.class, error.getCause().getClass());
    }

    @Test
    void shouldRejectUnsupportedSerializationBeforeStartingTransport() throws Exception {
        RpcellProperties properties = new RpcellProperties();
        properties.getApplication().setName("test");
        properties.getProtocol().setSerialization("json");
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(properties);
        Method verifyConfiguration = RpcellAutoConfiguration.class.getDeclaredMethod("verifyConfiguration");
        verifyConfiguration.setAccessible(true);

        Exception error = assertThrows(Exception.class, () -> verifyConfiguration.invoke(autoConfiguration));
        assertSame(RpcParamException.class, error.getCause().getClass());
    }

    @Test
    void shouldReleaseRegisteredResourcesOnDestroy() throws Exception {
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());
        autoConfiguration.postProcessAfterInitialization(new AlphaProvider(), "alphaProvider");
        autoConfiguration.postProcessAfterInitialization(new ConsumerBean(), "consumerBean");
        LifecycleRegistry registry = new LifecycleRegistry();
        setField(autoConfiguration, "registry", registry);
        setField(autoConfiguration, "clientInvoker", new RecordingClientInvoker());
        setField(autoConfiguration, "serverExecutor", new RpcServerExecutor("127.0.0.1", 20880));
        Method registerPendingServices = RpcellAutoConfiguration.class.getDeclaredMethod("registerPendingServices");
        registerPendingServices.setAccessible(true);
        registerPendingServices.invoke(autoConfiguration);
        Method injectReferences = RpcellAutoConfiguration.class.getDeclaredMethod("injectReferences");
        injectReferences.setAccessible(true);
        injectReferences.invoke(autoConfiguration);

        autoConfiguration.destroy();

        assertEquals(List.of("alpha"), registry.unregisteredServices);
        assertTrue(registry.listeners.isEmpty());
        assertTrue(registry.destroyed);
    }

    @Test
    void shouldIgnoreSpringNacosAddressInDirectMode() throws Exception {
        RpcellProperties properties = new RpcellProperties();
        properties.getRegistry().setType("direct");
        properties.getRegistry().setAddress("127.0.0.1:20990");
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(properties);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource(
                            "test", Map.of("spring.cloud.nacos.discovery.server-addr", (Object) "192.0.2.1:8848")));
            context.refresh();
            autoConfiguration.setApplicationContext(context);
            Method initRegistry = RpcellAutoConfiguration.class.getDeclaredMethod("initRegistry");
            initRegistry.setAccessible(true);
            initRegistry.invoke(autoConfiguration);
            Registry registry = (Registry) getField(autoConfiguration, "registry");
            List<URL> notifiedUrls = new ArrayList<>();
            registry.subscribe("alpha", urls -> {
                notifiedUrls.clear();
                notifiedUrls.addAll(urls);
            });

            assertEquals(1, notifiedUrls.size());
            assertEquals("127.0.0.1", notifiedUrls.get(0).getHost());
            assertEquals(20990, notifiedUrls.get(0).getPort());
            autoConfiguration.destroy();
        }
    }

    @Test
    void shouldFailCheckedReferenceWithoutProvider() throws Exception {
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());
        autoConfiguration.postProcessAfterInitialization(new CheckedConsumerBean(), "consumerBean");
        setField(autoConfiguration, "registry", new RecordingRegistry());
        setField(autoConfiguration, "clientInvoker", new RecordingClientInvoker());
        Method injectReferences = RpcellAutoConfiguration.class.getDeclaredMethod("injectReferences");
        injectReferences.setAccessible(true);

        Exception error = assertThrows(Exception.class, () -> injectReferences.invoke(autoConfiguration));
        assertSame(RpcParamException.class, error.getCause().getClass());
    }

    @Test
    void shouldInjectReferenceDeclaredOnSuperclass() throws Exception {
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());
        InheritedConsumerBean consumerBean = new InheritedConsumerBean();
        autoConfiguration.postProcessAfterInitialization(consumerBean, "consumerBean");
        RecordingRegistry registry = new RecordingRegistry();
        setField(autoConfiguration, "registry", registry);
        setField(autoConfiguration, "clientInvoker", new RecordingClientInvoker());
        Method injectReferences = RpcellAutoConfiguration.class.getDeclaredMethod("injectReferences");
        injectReferences.setAccessible(true);
        injectReferences.invoke(autoConfiguration);
        registry.notifyUrls("alpha", new URL("rpcell", "10.0.0.1", 20881, "alpha", null, null));

        assertEquals("ok", ((BaseConsumerBean) consumerBean).alphaService.call());
    }

    @Test
    void shouldApplyReferenceSpecificFilter() throws Exception {
        RecordingFilter.calls.set(0);
        ExtensionLoader.register("filter:reference-test", RecordingFilter.class);
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());
        FilteredConsumerBean consumerBean = new FilteredConsumerBean();
        autoConfiguration.postProcessAfterInitialization(consumerBean, "consumerBean");
        RecordingRegistry registry = new RecordingRegistry();
        setField(autoConfiguration, "registry", registry);
        setField(autoConfiguration, "clientInvoker", new RecordingClientInvoker());
        Method injectReferences = RpcellAutoConfiguration.class.getDeclaredMethod("injectReferences");
        injectReferences.setAccessible(true);
        injectReferences.invoke(autoConfiguration);
        registry.notifyUrls("alpha", new URL("rpcell", "10.0.0.1", 20881, "alpha", null, null));

        assertEquals("ok", consumerBean.alphaService.call());
        assertEquals(1, RecordingFilter.calls.get());
    }

    @Test
    void shouldApplyProviderGroupAndVersionDefaults() throws Exception {
        RpcellProperties properties = new RpcellProperties();
        properties.getProvider().setGroup("provider-group");
        properties.getProvider().setVersion("provider-version");
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(properties);
        autoConfiguration.postProcessAfterInitialization(new AlphaProvider(), "alphaProvider");
        RpcServerExecutor serverExecutor = new RpcServerExecutor("127.0.0.1", 20880);
        setField(autoConfiguration, "serverExecutor", serverExecutor);
        Method registerPendingServices = RpcellAutoConfiguration.class.getDeclaredMethod("registerPendingServices");
        registerPendingServices.setAccessible(true);
        registerPendingServices.invoke(autoConfiguration);

        var descriptor = serverExecutor.getServiceTable().values().iterator().next();
        assertEquals("provider-group", descriptor.getGroup());
        assertEquals("provider-version", descriptor.getVersion());
    }

    @Test
    void shouldApplyGlobalAndServiceSpecificProviderFilters() throws Exception {
        RecordingFilter.calls.set(0);
        ExtensionLoader.register("filter:provider-test", RecordingFilter.class);
        RpcellProperties properties = new RpcellProperties();
        properties.getFilter().getProvider().getNames().add("provider-test");
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(properties);
        autoConfiguration.postProcessAfterInitialization(new FilteredAlphaProvider(), "filteredAlphaProvider");
        RpcServerExecutor serverExecutor = new RpcServerExecutor("127.0.0.1", 20880);
        setField(autoConfiguration, "serverExecutor", serverExecutor);
        Method registerPendingServices = RpcellAutoConfiguration.class.getDeclaredMethod("registerPendingServices");
        registerPendingServices.setAccessible(true);
        registerPendingServices.invoke(autoConfiguration);
        Method buildHandler = RpcellAutoConfiguration.class.getDeclaredMethod("buildProviderRequestHandler");
        buildHandler.setAccessible(true);
        @SuppressWarnings("unchecked")
        Function<Invocation, Result> handler = (Function<Invocation, Result>) buildHandler.invoke(autoConfiguration);
        Invocation invocation = new Invocation();
        invocation.setInterfaceName(FilteredAlphaService.class.getName());
        invocation.setMethodName("call");

        Result result = handler.apply(invocation);

        assertEquals("alpha", result.getValue());
        assertEquals(2, RecordingFilter.calls.get());
    }

    @Test
    void shouldScanProviderInterfaceFromSuperclass() throws Exception {
        AlphaProvider provider = new ProxiedAlphaProvider();

        assertSame(provider, registeredInstance(scanProviders(provider)));
    }

    @Test
    void shouldShareProviderInstanceAcrossRpcInterfacesByDefault() throws Exception {
        MultiInterfaceProvider provider = new MultiInterfaceProvider(new ServiceDependency("shared"));

        RpcServerExecutor executor = scanProviders(provider);

        assertEquals(2, executor.getServiceTable().size());
        assertSame(provider, registeredInstance(executor, AlphaService.class));
        assertSame(provider, registeredInstance(executor, BetaService.class));
    }

    @Test
    void shouldControlIndependentProviderPerRpcServiceAnnotation() throws Exception {
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            ServiceDependency dependency = new ServiceDependency("autowired");
            context.registerBean(ServiceDependency.class, () -> dependency);
            context.refresh();
            autoConfiguration.setApplicationContext(context);
            context.getBeanFactory().addBeanPostProcessor(autoConfiguration);
            RpcServerExecutor serverExecutor = new RpcServerExecutor("127.0.0.1", 20880);
            setField(autoConfiguration, "serverExecutor", serverExecutor);
            IndependentMultiInterfaceProvider source = new IndependentMultiInterfaceProvider(dependency);

            autoConfiguration.postProcessAfterInitialization(source, "independentMultiInterfaceProvider");

            assertEquals(3, serverExecutor.getServiceTable().size());
            IndependentMultiInterfaceProvider alpha = (IndependentMultiInterfaceProvider)
                    registeredInstance(serverExecutor, IndependentAlphaService.class);
            IndependentMultiInterfaceProvider independentBeta = (IndependentMultiInterfaceProvider)
                    registeredInstance(serverExecutor, IndependentBetaService.class);
            IndependentMultiInterfaceProvider sharedBeta =
                    (IndependentMultiInterfaceProvider) registeredInstance(serverExecutor, BetaService.class);
            assertNotSame(source, alpha);
            assertNotSame(source, independentBeta);
            assertNotSame(alpha, independentBeta);
            assertSame(source, sharedBeta);
            assertEquals("autowired", alpha.call());
            assertEquals("autowired", independentBeta.call());
            assertEquals("autowired", sharedBeta.call());

            autoConfiguration.destroy();

            assertTrue(alpha.destroyed);
            assertTrue(independentBeta.destroyed);
        }
    }

    @Test
    void shouldRejectMultiInstanceWithoutApplicationContext() {
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());

        RpcParamException exception = assertThrows(
                RpcParamException.class,
                () -> autoConfiguration.postProcessAfterInitialization(
                        new IndependentAlphaProvider(), "independentAlphaProvider"));

        assertTrue(exception.getCause().getMessage().contains("ApplicationContext is required"));
    }

    @Test
    void shouldRejectJdkProxyWhenMultiInstanceEnabled() {
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            autoConfiguration.setApplicationContext(context);
            Object provider = Proxy.newProxyInstance(
                    IndependentAlphaService.class.getClassLoader(),
                    new Class<?>[] {IndependentAlphaService.class},
                    (instance, method, args) -> "independent");

            RpcParamException exception = assertThrows(
                    RpcParamException.class,
                    () -> autoConfiguration.postProcessAfterInitialization(provider, "independentAlphaProxy"));

            assertTrue(exception.getCause().getMessage().contains("concrete implementation class"));
        }
    }

    @Test
    void shouldAllowSameServiceNameWithDifferentVersions() throws Exception {
        RpcServerExecutor executor = scanProviders(new VersionOneProvider(), new VersionTwoProvider());

        assertEquals(2, executor.getServiceTable().size());
    }

    @Test
    void shouldHonorRegisterAndExportFlags() throws Exception {
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());
        autoConfiguration.postProcessAfterInitialization(new LocalOnlyProvider(), "localOnlyProvider");
        LifecycleRegistry registry = new LifecycleRegistry();
        RpcServerExecutor serverExecutor = new RpcServerExecutor("127.0.0.1", 20880);
        setField(autoConfiguration, "registry", registry);
        setField(autoConfiguration, "serverExecutor", serverExecutor);
        Method registerPendingServices = RpcellAutoConfiguration.class.getDeclaredMethod("registerPendingServices");
        registerPendingServices.setAccessible(true);
        registerPendingServices.invoke(autoConfiguration);

        assertTrue(registry.registeredServices.isEmpty());
        assertEquals(1, serverExecutor.getServiceTable().size());
        assertTrue(scanProviders(new NonExportedProvider()).getServiceTable().isEmpty());
    }

    @Test
    void shouldPreferPrimaryProviderRegardlessOfScanOrder() throws Exception {
        PriorityService primary = new PrimaryPriorityService();
        PriorityService regular = new RegularPriorityService();

        assertSame(primary, registeredInstance(scanProviders(regular, primary)));
        assertSame(primary, registeredInstance(scanProviders(primary, regular)));
    }

    @Test
    void shouldRejectDuplicateProvidersWithoutPrimaryPriority() {
        assertThrows(
                RpcParamException.class,
                () -> scanProviders(new RegularPriorityService(), new RegularPriorityService()));
    }

    private static RpcServerExecutor scanProviders(Object... providers) throws Exception {
        RpcellAutoConfiguration autoConfiguration = new RpcellAutoConfiguration(new RpcellProperties());
        RpcServerExecutor serverExecutor = new RpcServerExecutor("127.0.0.1", 20880);
        setField(autoConfiguration, "serverExecutor", serverExecutor);
        for (int i = 0; i < providers.length; i++) {
            autoConfiguration.postProcessAfterInitialization(providers[i], "provider-" + i);
        }
        return serverExecutor;
    }

    private static Object registeredInstance(RpcServerExecutor serverExecutor) {
        return serverExecutor.getServiceTable().values().iterator().next().getInstance();
    }

    private static Object registeredInstance(RpcServerExecutor serverExecutor, Class<?> interfaceClass) {
        return serverExecutor.getServiceTable().values().stream()
                .filter(descriptor -> descriptor.getInterfaceClass().equals(interfaceClass))
                .findFirst()
                .orElseThrow()
                .getInstance();
    }

    private static void setField(RpcellAutoConfiguration target, String name, Object value) throws Exception {
        Field field = RpcellAutoConfiguration.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(RpcellAutoConfiguration target, String name) throws Exception {
        Field field = RpcellAutoConfiguration.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @RpcService("alpha")
    interface AlphaService {
        String call();
    }

    @RpcService(value = "filtered-alpha", filter = "provider-test")
    public interface FilteredAlphaService {
        String call();
    }

    @RpcService("beta")
    interface BetaService {
        String call();
    }

    @RpcService(value = "independent-alpha", multiInstance = true)
    interface IndependentAlphaService {
        String call();
    }

    @RpcService(value = "independent-beta", multiInstance = true)
    interface IndependentBetaService {
        String call();
    }

    @RpcService("priority")
    interface PriorityService {
        String call();
    }

    @RpcService(value = "versioned", version = "1.0")
    interface VersionOneService {
        String call();
    }

    @RpcService(value = "versioned", version = "2.0")
    interface VersionTwoService {
        String call();
    }

    @RpcService(value = "local-only", register = false)
    interface LocalOnlyService {
        String call();
    }

    @RpcService(value = "not-exported", export = false)
    interface NonExportedService {
        String call();
    }

    private static class AlphaProvider implements AlphaService {
        @Override
        public String call() {
            return "alpha";
        }
    }

    private static class FilteredAlphaProvider implements FilteredAlphaService {
        @Override
        public String call() {
            return "alpha";
        }
    }

    private static class ProxiedAlphaProvider extends AlphaProvider {}

    private static class IndependentAlphaProvider implements IndependentAlphaService {
        @Override
        public String call() {
            return "independent";
        }
    }

    private static class ServiceDependency {
        private final String value;

        private ServiceDependency(String value) {
            this.value = value;
        }
    }

    private static class MultiInterfaceProvider implements AlphaService, BetaService, DisposableBean {
        private final ServiceDependency dependency;
        private boolean destroyed;

        private MultiInterfaceProvider(ServiceDependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public String call() {
            return dependency.value;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }

    private static class IndependentMultiInterfaceProvider
            implements IndependentAlphaService, IndependentBetaService, BetaService, DisposableBean {
        private final ServiceDependency dependency;
        private boolean destroyed;

        private IndependentMultiInterfaceProvider(ServiceDependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public String call() {
            return dependency.value;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }

    private static class VersionOneProvider implements VersionOneService {
        @Override
        public String call() {
            return "v1";
        }
    }

    private static class VersionTwoProvider implements VersionTwoService {
        @Override
        public String call() {
            return "v2";
        }
    }

    private static class LocalOnlyProvider implements LocalOnlyService {
        @Override
        public String call() {
            return "local";
        }
    }

    private static class NonExportedProvider implements NonExportedService {
        @Override
        public String call() {
            return "hidden";
        }
    }

    private static class RegularPriorityService implements PriorityService {
        @Override
        public String call() {
            return "regular";
        }
    }

    @Primary
    private static class PrimaryPriorityService implements PriorityService {
        @Override
        public String call() {
            return "primary";
        }
    }

    private static class ConsumerBean {
        @RpcReference
        private AlphaService alphaService;

        @RpcReference
        private BetaService betaService;
    }

    private static class BaseConsumerBean {
        @RpcReference
        private AlphaService alphaService;
    }

    private static class InheritedConsumerBean extends BaseConsumerBean {}

    private static class FilteredConsumerBean {
        @RpcReference(filter = "reference-test")
        private AlphaService alphaService;
    }

    private static class CheckedConsumerBean {
        @RpcReference(check = true)
        private AlphaService alphaService;
    }

    private static class RecordingRegistry implements Registry {
        private final Map<String, NotifyListener> listeners = new HashMap<>();

        @Override
        public void register(URL url) {}

        @Override
        public void unregister(URL url) {}

        @Override
        public void subscribe(String serviceName, NotifyListener listener) {
            listeners.put(serviceName, listener);
        }

        @Override
        public void unsubscribe(String serviceName, NotifyListener listener) {
            listeners.remove(serviceName);
        }

        void notifyUrls(String serviceName, URL... urls) {
            listeners.get(serviceName).notify(List.of(urls));
        }
    }

    private static class LifecycleRegistry extends AbstractRegistry {
        private final Map<String, NotifyListener> listeners = new HashMap<>();
        private final List<String> registeredServices = new ArrayList<>();
        private final List<String> unregisteredServices = new ArrayList<>();
        private boolean destroyed;

        LifecycleRegistry() {
            super(new RegistryDirectory());
            init();
        }

        @Override
        protected void doInit() {}

        @Override
        protected void doRegister(URL url) {
            registeredServices.add(url.getServiceName());
        }

        @Override
        protected void doUnregister(URL url) {
            unregisteredServices.add(url.getServiceName());
        }

        @Override
        protected void doSubscribe(String serviceName, NotifyListener listener) {
            listeners.put(serviceName, listener);
        }

        @Override
        protected void doUnsubscribe(String serviceName, NotifyListener listener) {
            listeners.remove(serviceName, listener);
        }

        @Override
        protected void doDestroy() {
            destroyed = true;
        }
    }

    public static class RecordingFilter implements Filter {
        private static final AtomicInteger calls = new AtomicInteger();

        @Override
        public Result invoke(Invoker next, Invocation invocation) {
            calls.incrementAndGet();
            return next.invoke(invocation);
        }
    }

    private static class RecordingClientInvoker extends ClientInvoker {
        private final List<String> addresses = new ArrayList<>();
        private Invocation lastInvocation;

        RecordingClientInvoker() {
            super(null);
        }

        @Override
        public Result invoke(Invocation invocation, URL targetUrl) {
            lastInvocation = invocation;
            addresses.add(targetUrl.toAddress());
            return Result.success("ok");
        }
    }
}
