package io.rpcell.registry;

import io.rpcell.api.constant.Attachments;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.NotifyListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds a volatile list of service URLs, updated atomically on registry notification.
 * This is the bridge between the registry center and the cluster layer.
 */
public class RegistryDirectory implements NotifyListener {

    private static final Logger LOG = LoggerFactory.getLogger(RegistryDirectory.class);

    private volatile List<URL> urls = new ArrayList<>();

    /**
     * Called by the registry when the service address list changes.
     * Replaces the internal list atomically via reference swap.
     *
     * @param newUrls the new list of service URLs
     */
    @Override
    public void notify(List<URL> newUrls) {
        if (newUrls == null) {
            newUrls = new ArrayList<>();
        }
        this.urls = new ArrayList<>(newUrls);
        LOG.info("RegistryDirectory updated: {} URLs", this.urls.size());
    }

    /**
     * Return the current snapshot of service URLs.
     *
     * @return unmodifiable list of URLs (snapshot)
     */
    public List<URL> list() {
        return Collections.unmodifiableList(urls);
    }

    /**
     * Return URLs whose group and version exactly match the invocation metadata.
     */
    public List<URL> list(Invocation invocation) {
        String group = invocation.getAttachments() != null
                ? invocation.getAttachments().get(Attachments.GROUP)
                : null;
        String version = invocation.getAttachments() != null
                ? invocation.getAttachments().get(Attachments.VERSION)
                : null;
        List<URL> matches = new ArrayList<>();
        for (URL url : urls) {
            boolean genericUrl = url.getServiceName() == null;
            String urlGroup = url.getGroup() != null ? url.getGroup() : "";
            String urlVersion = url.getVersion() != null ? url.getVersion() : "";
            if ((genericUrl || group == null || group.equals(urlGroup))
                    && (genericUrl || version == null || version.equals(urlVersion))) {
                matches.add(url);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * Check if the directory has any URLs.
     *
     * @return true if the directory is empty
     */
    public boolean isEmpty() {
        return urls.isEmpty();
    }
}
