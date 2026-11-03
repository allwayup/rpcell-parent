package io.rpcell.boot;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * RPCell configuration properties bound from rpcell.* namespace.
 */
@Component
@ConfigurationProperties(prefix = "rpcell")
public class RpcellProperties {

    private boolean enabled = true;

    @NestedConfigurationProperty
    private final Application application = new Application();

    @NestedConfigurationProperty
    private final Protocol protocol = new Protocol();

    @NestedConfigurationProperty
    private final Transport transport = new Transport();

    @NestedConfigurationProperty
    private final Registry registry = new Registry();

    @NestedConfigurationProperty
    private final Cluster cluster = new Cluster();

    @NestedConfigurationProperty
    private final Provider provider = new Provider();

    @NestedConfigurationProperty
    private final Consumer consumer = new Consumer();

    @NestedConfigurationProperty
    private final Security security = new Security();

    @NestedConfigurationProperty
    private final FilterConfig filter = new FilterConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Application getApplication() {
        return application;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public Transport getTransport() {
        return transport;
    }

    public Registry getRegistry() {
        return registry;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public Provider getProvider() {
        return provider;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public Security getSecurity() {
        return security;
    }

    public FilterConfig getFilter() {
        return filter;
    }

    public static class Application {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Protocol {
        private String name = "rpcell";
        private String serialization = "msgpack";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSerialization() {
            return serialization;
        }

        public void setSerialization(String serialization) {
            this.serialization = serialization;
        }
    }

    public static class Transport {
        private String host = "0.0.0.0";
        private int port = 20880;
        private int threads;
        private int connectTimeout = 3000;
        private int readTimeout = 5000;

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

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Registry {
        private String type = "nacos";
        private String address = "127.0.0.1:8848";
        private String username;
        private String password;
        private String namespace;
        private String group = "DEFAULT_GROUP";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }
    }

    public static class Cluster {
        private String loadbalance = "random";
        private int retries = 2;

        public String getLoadbalance() {
            return loadbalance;
        }

        public void setLoadbalance(String loadbalance) {
            this.loadbalance = loadbalance;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }
    }

    public static class Provider {
        private int timeout = 3000;
        private String group;
        private String version;
        private boolean export = true;
        private boolean register = true;
        private List<String> filter = new ArrayList<>();

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
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

        public boolean isExport() {
            return export;
        }

        public void setExport(boolean export) {
            this.export = export;
        }

        public boolean isRegister() {
            return register;
        }

        public void setRegister(boolean register) {
            this.register = register;
        }

        public List<String> getFilter() {
            return filter;
        }

        public void setFilter(List<String> filter) {
            this.filter = filter;
        }
    }

    public static class Consumer {
        private int timeout = 3000;
        private int retries = 2;
        private boolean check;
        private String group;
        private String version;
        private String loadbalance;
        private List<String> filter = new ArrayList<>();

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

        public boolean isCheck() {
            return check;
        }

        public void setCheck(boolean check) {
            this.check = check;
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

        public String getLoadbalance() {
            return loadbalance;
        }

        public void setLoadbalance(String loadbalance) {
            this.loadbalance = loadbalance;
        }

        public List<String> getFilter() {
            return filter;
        }

        public void setFilter(List<String> filter) {
            this.filter = filter;
        }
    }

    public static class Security {
        private final Serialization serialization = new Serialization();

        public Serialization getSerialization() {
            return serialization;
        }

        public static class Serialization {
            private String whitelist;
            private boolean allowJdk = true;

            public String getWhitelist() {
                return whitelist;
            }

            public void setWhitelist(String whitelist) {
                this.whitelist = whitelist;
            }

            public boolean isAllowJdk() {
                return allowJdk;
            }

            public void setAllowJdk(boolean allowJdk) {
                this.allowJdk = allowJdk;
            }
        }
    }

    public static class FilterConfig {
        @NestedConfigurationProperty
        private final FilterSide provider = new FilterSide();

        @NestedConfigurationProperty
        private final FilterSide consumer = new FilterSide();

        public FilterSide getProvider() {
            return provider;
        }

        public FilterSide getConsumer() {
            return consumer;
        }

        public static class FilterSide {
            private List<String> names = new ArrayList<>();

            public List<String> getNames() {
                return names;
            }

            public void setNames(List<String> names) {
                this.names = names;
            }
        }
    }
}
