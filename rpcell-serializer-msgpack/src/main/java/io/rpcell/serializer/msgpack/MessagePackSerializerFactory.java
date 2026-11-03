package io.rpcell.serializer.msgpack;

import io.rpcell.api.model.SerializerConfig;
import io.rpcell.api.spi.Serializer;
import io.rpcell.api.spi.SerializerFactory;

/**
 * Factory for creating MessagePackSerializer instances.
 * Registered via SPI as "serializer:msgpack".
 */
public final class MessagePackSerializerFactory implements SerializerFactory {

    @Override
    public Serializer create(SerializerConfig config) {
        return new MessagePackSerializer(config);
    }
}
