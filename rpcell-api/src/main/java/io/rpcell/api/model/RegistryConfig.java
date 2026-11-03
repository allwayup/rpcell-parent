package io.rpcell.api.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry center configuration.
 */
public class RegistryConfig {

    private String type;
    private String address;
    private String username;
    private String password;
    private Map<String, String> params;

    public RegistryConfig() {
        this.params = new HashMap<>();
    }

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

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }
}
