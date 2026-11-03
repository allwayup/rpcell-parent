package io.rpcell.api.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class URLTest {

    @Test
    public void testDefaultConstructor() {
        URL url = new URL();
        assertNotNull(url.getParams());
        assertTrue(url.getParams().isEmpty());
    }

    @Test
    public void testParamConstructor() {
        URL url = new URL("rpcell", "127.0.0.1", 8080, "TestService", "default", "1.0.0");
        assertEquals("rpcell", url.getProtocol());
        assertEquals("127.0.0.1", url.getHost());
        assertEquals(8080, url.getPort());
        assertEquals("TestService", url.getServiceName());
        assertEquals("default", url.getGroup());
        assertEquals("1.0.0", url.getVersion());
    }

    @Test
    public void testFullConstructor() {
        Map<String, String> params = new HashMap<>();
        params.put("timeout", "3000");
        URL url = new URL("rpcell", "127.0.0.1", 8080, "TestService", "default", "1.0.0", params);
        assertEquals("3000", url.getParam("timeout"));
    }

    @Test
    public void testAddAndGetParam() {
        URL url = new URL();
        url.addParam("key", "value");
        assertEquals("value", url.getParam("key"));
        assertNull(url.getParam("nonexistent"));
    }

    @Test
    public void testToAddress() {
        URL url = new URL("rpcell", "192.168.1.1", 9090, "Svc", "g", "1");
        assertEquals("192.168.1.1:9090", url.toAddress());
    }

    @Test
    public void testToServiceKey() {
        URL url = new URL();
        url.setServiceName("MyService");
        url.setGroup("production");
        assertEquals("MyService:production:null", url.toServiceKey());
    }

    @Test
    public void testEqualsAndHashCode() {
        URL url1 = new URL("rpcell", "127.0.0.1", 8080, "Svc", "g", "v");
        URL url2 = new URL("rpcell", "127.0.0.1", 8080, "Svc", "g", "v");
        URL url3 = new URL("rpcell", "127.0.0.1", 8081, "Svc", "g", "v");
        assertEquals(url1, url2);
        assertEquals(url1.hashCode(), url2.hashCode());
        assertNotEquals(url1, url3);
    }

    @Test
    public void testSetParamsDefensiveCopy() {
        URL url = new URL();
        Map<String, String> params = new HashMap<>();
        params.put("k", "v");
        url.setParams(params);
        params.put("modified", "yes");
        assertNull(url.getParam("modified"));
    }

    @Test
    public void testGetParamsUnmodifiable() {
        URL url = new URL();
        url.addParam("k", "v");
        assertThrows(UnsupportedOperationException.class, () -> url.getParams().put("new", "val"));
    }

    @Test
    public void testNullParamsInConstructor() {
        URL url = new URL("rpcell", "host", 80, "name", "group", "v1", null);
        assertNotNull(url.getParams());
        assertTrue(url.getParams().isEmpty());
    }

    @Test
    public void testSetParamsNull() {
        URL url = new URL();
        url.addParam("k", "v");
        url.setParams(null);
        assertNotNull(url.getParams());
        assertTrue(url.getParams().isEmpty());
    }

    @Test
    public void testSettersAndGetters() {
        URL url = new URL();
        url.setProtocol("http");
        url.setHost("localhost");
        url.setPort(1234);
        url.setServiceName("Svc");
        url.setGroup("test");
        url.setVersion("2.0");
        assertEquals("http", url.getProtocol());
        assertEquals("localhost", url.getHost());
        assertEquals(1234, url.getPort());
        assertEquals("Svc", url.getServiceName());
        assertEquals("test", url.getGroup());
        assertEquals("2.0", url.getVersion());
    }
}
