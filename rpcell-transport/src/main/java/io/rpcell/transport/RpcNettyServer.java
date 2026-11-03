package io.rpcell.transport;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.rpcell.api.constant.ProtocolConstants;
import io.rpcell.api.exception.RpcTransportException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.codec.RpcCellProtocol;
import io.rpcell.serializer.SerializerManager;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty-based RPC server that listens for connections and dispatches requests.
 */
public class RpcNettyServer {

    private static final Logger LOG = LoggerFactory.getLogger(RpcNettyServer.class);

    private final RpcCellProtocol protocol;
    private final ServerBootstrap bootstrap = new ServerBootstrap();
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final EventExecutorGroup businessGroup;
    private final int port;
    private final String host;
    private final int workerThreads;
    private volatile boolean started;
    private volatile Channel serverChannel;
    private volatile Function<Invocation, Result> requestHandler;

    public RpcNettyServer(RpcCellProtocol protocol, String host, int port) {
        this(protocol, host, port, 0);
    }

    public RpcNettyServer(RpcCellProtocol protocol, String host, int port, int workerThreads) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.workerThreads =
                workerThreads > 0 ? workerThreads : Runtime.getRuntime().availableProcessors() * 2;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup(this.workerThreads);
        this.businessGroup = new DefaultEventExecutorGroup(this.workerThreads);
    }

    public void setRequestHandler(Function<Invocation, Result> requestHandler) {
        this.requestHandler = requestHandler;
    }

    public void start() throws InterruptedException {
        bootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder(
                                        ProtocolConstants.MAX_FRAME_LENGTH,
                                        ProtocolConstants.LENGTH_FIELD_OFFSET,
                                        ProtocolConstants.LENGTH_FIELD_LENGTH,
                                        ProtocolConstants.LENGTH_ADJUSTMENT,
                                        ProtocolConstants.INITIAL_BYTES_TO_STRIP))
                                .addLast(businessGroup, new RpcServerHandler());
                    }
                });

        ChannelFuture future = bootstrap.bind(host, port);
        try {
            future.sync();
        } catch (InterruptedException e) {
            try {
                future.addListener((ChannelFuture bindResult) -> {
                    if (bindResult.isSuccess()) {
                        bindResult.channel().close();
                    }
                });
                future.cancel(false);
            } catch (RuntimeException cleanupException) {
                e.addSuppressed(cleanupException);
            }
            throw e;
        }
        serverChannel = future.channel();
        started = true;
        LOG.info("RPC server started on {}:{}", host, port);
    }

    public void shutdown() {
        Channel channel = serverChannel;
        serverChannel = null;
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
        businessGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        started = false;
        LOG.info("RPC server shutdown");
    }

    public boolean isStarted() {
        return started;
    }

    private class RpcServerHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof io.netty.buffer.ByteBuf) {
                io.netty.buffer.ByteBuf buf = (io.netty.buffer.ByteBuf) msg;
                long requestId = 0L;
                int serializationId = SerializerManager.getDefaultSerializer().id();
                try {
                    byte[] fullFrame = new byte[buf.readableBytes()];
                    buf.readBytes(fullFrame);

                    RpcCellProtocol.FrameHeader header =
                            protocol.parseHeader(java.util.Arrays.copyOf(fullFrame, ProtocolConstants.HEADER_LENGTH));
                    requestId = header.requestId;
                    serializationId = header.serializationId;
                    byte[] bodyBytes = protocol.extractBody(fullFrame);
                    Invocation invocation = protocol.decodeRequest(bodyBytes, header.serializationId);

                    Result result;
                    if (requestHandler != null) {
                        result = requestHandler.apply(invocation);
                    } else {
                        result = Result.failure(new Result.RpcResultException(
                                "No request handler set",
                                RpcTransportException.class.getName(),
                                io.rpcell.api.enums.ResultCodes.SERVICE_NOT_FOUND.getCode()));
                    }

                    byte[] responseFrame = protocol.encodeResponse(result, header.requestId, header.serializationId);
                    ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(responseFrame));
                } catch (Exception e) {
                    LOG.error("Failed to process request", e);
                    sendErrorResponse(ctx, e, requestId, serializationId);
                } finally {
                    buf.release();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.error("Server channel exception: {}", cause.getMessage());
            ctx.close();
        }

        private void sendErrorResponse(ChannelHandlerContext ctx, Exception e, long requestId, int serializationId) {
            try {
                Result errorResult = Result.failure(new Result.RpcResultException(
                        "Server internal error: " + e.getMessage(),
                        e.getClass().getName(),
                        io.rpcell.api.enums.ResultCodes.UNKNOWN_ERROR.getCode()));
                byte[] responseFrame = protocol.encodeResponse(errorResult, requestId, serializationId);
                ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(responseFrame));
            } catch (Exception ex) {
                LOG.error("Failed to send error response", ex);
                ctx.close();
            }
        }
    }
}
