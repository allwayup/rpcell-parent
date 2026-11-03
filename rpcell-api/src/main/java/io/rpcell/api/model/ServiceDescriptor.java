package io.rpcell.api.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * Service descriptor holding the complete metadata for a registered RPC service.
 */
public class ServiceDescriptor {

    private Class<?> interfaceClass;
    private String serviceName;
    private String group;
    private String version;
    private Set<String> methods;
    private Object instance;

    private int timeout = -1;
    private int retries = -1;
    private boolean register = true;
    private String[] filter = {};

    public ServiceDescriptor() {}

    public Class<?> getInterfaceClass() {
        return interfaceClass;
    }

    public void setInterfaceClass(Class<?> interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Set<String> getMethods() {
        return methods != null ? Collections.unmodifiableSet(methods) : Collections.emptySet();
    }

    public void setMethods(Set<String> methods) {
        this.methods = methods;
    }

    public Object getInstance() {
        return instance;
    }

    public void setInstance(Object instance) {
        this.instance = instance;
    }

    public boolean containsMethod(String methodName) {
        return methods != null && methods.contains(methodName);
    }

    public String toServiceKey() {
        return serviceName + ":" + group + ":" + version;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public boolean isRegister() {
        return register;
    }

    public void setRegister(boolean register) {
        this.register = register;
    }

    public String[] getFilter() {
        return filter != null ? Arrays.copyOf(filter, filter.length) : new String[0];
    }

    public void setFilter(String[] filter) {
        this.filter = filter;
    }
}
