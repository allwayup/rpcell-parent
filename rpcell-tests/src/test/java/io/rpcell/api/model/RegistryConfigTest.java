package io.rpcell.api.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistryConfigTest {

    @Test
    void shouldHaveEmptyParamsByDefault() {
        RegistryConfig config = new RegistryConfig();
        assertNotNull(config.getParams());
        assertTrue(config.getParams().isEmpty());
    }

    @Test
    void shouldStoreAndRetrieveType() {
        RegistryConfig config = new RegistryConfig();
        config.setType("direct");
        assertEquals("direct", config.getType());
    }

    @Test
    void shouldStoreAndRetrieveAddress() {
        RegistryConfig config = new RegistryConfig();
        config.setAddress("127.0.0.1:8848");
        assertEquals("127.0.0.1:8848", config.getAddress());
    }

    @Test
    void shouldStoreAndRetrieveUsername() {
        RegistryConfig config = new RegistryConfig();
        config.setUsername("admin");
        assertEquals("admin", config.getUsername());
    }

    @Test
    void shouldStoreAndRetrievePassword() {
        RegistryConfig config = new RegistryConfig();
        config.setPassword("secret123");
        assertEquals("secret123", config.getPassword());
    }

    @Test
    void shouldReplaceParamsEntirely() {
        RegistryConfig config = new RegistryConfig();
        Map<String, String> first = new HashMap<>();
        first.put("a", "1");
        config.setParams(first);

        Map<String, String> second = new HashMap<>();
        second.put("b", "2");
        config.setParams(second);

        assertNull(config.getParams().get("a"));
        assertEquals("2", config.getParams().get("b"));
    }

    @Test
    void shouldSupportMultipleParams() {
        RegistryConfig config = new RegistryConfig();
        config.getParams().put("namespace", "dev");
        config.getParams().put("group", "DEFAULT_GROUP");
        assertEquals("dev", config.getParams().get("namespace"));
        assertEquals("DEFAULT_GROUP", config.getParams().get("group"));
    }
}
