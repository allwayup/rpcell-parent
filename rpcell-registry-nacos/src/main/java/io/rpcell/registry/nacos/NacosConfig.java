package io.rpcell.registry.nacos;

/**
 * Nacos connection configuration with environment variable override support.
 */
public class NacosConfig {

    private String serverAddr;
    private String username;
    private String password;
    private String namespace;
    private String group;

    public NacosConfig() {
        this.serverAddr = envOr("NACOS_HOST", "127.0.0.1") + ":" + envOr("NACOS_PORT", "8848");
        this.username = System.getenv("NACOS_USERNAME");
        this.password = System.getenv("NACOS_PASSWORD");
        this.group = "DEFAULT_GROUP";
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        if (serverAddr != null && !serverAddr.isEmpty()) {
            this.serverAddr = serverAddr;
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username != null) {
            this.username = username;
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        if (password != null) {
            this.password = password;
        }
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        if (namespace != null) {
            this.namespace = namespace;
        }
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        if (group != null && !group.isEmpty()) {
            this.group = group;
        }
    }

    private static String envOr(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }
}
