package io.rpcell.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.rpcell.api.exception.RpcRegistryException;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.NotifyListener;
import io.rpcell.registry.AbstractRegistry;
import io.rpcell.registry.RegistryDirectory;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NacosRegistryTest {

    @Test
    void shouldPreserveVersionMetadataDuringRegistrationAndSubscription() throws Exception {
        AtomicReference<Instance> registeredInstance = new AtomicReference<>();
        AtomicReference<List<URL>> notifiedUrls = new AtomicReference<>();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("registerInstance".equals(method.getName())) {
                        registeredInstance.set((Instance) args[2]);
                        return null;
                    }
                    if ("subscribe".equals(method.getName())) {
                        return null;
                    }
                    if ("getAllInstances".equals(method.getName())) {
                        return List.of(registeredInstance.get());
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field field = NacosRegistry.class.getDeclaredField("namingService");
        field.setAccessible(true);
        field.set(registry, namingService);

        registry.doRegister(new URL("rpcell", "127.0.0.1", 20880, "TestService", "providers", "2.0"));
        registry.doSubscribe("TestService", notifiedUrls::set);

        assertEquals("2.0", registeredInstance.get().getMetadata().get("version"));
        assertEquals("2.0", notifiedUrls.get().get(0).getVersion());
        assertEquals("providers", notifiedUrls.get().get(0).getGroup());
    }

    @Test
    void shouldDeregisterFromOriginalCluster() throws Exception {
        AtomicReference<Object[]> deregistrationArgs = new AtomicReference<>();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("deregisterInstance".equals(method.getName())) {
                        deregistrationArgs.set(args.clone());
                        return null;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field field = NacosRegistry.class.getDeclaredField("namingService");
        field.setAccessible(true);
        field.set(registry, namingService);

        registry.doUnregister(new URL("rpcell", "127.0.0.1", 20880, "TestService", "providers", "2.0"));

        assertEquals(5, deregistrationArgs.get().length);
        assertEquals("providers", deregistrationArgs.get()[4]);
    }

    @Test
    void shouldUnsubscribeWithOriginalNacosListener() throws Exception {
        AtomicReference<EventListener> subscribedListener = new AtomicReference<>();
        AtomicReference<EventListener> unsubscribedListener = new AtomicReference<>();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("subscribe".equals(method.getName())) {
                        subscribedListener.set((EventListener) args[2]);
                        return null;
                    }
                    if ("unsubscribe".equals(method.getName())) {
                        unsubscribedListener.set((EventListener) args[2]);
                        return null;
                    }
                    if ("getAllInstances".equals(method.getName())) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field field = NacosRegistry.class.getDeclaredField("namingService");
        field.setAccessible(true);
        field.set(registry, namingService);
        NotifyListener listener = urls -> {};

        registry.doSubscribe("TestService", listener);
        registry.doUnsubscribe("TestService", listener);

        assertNotNull(subscribedListener.get());
        assertSame(subscribedListener.get(), unsubscribedListener.get());
    }

    @Test
    void shouldIgnoreEventsAfterUnsubscribeFails() throws Exception {
        AtomicReference<EventListener> subscribedListener = new AtomicReference<>();
        AtomicInteger notifications = new AtomicInteger();
        AtomicInteger unsubscribeAttempts = new AtomicInteger();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("subscribe".equals(method.getName())) {
                        subscribedListener.set((EventListener) args[2]);
                        return null;
                    }
                    if ("unsubscribe".equals(method.getName())) {
                        if (unsubscribeAttempts.incrementAndGet() == 1) {
                            throw new NacosException(NacosException.SERVER_ERROR, "unsubscribe failed");
                        }
                        return null;
                    }
                    if ("getAllInstances".equals(method.getName())) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field field = NacosRegistry.class.getDeclaredField("namingService");
        field.setAccessible(true);
        field.set(registry, namingService);
        NotifyListener listener = urls -> notifications.incrementAndGet();
        registry.doSubscribe("TestService", listener);
        int notificationsBeforeUnsubscribe = notifications.get();

        registry.doUnsubscribe("TestService", listener);
        subscribedListener.get().onEvent(new NamingEvent("TestService", List.of()));

        assertEquals(notificationsBeforeUnsubscribe, notifications.get());

        registry.doUnsubscribe("TestService", listener);

        assertEquals(2, unsubscribeAttempts.get());
        Field nacosListenersField = NacosRegistry.class.getDeclaredField("nacosListeners");
        nacosListenersField.setAccessible(true);
        assertTrue(((Map<?, ?>) nacosListenersField.get(registry)).isEmpty());
    }

    @Test
    void shouldRollbackSubscriptionWhenInitialQueryFails() throws Exception {
        AtomicReference<EventListener> subscribedListener = new AtomicReference<>();
        AtomicReference<EventListener> unsubscribedListener = new AtomicReference<>();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("subscribe".equals(method.getName())) {
                        subscribedListener.set((EventListener) args[2]);
                        return null;
                    }
                    if ("unsubscribe".equals(method.getName())) {
                        unsubscribedListener.set((EventListener) args[2]);
                        return null;
                    }
                    if ("getAllInstances".equals(method.getName())) {
                        throw new NacosException(NacosException.SERVER_ERROR, "initial query failed");
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field field = NacosRegistry.class.getDeclaredField("namingService");
        field.setAccessible(true);
        field.set(registry, namingService);
        NotifyListener listener = urls -> {};

        assertThrows(RpcRegistryException.class, () -> registry.doSubscribe("TestService", listener));

        assertNotNull(subscribedListener.get());
        assertSame(subscribedListener.get(), unsubscribedListener.get());
        Field nacosListenersField = NacosRegistry.class.getDeclaredField("nacosListeners");
        nacosListenersField.setAccessible(true);
        assertTrue(((Map<?, ?>) nacosListenersField.get(registry)).isEmpty());
    }

    @Test
    void shouldRollbackSubscriptionWhenInitialNotificationFails() throws Exception {
        AtomicReference<EventListener> subscribedListener = new AtomicReference<>();
        AtomicReference<EventListener> unsubscribedListener = new AtomicReference<>();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("subscribe".equals(method.getName())) {
                        subscribedListener.set((EventListener) args[2]);
                        return null;
                    }
                    if ("unsubscribe".equals(method.getName())) {
                        unsubscribedListener.set((EventListener) args[2]);
                        return null;
                    }
                    if ("getAllInstances".equals(method.getName())) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field field = NacosRegistry.class.getDeclaredField("namingService");
        field.setAccessible(true);
        field.set(registry, namingService);
        NotifyListener listener = urls -> {
            throw new IllegalStateException("notification failed");
        };

        assertThrows(IllegalStateException.class, () -> registry.doSubscribe("TestService", listener));

        assertNotNull(subscribedListener.get());
        assertSame(subscribedListener.get(), unsubscribedListener.get());
        Field nacosListenersField = NacosRegistry.class.getDeclaredField("nacosListeners");
        nacosListenersField.setAccessible(true);
        assertTrue(((Map<?, ?>) nacosListenersField.get(registry)).isEmpty());
    }

    @Test
    void shouldNotReviveSubscriptionWhenUnsubscribeRacesSubscribe() throws Exception {
        CountDownLatch subscribeEntered = new CountDownLatch(1);
        CountDownLatch finishSubscribe = new CountDownLatch(1);
        AtomicReference<EventListener> activeListener = new AtomicReference<>();
        AtomicInteger notifications = new AtomicInteger();
        AtomicReference<Throwable> subscribeFailure = new AtomicReference<>();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("subscribe".equals(method.getName())) {
                        subscribeEntered.countDown();
                        if (!finishSubscribe.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("subscribe was not released");
                        }
                        activeListener.set((EventListener) args[2]);
                        return null;
                    }
                    if ("unsubscribe".equals(method.getName())) {
                        activeListener.set(null);
                        return null;
                    }
                    if ("getAllInstances".equals(method.getName())) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field field = NacosRegistry.class.getDeclaredField("namingService");
        field.setAccessible(true);
        field.set(registry, namingService);
        NotifyListener listener = urls -> notifications.incrementAndGet();
        Thread subscriber = new Thread(() -> {
            try {
                registry.doSubscribe("TestService", listener);
            } catch (Throwable e) {
                subscribeFailure.set(e);
            }
        });

        subscriber.start();
        assertTrue(subscribeEntered.await(5, TimeUnit.SECONDS));
        registry.doUnsubscribe("TestService", listener);
        finishSubscribe.countDown();
        subscriber.join(5000);

        assertTrue(!subscriber.isAlive());
        assertNull(subscribeFailure.get());
        assertNull(activeListener.get());
        assertEquals(0, notifications.get());
    }

    @Test
    void shouldNotUnsubscribeReplacementWhenStaleSubscribeFails() throws Exception {
        CountDownLatch firstQueryEntered = new CountDownLatch(1);
        CountDownLatch failFirstQuery = new CountDownLatch(1);
        AtomicInteger queryAttempts = new AtomicInteger();
        List<EventListener> subscribedListeners = Collections.synchronizedList(new ArrayList<>());
        List<EventListener> unsubscribedListeners = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger notifications = new AtomicInteger();
        AtomicReference<Throwable> subscribeFailure = new AtomicReference<>();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("subscribe".equals(method.getName())) {
                        subscribedListeners.add((EventListener) args[2]);
                        return null;
                    }
                    if ("unsubscribe".equals(method.getName())) {
                        unsubscribedListeners.add((EventListener) args[2]);
                        return null;
                    }
                    if ("getAllInstances".equals(method.getName())) {
                        if (queryAttempts.incrementAndGet() == 1) {
                            firstQueryEntered.countDown();
                            if (!failFirstQuery.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("initial query was not released");
                            }
                            throw new NacosException(NacosException.SERVER_ERROR, "initial query failed");
                        }
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field field = NacosRegistry.class.getDeclaredField("namingService");
        field.setAccessible(true);
        field.set(registry, namingService);
        NotifyListener listener = urls -> notifications.incrementAndGet();
        Thread staleSubscriber = new Thread(() -> {
            try {
                registry.doSubscribe("TestService", listener);
            } catch (Throwable e) {
                subscribeFailure.set(e);
            }
        });

        staleSubscriber.start();
        assertTrue(firstQueryEntered.await(5, TimeUnit.SECONDS));
        registry.doUnsubscribe("TestService", listener);
        registry.doSubscribe("TestService", listener);
        failFirstQuery.countDown();
        staleSubscriber.join(5000);

        assertTrue(!staleSubscriber.isAlive());
        assertTrue(subscribeFailure.get() instanceof RpcRegistryException);
        assertEquals(2, subscribedListeners.size());
        for (EventListener unsubscribedListener : unsubscribedListeners) {
            assertSame(subscribedListeners.get(0), unsubscribedListener);
        }
        int notificationsBeforeEvent = notifications.get();
        subscribedListeners.get(1).onEvent(new NamingEvent("TestService", List.of()));
        assertEquals(notificationsBeforeEvent + 1, notifications.get());
    }

    @Test
    void shouldRetryLateUnsubscribeFailureWithoutDroppingReplacement() throws Exception {
        CountDownLatch subscribeEntered = new CountDownLatch(1);
        CountDownLatch finishSubscribe = new CountDownLatch(1);
        AtomicInteger subscribeAttempts = new AtomicInteger();
        AtomicInteger firstListenerUnsubscribeAttempts = new AtomicInteger();
        AtomicReference<EventListener> firstListener = new AtomicReference<>();
        Set<EventListener> activeListeners = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> subscribeFailure = new AtomicReference<>();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("subscribe".equals(method.getName())) {
                        EventListener eventListener = (EventListener) args[2];
                        if (subscribeAttempts.incrementAndGet() == 1) {
                            firstListener.set(eventListener);
                            subscribeEntered.countDown();
                            if (!finishSubscribe.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("subscribe was not released");
                            }
                        }
                        activeListeners.add(eventListener);
                        return null;
                    }
                    if ("unsubscribe".equals(method.getName())) {
                        EventListener eventListener = (EventListener) args[2];
                        if (eventListener == firstListener.get()
                                && firstListenerUnsubscribeAttempts.incrementAndGet() == 2) {
                            throw new NacosException(NacosException.SERVER_ERROR, "late unsubscribe failed");
                        }
                        activeListeners.remove(eventListener);
                        return null;
                    }
                    if ("getAllInstances".equals(method.getName())) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field field = NacosRegistry.class.getDeclaredField("namingService");
        field.setAccessible(true);
        field.set(registry, namingService);
        NotifyListener listener = urls -> {};
        Thread staleSubscriber = new Thread(() -> {
            try {
                registry.doSubscribe("TestService", listener);
            } catch (Throwable e) {
                subscribeFailure.set(e);
            }
        });

        staleSubscriber.start();
        assertTrue(subscribeEntered.await(5, TimeUnit.SECONDS));
        registry.doUnsubscribe("TestService", listener);
        registry.doSubscribe("TestService", listener);
        finishSubscribe.countDown();
        staleSubscriber.join(5000);

        assertTrue(!staleSubscriber.isAlive());
        assertNull(subscribeFailure.get());
        assertEquals(2, activeListeners.size());

        registry.doUnsubscribe("TestService", listener);

        assertTrue(activeListeners.isEmpty());
        assertEquals(3, firstListenerUnsubscribeAttempts.get());
        Field retiredListenersField = NacosRegistry.class.getDeclaredField("retiredListeners");
        retiredListenersField.setAccessible(true);
        assertTrue(((Map<?, ?>) retiredListenersField.get(registry)).isEmpty());
    }

    @Test
    void shouldPreserveRetiredListenerWhenEarlierUnsubscribeCompletesLast() throws Exception {
        CountDownLatch subscribeEntered = new CountDownLatch(1);
        CountDownLatch finishSubscribe = new CountDownLatch(1);
        CountDownLatch firstUnsubscribeEntered = new CountDownLatch(1);
        CountDownLatch finishFirstUnsubscribe = new CountDownLatch(1);
        CountDownLatch lateUnsubscribeFailed = new CountDownLatch(1);
        AtomicInteger firstListenerUnsubscribeAttempts = new AtomicInteger();
        AtomicReference<EventListener> firstListener = new AtomicReference<>();
        Set<EventListener> activeListeners = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> subscribeFailure = new AtomicReference<>();
        AtomicReference<Throwable> unsubscribeFailure = new AtomicReference<>();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("subscribe".equals(method.getName())) {
                        EventListener eventListener = (EventListener) args[2];
                        firstListener.set(eventListener);
                        subscribeEntered.countDown();
                        if (!finishSubscribe.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("subscribe was not released");
                        }
                        activeListeners.add(eventListener);
                        return null;
                    }
                    if ("unsubscribe".equals(method.getName())) {
                        EventListener eventListener = (EventListener) args[2];
                        int attempt = firstListenerUnsubscribeAttempts.incrementAndGet();
                        if (attempt == 1) {
                            activeListeners.remove(eventListener);
                            firstUnsubscribeEntered.countDown();
                            if (!finishFirstUnsubscribe.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("first unsubscribe was not released");
                            }
                            return null;
                        }
                        if (attempt == 2) {
                            lateUnsubscribeFailed.countDown();
                            throw new NacosException(NacosException.SERVER_ERROR, "late unsubscribe failed");
                        }
                        activeListeners.remove(eventListener);
                        return null;
                    }
                    if ("getAllInstances".equals(method.getName())) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field field = NacosRegistry.class.getDeclaredField("namingService");
        field.setAccessible(true);
        field.set(registry, namingService);
        NotifyListener listener = urls -> {};
        Thread subscriber = new Thread(() -> {
            try {
                registry.doSubscribe("TestService", listener);
            } catch (Throwable e) {
                subscribeFailure.set(e);
            }
        });
        Thread earlyUnsubscriber = new Thread(() -> {
            try {
                registry.doUnsubscribe("TestService", listener);
            } catch (Throwable e) {
                unsubscribeFailure.set(e);
            }
        });

        subscriber.start();
        assertTrue(subscribeEntered.await(5, TimeUnit.SECONDS));
        earlyUnsubscriber.start();
        assertTrue(firstUnsubscribeEntered.await(5, TimeUnit.SECONDS));
        finishSubscribe.countDown();
        assertTrue(lateUnsubscribeFailed.await(5, TimeUnit.SECONDS));
        finishFirstUnsubscribe.countDown();
        subscriber.join(5000);
        earlyUnsubscriber.join(5000);

        assertTrue(!subscriber.isAlive());
        assertTrue(!earlyUnsubscriber.isAlive());
        assertNull(subscribeFailure.get());
        assertNull(unsubscribeFailure.get());
        assertTrue(activeListeners.contains(firstListener.get()));

        registry.doUnsubscribe("TestService", listener);

        assertTrue(activeListeners.isEmpty());
        assertEquals(3, firstListenerUnsubscribeAttempts.get());
    }

    @Test
    void shouldRetryDestroyWhenNacosShutdownFails() throws Exception {
        AtomicInteger shutdownAttempts = new AtomicInteger();
        NamingService namingService = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(), new Class<?>[] {NamingService.class}, (proxy, method, args) -> {
                    if ("shutDown".equals(method.getName())) {
                        if (shutdownAttempts.incrementAndGet() == 1) {
                            throw new NacosException(NacosException.SERVER_ERROR, "shutdown failed");
                        }
                        return null;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        NacosRegistry registry = new NacosRegistry(new RegistryDirectory(), new NacosConfig());
        Field namingServiceField = NacosRegistry.class.getDeclaredField("namingService");
        namingServiceField.setAccessible(true);
        namingServiceField.set(registry, namingService);
        Field initializedField = AbstractRegistry.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.setBoolean(registry, true);

        registry.destroy();
        registry.destroy();
        registry.destroy();

        assertEquals(2, shutdownAttempts.get());
    }
}
