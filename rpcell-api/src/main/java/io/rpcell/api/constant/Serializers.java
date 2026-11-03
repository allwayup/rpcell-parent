package io.rpcell.api.constant;

/**
 * Serializer type constants and serialization ID allocation.
 * Serialization IDs must be consistent across all language implementations.
 */
public final class Serializers {

    private Serializers() {}

    public static final byte MSGPACK = 0x01;

    public static final String MSGPACK_NAME = "msgpack";

    public static String nameOf(byte id) {
        if (id == MSGPACK) {
            return MSGPACK_NAME;
        }
        throw new IllegalArgumentException("Unknown serialization id: " + id);
    }

    public static byte idOf(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Serialization name must not be null");
        }
        if (MSGPACK_NAME.equals(name.toLowerCase())) {
            return MSGPACK;
        }
        throw new IllegalArgumentException("Unknown serialization name: " + name);
    }
}
