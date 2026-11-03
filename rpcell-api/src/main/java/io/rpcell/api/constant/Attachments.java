package io.rpcell.api.constant;

/**
 * Standard attachment keys carried in {@link io.rpcell.api.model.Invocation#getAttachments()}.
 * <p>
 * These keys enable annotation-level and Filter-level configuration to flow through
 * the RPC invocation chain without modifying the {@code Invocation} class signature.
 */
public final class Attachments {

    private Attachments() {}

    public static final String TIMEOUT = "rpcell.timeout";
    public static final String RETRIES = "rpcell.retries";
    public static final String GROUP = "rpcell.group";
    public static final String VERSION = "rpcell.version";
    public static final String LOADBALANCE = "rpcell.loadbalance";
    public static final String CHECK = "rpcell.check";
    public static final String PROVIDER_FILTER = "rpcell.provider.filter";
    public static final String CONSUMER_FILTER = "rpcell.consumer.filter";
    public static final String XID = "SEATA_XID";
}
