package io.rpcell.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.rpcell.api.constant.Attachments;
import io.rpcell.api.constant.ProtocolConstants;
import io.rpcell.api.exception.RpcTransportException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.codec.RpcCellProtocol;
import io.rpcell.serializer.SerializerManager;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty-based RPC client with request ID to Future mapping.
 */
public class RpcNettyClient {

    private static final Logger LOG = LoggerFactory.getLogger(RpcNettyClient.class);

    private final Map<Long, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong requestIdGenerator = new AtomicLong(0);
    private final RpcCellProtocol protocol;
    private final EventLoopGroup group;
    private final Bootstrap bootstrap;
    private final int connectTimeout;
    private final int readTimeout;
    private final Map<String, Channel> channelCache = new ConcurrentHashMap<>();

    private final int defaultPort;

    public RpcNettyClient(RpcCellProtocol protocol) {
        this(protocol, 3000, 5000, 20880);
    }

    public RpcNettyClient(RpcCellProtocol protocol, int connectTimeout, int readTimeout) {
        this(protocol, connectTimeout, readTimeout, 20880);
    }

    public RpcNettyClient(RpcCellProtocol protocol, int connectTimeout, int readTimeout, int defaultPort) {
        this.protocol = protocol;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.defaultPort = defaultPort;
        this.group = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        this.bootstrap
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder(
                                        ProtocolConstants.MAX_FRAME_LENGTH,
                                        ProtocolConstants.LENGTH_FIELD_OFFSET,
                                        ProtocolConstants.LENGTH_FIELD_LENGTH,
                                        ProtocolConstants.LENGTH_ADJUSTMENT,
                                        ProtocolConstants.INITIAL_BYTES_TO_STRIP))
                                .addLast(new RpcClientHandler());
                    }
                });
    }

    public Result send(Invocation invocation) {
        String host = invocation.getAttachments().get("targetHost");
        int port =
                Integer.parseInt(invocation.getAttachments().getOrDefault("targetPort", String.valueOf(defaultPort)));
        return send(invocation, host, port);
    }

    public Result send(Invocation invocation, String host, int port) {
        long requestId = requestIdGenerator.incrementAndGet();
        PendingRequest pending = new PendingRequest();
        pendingRequests.put(requestId, pending);

        try {
            Channel channel = getChannel(host, port);
            pending.channel = channel;
            byte[] frame = protocol.encodeRequest(
                    invocation,
                    requestId,
                    SerializerManager.getDefaultSerializer().id());
            ChannelFuture writeFuture =
                    channel.writeAndFlush(channel.alloc().buffer().writeBytes(frame));
            writeFuture.addListener(result -> {
                if (!result.isSuccess()) {
                    failRequest(
                            requestId,
                            new RpcTransportException(
                                    "Failed to write request to " + host + ":" + port, result.cause()));
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failRequest(
                    requestId,
                    new RpcTransportException("Interrupted while sending request to " + host + ":" + port, e));
        } catch (Exception e) {
            failRequest(requestId, new RpcTransportException("Failed to send request to " + host + ":" + port, e));
        }

        try {
            return pending.future.get(resolveTimeout(invocation), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(requestId);
            return Result.failure(new Result.RpcResultException(
                    "Request timeout: " + host + ":" + port,
                    RpcTransportException.class.getName(),
                    io.rpcell.api.enums.ResultCodes.CLIENT_TIMEOUT.getCode()));
        } catch (InterruptedException e) {
            pendingRequests.remove(requestId);
            Thread.currentThread().interrupt();
            return Result.failure(new Result.RpcResultException(
                    "Request interrupted: " + host + ":" + port,
                    RpcTransportException.class.getName(),
                    io.rpcell.api.enums.ResultCodes.TRANSPORT_ERROR.getCode()));
        } catch (ExecutionException e) {
            pendingRequests.remove(requestId);
            Throwable cause = e.getCause();
            String msg = cause != null ? cause.getMessage() : e.getMessage();
            return Result.failure(new Result.RpcResultException(
                    "Request failed: " + msg,
                    cause != null ? cause.getClass().getName() : RpcTransportException.class.getName(),
                    io.rpcell.api.enums.ResultCodes.TRANSPORT_ERROR.getCode()));
        }
    }

    private int resolveTimeout(Invocation invocation) {
        if (invocation.getAttachments() != null) {
            String timeout = invocation.getAttachments().get(Attachments.TIMEOUT);
            if (timeout != null) {
                try {
                    int value = Integer.parseInt(timeout);
                    if (value > 0) {
                        return value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return readTimeout;
    }

    private Channel getChannel(String host, int port) throws Exception {
        String key = host + ":" + port;
        Channel channel = channelCache.get(key);
        if (channel == null || !channel.isActive()) {
            synchronized (channelCache) {
                channel = channelCache.get(key);
                if (channel == null || !channel.isActive()) {
                    ChannelFuture future = bootstrap.connect(host, port);
                    try {
                        future.sync();
                    } catch (InterruptedException e) {
                        try {
                            future.addListener((ChannelFuture connectResult) -> {
                                if (connectResult.isSuccess()) {
                                    connectResult.channel().close();
                                }
                            });
                            future.cancel(false);
                        } catch (RuntimeException cleanupException) {
                            e.addSuppressed(cleanupException);
                        }
                        throw e;
                    }
                    channel = future.channel();
                    channelCache.put(key, channel);
                    LOG.info("Connected to {}:{}", host, port);
                }
            }
        }
        return channel;
    }

    private void failRequest(long requestId, Throwable cause) {
        PendingRequest pending = pendingRequests.remove(requestId);
        if (pending != null) {
            pending.future.completeExceptionally(cause);
        }
    }

    private void failPendingRequests(Channel channel, Throwable cause) {
        pendingRequests.entrySet().removeIf(entry -> {
            PendingRequest pending = entry.getValue();
            if (pending.channel != channel) {
                return false;
            }
            pending.future.completeExceptionally(cause);
            return true;
        });
    }

    public void shutdown() {
        boolean interrupted = false;
        for (Channel channel : channelCache.values()) {
            try {
                channel.close().sync();
            } catch (InterruptedException e) {
                interrupted = true;
            } catch (Exception ignored) {
            }
        }
        channelCache.clear();
        group.shutdownGracefully();
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        LOG.info("RPC client shutdown");
    }

    private static class PendingRequest {
        private final CompletableFuture<Result> future = new CompletableFuture<>();
        private volatile Channel channel;
    }

    private class RpcClientHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof io.netty.buffer.ByteBuf) {
                io.netty.buffer.ByteBuf buf = (io.netty.buffer.ByteBuf) msg;
                long requestId = 0L;
                boolean responseIdentified = false;
                try {
                    byte[] fullFrame = new byte[buf.readableBytes()];
                    buf.readBytes(fullFrame);
                    byte[] bodyBytes = protocol.extractBody(fullFrame);
                    RpcCellProtocol.FrameHeader header =
                            protocol.parseHeader(java.util.Arrays.copyOf(fullFrame, ProtocolConstants.HEADER_LENGTH));
                    requestId = header.requestId;
                    responseIdentified = true;
                    Result result = protocol.decodeResponse(bodyBytes, header.serializationId);
                    PendingRequest pending = pendingRequests.remove(requestId);
                    if (pending != null) {
                        pending.future.complete(result);
                    }
                } catch (Exception e) {
                    LOG.error("Failed to decode response", e);
                    if (responseIdentified) {
                        failRequest(requestId, e);
                    } else {
                        failPendingRequests(ctx.channel(), e);
                    }
                } finally {
                    buf.release();
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            failPendingRequests(
                    ctx.channel(),
                    new RpcTransportException("Channel closed: " + ctx.channel().remoteAddress()));
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.error("Client channel exception: {}", cause.getMessage());
            failPendingRequests(ctx.channel(), cause);
            ctx.close();
        }
    }
}
