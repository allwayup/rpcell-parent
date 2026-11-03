package io.rpcell.cluster;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.LoadBalance;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random load balance strategy: selects a URL randomly from the list.
 */
public class RandomLoadBalance implements LoadBalance {

    @Override
    public URL select(List<URL> urls, Invocation invocation) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        if (urls.size() == 1) {
            return urls.get(0);
        }
        int index = ThreadLocalRandom.current().nextInt(urls.size());
        return urls.get(index);
    }
}
