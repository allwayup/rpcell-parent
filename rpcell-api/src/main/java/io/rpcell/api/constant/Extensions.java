package io.rpcell.api.constant;

/**
 * SPI extension name constants.
 */
public final class Extensions {

    private Extensions() {}

    public static final String PROTOCOL_RPCELL = "rpcell";
    public static final String SERIALIZER_MSGPACK = "serializer:msgpack";
    public static final String CLUSTER_FAILOVER = "failover";
    public static final String LOADBALANCE_RANDOM = "random";
    public static final String LOADBALANCE_ROUNDROBIN = "roundrobin";
    public static final String REGISTRY_NACOS = "nacos";
    public static final String REGISTRY_DIRECT = "direct";
}
