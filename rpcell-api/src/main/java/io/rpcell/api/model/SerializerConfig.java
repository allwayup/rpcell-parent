package io.rpcell.api.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration for serializer factory creation.
 */
public class SerializerConfig {

    private String type;
    private List<String> whitelist;
    private boolean allowJdkTypes;
    private Map<String, String> params;

    public SerializerConfig() {
        this.type = "msgpack";
        this.whitelist = Collections.emptyList();
        this.allowJdkTypes = true;
        this.params = Collections.emptyMap();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }

    public boolean isAllowJdkTypes() {
        return allowJdkTypes;
    }

    public void setAllowJdkTypes(boolean allowJdkTypes) {
        this.allowJdkTypes = allowJdkTypes;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }
}
