package io.rpcell.codec;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.constant.ProtocolConstants;
import io.rpcell.api.constant.Serializers;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.SerializerConfig;
import io.rpcell.serializer.SerializerManager;
import io.rpcell.serializer.msgpack.MessagePackSerializerFactory;
import io.rpcell.spi.ExtensionLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RpcCellProtocolTest {

    private static RpcCellProtocol protocol;

    @BeforeAll
    public static void setUp() {
        ExtensionLoader.register("serializer:msgpack", MessagePackSerializerFactory.class);
        SerializerManager.init(new SerializerConfig());
        protocol = new RpcCellProtocol();
    }

    @Test
    public void testEncodeDecodeRequestRoundtrip() {
        Invocation inv = new Invocation();
        inv.setInterfaceName("io.rpcell.TestService");
        inv.setMethodName("sayHello");
        inv.setArguments(new Object[] {"world"});

        byte[] frame = protocol.encodeRequest(inv, 12345L, Serializers.MSGPACK);
        assertNotNull(frame);
        assertTrue(frame.length > ProtocolConstants.HEADER_LENGTH);

        byte[] body = protocol.extractBody(frame);
        Invocation decoded = protocol.decodeRequest(body, Serializers.MSGPACK);
        assertEquals("io.rpcell.TestService", decoded.getInterfaceName());
        assertEquals("sayHello", decoded.getMethodName());
    }

    @Test
    public void testEncodeDecodeResponseRoundtrip() {
        Result result = Result.success("ok");
        byte[] frame = protocol.encodeResponse(result, 67890L, Serializers.MSGPACK);

        byte[] body = protocol.extractBody(frame);
        Result decoded = protocol.decodeResponse(body, Serializers.MSGPACK);
        assertEquals("ok", decoded.getValue());
    }

    @Test
    public void testFrameHeaderParsing() {
        Invocation inv = new Invocation();
        inv.setInterfaceName("test");
        inv.setMethodName("m");

        byte[] frame = protocol.encodeRequest(inv, 0xABCDL, Serializers.MSGPACK);
        byte[] header = new byte[ProtocolConstants.HEADER_LENGTH];
        System.arraycopy(frame, 0, header, 0, ProtocolConstants.HEADER_LENGTH);

        RpcCellProtocol.FrameHeader fh = protocol.parseHeader(header);
        assertEquals(frame.length, fh.totalLength);
        assertEquals(Serializers.MSGPACK, fh.serializationId);
        assertEquals(ProtocolConstants.MESSAGE_TYPE_REQUEST, fh.messageType);
        assertEquals(0xABCDL, fh.requestId);
    }

    @Test
    public void testExtractBodyEmpty() {
        byte[] body = protocol.extractBody(new byte[ProtocolConstants.HEADER_LENGTH]);
        assertEquals(0, body.length);
    }

    @Test
    public void testFrameTooShort() {
        assertThrows(Exception.class, () -> protocol.extractBody(new byte[10]));
    }

    @Test
    public void testHeaderTooShort() {
        assertThrows(Exception.class, () -> protocol.parseHeader(new byte[10]));
    }
}
