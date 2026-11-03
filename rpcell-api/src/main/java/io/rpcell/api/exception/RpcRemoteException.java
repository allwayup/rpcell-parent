package io.rpcell.api.exception;

/**
 * Client-side exception for a remote invocation failure.
 * Preserves the remote error code and exception type without instantiating an arbitrary remote class.
 */
public class RpcRemoteException extends RpcException {

    private final String remoteExceptionClassName;

    public RpcRemoteException(int errorCode, String message, String remoteExceptionClassName) {
        super(errorCode, message);
        this.remoteExceptionClassName = remoteExceptionClassName;
    }

    public String getRemoteExceptionClassName() {
        return remoteExceptionClassName;
    }
}
