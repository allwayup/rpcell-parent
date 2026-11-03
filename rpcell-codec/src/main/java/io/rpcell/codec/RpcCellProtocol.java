package io.rpcell.codec;

import io.rpcell.api.constant.ProtocolConstants;
import io.rpcell.api.exception.RpcSerializationException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.spi.Protocol;
import io.rpcell.api.spi.Serializer;
import io.rpcell.serializer.SerializerManager;

/**
 * RPCell protocol implementation handling frame encoding/decoding.
 * Uses SPI-loaded serializers via SerializerManager (no compile-time dependency on any serializer implementation).
 * Frame: [4-byte length][1-byte serialization ID][1-byte message type][8-byte request ID][body]
 */
public class RpcCellProtocol implements Protocol {

    public RpcCellProtocol() {}

    private Serializer resolveSerializer(int serializationId) {
        return SerializerManager.getSerializer((byte) serializationId);
    }

    /**
     * Encode a request message into the frame format.
     *
     * @param invocation the invocation to encode
     * @param requestId the request ID
     * @return the encoded frame bytes
     */
    public byte[] encodeRequest(Invocation invocation, long requestId, int serializationId) {
        Serializer serializer = resolveSerializer(serializationId);
        byte[] body = serializer.serialize(invocation);
        return encodeFrame(body, serializationId, ProtocolConstants.MESSAGE_TYPE_REQUEST, requestId);
    }

    /**
     * Encode a response message into the frame format.
     *
     * @param result the result to encode
     * @param requestId the request ID
     * @return the encoded frame bytes
     */
    public byte[] encodeResponse(Result result, long requestId, int serializationId) {
        Serializer serializer = resolveSerializer(serializationId);
        byte[] body = serializer.serialize(result);
        return encodeFrame(body, serializationId, ProtocolConstants.MESSAGE_TYPE_RESPONSE, requestId);
    }

    /**
     * Decode a frame body into an Invocation.
     *
     * @param frameBody the frame body bytes
     * @param serializationId the serialization ID from the frame header
     * @return the decoded Invocation
     */
    public Invocation decodeRequest(byte[] frameBody, int serializationId) {
        try {
            Serializer serializer = resolveSerializer(serializationId);
            return serializer.deserialize(frameBody, Invocation.class);
        } catch (Exception e) {
            throw new RpcSerializationException("Failed to decode request body", e);
        }
    }

    /**
     * Decode a frame body into a Result.
     *
     * @param frameBody the frame body bytes
     * @param serializationId the serialization ID from the frame header
     * @return the decoded Result
     */
    public Result decodeResponse(byte[] frameBody, int serializationId) {
        try {
            Serializer serializer = resolveSerializer(serializationId);
            return serializer.deserialize(frameBody, Result.class);
        } catch (Exception e) {
            throw new RpcSerializationException("Failed to decode response body: " + e.getMessage(), e);
        }
    }

    /**
     * Build a complete frame with header and body.
     */
    private byte[] encodeFrame(byte[] body, int serializationId, byte messageType, long requestId) {
        int totalLength = ProtocolConstants.HEADER_LENGTH + body.length;
        byte[] frame = new byte[totalLength];

        frame[0] = (byte) (totalLength >> 24);
        frame[1] = (byte) (totalLength >> 16);
        frame[2] = (byte) (totalLength >> 8);
        frame[3] = (byte) totalLength;

        frame[ProtocolConstants.SERIALIZATION_ID_OFFSET] = (byte) serializationId;
        frame[ProtocolConstants.MESSAGE_TYPE_OFFSET] = messageType;

        frame[ProtocolConstants.REQUEST_ID_OFFSET] = (byte) (requestId >> 56);
        frame[ProtocolConstants.REQUEST_ID_OFFSET + 1] = (byte) (requestId >> 48);
        frame[ProtocolConstants.REQUEST_ID_OFFSET + 2] = (byte) (requestId >> 40);
        frame[ProtocolConstants.REQUEST_ID_OFFSET + 3] = (byte) (requestId >> 32);
        frame[ProtocolConstants.REQUEST_ID_OFFSET + 4] = (byte) (requestId >> 24);
        frame[ProtocolConstants.REQUEST_ID_OFFSET + 5] = (byte) (requestId >> 16);
        frame[ProtocolConstants.REQUEST_ID_OFFSET + 6] = (byte) (requestId >> 8);
        frame[ProtocolConstants.REQUEST_ID_OFFSET + 7] = (byte) requestId;

        System.arraycopy(body, 0, frame, ProtocolConstants.HEADER_LENGTH, body.length);
        return frame;
    }

    /**
     * Extract the body bytes from a full frame (header + body).
     *
     * @param fullFrame the complete frame bytes including header
     * @return the body bytes (after the 14-byte header)
     */
    public byte[] extractBody(byte[] fullFrame) {
        if (fullFrame.length < ProtocolConstants.HEADER_LENGTH) {
            throw new RpcSerializationException("Frame too short: " + fullFrame.length);
        }
        int bodyLength = fullFrame.length - ProtocolConstants.HEADER_LENGTH;
        if (bodyLength == 0) {
            return new byte[0];
        }
        byte[] body = new byte[bodyLength];
        System.arraycopy(fullFrame, ProtocolConstants.HEADER_LENGTH, body, 0, bodyLength);
        return body;
    }

    /**
     * Parse frame-level fields from the header bytes (full 14-byte header including length prefix).
     */
    public FrameHeader parseHeader(byte[] header) {
        if (header.length < ProtocolConstants.HEADER_LENGTH) {
            throw new RpcSerializationException("Frame header too short: " + header.length);
        }
        FrameHeader fh = new FrameHeader();
        int totalLength = ((header[0] & 0xFF) << 24)
                | ((header[1] & 0xFF) << 16)
                | ((header[2] & 0xFF) << 8)
                | (header[3] & 0xFF);
        fh.totalLength = totalLength;
        fh.serializationId = header[ProtocolConstants.SERIALIZATION_ID_OFFSET] & 0xFF;
        fh.messageType = header[ProtocolConstants.MESSAGE_TYPE_OFFSET];
        long requestId = ((long) (header[ProtocolConstants.REQUEST_ID_OFFSET] & 0xFF) << 56)
                | ((long) (header[ProtocolConstants.REQUEST_ID_OFFSET + 1] & 0xFF) << 48)
                | ((long) (header[ProtocolConstants.REQUEST_ID_OFFSET + 2] & 0xFF) << 40)
                | ((long) (header[ProtocolConstants.REQUEST_ID_OFFSET + 3] & 0xFF) << 32)
                | ((long) (header[ProtocolConstants.REQUEST_ID_OFFSET + 4] & 0xFF) << 24)
                | ((long) (header[ProtocolConstants.REQUEST_ID_OFFSET + 5] & 0xFF) << 16)
                | ((long) (header[ProtocolConstants.REQUEST_ID_OFFSET + 6] & 0xFF) << 8)
                | (header[ProtocolConstants.REQUEST_ID_OFFSET + 7] & 0xFF);
        fh.requestId = requestId;
        return fh;
    }

    @Override
    public void export(io.rpcell.api.model.ServiceDescriptor descriptor, Object instance) {
        throw new UnsupportedOperationException("Protocol.export should be handled by rpcell-server");
    }

    @Override
    public void refer(String serviceName) {
        throw new UnsupportedOperationException("Protocol.refer should be handled by rpcell-proxy");
    }

    @Override
    public Object codecHandler() {
        return this;
    }

    /**
     * Parsed frame header fields.
     */
    public static class FrameHeader {
        public int totalLength;
        public int serializationId;
        public byte messageType;
        public long requestId;
    }
}
