package io.rpcell.api.enums;

/**
 * RPC result codes for error classification.
 * Code values must be consistent across all language implementations.
 */
public enum ResultCodes {
    OK(0, "Success"),
    CLIENT_TIMEOUT(1001, "Client side timeout"),
    SERVER_TIMEOUT(1002, "Server side timeout"),
    SERVICE_NOT_FOUND(2001, "Service not found"),
    METHOD_NOT_EXPOSED(2002, "Method not exposed"),
    METHOD_NOT_FOUND(2003, "Method not found"),
    PARAMETER_ERROR(3001, "Parameter validation error"),
    SERIALIZATION_ERROR(4001, "Serialization error"),
    TRANSPORT_ERROR(5001, "Transport error"),
    REGISTRY_ERROR(6001, "Registry error"),
    CLUSTER_ERROR(7001, "Cluster invocation error"),
    UNKNOWN_ERROR(9999, "Unknown error");

    private final int code;
    private final String description;

    ResultCodes(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
