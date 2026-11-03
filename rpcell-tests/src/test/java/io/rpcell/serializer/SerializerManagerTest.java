package io.rpcell.serializer;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.model.SerializerConfig;
import io.rpcell.serializer.msgpack.MessagePackSerializerFactory;
import io.rpcell.spi.ExtensionLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SerializerManagerTest {

    @BeforeAll
    static void registerExtensions() {
        ExtensionLoader.register("serializer:msgpack", MessagePackSerializerFactory.class);
    }

    @BeforeEach
    void setUp() {
        SerializerManager.clear();
    }

    @AfterEach
    void tearDown() {
        SerializerManager.clear();
    }

    @Nested
    class Initialization {

        @Test
        void shouldInitSuccessfully() {
            SerializerManager.init(new SerializerConfig());
            assertNotNull(SerializerManager.getDefaultSerializer());
        }

        @Test
        void shouldOnlyInitOnce() {
            SerializerManager.init(new SerializerConfig());
            SerializerManager.init(new SerializerConfig());
            assertNotNull(SerializerManager.getDefaultSerializer());
        }

        @Test
        void shouldFailWhenDefaultSerializerIsNotRegistered() {
            SerializerConfig config = new SerializerConfig();
            config.setType("json");
            assertThrows(IllegalStateException.class, () -> SerializerManager.init(config));
        }

        @Test
        void shouldGetSerializerById() {
            SerializerManager.init(new SerializerConfig());
            assertNotNull(SerializerManager.getSerializer((byte) 0x01));
        }

        @Test
        void shouldDefaultSerializerBeMsgpack() {
            SerializerManager.init(new SerializerConfig());
            assertNotNull(SerializerManager.getDefaultOrNull());
        }
    }

    @Nested
    class NotInitialized {

        @Test
        void shouldThrowWhenGetDefaultSerializer() {
            assertThrows(IllegalStateException.class, SerializerManager::getDefaultSerializer);
        }

        @Test
        void shouldReturnNullFromGetDefaultOrNull() {
            assertNull(SerializerManager.getDefaultOrNull());
        }

        @Test
        void shouldThrowForUnknownSerializer() {
            SerializerManager.init(new SerializerConfig());
            assertThrows(IllegalArgumentException.class, () -> SerializerManager.getSerializer((byte) 0xFF));
        }
    }
}
