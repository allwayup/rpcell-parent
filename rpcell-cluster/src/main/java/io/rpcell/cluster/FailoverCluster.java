package io.rpcell.cluster;

import io.rpcell.api.constant.Attachments;
import io.rpcell.api.constant.Extensions;
import io.rpcell.api.enums.ResultCodes;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.Cluster;
import io.rpcell.api.spi.Invoker;
import io.rpcell.api.spi.LoadBalance;
import io.rpcell.registry.RegistryDirectory;
import io.rpcell.spi.ExtensionLoader;
import io.rpcell.transport.ClientInvoker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Failover cluster: retries on failure up to a configured number of times,
 * selecting different nodes via the load balance strategy.
 */
public class FailoverCluster implements Cluster, Invoker {

    private static final Logger LOG = LoggerFactory.getLogger(FailoverCluster.class);

    private final RegistryDirectory directory;
    private final LoadBalance loadBalance;
    private final ClientInvoker clientInvoker;
    private final int retries;

    public FailoverCluster(
            RegistryDirectory directory, LoadBalance loadBalance, ClientInvoker clientInvoker, int retries) {
        this.directory = directory;
        this.loadBalance = loadBalance;
        this.clientInvoker = clientInvoker;
        this.retries = retries;
    }

    @Override
    public Result invoke(Invocation invocation) {
        List<URL> urls = directory.list(invocation);
        return invoke(invocation, urls);
    }

    @Override
    public Result invoke(Invocation invocation, List<URL> urls) {
        if (urls == null || urls.isEmpty()) {
            LOG.warn("No available service URLs for {}", invocation.getInterfaceName());
            return Result.failure(new Result.RpcResultException(
                    "No available service URLs",
                    "io.rpcell.api.exception.RpcTransportException",
                    ResultCodes.CLUSTER_ERROR.getCode()));
        }

        int effectiveRetries = resolveRetries(invocation);
        LoadBalance effectiveLoadBalance = resolveLoadBalance(invocation);

        List<URL> remaining = new ArrayList<>(urls);
        int maxAttempts = (int) Math.min((long) effectiveRetries + 1, remaining.size());
        Result lastResult = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (remaining.isEmpty()) {
                break;
            }
            URL selected = effectiveLoadBalance.select(remaining, invocation);
            if (selected == null) {
                break;
            }
            remaining.remove(selected);

            try {
                LOG.debug(
                        "Invoking {}.{} via {} (attempt {}/{})",
                        invocation.getInterfaceName(),
                        invocation.getMethodName(),
                        selected.toAddress(),
                        attempt + 1,
                        maxAttempts);

                Result result = clientInvoker.invoke(invocation, selected);
                if (result.hasValue() || !isRetryable(result)) {
                    return result;
                }
                lastResult = result;
                LOG.warn(
                        "Call to {} failed (attempt {}): {}",
                        selected.toAddress(),
                        attempt + 1,
                        result.getException() != null ? result.getException().getMessage() : "unknown");
            } catch (Exception e) {
                LOG.warn(
                        "Call to {} threw exception (attempt {}): {}",
                        selected.toAddress(),
                        attempt + 1,
                        e.getMessage());
                lastResult = Result.failure(new Result.RpcResultException(
                        e.getMessage(), e.getClass().getName(), ResultCodes.CLUSTER_ERROR.getCode()));
            }
        }

        LOG.error("All retries exhausted for {}.{}", invocation.getInterfaceName(), invocation.getMethodName());

        if (lastResult != null) {
            return lastResult;
        }
        return Result.failure(new Result.RpcResultException(
                "All retries exhausted",
                "io.rpcell.api.exception.RpcTransportException",
                ResultCodes.CLUSTER_ERROR.getCode()));
    }

    private boolean isRetryable(Result result) {
        if (result == null || result.getException() == null) {
            return false;
        }
        int code = result.getException().getErrorCode();
        return code == ResultCodes.CLIENT_TIMEOUT.getCode()
                || code == ResultCodes.SERVER_TIMEOUT.getCode()
                || code == ResultCodes.TRANSPORT_ERROR.getCode()
                || code == ResultCodes.CLUSTER_ERROR.getCode();
    }

    private LoadBalance resolveLoadBalance(Invocation invocation) {
        Map<String, String> attachments = invocation.getAttachments();
        String name = attachments != null ? attachments.get(Attachments.LOADBALANCE) : null;
        if (name == null
                || name.isEmpty()
                || (Extensions.LOADBALANCE_RANDOM.equals(name) && loadBalance instanceof RandomLoadBalance)) {
            return loadBalance;
        }
        return ExtensionLoader.load(name);
    }

    private int resolveRetries(Invocation invocation) {
        Map<String, String> attachments = invocation.getAttachments();
        if (attachments != null) {
            String retriesStr = attachments.get(Attachments.RETRIES);
            if (retriesStr != null) {
                try {
                    int annotationRetries = Integer.parseInt(retriesStr);
                    if (annotationRetries >= 0) {
                        return annotationRetries;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return retries;
    }
}
