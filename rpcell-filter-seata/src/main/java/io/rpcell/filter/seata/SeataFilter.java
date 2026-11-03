package io.rpcell.filter.seata;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.spi.Filter;
import io.rpcell.api.spi.Invoker;
import io.seata.core.context.RootContext;
import io.seata.core.model.BranchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seata distributed transaction propagation filter.
 * Propagates the Seata global transaction ID (xid) across rpcell RPC calls,
 * enabling distributed transaction coordination via {@code @GlobalTransactional}.
 *
 * <h3>How it works</h3>
 * <ul>
 *   <li><b>Consumer side</b>: If the current thread holds an xid (RootContext.getXID() != null),
 *       the xid is attached to the outgoing {@link Invocation} so the provider can join the transaction.</li>
 *   <li><b>Provider side</b>: If the incoming {@link Invocation} carries an xid but the current
 *       thread does not, the xid is bound to {@link RootContext} before business logic executes,
 *       and unbound in the {@code finally} block after execution completes.</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * Register via SPI and configure both sides:
 * <pre>{@code
 * ExtensionLoader.register("filter:seata", SeataFilter.class);
 *
 * // application.yml
 * rpcell:
 *   filter:
 *     provider:
 *       names: [filter:seata]
 *     consumer:
 *       names: [filter:seata]
 * }</pre>
 */
public class SeataFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(SeataFilter.class);

    private static final String XID_KEY = RootContext.KEY_XID;

    private static final String BRANCH_TYPE_KEY = RootContext.KEY_BRANCH_TYPE;

    public SeataFilter() {}

    @Override
    public Result invoke(Invoker invoker, Invocation invocation) {
        String currentXid = RootContext.getXID();
        String rpcXid = invocation.getAttachments().get(XID_KEY);

        if (currentXid != null) {
            LOG.debug("Propagating Seata xid to downstream: {}", currentXid);
            invocation.getAttachments().put(XID_KEY, currentXid);
            BranchType branchType = RootContext.getBranchType();
            if (branchType != null) {
                invocation.getAttachments().put(BRANCH_TYPE_KEY, branchType.name());
            }
            return invoker.invoke(invocation);
        }

        if (rpcXid != null) {
            LOG.debug("Binding Seata xid from upstream: {}", rpcXid);
            String rpcBranchType = invocation.getAttachments().get(BRANCH_TYPE_KEY);
            BranchType branchType = rpcBranchType != null ? BranchType.valueOf(rpcBranchType) : null;
            try {
                RootContext.bind(rpcXid);
                if (branchType != null) {
                    RootContext.bindBranchType(branchType);
                }
                return invoker.invoke(invocation);
            } finally {
                String unbindXid = RootContext.unbind();
                LOG.debug("Unbound Seata xid: {}", unbindXid);
                if (branchType != null) {
                    RootContext.unbindBranchType();
                }
            }
        }

        return invoker.invoke(invocation);
    }
}
