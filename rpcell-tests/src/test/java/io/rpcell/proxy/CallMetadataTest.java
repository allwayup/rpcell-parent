package io.rpcell.proxy;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.constant.Attachments;
import io.rpcell.proxy.RpcProxyFactory.CallMetadata;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CallMetadataTest {

    @Nested
    class ToAttachments {

        @Test
        void shouldReturnEmptyMapForDefaults() {
            CallMetadata meta = CallMetadata.builder().build();
            Map<String, String> attachments = meta.toAttachments();
            assertNotNull(attachments);
            assertFalse(attachments.containsKey(Attachments.TIMEOUT));
            assertFalse(attachments.containsKey(Attachments.RETRIES));
            assertFalse(attachments.containsKey(Attachments.LOADBALANCE));
            assertEquals("false", attachments.get(Attachments.CHECK));
        }

        @Test
        void shouldIncludeTimeoutWhenSet() {
            CallMetadata meta = CallMetadata.builder().timeout(5000).build();
            assertEquals("5000", meta.toAttachments().get(Attachments.TIMEOUT));
        }

        @Test
        void shouldExcludeTimeoutWhenNegative() {
            CallMetadata meta = CallMetadata.builder().timeout(-1).build();
            assertFalse(meta.toAttachments().containsKey(Attachments.TIMEOUT));
        }

        @Test
        void shouldIncludeRetriesWhenSet() {
            CallMetadata meta = CallMetadata.builder().retries(3).build();
            assertEquals("3", meta.toAttachments().get(Attachments.RETRIES));
        }

        @Test
        void shouldExcludeRetriesWhenNegative() {
            CallMetadata meta = CallMetadata.builder().retries(-1).build();
            assertFalse(meta.toAttachments().containsKey(Attachments.RETRIES));
        }

        @Test
        void shouldIncludeLoadbalanceWhenSet() {
            CallMetadata meta = CallMetadata.builder().loadbalance("roundrobin").build();
            assertEquals("roundrobin", meta.toAttachments().get(Attachments.LOADBALANCE));
        }

        @Test
        void shouldExcludeLoadbalanceWhenEmpty() {
            CallMetadata meta = CallMetadata.builder().loadbalance("").build();
            assertFalse(meta.toAttachments().containsKey(Attachments.LOADBALANCE));
        }

        @Test
        void shouldIncludeAllWhenFullyPopulated() {
            CallMetadata meta = CallMetadata.builder()
                    .timeout(10000)
                    .retries(5)
                    .loadbalance("leastactive")
                    .check(true)
                    .filter(new String[] {"filter:seata"})
                    .build();

            Map<String, String> m = meta.toAttachments();
            assertEquals("10000", m.get(Attachments.TIMEOUT));
            assertEquals("5", m.get(Attachments.RETRIES));
            assertEquals("leastactive", m.get(Attachments.LOADBALANCE));
            assertEquals("true", m.get(Attachments.CHECK));
            assertFalse(m.containsKey(Attachments.GROUP));
            assertFalse(m.containsKey(Attachments.VERSION));
        }
    }

    @Nested
    class BuilderImmutability {

        @Test
        void shouldReturnNewMetadataFromBuild() {
            CallMetadata.Builder builder = CallMetadata.builder().timeout(3000);
            CallMetadata first = builder.build();
            CallMetadata second = builder.timeout(5000).build();
            assertEquals("3000", first.toAttachments().get(Attachments.TIMEOUT));
            assertEquals("5000", second.toAttachments().get(Attachments.TIMEOUT));
        }
    }
}
