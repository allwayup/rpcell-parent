package io.rpcell.cluster;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.LoadBalance;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balance strategy: selects URLs in a circular order.
 */
public class RoundRobinLoadBalance implements LoadBalance {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public URL select(List<URL> urls, Invocation invocation) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        if (urls.size() == 1) {
            return urls.get(0);
        }
        int index = Math.abs(counter.getAndIncrement() % urls.size());
        return urls.get(index);
    }
}
