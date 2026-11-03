package io.rpcell.api.spi;

import io.rpcell.api.model.SerializerConfig;

/**
 * Factory for creating Serializer instances.
 * Each serialization implementation provides its own factory registered via SPI.
 */
public interface SerializerFactory {

    Serializer create(SerializerConfig config);
}
