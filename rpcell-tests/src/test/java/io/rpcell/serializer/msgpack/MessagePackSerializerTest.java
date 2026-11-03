package io.rpcell.serializer.msgpack;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.exception.RpcSerializationException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.SerializerConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessagePackSerializerTest {

    private MessagePackSerializer serializer;

    @BeforeEach
    public void setUp() {
        serializer = new MessagePackSerializer(new SerializerConfig());
    }

    @Test
    public void testId() {
        assertEquals((byte) 0x01, serializer.id());
    }

    @Test
    public void testContentType() {
        assertEquals("application/x-msgpack", serializer.contentType());
    }

    @Test
    public void testSerializeNull() {
        byte[] data = serializer.serialize(null);
        assertEquals(0, data.length);
    }

    @Test
    public void testDeserializeNull() {
        assertNull(serializer.deserialize(null, Invocation.class));
        assertNull(serializer.deserialize(new byte[0], Invocation.class));
    }

    @Test
    public void testRoundtripInvocation() {
        Invocation inv = new Invocation();
        inv.setInterfaceName("io.rpcell.TestService");
        inv.setMethodName("sayHello");
        inv.setParameterTypes(new Class<?>[] {String.class, int.class});
        inv.setArguments(new Object[] {"world", 42});

        byte[] data = serializer.serialize(inv);
        Invocation result = serializer.deserialize(data, Invocation.class);

        assertNotNull(result);
        assertEquals("io.rpcell.TestService", result.getInterfaceName());
        assertEquals("sayHello", result.getMethodName());
        assertEquals(2, result.getArguments().length);
        assertEquals("world", result.getArguments()[0]);
    }

    @Test
    public void testRoundtripWithAttachments() {
        Invocation inv = new Invocation();
        inv.setInterfaceName("io.rpcell.EchoService");
        inv.setMethodName("echo");

        byte[] data = serializer.serialize(inv);
        Invocation result = serializer.deserialize(data, Invocation.class);

        assertEquals("io.rpcell.EchoService", result.getInterfaceName());
        assertEquals("echo", result.getMethodName());
    }

    @Test
    public void testRoundtripString() {
        byte[] data = serializer.serialize("hello");
        String result = serializer.deserialize(data, String.class);
        assertEquals("hello", result);
    }

    @Test
    public void testRoundtripInt() {
        byte[] data = serializer.serialize(42);
        Integer result = serializer.deserialize(data, Integer.class);
        assertEquals(42, result);
    }

    @Test
    public void testDeserializeHonorsWhitelist() {
        SerializerConfig config = new SerializerConfig();
        config.setWhitelist(List.of("io.rpcell.api.model.*"));
        MessagePackSerializer restricted = new MessagePackSerializer(config);
        byte[] invocationData = restricted.serialize(new Invocation());
        byte[] stringData = restricted.serialize("hello");

        assertNotNull(restricted.deserialize(invocationData, Invocation.class));
        assertThrows(RpcSerializationException.class, () -> restricted.deserialize(stringData, String.class));
    }

    @Test
    public void testDeserializeRejectsJdkTypesWhenDisabled() {
        SerializerConfig config = new SerializerConfig();
        config.setAllowJdkTypes(false);
        MessagePackSerializer restricted = new MessagePackSerializer(config);
        byte[] data = restricted.serialize("hello");

        assertThrows(RpcSerializationException.class, () -> restricted.deserialize(data, String.class));
    }
}
