package io.rpcell.api.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServiceDescriptorTest {

    private ServiceDescriptor descriptor;

    @BeforeEach
    void setUp() {
        descriptor = new ServiceDescriptor();
    }

    @Test
    void shouldDefaultMethodsBeEmpty() {
        assertTrue(descriptor.getMethods().isEmpty());
    }

    @Test
    void shouldDefaultInstanceBeNull() {
        assertNull(descriptor.getInstance());
    }

    @Test
    void shouldReturnUnmodifiableMethods() {
        Set<String> methods = new LinkedHashSet<>();
        methods.add("sayHello");
        descriptor.setMethods(methods);

        Set<String> returned = descriptor.getMethods();
        assertThrows(UnsupportedOperationException.class, () -> returned.add("newMethod"));
    }

    @Test
    void shouldContainsMethodWork() {
        Set<String> methods = new LinkedHashSet<>();
        methods.add("sayHello");
        methods.add("getInfo");
        descriptor.setMethods(methods);

        assertTrue(descriptor.containsMethod("sayHello"));
        assertTrue(descriptor.containsMethod("getInfo"));
        assertFalse(descriptor.containsMethod("unknownMethod"));
    }

    @Test
    void shouldContainsMethodReturnFalseWhenNull() {
        descriptor.setMethods(null);
        assertFalse(descriptor.containsMethod("anything"));
    }

    @Test
    void shouldToServiceKeyCombineServiceNameGroupAndVersion() {
        descriptor.setServiceName("MyService");
        descriptor.setGroup("production");
        descriptor.setVersion("1.0");
        assertEquals("MyService:production:1.0", descriptor.toServiceKey());
    }

    @Test
    void shouldToServiceKeyHandleNulls() {
        assertEquals("null:null:null", descriptor.toServiceKey());
    }

    @Test
    void shouldStoreAndRetrieveInterfaceClass() {
        descriptor.setInterfaceClass(String.class);
        assertEquals(String.class, descriptor.getInterfaceClass());
    }

    @Test
    void shouldStoreAndRetrieveServiceName() {
        descriptor.setServiceName("TestService");
        assertEquals("TestService", descriptor.getServiceName());
    }

    @Test
    void shouldStoreAndRetrieveGroup() {
        descriptor.setGroup("staging");
        assertEquals("staging", descriptor.getGroup());
    }

    @Test
    void shouldStoreAndRetrieveVersion() {
        descriptor.setVersion("2.0.0");
        assertEquals("2.0.0", descriptor.getVersion());
    }

    @Test
    void shouldStoreAndRetrieveInstance() {
        Object obj = new Object();
        descriptor.setInstance(obj);
        assertSame(obj, descriptor.getInstance());
    }

    @Nested
    class NullMethodsEdgeCases {

        @Test
        void shouldGetMethodsBeEmptyWhenNull() {
            descriptor.setMethods(null);
            assertTrue(descriptor.getMethods().isEmpty());
        }

        @Test
        void shouldContainMethodWorkWithEmptySet() {
            descriptor.setMethods(Collections.emptySet());
            assertFalse(descriptor.containsMethod("foo"));
        }
    }
}
