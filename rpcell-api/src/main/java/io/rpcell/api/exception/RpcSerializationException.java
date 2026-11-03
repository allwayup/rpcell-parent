package io.rpcell.api.exception;

/**
 * Thrown when serialization or deserialization fails.
 */
public class RpcSerializationException extends RpcException {

    public RpcSerializationException(String message) {
        super(message);
    }

    public RpcSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcSerializationException(int errorCode, String message) {
        super(errorCode, message);
    }

    public RpcSerializationException(int errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
