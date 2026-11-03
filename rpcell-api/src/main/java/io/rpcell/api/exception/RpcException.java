package io.rpcell.api.exception;

/**
 * Base exception for all RPCell framework errors.
 */
public class RpcException extends RuntimeException {

    private final int errorCode;

    public RpcException(String message) {
        super(message);
        this.errorCode = 0;
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
    }

    public RpcException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RpcException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
