package io.rpcell.serializer.msgpack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.rpcell.api.constant.Serializers;
import io.rpcell.api.exception.RpcSerializationException;
import io.rpcell.api.model.SerializerConfig;
import io.rpcell.api.spi.Serializer;
import java.util.List;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * MessagePack serializer implementation using Jackson MessagePack backend.
 * ID: 0x01 (MSGPACK). Uses SNAKE_CASE property naming for cross-language
 * wire-format consistency with Rust implementations.
 */
public final class MessagePackSerializer implements Serializer {

    private final ObjectMapper mapper;
    private final List<String> whitelist;
    private final boolean allowJdkTypes;

    public MessagePackSerializer(SerializerConfig config) {
        this.mapper = new ObjectMapper(new MessagePackFactory());
        this.mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.whitelist = config.getWhitelist() == null ? List.of() : List.copyOf(config.getWhitelist());
        this.allowJdkTypes = config.isAllowJdkTypes();
    }

    @Override
    public byte id() {
        return Serializers.MSGPACK;
    }

    @Override
    public byte[] serialize(Object object) {
        if (object == null) {
            return new byte[0];
        }
        try {
            return mapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new RpcSerializationException("MessagePack serialization failed", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> type) {
        if (data == null || data.length == 0) {
            return null;
        }
        validateDeserializationType(type);
        try {
            return mapper.readValue(data, type);
        } catch (Exception e) {
            throw new RpcSerializationException("MessagePack deserialization failed", e);
        }
    }

    private void validateDeserializationType(Class<?> type) {
        Class<?> targetType = type.isArray() ? type.getComponentType() : type;
        if (targetType.isPrimitive()) {
            return;
        }
        String className = targetType.getName();
        if (!allowJdkTypes && className.startsWith("java.")) {
            throw new RpcSerializationException("JDK type is not allowed for deserialization: " + className);
        }
        if (whitelist.isEmpty()) {
            return;
        }
        for (String allowed : whitelist) {
            if (allowed == null || allowed.isBlank()) {
                continue;
            }
            String rule = allowed.trim();
            if (className.equals(rule)
                    || (rule.endsWith(".*") && className.startsWith(rule.substring(0, rule.length() - 1)))) {
                return;
            }
        }
        throw new RpcSerializationException("Type is not allowed for deserialization: " + className);
    }

    @Override
    public String contentType() {
        return "application/x-msgpack";
    }
}
