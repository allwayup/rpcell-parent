package io.rpcell.boot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RpcellPropertiesTest {

    @Test
    void shouldHaveDefaultEnabled() {
        RpcellProperties props = new RpcellProperties();
        assertTrue(props.isEnabled());
    }

    @Test
    void shouldSetEnabled() {
        RpcellProperties props = new RpcellProperties();
        props.setEnabled(false);
        assertFalse(props.isEnabled());
    }

    @Nested
    class ProtocolDefaults {

        @Test
        void shouldHaveDefaultProtocolName() {
            RpcellProperties props = new RpcellProperties();
            assertEquals("rpcell", props.getProtocol().getName());
        }

        @Test
        void shouldHaveDefaultSerialization() {
            RpcellProperties props = new RpcellProperties();
            assertEquals("msgpack", props.getProtocol().getSerialization());
        }
    }

    @Nested
    class TransportDefaults {

        @Test
        void shouldHaveDefaultHost() {
            RpcellProperties props = new RpcellProperties();
            assertEquals("0.0.0.0", props.getTransport().getHost());
        }

        @Test
        void shouldHaveDefaultPort() {
            RpcellProperties props = new RpcellProperties();
            assertEquals(20880, props.getTransport().getPort());
        }

        @Test
        void shouldHaveDefaultConnectTimeout() {
            RpcellProperties props = new RpcellProperties();
            assertEquals(3000, props.getTransport().getConnectTimeout());
        }

        @Test
        void shouldHaveDefaultReadTimeout() {
            RpcellProperties props = new RpcellProperties();
            assertEquals(5000, props.getTransport().getReadTimeout());
        }
    }

    @Nested
    class RegistryDefaults {

        @Test
        void shouldHaveDefaultType() {
            RpcellProperties props = new RpcellProperties();
            assertEquals("nacos", props.getRegistry().getType());
        }

        @Test
        void shouldHaveDefaultAddress() {
            RpcellProperties props = new RpcellProperties();
            assertEquals("127.0.0.1:8848", props.getRegistry().getAddress());
        }

        @Test
        void shouldHaveDefaultGroup() {
            RpcellProperties props = new RpcellProperties();
            assertEquals("DEFAULT_GROUP", props.getRegistry().getGroup());
        }
    }

    @Nested
    class ClusterDefaults {

        @Test
        void shouldHaveDefaultLoadbalance() {
            RpcellProperties props = new RpcellProperties();
            assertEquals("random", props.getCluster().getLoadbalance());
        }

        @Test
        void shouldHaveDefaultRetries() {
            RpcellProperties props = new RpcellProperties();
            assertEquals(2, props.getCluster().getRetries());
        }
    }

    @Nested
    class ProviderDefaults {

        @Test
        void shouldHaveDefaultTimeout() {
            RpcellProperties props = new RpcellProperties();
            assertEquals(3000, props.getProvider().getTimeout());
        }

        @Test
        void shouldExportByDefault() {
            RpcellProperties props = new RpcellProperties();
            assertTrue(props.getProvider().isExport());
        }
    }

    @Nested
    class ConsumerDefaults {

        @Test
        void shouldHaveDefaultTimeout() {
            RpcellProperties props = new RpcellProperties();
            assertEquals(3000, props.getConsumer().getTimeout());
        }

        @Test
        void shouldHaveDefaultRetries() {
            RpcellProperties props = new RpcellProperties();
            assertEquals(2, props.getConsumer().getRetries());
        }
    }

    @Nested
    class FilterConfigDefaults {

        @Test
        void shouldHaveEmptyProviderFilterNames() {
            RpcellProperties props = new RpcellProperties();
            assertNotNull(props.getFilter().getProvider().getNames());
            assertTrue(props.getFilter().getProvider().getNames().isEmpty());
        }

        @Test
        void shouldHaveEmptyConsumerFilterNames() {
            RpcellProperties props = new RpcellProperties();
            assertNotNull(props.getFilter().getConsumer().getNames());
            assertTrue(props.getFilter().getConsumer().getNames().isEmpty());
        }

        @Test
        void shouldSetProviderFilterNames() {
            RpcellProperties props = new RpcellProperties();
            props.getFilter().getProvider().setNames(Arrays.asList("filter:seata"));
            assertEquals(1, props.getFilter().getProvider().getNames().size());
            assertEquals(
                    "filter:seata", props.getFilter().getProvider().getNames().get(0));
        }

        @Test
        void shouldSetConsumerFilterNames() {
            RpcellProperties props = new RpcellProperties();
            props.getFilter().getConsumer().setNames(Arrays.asList("filter:auth", "filter:seata"));
            assertEquals(2, props.getFilter().getConsumer().getNames().size());
        }
    }

    @Nested
    class SecurityDefaults {

        @Test
        void shouldAllowJdkByDefault() {
            RpcellProperties props = new RpcellProperties();
            assertTrue(props.getSecurity().getSerialization().isAllowJdk());
        }
    }
}
