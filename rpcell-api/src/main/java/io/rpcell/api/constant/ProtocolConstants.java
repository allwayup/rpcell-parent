package io.rpcell.api.constant;

/**
 * Protocol frame format constants.
 * Frame: [4-byte total length][1-byte serialization ID][1-byte message type][8-byte request ID][body]
 */
public final class ProtocolConstants {

    private ProtocolConstants() {}

    public static final int LENGTH_FIELD_OFFSET = 0;
    public static final int LENGTH_FIELD_LENGTH = 4;
    public static final int LENGTH_ADJUSTMENT = -4;
    public static final int INITIAL_BYTES_TO_STRIP = 0;

    public static final int POST_LENGTH_HEADER_LENGTH = 10;

    public static final int SERIALIZATION_ID_OFFSET = 4;
    public static final int SERIALIZATION_ID_LENGTH = 1;

    public static final int MESSAGE_TYPE_OFFSET = 5;
    public static final int MESSAGE_TYPE_LENGTH = 1;

    public static final int REQUEST_ID_OFFSET = 6;
    public static final int REQUEST_ID_LENGTH = 8;

    public static final int HEADER_LENGTH = 14;

    public static final byte MESSAGE_TYPE_REQUEST = 1;
    public static final byte MESSAGE_TYPE_RESPONSE = 2;

    public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024;
}
