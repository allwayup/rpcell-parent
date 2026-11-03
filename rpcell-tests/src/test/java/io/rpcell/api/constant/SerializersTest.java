package io.rpcell.api.constant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SerializersTest {

    @Test
    void shouldMapNamesToIds() {
        assertEquals((byte) 0x01, Serializers.idOf("msgpack"));
    }

    @Test
    void shouldThrowOnNullName() {
        assertThrows(IllegalArgumentException.class, () -> Serializers.idOf(null));
    }

    @Test
    void shouldThrowOnUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> Serializers.idOf("unknown-serializer"));
    }

    @Test
    void shouldMapIdsToNames() {
        assertEquals("msgpack", Serializers.nameOf((byte) 0x01));
    }

    @Test
    void shouldThrowOnUnknownId() {
        assertThrows(IllegalArgumentException.class, () -> Serializers.nameOf((byte) 0xFF));
    }

    @Nested
    class KnownConstants {

        @Test
        void msgpackIdShouldBeOne() {
            assertEquals(0x01, Serializers.MSGPACK);
        }
    }
}
