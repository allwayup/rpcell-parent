package io.rpcell.api.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Service address metadata. Carries protocol, host, port, service identity, and parameters.
 */
public class URL {

    private String protocol;
    private String host;
    private int port;
    private String serviceName;
    private String group;
    private String version;
    private Map<String, String> params;

    public URL() {
        this.params = new HashMap<>();
    }

    public URL(String protocol, String host, int port, String serviceName, String group, String version) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.serviceName = serviceName;
        this.group = group;
        this.version = version;
        this.params = new HashMap<>();
    }

    public URL(
            String protocol,
            String host,
            int port,
            String serviceName,
            String group,
            String version,
            Map<String, String> params) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.serviceName = serviceName;
        this.group = group;
        this.version = version;
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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

    public Map<String, String> getParams() {
        return Collections.unmodifiableMap(params);
    }

    public void setParams(Map<String, String> params) {
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
    }

    public void addParam(String key, String value) {
        this.params.put(key, value);
    }

    public String getParam(String key) {
        return this.params.get(key);
    }

    public String toAddress() {
        return host + ":" + port;
    }

    public String toServiceKey() {
        return serviceName + ":" + group + ":" + version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        URL url = (URL) o;
        return port == url.port
                && java.util.Objects.equals(protocol, url.protocol)
                && java.util.Objects.equals(host, url.host)
                && java.util.Objects.equals(serviceName, url.serviceName)
                && java.util.Objects.equals(group, url.group)
                && java.util.Objects.equals(version, url.version);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(protocol, host, port, serviceName, group, version);
    }

    @Override
    public String toString() {
        return "URL{protocol='" + protocol + "', host='" + host + "', port=" + port + ", serviceName='" + serviceName
                + "', group='" + group + "', version='" + version + "'}";
    }
}
