package io.rpcell.api.model;

/**
 * Unified RPC call response: success (value set) or failure (exception set).
 */
public class Result implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Object value;
    private RpcResultException exception;

    public Result() {}

    private Result(Object value, RpcResultException exception) {
        this.value = value;
        this.exception = exception;
    }

    public static Result success(Object value) {
        return new Result(value, null);
    }

    public static Result failure(RpcResultException exception) {
        return new Result(null, exception);
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public RpcResultException getException() {
        return exception;
    }

    public void setException(RpcResultException exception) {
        this.exception = exception;
    }

    public boolean hasValue() {
        return exception == null;
    }

    public boolean hasException() {
        return exception != null;
    }

    /**
     * Lightweight exception carrier for RPC responses; only message + class name,
     * not the full stack trace.
     */
    public static class RpcResultException implements java.io.Serializable {

        private static final long serialVersionUID = 1L;

        private String message;
        private String exceptionClassName;
        private int errorCode;

        public RpcResultException() {}

        public RpcResultException(String message, String exceptionClassName, int errorCode) {
            this.message = message;
            this.exceptionClassName = exceptionClassName;
            this.errorCode = errorCode;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getExceptionClassName() {
            return exceptionClassName;
        }

        public void setExceptionClassName(String exceptionClassName) {
            this.exceptionClassName = exceptionClassName;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(int errorCode) {
            this.errorCode = errorCode;
        }
    }
}
