package io.rpcell.filter.seata;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.spi.Filter;
import io.rpcell.api.spi.Invoker;
import io.rpcell.filter.FilterChain;
import io.rpcell.spi.ExtensionLoader;
import io.seata.core.context.RootContext;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SeataFilterTest {

    private static final String TEST_XID = "192.168.1.1:8091:1234567890";
    private static final Invoker ECHO_INVOKER = invocation -> {
        Result result = new Result();
        result.setValue(invocation.getMethodName());
        return result;
    };

    private SeataFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SeataFilter();
        RootContext.unbind();
        RootContext.unbindBranchType();
    }

    @AfterEach
    void tearDown() {
        RootContext.unbind();
        RootContext.unbindBranchType();
        ExtensionLoader.clear();
    }

    @Nested
    class ConsumerSide {

        @Test
        void shouldPropagateXidToAttachments() {
            RootContext.bind(TEST_XID);
            try {
                Invocation inv = createInvocation();
                assertNull(inv.getAttachments().get(RootContext.KEY_XID));

                filter.invoke(ECHO_INVOKER, inv);
                assertEquals(TEST_XID, inv.getAttachments().get(RootContext.KEY_XID));
            } finally {
                RootContext.unbind();
            }
        }

        @Test
        void shouldNotModifyAttachmentsWhenNoXid() {
            Invocation inv = createInvocation();
            int originalSize = inv.getAttachments().size();

            filter.invoke(ECHO_INVOKER, inv);
            assertNull(inv.getAttachments().get(RootContext.KEY_XID));
            assertEquals(originalSize, inv.getAttachments().size());
        }
    }

    @Nested
    class ProviderSide {

        @Test
        void shouldBindIncomingXidToRootContext() {
            Invocation inv = createInvocation();
            inv.getAttachments().put(RootContext.KEY_XID, TEST_XID);

            assertNull(RootContext.getXID());
            Result result = filter.invoke(ECHO_INVOKER, inv);
            assertEquals("hello", result.getValue());
            assertNull(RootContext.getXID());
        }

        @Test
        void shouldUnbindXidAfterInvocation() {
            Invocation inv = createInvocation();
            inv.getAttachments().put(RootContext.KEY_XID, TEST_XID);

            filter.invoke(ECHO_INVOKER, inv);
            assertNull(RootContext.getXID());
        }

        @Test
        void shouldUnbindXidEvenOnException() {
            Invocation inv = createInvocation();
            inv.getAttachments().put(RootContext.KEY_XID, TEST_XID);
            Invoker failingInvoker = invocation -> {
                throw new RuntimeException("business error");
            };

            assertNull(RootContext.getXID());
            try {
                filter.invoke(failingInvoker, inv);
            } catch (RuntimeException ignored) {
            }
            assertNull(RootContext.getXID());
        }

        @Test
        void shouldNotLeakXidForInvalidBranchType() {
            Invocation inv = createInvocation();
            inv.getAttachments().put(RootContext.KEY_XID, TEST_XID);
            inv.getAttachments().put(RootContext.KEY_BRANCH_TYPE, "INVALID");

            assertThrows(IllegalArgumentException.class, () -> filter.invoke(ECHO_INVOKER, inv));
            assertNull(RootContext.getXID());
            assertNull(RootContext.getBranchType());
        }

        @Test
        void shouldPassthroughWhenNoXidPresent() {
            Invocation inv = createInvocation();
            inv.setMethodName("world");
            assertNull(RootContext.getXID());

            Result result = filter.invoke(ECHO_INVOKER, inv);
            assertEquals("world", result.getValue());
            assertNull(RootContext.getXID());
        }
    }

    @Nested
    class IntegrationWithFilterChain {

        @Test
        void shouldPropagateAndBindAcrossFilterChain() {
            RootContext.bind(TEST_XID);
            try {
                Invocation inv = createInvocation();
                Invoker chain = FilterChain.buildFilterChain(ECHO_INVOKER, Collections.singletonList(filter));

                assertNull(inv.getAttachments().get(RootContext.KEY_XID));
                Result result = chain.invoke(inv);
                assertEquals("hello", result.getValue());
                assertEquals(TEST_XID, inv.getAttachments().get(RootContext.KEY_XID));
            } finally {
                RootContext.unbind();
            }
        }
    }

    @Nested
    class SpiRegistration {

        @Test
        void shouldBeLoadableViaExtensionLoader() {
            ExtensionLoader.register("filter:seata", SeataFilter.class);
            Filter loaded = ExtensionLoader.load("filter:seata");
            assertNotNull(loaded);
            assertTrue(loaded instanceof SeataFilter);
        }
    }

    private Invocation createInvocation() {
        Invocation inv = new Invocation();
        inv.setInterfaceName("TestService");
        inv.setMethodName("hello");
        return inv;
    }
}
