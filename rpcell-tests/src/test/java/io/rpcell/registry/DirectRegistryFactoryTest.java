package io.rpcell.registry;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.exception.RpcRegistryException;
import io.rpcell.api.model.RegistryConfig;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.NotifyListener;
import io.rpcell.api.spi.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DirectRegistryFactoryTest {

    private final DirectRegistry.DirectRegistryFactory factory = new DirectRegistry.DirectRegistryFactory();

    private DirectRegistry createAndInit(RegistryConfig config) {
        Registry registry = factory.create(config);
        assertTrue(registry instanceof DirectRegistry);
        DirectRegistry dr = (DirectRegistry) registry;
        dr.init();
        return dr;
    }

    @Nested
    class AddressParsing {

        @Test
        void shouldParseSingleAddress() {
            assertNotNull(createAndInit(buildConfig("127.0.0.1:20880")));
        }

        @Test
        void shouldParseMultipleAddresses() {
            DirectRegistry registry = createAndInit(buildConfig("192.168.1.1:20880,192.168.1.2:20881"));
            AtomicInteger count = new AtomicInteger();
            registry.subscribe("*", urls -> count.set(urls.size()));
            assertEquals(2, count.get());
        }

        @Test
        void shouldParseAddressesWithSpaces() {
            DirectRegistry registry = createAndInit(buildConfig(" 10.0.0.1:8080 , 10.0.0.2:8081 "));
            registry.subscribe("*", urls -> assertEquals(2, urls.size()));
        }

        @Test
        void shouldRejectEmptyAddress() {
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("")));
        }

        @Test
        void shouldRejectNullAddress() {
            RegistryConfig config = new RegistryConfig();
            config.setAddress(null);
            assertThrows(RpcRegistryException.class, () -> factory.create(config));
        }

        @Test
        void shouldRejectMissingPort() {
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("127.0.0.1")));
        }

        @Test
        void shouldRejectInvalidPortFormat() {
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("127.0.0.1:abc")));
        }

        @Test
        void shouldRejectOutOfRangePort() {
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("127.0.0.1:0")));
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("127.0.0.1:65536")));
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("127.0.0.1:-1")));
        }

        @Test
        void shouldRejectEmptyHost() {
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig(":20880")));
        }

        @Test
        void shouldRejectExtraColons() {
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("127.0.0.1:8080:extra")));
        }

        @Test
        void shouldParseIPv6Address() {
            DirectRegistry registry = createAndInit(buildConfig("[::1]:20880"));
            AtomicInteger count = new AtomicInteger();
            registry.subscribe("*", urls -> count.set(urls.size()));
            assertEquals(1, count.get());
        }

        @Test
        void shouldParseFullIPv6Address() {
            DirectRegistry registry = createAndInit(buildConfig("[2001:db8::1]:20880"));
            AtomicInteger count = new AtomicInteger();
            registry.subscribe("*", urls -> count.set(urls.size()));
            assertEquals(1, count.get());
        }

        @Test
        void shouldParseMixedIPv4AndIPv6() {
            DirectRegistry registry = createAndInit(buildConfig("192.168.1.1:20880,[::1]:20881"));
            AtomicInteger count = new AtomicInteger();
            registry.subscribe("*", urls -> count.set(urls.size()));
            assertEquals(2, count.get());
        }

        @Test
        void shouldRejectInvalidIPv6MissingBracket() {
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("[::1:20880")));
        }

        @Test
        void shouldRejectInvalidIPv6MissingPort() {
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("[::1]")));
        }

        @Test
        void shouldRejectIPv6EmptyHost() {
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("[]:20880")));
        }

        @Test
        void shouldRejectIPv6InvalidPort() {
            assertThrows(RpcRegistryException.class, () -> factory.create(buildConfig("[::1]:abc")));
        }
    }

    @Nested
    class SubscriptionBehavior {

        @Test
        void shouldReturnInitializedRegistry() {
            Registry registry = factory.create(buildConfig("127.0.0.1:20880"));
            assertDoesNotThrow(() -> registry.subscribe("TestService", urls -> {}));
        }

        @Test
        void shouldNotifyImmediatelyOnSubscribe() {
            DirectRegistry registry = createAndInit(buildConfig("127.0.0.1:20880,127.0.0.1:20881"));
            List<URL> received = new ArrayList<>();
            registry.subscribe("TestService", urls -> {
                received.clear();
                received.addAll(urls);
            });
            assertEquals(2, received.size());
        }

        @Test
        void shouldNotifyWildcardSubscriber() {
            DirectRegistry registry = createAndInit(buildConfig("127.0.0.1:20880"));
            AtomicInteger count = new AtomicInteger();
            registry.subscribe("*", urls -> count.set(urls.size()));
            assertEquals(1, count.get());
        }

        @Test
        void shouldNotThrowOnUnsubscribe() {
            DirectRegistry registry = createAndInit(buildConfig("127.0.0.1:20880"));
            NotifyListener listener = urls -> {};
            registry.subscribe("TestService", listener);
            registry.unsubscribe("TestService", listener);
        }
    }

    private static RegistryConfig buildConfig(String address) {
        RegistryConfig config = new RegistryConfig();
        config.setAddress(address);
        return config;
    }
}
