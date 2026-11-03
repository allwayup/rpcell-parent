package io.rpcell.api.exception;

/**
 * Thrown when an RPC parameter validation fails.
 */
public class RpcParamException extends RpcException {

    public RpcParamException(String message) {
        super(message);
    }

    public RpcParamException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcParamException(int errorCode, String message) {
        super(errorCode, message);
    }

    public RpcParamException(int errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
