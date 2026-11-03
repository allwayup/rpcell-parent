package io.rpcell.transport;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.rpcell.api.constant.Attachments;
import io.rpcell.api.enums.ResultCodes;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.SerializerConfig;
import io.rpcell.codec.RpcCellProtocol;
import io.rpcell.serializer.SerializerManager;
import io.rpcell.serializer.msgpack.MessagePackSerializerFactory;
import io.rpcell.spi.ExtensionLoader;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RpcNettyIntegrationTest {

    private RpcNettyServer server;
    private RpcNettyClient client;
    private RpcCellProtocol protocol;
    private int port;

    @BeforeAll
    public static void initSerializer() {
        ExtensionLoader.register("serializer:msgpack", MessagePackSerializerFactory.class);
        SerializerManager.init(new SerializerConfig());
    }

    @BeforeEach
    public void setUp() throws Exception {
        port = findFreePort();
        protocol = new RpcCellProtocol();
        server = new RpcNettyServer(protocol, "127.0.0.1", port);
        server.setRequestHandler(invocation -> {
            Object arg = invocation.getArguments() != null && invocation.getArguments().length > 0
                    ? invocation.getArguments()[0]
                    : null;
            return Result.success("echo: " + arg);
        });
        server.start();
        client = new RpcNettyClient(protocol);
    }

    @AfterEach
    public void tearDown() {
        try {
            server.shutdown();
        } catch (Exception ignored) {
        }
        try {
            client.shutdown();
        } catch (Exception ignored) {
        }
    }

    @Test
    public void testSendAndReceive() {
        Invocation inv = new Invocation();
        inv.setInterfaceName("EchoService");
        inv.setMethodName("echo");
        inv.setParameterTypes(new Class<?>[] {String.class});
        inv.setArguments(new Object[] {"hello"});
        inv.getAttachments().put("targetHost", "127.0.0.1");
        inv.getAttachments().put("targetPort", String.valueOf(port));

        Result result = client.send(inv);
        assertNotNull(result);
        assertEquals("echo: hello", result.getValue());
    }

    @Test
    public void testServerErrorReturnsToOriginalRequest() {
        server.setRequestHandler(invocation -> {
            throw new IllegalStateException("simulated failure");
        });
        Invocation inv = new Invocation();
        inv.setInterfaceName("EchoService");
        inv.setMethodName("echo");
        inv.getAttachments().put("targetHost", "127.0.0.1");
        inv.getAttachments().put("targetPort", String.valueOf(port));

        Result result = client.send(inv);
        assertNotNull(result);
        assertTrue(result.hasException());
        assertTrue(result.getException().getMessage().contains("simulated failure"));
    }

    @Test
    public void testDecodeFailureCompletesPendingRequest() {
        RpcCellProtocol brokenProtocol = new RpcCellProtocol() {
            @Override
            public Result decodeResponse(byte[] data, int serializationId) {
                throw new IllegalStateException("broken response");
            }
        };
        RpcNettyClient brokenClient = new RpcNettyClient(brokenProtocol, 3000, 1000);
        try {
            Invocation inv = new Invocation();
            inv.setInterfaceName("EchoService");
            inv.setMethodName("echo");
            inv.getAttachments().put("targetHost", "127.0.0.1");
            inv.getAttachments().put("targetPort", String.valueOf(port));

            Result result = brokenClient.send(inv);
            assertNotNull(result);
            assertTrue(result.hasException());
            assertTrue(result.getException().getMessage().contains("broken response"));
            assertFalse(result.getException().getMessage().contains("Request timeout"));
        } finally {
            brokenClient.shutdown();
        }
    }

    @Test
    public void testHeaderDecodeFailureCompletesPendingRequest() {
        RpcCellProtocol brokenProtocol = new RpcCellProtocol() {
            @Override
            public RpcCellProtocol.FrameHeader parseHeader(byte[] header) {
                throw new IllegalStateException("broken header");
            }
        };
        RpcNettyClient brokenClient = new RpcNettyClient(brokenProtocol, 3000, 1000);
        try {
            Invocation inv = new Invocation();
            inv.setInterfaceName("EchoService");
            inv.setMethodName("echo");
            inv.getAttachments().put("targetHost", "127.0.0.1");
            inv.getAttachments().put("targetPort", String.valueOf(port));

            Result result = brokenClient.send(inv);
            assertNotNull(result);
            assertTrue(result.hasException());
            assertTrue(result.getException().getMessage().contains("broken header"));
            assertFalse(result.getException().getMessage().contains("Request timeout"));
        } finally {
            brokenClient.shutdown();
        }
    }

    @Test
    public void testShortHeaderCompletesPendingRequest() throws Exception {
        RpcNettyClient shortResponseClient = new RpcNettyClient(protocol, 3000, 200);
        CountDownLatch releaseServer = new CountDownLatch(1);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        try (ServerSocket malformedServer = new ServerSocket(0)) {
            Thread responder = new Thread(() -> {
                try (Socket socket = malformedServer.accept()) {
                    socket.getInputStream().readNBytes(14);
                    socket.getOutputStream().write(new byte[] {0, 0, 0, 6, 1, 2});
                    socket.getOutputStream().flush();
                    if (!releaseServer.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("client was not released");
                    }
                } catch (Throwable e) {
                    serverFailure.set(e);
                }
            });
            responder.start();
            Invocation invocation = new Invocation();
            invocation.setInterfaceName("EchoService");
            invocation.setMethodName("echo");

            Result result;
            try {
                result = shortResponseClient.send(invocation, "127.0.0.1", malformedServer.getLocalPort());
            } finally {
                releaseServer.countDown();
                responder.join(5000);
            }

            assertNull(serverFailure.get());
            assertNotNull(result);
            assertTrue(result.hasException());
            assertTrue(result.getException().getMessage().contains("Frame too short"));
            assertFalse(result.getException().getMessage().contains("Request timeout"));
        } finally {
            shortResponseClient.shutdown();
        }
    }

    @Test
    public void testUnknownRequestSerializerClosesConnection() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            byte[] request = new byte[14];
            request[3] = 14;
            request[4] = 127;
            request[5] = 1;
            request[13] = 42;
            socket.getOutputStream().write(request);
            socket.getOutputStream().flush();

            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test
    public void testInvocationTimeoutOverridesClientDefault() {
        server.setRequestHandler(invocation -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Result.success("late");
        });
        Invocation inv = new Invocation();
        inv.setInterfaceName("EchoService");
        inv.setMethodName("echo");
        inv.getAttachments().put("targetHost", "127.0.0.1");
        inv.getAttachments().put("targetPort", String.valueOf(port));
        inv.getAttachments().put(Attachments.TIMEOUT, "30");

        long start = System.nanoTime();
        Result result = client.send(inv);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(result.hasException());
        assertEquals(ResultCodes.CLIENT_TIMEOUT.getCode(), result.getException().getErrorCode());
        assertTrue(elapsedMillis < 1000, "Invocation timeout was not applied");
    }

    @Test
    public void testInterruptedCallerPreservesInterruptStatus() throws Exception {
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        server.setRequestHandler(invocation -> {
            handlerStarted.countDown();
            try {
                releaseHandler.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Result.success("late");
        });
        Invocation invocation = new Invocation();
        invocation.setInterfaceName("EchoService");
        invocation.setMethodName("echo");
        invocation.getAttachments().put("targetHost", "127.0.0.1");
        invocation.getAttachments().put("targetPort", String.valueOf(port));
        AtomicReference<Result> callResult = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            callResult.set(client.send(invocation));
            interrupted.set(Thread.currentThread().isInterrupted());
        });

        try {
            caller.start();
            assertTrue(handlerStarted.await(5, TimeUnit.SECONDS));
            caller.interrupt();
            caller.join(5000);

            assertFalse(caller.isAlive());
            assertNotNull(callResult.get());
            assertTrue(callResult.get().hasException());
            assertEquals(
                    ResultCodes.TRANSPORT_ERROR.getCode(),
                    callResult.get().getException().getErrorCode());
            assertEquals(Boolean.TRUE, interrupted.get());
        } finally {
            releaseHandler.countDown();
            caller.join(5000);
        }
    }

    @Test
    public void testInterruptedConnectPreservesInterruptStatus() throws Exception {
        RpcNettyClient blockingClient = new RpcNettyClient(protocol);
        CountDownLatch connectStarted = new CountDownLatch(1);
        NioSocketChannel connectChannel = new NioSocketChannel();
        DefaultChannelPromise connectPromise =
                new DefaultChannelPromise(connectChannel, ImmediateEventExecutor.INSTANCE);
        Bootstrap blockingBootstrap = new Bootstrap() {
            @Override
            public ChannelFuture connect(String host, int port) {
                connectStarted.countDown();
                return connectPromise;
            }
        };
        Field bootstrapField = RpcNettyClient.class.getDeclaredField("bootstrap");
        bootstrapField.setAccessible(true);
        bootstrapField.set(blockingClient, blockingBootstrap);
        AtomicReference<Result> callResult = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            callResult.set(blockingClient.send(new Invocation(), "127.0.0.1", port));
            interrupted.set(Thread.currentThread().isInterrupted());
        });

        try {
            caller.start();
            assertTrue(connectStarted.await(5, TimeUnit.SECONDS));
            caller.interrupt();
            caller.join(5000);

            assertFalse(caller.isAlive());
            assertNotNull(callResult.get());
            assertTrue(callResult.get().hasException());
            assertEquals(
                    ResultCodes.TRANSPORT_ERROR.getCode(),
                    callResult.get().getException().getErrorCode());
            assertEquals(Boolean.TRUE, interrupted.get());
            assertTrue(connectPromise.isCancelled());
        } finally {
            connectPromise.cancel(false);
            connectChannel.unsafe().closeForcibly();
            caller.join(5000);
            blockingClient.shutdown();
        }
    }

    @Test
    public void testLateSuccessfulConnectAfterInterruptClosesChannel() throws Exception {
        RpcNettyClient blockingClient = new RpcNettyClient(protocol);
        CountDownLatch connectStarted = new CountDownLatch(1);
        AtomicReference<Boolean> closeCalled = new AtomicReference<>(false);
        NioSocketChannel connectChannel = new NioSocketChannel() {
            @Override
            public ChannelFuture close() {
                closeCalled.set(true);
                return new DefaultChannelPromise(this, ImmediateEventExecutor.INSTANCE).setSuccess(null);
            }
        };
        DefaultChannelPromise connectPromise =
                new DefaultChannelPromise(connectChannel, ImmediateEventExecutor.INSTANCE) {
                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return false;
                    }
                };
        Bootstrap blockingBootstrap = new Bootstrap() {
            @Override
            public ChannelFuture connect(String host, int port) {
                connectStarted.countDown();
                return connectPromise;
            }
        };
        Field bootstrapField = RpcNettyClient.class.getDeclaredField("bootstrap");
        bootstrapField.setAccessible(true);
        bootstrapField.set(blockingClient, blockingBootstrap);
        Thread caller = new Thread(() -> blockingClient.send(new Invocation(), "127.0.0.1", port));

        try {
            caller.start();
            assertTrue(connectStarted.await(5, TimeUnit.SECONDS));
            caller.interrupt();
            caller.join(5000);
            assertFalse(caller.isAlive());

            connectPromise.setSuccess(null);
            assertTrue(connectPromise.isSuccess());
            assertTrue(closeCalled.get());
        } finally {
            connectPromise.cancel(false);
            connectChannel.unsafe().closeForcibly();
            caller.join(5000);
            blockingClient.shutdown();
        }
    }

    @Test
    public void testLateSuccessfulBindAfterInterruptClosesChannel() throws Exception {
        RpcNettyServer blockingServer = new RpcNettyServer(protocol, "127.0.0.1", port);
        CountDownLatch bindStarted = new CountDownLatch(1);
        AtomicReference<Boolean> closeCalled = new AtomicReference<>(false);
        NioSocketChannel bindChannel = new NioSocketChannel() {
            @Override
            public ChannelFuture close() {
                closeCalled.set(true);
                return new DefaultChannelPromise(this, ImmediateEventExecutor.INSTANCE).setSuccess(null);
            }
        };
        DefaultChannelPromise bindPromise = new DefaultChannelPromise(bindChannel, ImmediateEventExecutor.INSTANCE) {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }
        };
        ServerBootstrap blockingBootstrap = new ServerBootstrap() {
            @Override
            public ChannelFuture bind(String host, int port) {
                bindStarted.countDown();
                return bindPromise;
            }
        };
        Field bootstrapField = RpcNettyServer.class.getDeclaredField("bootstrap");
        bootstrapField.setAccessible(true);
        bootstrapField.set(blockingServer, blockingBootstrap);
        Thread starter = new Thread(() -> {
            try {
                blockingServer.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            starter.start();
            assertTrue(bindStarted.await(5, TimeUnit.SECONDS));
            starter.interrupt();
            starter.join(5000);
            assertFalse(starter.isAlive());

            bindPromise.setSuccess(null);
            assertTrue(bindPromise.isSuccess());
            assertTrue(closeCalled.get());
        } finally {
            bindPromise.cancel(false);
            bindChannel.unsafe().closeForcibly();
            starter.join(5000);
            blockingServer.shutdown();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testShutdownPreservesInterruptStatus() throws Exception {
        RpcNettyClient closingClient = new RpcNettyClient(protocol);
        CountDownLatch closeStarted = new CountDownLatch(1);
        AtomicReference<DefaultChannelPromise> closePromiseRef = new AtomicReference<>();
        NioSocketChannel channel = new NioSocketChannel() {
            @Override
            public ChannelFuture close() {
                closeStarted.countDown();
                return closePromiseRef.get();
            }
        };
        DefaultChannelPromise closePromise = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        closePromiseRef.set(closePromise);
        Field channelCacheField = RpcNettyClient.class.getDeclaredField("channelCache");
        channelCacheField.setAccessible(true);
        ((Map<String, Channel>) channelCacheField.get(closingClient)).put("test", channel);
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread shutdownThread = new Thread(() -> {
            closingClient.shutdown();
            interrupted.set(Thread.currentThread().isInterrupted());
        });

        try {
            shutdownThread.start();
            assertTrue(closeStarted.await(5, TimeUnit.SECONDS));
            shutdownThread.interrupt();
            shutdownThread.join(5000);

            assertFalse(shutdownThread.isAlive());
            assertEquals(Boolean.TRUE, interrupted.get());
        } finally {
            closePromise.cancel(false);
            channel.unsafe().closeForcibly();
            shutdownThread.join(5000);
            closingClient.shutdown();
        }
    }

    @Test
    public void testBusinessHandlerDoesNotRunOnNioEventLoop() {
        AtomicReference<Thread> handlerThread = new AtomicReference<>();
        server.setRequestHandler(invocation -> {
            handlerThread.set(Thread.currentThread());
            return Result.success("ok");
        });
        Invocation invocation = new Invocation();
        invocation.setInterfaceName("EchoService");
        invocation.setMethodName("echo");
        invocation.getAttachments().put("targetHost", "127.0.0.1");
        invocation.getAttachments().put("targetPort", String.valueOf(port));

        assertEquals("ok", client.send(invocation).getValue());
        assertNotNull(handlerThread.get());
        assertFalse(handlerThread.get().getName().contains("nioEventLoopGroup"));
    }

    @Test
    public void testMultipleRequests() {
        for (int i = 0; i < 5; i++) {
            Invocation inv = new Invocation();
            inv.setInterfaceName("EchoService");
            inv.setMethodName("echo");
            inv.setParameterTypes(new Class<?>[] {String.class});
            inv.setArguments(new Object[] {"msg" + i});
            inv.getAttachments().put("targetHost", "127.0.0.1");
            inv.getAttachments().put("targetPort", String.valueOf(port));

            Result result = client.send(inv);
            assertEquals("echo: msg" + i, result.getValue());
        }
    }

    @Test
    public void testRequestWithoutArguments() {
        Invocation inv = new Invocation();
        inv.setInterfaceName("EchoService");
        inv.setMethodName("ping");
        inv.getAttachments().put("targetHost", "127.0.0.1");
        inv.getAttachments().put("targetPort", String.valueOf(port));

        Result result = client.send(inv);
        assertNotNull(result);
        assertEquals("echo: null", result.getValue());
    }

    @Test
    public void testConcurrentRequests() throws Exception {
        Thread[] threads = new Thread[10];
        Exception[] failures = new Exception[1];
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    Invocation inv = new Invocation();
                    inv.setInterfaceName("EchoService");
                    inv.setMethodName("echo");
                    inv.setParameterTypes(new Class<?>[] {String.class});
                    inv.setArguments(new Object[] {"hello" + index});
                    inv.getAttachments().put("targetHost", "127.0.0.1");
                    inv.getAttachments().put("targetPort", String.valueOf(port));

                    Result result = client.send(inv);
                    assertEquals("echo: hello" + index, result.getValue());
                } catch (Exception e) {
                    failures[0] = e;
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertNull(failures[0]);
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
