package io.rpcell.api.spi;

import io.rpcell.api.model.URL;
import java.util.List;

/**
 * Callback interface for registry center address change notifications.
 */
public interface NotifyListener {

    void notify(List<URL> urls);
}
