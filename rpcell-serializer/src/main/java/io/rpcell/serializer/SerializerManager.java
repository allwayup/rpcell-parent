package io.rpcell.serializer;

import io.rpcell.api.constant.Serializers;
import io.rpcell.api.model.SerializerConfig;
import io.rpcell.api.spi.Serializer;
import io.rpcell.api.spi.SerializerFactory;
import io.rpcell.spi.ExtensionLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SerializerManager {

    private static final Logger LOG = LoggerFactory.getLogger(SerializerManager.class);

    private static final Map<Byte, Serializer> CACHE = new ConcurrentHashMap<>();
    private static volatile Serializer defaultSerializer;

    private SerializerManager() {}

    public static synchronized void init(SerializerConfig config) {
        if (defaultSerializer != null) {
            return;
        }
        byte defaultId;
        try {
            defaultId = Serializers.idOf(config.getType());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("No serializer registered for default type: " + config.getType(), e);
        }

        for (Map.Entry<String, Class<?>> entry :
                ExtensionLoader.registeredExtensions().entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith("serializer:")) {
                continue;
            }
            Class<?> factoryClass = entry.getValue();
            if (!SerializerFactory.class.isAssignableFrom(factoryClass)) {
                continue;
            }
            try {
                SerializerFactory factory = (SerializerFactory)
                        factoryClass.getDeclaredConstructor().newInstance();
                Serializer serializer = factory.create(config);
                byte id = serializer.id();
                CACHE.put(id, serializer);
                LOG.info("Loaded serializer: {} (id=0x{})", Serializers.nameOf(id), Integer.toHexString(id));

                if (id == defaultId && defaultSerializer == null) {
                    defaultSerializer = serializer;
                }
            } catch (Exception e) {
                if (name.equals("serializer:" + config.getType())) {
                    throw new IllegalStateException("Failed to load default serializer: " + config.getType(), e);
                }
                LOG.warn("Failed to load serializer from factory: {}", name, e);
            }
        }

        if (defaultSerializer == null) {
            throw new IllegalStateException("No serializer registered for default type: " + config.getType());
        } else {
            LOG.info(
                    "Default serializer set to: {} (id=0x{})",
                    Serializers.nameOf(defaultSerializer.id()),
                    Integer.toHexString(defaultSerializer.id()));
        }
    }

    public static Serializer getSerializer(byte serializationId) {
        Serializer s = CACHE.get(serializationId);
        if (s == null) {
            throw new IllegalArgumentException("No serializer found for id: 0x" + Integer.toHexString(serializationId));
        }
        return s;
    }

    public static Serializer getDefaultSerializer() {
        if (defaultSerializer == null) {
            throw new IllegalStateException("SerializerManager not initialized");
        }
        return defaultSerializer;
    }

    public static Serializer getDefaultOrNull() {
        return defaultSerializer;
    }

    public static boolean isInitialized() {
        return defaultSerializer != null;
    }

    public static void clear() {
        CACHE.clear();
        defaultSerializer = null;
    }
}
