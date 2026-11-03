package io.rpcell.api.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InvocationTest {

    private Invocation inv;

    @BeforeEach
    void setUp() {
        inv = new Invocation();
    }

    @Test
    void shouldHaveEmptyAttachmentsByDefault() {
        assertNotNull(inv.getAttachments());
        assertTrue(inv.getAttachments().isEmpty());
    }

    @Test
    void shouldStoreAndRetrieveInterfaceName() {
        inv.setInterfaceName("com.example.TestService");
        assertEquals("com.example.TestService", inv.getInterfaceName());
    }

    @Test
    void shouldStoreAndRetrieveMethodName() {
        inv.setMethodName("sayHello");
        assertEquals("sayHello", inv.getMethodName());
    }

    @Test
    void shouldStoreAndRetrieveParameterTypes() {
        Class<?>[] types = {String.class, Integer.class};
        inv.setParameterTypes(types);
        assertArrayEquals(types, inv.getParameterTypes());
    }

    @Test
    void shouldStoreAndRetrieveArguments() {
        Object[] args = {"hello", 42};
        inv.setArguments(args);
        assertArrayEquals(args, inv.getArguments());
    }

    @Test
    void shouldStoreAndRetrieveAttachments() {
        Map<String, String> attachments = new HashMap<>();
        attachments.put("key", "value");
        inv.setAttachments(attachments);
        assertEquals(attachments, inv.getAttachments());
        assertEquals("value", inv.getAttachments().get("key"));
    }

    @Test
    void shouldReplaceAttachmentsReference() {
        Map<String, String> m1 = new HashMap<>();
        m1.put("a", "1");
        inv.setAttachments(m1);

        Map<String, String> m2 = new HashMap<>();
        m2.put("b", "2");
        inv.setAttachments(m2);

        assertNull(inv.getAttachments().get("a"));
        assertEquals("2", inv.getAttachments().get("b"));
    }

    @Test
    void shouldReturnNullForUnsetFields() {
        assertNull(inv.getInterfaceName());
        assertNull(inv.getMethodName());
        assertNull(inv.getParameterTypes());
        assertNull(inv.getArguments());
    }

    @Test
    void shouldAllowNullParameterTypes() {
        inv.setParameterTypes(null);
        assertNull(inv.getParameterTypes());
    }

    @Test
    void shouldAllowNullArguments() {
        inv.setArguments(null);
        assertNull(inv.getArguments());
    }

    @Nested
    class AttachmentsMutability {

        @Test
        void shouldAllowDirectMutation() {
            inv.getAttachments().put("direct", "yes");
            assertEquals("yes", inv.getAttachments().get("direct"));
        }

        @Test
        void shouldSupportMultipleKeys() {
            inv.getAttachments().put("host", "127.0.0.1");
            inv.getAttachments().put("port", "20880");
            assertEquals(2, inv.getAttachments().size());
        }
    }
}
