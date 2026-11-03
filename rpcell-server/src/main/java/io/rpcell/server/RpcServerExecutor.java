package io.rpcell.server;

import io.rpcell.api.constant.Attachments;
import io.rpcell.api.enums.ResultCodes;
import io.rpcell.api.exception.RpcParamException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.ServiceDescriptor;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.Serializer;
import io.rpcell.serializer.SerializerManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side service execution engine.
 * Maintains a service table and performs method visibility checks before reflection execution.
 */
public class RpcServerExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RpcServerExecutor.class);

    private final Map<String, ServiceDescriptor> serviceTable = new ConcurrentHashMap<>();
    private volatile String host;
    private volatile int port;

    public RpcServerExecutor(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Register a service descriptor into the service table.
     * The key is serviceName + ":" + group.
     *
     * @param descriptor the service descriptor to register
     * @param checkConflict if true, throws on duplicate service key without explicit group
     */
    public void registerService(ServiceDescriptor descriptor, boolean checkConflict) {
        String serviceKey = descriptor.toServiceKey();
        if (checkConflict && serviceTable.containsKey(serviceKey)) {
            throw new RpcParamException(
                    "Duplicate service key '" + serviceKey + "': multiple implementations without distinct group");
        }
        serviceTable.put(serviceKey, descriptor);
        LOG.info(
                "Registered service: {} (interface={}, methods={})",
                serviceKey,
                descriptor.getInterfaceClass().getName(),
                descriptor.getMethods().size());
    }

    public void replaceService(ServiceDescriptor oldDescriptor, ServiceDescriptor newDescriptor) {
        String oldKey = oldDescriptor.toServiceKey();
        String newKey = newDescriptor.toServiceKey();
        serviceTable.remove(oldKey);
        serviceTable.put(newKey, newDescriptor);
        LOG.info(
                "Replaced service: {} -> {} (interface={})",
                oldKey,
                newKey,
                newDescriptor.getInterfaceClass().getName());
    }

    /**
     * Execute an invocation: lookup service descriptor, validate method, reflectively invoke.
     *
     * @param invocation the invocation to execute
     * @return the result
     */
    public Result execute(Invocation invocation) {
        long startTime = System.currentTimeMillis();
        String interfaceName = invocation.getInterfaceName();
        String methodName = invocation.getMethodName();

        ServiceDescriptor descriptor = findService(invocation);
        if (descriptor == null) {
            LOG.warn("Service not found: {}", interfaceName);
            return Result.failure(new Result.RpcResultException(
                    "Service not found: " + interfaceName,
                    RpcParamException.class.getName(),
                    ResultCodes.SERVICE_NOT_FOUND.getCode()));
        }

        if (!descriptor.containsMethod(methodName)) {
            LOG.warn("Method not exposed: {}#{}", interfaceName, methodName);
            return Result.failure(new Result.RpcResultException(
                    "Method not exposed: " + interfaceName + "#" + methodName,
                    RpcParamException.class.getName(),
                    ResultCodes.METHOD_NOT_EXPOSED.getCode()));
        }

        Object instance = descriptor.getInstance();
        if (instance == null) {
            return Result.failure(new Result.RpcResultException(
                    "Service instance is null: " + interfaceName,
                    RpcParamException.class.getName(),
                    ResultCodes.SERVICE_NOT_FOUND.getCode()));
        }

        try {
            Method method = findMethod(descriptor.getInterfaceClass(), methodName, invocation.getParameterTypes());
            if (method == null) {
                return Result.failure(new Result.RpcResultException(
                        "Method not found: " + interfaceName + "#" + methodName,
                        RpcParamException.class.getName(),
                        ResultCodes.METHOD_NOT_FOUND.getCode()));
            }

            Object[] args = convertArguments(invocation.getArguments(), method);

            Object value = method.invoke(instance, args);
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.debug("Executed {}.{} in {}ms", interfaceName, methodName, elapsed);
            return Result.success(value);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOG.error("Error executing {}.{}: {}", interfaceName, methodName, cause.getMessage());
            return Result.failure(new Result.RpcResultException(
                    cause.getMessage() != null
                            ? cause.getMessage()
                            : cause.getClass().getName(),
                    cause.getClass().getName(),
                    ResultCodes.UNKNOWN_ERROR.getCode()));
        } catch (Exception e) {
            LOG.error("Error executing {}.{}: {}", interfaceName, methodName, e.getMessage());
            return Result.failure(new Result.RpcResultException(
                    e.getMessage() != null ? e.getMessage() : e.getClass().getName(),
                    e.getClass().getName(),
                    ResultCodes.UNKNOWN_ERROR.getCode()));
        }
    }

    /**
     * Export this executor's URL for registration.
     *
     * @param serviceName the service name
     * @param group the service group
     * @param version the service version
     * @return the URL for registry registration
     */
    public URL exportUrl(String serviceName, String group, String version) {
        return new URL("rpcell", host, port, serviceName, group, version);
    }

    /**
     * Build the URL list for all registered services.
     *
     * @return unmodifiable set of all URLs
     */
    public Set<String> getRegisteredServiceKeys() {
        return Collections.unmodifiableSet(serviceTable.keySet());
    }

    public Map<String, ServiceDescriptor> getServiceTable() {
        return Collections.unmodifiableMap(serviceTable);
    }

    public ServiceDescriptor findService(Invocation invocation) {
        String group = invocation.getAttachments() != null
                ? invocation.getAttachments().get(Attachments.GROUP)
                : null;
        String version = invocation.getAttachments() != null
                ? invocation.getAttachments().get(Attachments.VERSION)
                : null;
        for (ServiceDescriptor descriptor : serviceTable.values()) {
            String descriptorGroup = descriptor.getGroup() != null ? descriptor.getGroup() : "";
            String descriptorVersion = descriptor.getVersion() != null ? descriptor.getVersion() : "";
            if (descriptor.getInterfaceClass().getName().equals(invocation.getInterfaceName())
                    && (group == null || group.equals(descriptorGroup))
                    && (version == null || version.equals(descriptorVersion))) {
                return descriptor;
            }
        }
        return null;
    }

    private Method findMethod(Class<?> interfaceClass, String methodName, Class<?>[] parameterTypes) {
        try {
            return interfaceClass.getMethod(methodName, parameterTypes != null ? parameterTypes : new Class<?>[0]);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Object[] convertArguments(Object[] arguments, Method method) {
        if (arguments == null) {
            return null;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes == null || parameterTypes.length != arguments.length) {
            return arguments;
        }
        Serializer serializer = SerializerManager.getDefaultOrNull();
        if (serializer == null) {
            return arguments;
        }
        Object[] converted = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof Map && !parameterTypes[i].isAssignableFrom(Map.class)) {
                try {
                    byte[] bytes = serializer.serialize(arguments[i]);
                    converted[i] = serializer.deserialize(bytes, parameterTypes[i]);
                } catch (Exception e) {
                    LOG.warn(
                            "Failed to convert argument {} from Map to {}: {}",
                            i,
                            parameterTypes[i].getName(),
                            e.getMessage());
                    converted[i] = arguments[i];
                }
            } else {
                converted[i] = arguments[i];
            }
        }
        return converted;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
