package io.rpcell.registry;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.constant.Attachments;
import io.rpcell.api.exception.RpcRegistryException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.URL;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DirectRegistryTest {

    private RegistryDirectory directory;
    private DirectRegistry registry;
    private List<URL> staticUrls;

    @BeforeEach
    public void setUp() {
        directory = new RegistryDirectory();
        staticUrls = new ArrayList<>();
        staticUrls.add(new URL("rpcell", "127.0.0.1", 8080, "TestService", "default", "1.0"));
        staticUrls.add(new URL("rpcell", "127.0.0.1", 8081, "TestService", "default", "1.0"));
        registry = new DirectRegistry(directory, staticUrls);
        registry.init();
    }

    @Test
    public void testInit() {
        assertNotNull(registry);
    }

    @Test
    public void testSubscribeNotifiesImmediately() {
        AtomicInteger count = new AtomicInteger(0);
        registry.subscribe("TestService", urls -> count.incrementAndGet());
        assertTrue(count.get() > 0);
    }

    @Test
    public void testSubscribeWithWildcard() {
        List<URL> received = new ArrayList<>();
        registry.subscribe("*", received::addAll);
        assertEquals(staticUrls, received);
    }

    @Test
    public void testSubscribeNonMatchingService() {
        List<URL> received = new ArrayList<>();
        registry.subscribe("NonExistentService", received::addAll);
        assertTrue(received.isEmpty());
    }

    @Test
    public void testSubscribeIncludesGenericUrls() {
        staticUrls.add(new URL("rpcell", "127.0.0.2", 8080, null, null, null));
        DirectRegistry genericRegistry = new DirectRegistry(directory, staticUrls);
        genericRegistry.init();
        List<URL> received = new ArrayList<>();

        genericRegistry.subscribe("TestService", received::addAll);

        assertEquals(3, received.size());
        assertEquals("127.0.0.2", received.get(2).getHost());
    }

    @Test
    public void testGenericUrlsMatchGroupAndVersion() {
        DirectRegistry genericRegistry =
                new DirectRegistry(directory, List.of(new URL("rpcell", "127.0.0.2", 8080, null, null, null)));
        genericRegistry.init();
        genericRegistry.subscribe("TestService", directory);
        Invocation invocation = new Invocation();
        invocation.getAttachments().put(Attachments.GROUP, "default");
        invocation.getAttachments().put(Attachments.VERSION, "1.0");

        assertEquals(1, directory.list(invocation).size());
    }

    @Test
    public void testSubscribeRollsBackWhenNotificationFails() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () -> registry.subscribe("TestService", urls -> {
                    throw new IllegalStateException("notification failed");
                }));

        Field listenersField = DirectRegistry.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        assertTrue(((Map<?, ?>) listenersField.get(registry)).isEmpty());
    }

    @Test
    public void testRegisterIsNoop() {
        URL url = new URL("rpcell", "127.0.0.1", 9090, "Test", "g", "v");
        registry.register(url);
    }

    @Test
    public void testUnregisterIsNoop() {
        URL url = new URL("rpcell", "127.0.0.1", 9090, "Test", "g", "v");
        registry.unregister(url);
    }

    @Test
    public void testUnsubscribe() {
        AtomicInteger count = new AtomicInteger(0);
        registry.subscribe("TestService", urls -> count.incrementAndGet());
        registry.unsubscribe("TestService", urls -> count.incrementAndGet());
    }

    @Test
    public void testRegisterBeforeInitFails() {
        RegistryDirectory dir = new RegistryDirectory();
        DirectRegistry uninitialized = new DirectRegistry(dir, staticUrls);
        assertThrows(Exception.class, () -> uninitialized.register(new URL()));
    }

    @Test
    public void testMultipleSubscribers() {
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);
        registry.subscribe("TestService", urls -> count1.incrementAndGet());
        registry.subscribe("TestService", urls -> count2.incrementAndGet());
        assertTrue(count1.get() > 0);
        assertTrue(count2.get() > 0);
    }

    @Test
    public void testDestroyFailurePreventsFurtherUse() {
        AtomicBoolean failDestroy = new AtomicBoolean(true);
        AbstractRegistry failingRegistry = new AbstractRegistry(new RegistryDirectory()) {
            @Override
            protected void doInit() {}

            @Override
            protected void doRegister(URL url) {}

            @Override
            protected void doUnregister(URL url) {}

            @Override
            protected void doSubscribe(String serviceName, io.rpcell.api.spi.NotifyListener listener) {}

            @Override
            protected void doUnsubscribe(String serviceName, io.rpcell.api.spi.NotifyListener listener) {}

            @Override
            protected void doDestroy() {
                if (failDestroy.get()) {
                    throw new IllegalStateException("destroy failed");
                }
            }
        };
        failingRegistry.init();

        failingRegistry.destroy();

        assertThrows(RpcRegistryException.class, () -> failingRegistry.register(new URL()));
        assertThrows(RpcRegistryException.class, failingRegistry::init);

        failDestroy.set(false);
        failingRegistry.destroy();
        failingRegistry.init();
        failingRegistry.register(new URL());
        failingRegistry.destroy();
    }

    @Test
    public void testDestroyWaitsForInflightSubscribe() throws Exception {
        CountDownLatch subscribeEntered = new CountDownLatch(1);
        CountDownLatch releaseSubscribe = new CountDownLatch(1);
        CountDownLatch destroyFinished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AbstractRegistry blockingRegistry = new AbstractRegistry(new RegistryDirectory()) {
            @Override
            protected void doInit() {}

            @Override
            protected void doRegister(URL url) {}

            @Override
            protected void doUnregister(URL url) {}

            @Override
            protected void doSubscribe(String serviceName, io.rpcell.api.spi.NotifyListener listener) {
                subscribeEntered.countDown();
                try {
                    if (!releaseSubscribe.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("subscribe was not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RpcRegistryException("subscribe interrupted", e);
                }
            }

            @Override
            protected void doUnsubscribe(String serviceName, io.rpcell.api.spi.NotifyListener listener) {}

            @Override
            protected void doDestroy() {}
        };
        blockingRegistry.init();
        Thread subscriber = new Thread(() -> {
            try {
                blockingRegistry.subscribe("TestService", urls -> {});
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        Thread destroyer = new Thread(() -> {
            try {
                blockingRegistry.destroy();
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                destroyFinished.countDown();
            }
        });

        try {
            subscriber.start();
            assertTrue(subscribeEntered.await(5, TimeUnit.SECONDS));
            destroyer.start();
            assertFalse(destroyFinished.await(200, TimeUnit.MILLISECONDS));
        } finally {
            releaseSubscribe.countDown();
            subscriber.join(5000);
            destroyer.join(5000);
        }

        assertTrue(!subscriber.isAlive());
        assertTrue(!destroyer.isAlive());
        assertTrue(destroyFinished.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());
    }
}
