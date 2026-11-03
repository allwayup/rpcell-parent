package io.rpcell.codec;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.constant.ProtocolConstants;
import io.rpcell.api.constant.Serializers;
import io.rpcell.api.exception.RpcSerializationException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.SerializerConfig;
import io.rpcell.serializer.SerializerManager;
import io.rpcell.serializer.msgpack.MessagePackSerializerFactory;
import io.rpcell.spi.ExtensionLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RpcCellProtocolEdgeTest {

    private static RpcCellProtocol protocol;

    @BeforeAll
    static void setUpOnce() {
        ExtensionLoader.register("serializer:msgpack", MessagePackSerializerFactory.class);
        SerializerManager.init(new SerializerConfig());
        protocol = new RpcCellProtocol();
    }

    @Nested
    class LargeFrame {

        @Test
        void shouldHandleLargeInvocation() {
            Invocation inv = new Invocation();
            inv.setInterfaceName("io.rpcell.LargeService");
            inv.setMethodName("processLarge");
            byte[] payload = new byte[65536];
            inv.setArguments(new Object[] {payload});

            byte[] frame = protocol.encodeRequest(inv, 1L, Serializers.MSGPACK);
            assertTrue(frame.length > ProtocolConstants.HEADER_LENGTH + 65500);

            byte[] body = protocol.extractBody(frame);
            Invocation decoded = protocol.decodeRequest(body, Serializers.MSGPACK);
            assertEquals("io.rpcell.LargeService", decoded.getInterfaceName());
        }

        @Test
        void shouldSupportMaxJavaLong() {
            Invocation inv = new Invocation();
            inv.setInterfaceName("test");
            inv.setMethodName("m");
            byte[] frame = protocol.encodeRequest(inv, Long.MAX_VALUE, Serializers.MSGPACK);
            byte[] header = new byte[ProtocolConstants.HEADER_LENGTH];
            System.arraycopy(frame, 0, header, 0, ProtocolConstants.HEADER_LENGTH);

            RpcCellProtocol.FrameHeader fh = protocol.parseHeader(header);
            assertEquals(Long.MAX_VALUE, fh.requestId);
        }

        @Test
        void shouldSupportMinJavaLong() {
            Invocation inv = new Invocation();
            inv.setInterfaceName("test");
            inv.setMethodName("m");
            byte[] frame = protocol.encodeRequest(inv, Long.MIN_VALUE, Serializers.MSGPACK);
            byte[] header = new byte[ProtocolConstants.HEADER_LENGTH];
            System.arraycopy(frame, 0, header, 0, ProtocolConstants.HEADER_LENGTH);

            RpcCellProtocol.FrameHeader fh = protocol.parseHeader(header);
            assertEquals(Long.MIN_VALUE, fh.requestId);
        }
    }

    @Nested
    class MultipleRoundtrips {

        @Test
        void shouldMaintainConsistencyOver100Roundtrips() {
            for (int i = 0; i < 100; i++) {
                Invocation inv = new Invocation();
                inv.setInterfaceName("io.rpcell.Service" + i);
                inv.setMethodName("method" + i);
                inv.setArguments(new Object[] {"arg" + i, i});

                byte[] frame = protocol.encodeRequest(inv, i, Serializers.MSGPACK);
                byte[] body = protocol.extractBody(frame);
                Invocation decoded = protocol.decodeRequest(body, Serializers.MSGPACK);

                assertEquals("io.rpcell.Service" + i, decoded.getInterfaceName());
                assertEquals("method" + i, decoded.getMethodName());
                assertArrayEquals(new Object[] {"arg" + i, i}, decoded.getArguments());
            }
        }
    }

    @Nested
    class ErrorResponseRoundtrip {

        @Test
        void shouldEncodeDecodeErrorResult() {
            Result.RpcResultException rpcEx = new Result.RpcResultException("server timeout", "TimeoutException", 1002);
            Result errorResult = Result.failure(rpcEx);
            byte[] frame = protocol.encodeResponse(errorResult, 42L, Serializers.MSGPACK);

            byte[] body = protocol.extractBody(frame);
            Result decoded = protocol.decodeResponse(body, Serializers.MSGPACK);

            assertTrue(decoded.hasException());
            assertNull(decoded.getValue());
        }
    }

    @Nested
    class DecodeError {

        @Test
        void shouldThrowOnInvalidBodyBytes() {
            byte[] corrupt = {0x01, 0x02, 0x03};
            assertThrows(RpcSerializationException.class, () -> protocol.decodeRequest(corrupt, Serializers.MSGPACK));
        }

        @Test
        void shouldThrowOnInvalidResponseBody() {
            byte[] corrupt = {(byte) 0xFF, (byte) 0xFE};
            assertThrows(RpcSerializationException.class, () -> protocol.decodeResponse(corrupt, Serializers.MSGPACK));
        }

        @Test
        void shouldNotFallbackForUnknownSerializationId() {
            assertThrows(RpcSerializationException.class, () -> protocol.decodeRequest(new byte[] {0x01}, (byte) 0x7F));
            assertThrows(
                    IllegalArgumentException.class, () -> protocol.encodeRequest(new Invocation(), 1L, (byte) 0x7F));
        }
    }

    @Nested
    class ExtractBodyEdgeCases {

        @Test
        void shouldReturnEmptyBodyForHeaderOnlyFrame() {
            byte[] headerOnly = new byte[ProtocolConstants.HEADER_LENGTH];
            byte[] body = protocol.extractBody(headerOnly);
            assertEquals(0, body.length);
        }

        @Test
        void shouldThrowOnSingleByteFrame() {
            assertThrows(RpcSerializationException.class, () -> protocol.extractBody(new byte[1]));
        }

        @Test
        void shouldThrowOnNull() {
            assertThrows(NullPointerException.class, () -> protocol.extractBody(null));
        }
    }

    @Nested
    class ParseHeaderEdgeCases {

        @Test
        void shouldParseHeaderWithSingleByteBody() {
            int serializationId = Serializers.MSGPACK;
            byte messageType = ProtocolConstants.MESSAGE_TYPE_RESPONSE;
            long requestId = 0xCAFEBABE;

            byte[] body = {0x00};
            int totalLength = ProtocolConstants.HEADER_LENGTH + body.length;
            byte[] frame = new byte[totalLength];

            frame[0] = (byte) (totalLength >> 24);
            frame[1] = (byte) (totalLength >> 16);
            frame[2] = (byte) (totalLength >> 8);
            frame[3] = (byte) totalLength;
            frame[4] = (byte) serializationId;
            frame[5] = messageType;
            frame[6] = (byte) (requestId >> 56);
            frame[7] = (byte) (requestId >> 48);
            frame[8] = (byte) (requestId >> 40);
            frame[9] = (byte) (requestId >> 32);
            frame[10] = (byte) (requestId >> 24);
            frame[11] = (byte) (requestId >> 16);
            frame[12] = (byte) (requestId >> 8);
            frame[13] = (byte) requestId;

            byte[] header = new byte[ProtocolConstants.HEADER_LENGTH];
            System.arraycopy(frame, 0, header, 0, ProtocolConstants.HEADER_LENGTH);
            RpcCellProtocol.FrameHeader fh = protocol.parseHeader(header);

            assertEquals(totalLength, fh.totalLength);
            assertEquals(serializationId, fh.serializationId);
            assertEquals(ProtocolConstants.MESSAGE_TYPE_RESPONSE, fh.messageType);
            assertEquals(0xCAFEBABE, fh.requestId);
        }

        @Test
        void shouldThrowOnEmptyHeader() {
            assertThrows(RpcSerializationException.class, () -> protocol.parseHeader(new byte[0]));
        }

        @Test
        void shouldThrowOn13ByteHeader() {
            assertThrows(RpcSerializationException.class, () -> protocol.parseHeader(new byte[13]));
        }
    }

    @Nested
    class UnsupportedOperations {

        @Test
        void shouldThrowOnExport() {
            assertThrows(UnsupportedOperationException.class, () -> protocol.export(null, null));
        }

        @Test
        void shouldThrowOnRefer() {
            assertThrows(UnsupportedOperationException.class, () -> protocol.refer("test"));
        }

        @Test
        void shouldReturnSelfFromCodecHandler() {
            assertSame(protocol, protocol.codecHandler());
        }
    }
}
