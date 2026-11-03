package io.rpcell.transport;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.Invoker;

/**
 * Client-side invoker that sends RPC requests to a specific remote URL via Netty.
 * Implements the Invoker SPI for cluster layer consumption.
 */
public class ClientInvoker implements Invoker {

    private final RpcNettyClient rpcNettyClient;

    public ClientInvoker(RpcNettyClient rpcNettyClient) {
        this.rpcNettyClient = rpcNettyClient;
    }

    /**
     * Invoke with a default target URL (set in attachments).
     */
    @Override
    public Result invoke(Invocation invocation) {
        return rpcNettyClient.send(invocation);
    }

    /**
     * Invoke to a specific target URL.
     *
     * @param invocation the invocation data
     * @param targetUrl the target service URL
     * @return the RPC result
     */
    public Result invoke(Invocation invocation, URL targetUrl) {
        return rpcNettyClient.send(invocation, targetUrl.getHost(), targetUrl.getPort());
    }
}
