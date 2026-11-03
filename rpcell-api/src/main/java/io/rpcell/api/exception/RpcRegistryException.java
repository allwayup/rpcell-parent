package io.rpcell.api.exception;

/**
 * Thrown when a registry center operation fails.
 */
public class RpcRegistryException extends RpcException {

    public RpcRegistryException(String message) {
        super(message);
    }

    public RpcRegistryException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcRegistryException(int errorCode, String message) {
        super(errorCode, message);
    }

    public RpcRegistryException(int errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
