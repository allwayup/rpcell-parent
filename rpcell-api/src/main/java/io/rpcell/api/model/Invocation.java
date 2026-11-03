package io.rpcell.api.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * RPC invocation data carrier, carrying all information needed for a single call.
 */
public class Invocation implements Serializable {

    private static final long serialVersionUID = 1L;

    private String interfaceName;
    private String methodName;
    private Class<?>[] parameterTypes;
    private Object[] arguments;
    private Map<String, String> attachments;

    public Invocation() {
        this.attachments = new HashMap<>();
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    public Map<String, String> getAttachments() {
        return attachments;
    }

    public void setAttachments(Map<String, String> attachments) {
        this.attachments = attachments;
    }
}
