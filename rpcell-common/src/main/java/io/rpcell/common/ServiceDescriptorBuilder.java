package io.rpcell.common;

import io.rpcell.api.annotation.RpcService;
import io.rpcell.api.exception.RpcParamException;
import io.rpcell.api.model.ServiceDescriptor;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to build a ServiceDescriptor from an interface class and its @RpcService annotation.
 */
public final class ServiceDescriptorBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceDescriptorBuilder.class);

    private ServiceDescriptorBuilder() {}

    /**
     * Build a ServiceDescriptor from the given interface class.
     * Iterates all methods (including inherited), applies the filter rules,
     * and populates the methods set.
     *
     * @param interfaceClass the interface annotated with @RpcService
     * @param instance the service implementation instance (may be null for consumer side)
     * @return the built ServiceDescriptor
     */
    public static ServiceDescriptor build(Class<?> interfaceClass, Object instance) {
        RpcService annotation = interfaceClass.getAnnotation(RpcService.class);
        if (annotation == null) {
            throw new RpcParamException("Class " + interfaceClass.getName() + " is not annotated with @RpcService");
        }

        String serviceName = resolveServiceName(annotation, interfaceClass);
        String group = annotation.group();
        String version = annotation.version();

        Set<String> methods = new LinkedHashSet<>();
        Method[] allMethods = interfaceClass.getMethods();
        for (Method method : allMethods) {
            if (ServiceFilter.isExposed(method, annotation)) {
                methods.add(method.getName());
            }
        }

        LOG.debug(
                "Built ServiceDescriptor for {}: serviceName={}, group={}, methods={}",
                interfaceClass.getName(),
                serviceName,
                group,
                methods);

        ServiceDescriptor descriptor = new ServiceDescriptor();
        descriptor.setInterfaceClass(interfaceClass);
        descriptor.setServiceName(serviceName);
        descriptor.setGroup(group);
        descriptor.setVersion(version);
        descriptor.setMethods(methods);
        descriptor.setInstance(instance);
        return descriptor;
    }

    private static String resolveServiceName(RpcService annotation, Class<?> interfaceClass) {
        String value = annotation.value();
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return interfaceClass.getName();
    }
}
