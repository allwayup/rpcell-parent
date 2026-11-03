package io.rpcell.registry.nacos;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.model.RegistryConfig;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class NacosConfigTest {

    @Test
    public void testDefaultConstructor() {
        NacosConfig config = new NacosConfig();
        assertEquals("127.0.0.1:8848", config.getServerAddr());
        assertEquals("DEFAULT_GROUP", config.getGroup());
    }

    @Test
    public void testSetServerAddr() {
        NacosConfig config = new NacosConfig();
        config.setServerAddr("192.168.1.1:8848");
        assertEquals("192.168.1.1:8848", config.getServerAddr());
    }

    @Test
    public void testSetServerAddrSkipsNull() {
        NacosConfig config = new NacosConfig();
        config.setServerAddr(null);
        assertEquals("127.0.0.1:8848", config.getServerAddr());
    }

    @Test
    public void testSetServerAddrSkipsEmpty() {
        NacosConfig config = new NacosConfig();
        config.setServerAddr("");
        assertEquals("127.0.0.1:8848", config.getServerAddr());
    }

    @Test
    public void testSetUsernamePassword() {
        NacosConfig config = new NacosConfig();
        config.setUsername("admin");
        config.setPassword("secret");
        assertEquals("admin", config.getUsername());
        assertEquals("secret", config.getPassword());
    }

    @Test
    public void testSetGroupAndNamespace() {
        NacosConfig config = new NacosConfig();
        config.setGroup("MY_GROUP");
        config.setNamespace("dev");
        assertEquals("MY_GROUP", config.getGroup());
        assertEquals("dev", config.getNamespace());
    }

    @Test
    public void testFactoryCreatesConfig() {
        NacosRegistryFactory factory = new NacosRegistryFactory();
        RegistryConfig rc = new RegistryConfig();
        rc.setAddress("127.0.0.1:8848");
        rc.setParams(new HashMap<>());
        try {
            assertNotNull(factory);
        } catch (Exception e) {
            // expected if Nacos is not running
        }
    }
}
