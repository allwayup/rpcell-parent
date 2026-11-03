package io.rpcell.api.spi;

/**
 * Serialization extension point.
 */
public interface Serializer {

    byte id();

    byte[] serialize(Object object);

    <T> T deserialize(byte[] data, Class<T> type);

    String contentType();
}
