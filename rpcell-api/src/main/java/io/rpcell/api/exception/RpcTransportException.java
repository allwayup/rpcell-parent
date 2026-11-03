package io.rpcell.api.exception;

/**
 * Thrown when a transport-level error occurs (connection, timeout, send/receive failure).
 */
public class RpcTransportException extends RpcException {

    public RpcTransportException(String message) {
        super(message);
    }

    public RpcTransportException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcTransportException(int errorCode, String message) {
        super(errorCode, message);
    }

    public RpcTransportException(int errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
