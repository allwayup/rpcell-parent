package io.rpcell.cluster;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.constant.Attachments;
import io.rpcell.api.enums.ResultCodes;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.LoadBalance;
import io.rpcell.registry.RegistryDirectory;
import io.rpcell.spi.ExtensionLoader;
import io.rpcell.transport.ClientInvoker;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FailoverClusterTest {

    private List<URL> urls;
    private RegistryDirectory directory;

    @BeforeEach
    void setUp() {
        urls = new ArrayList<>();
        urls.add(new URL("rpcell", "127.0.0.1", 8080, "TestService", "default", "1.0"));
        urls.add(new URL("rpcell", "127.0.0.1", 8081, "TestService", "default", "1.0"));
        urls.add(new URL("rpcell", "127.0.0.2", 8080, "TestService", "default", "1.0"));
        directory = new RegistryDirectory();
        directory.notify(urls);
    }

    @Nested
    class BasicInvoke {

        @Test
        void shouldUseInvocationLoadBalance() {
            ExtensionLoader.register("test-last", LastUrlLoadBalance.class);
            AtomicReference<URL> selectedUrl = new AtomicReference<>();
            ClientInvoker client = new ClientInvoker(null) {
                @Override
                public Result invoke(Invocation invocation, URL targetUrl) {
                    selectedUrl.set(targetUrl);
                    return Result.success("ok");
                }
            };
            FailoverCluster cluster = new FailoverCluster(directory, (u, i) -> u.get(0), client, 0);
            Invocation invocation = new Invocation();
            invocation.getAttachments().put(Attachments.LOADBALANCE, "test-last");

            assertEquals("ok", cluster.invoke(invocation).getValue());
            assertEquals(urls.get(urls.size() - 1), selectedUrl.get());
        }

        @Test
        void shouldSucceedOnFirstTry() {
            AtomicInteger callCount = new AtomicInteger();
            ClientInvoker client = new ClientInvoker(null) {
                @Override
                public Result invoke(Invocation invocation) {
                    callCount.incrementAndGet();
                    return Result.success("ok");
                }

                @Override
                public Result invoke(Invocation invocation, URL targetUrl) {
                    callCount.incrementAndGet();
                    return Result.success("ok");
                }
            };
            LoadBalance load = (u, i) -> u.get(0);
            FailoverCluster cluster = new FailoverCluster(directory, load, client, 3);

            Result result = cluster.invoke(new Invocation());
            assertEquals("ok", result.getValue());
            assertEquals(1, callCount.get());
        }
    }

    public static class LastUrlLoadBalance implements LoadBalance {
        @Override
        public URL select(List<URL> urls, Invocation invocation) {
            return urls.get(urls.size() - 1);
        }
    }

    @Nested
    class Retry {

        @Test
        void shouldRetryOnFailure() {
            AtomicInteger attempts = new AtomicInteger();
            ClientInvoker client = new ClientInvoker(null) {
                @Override
                public Result invoke(Invocation invocation) {
                    if (attempts.incrementAndGet() < 3) throw new RuntimeException("fail");
                    return Result.success("recovered");
                }

                @Override
                public Result invoke(Invocation invocation, URL targetUrl) {
                    if (attempts.incrementAndGet() < 3) throw new RuntimeException("fail");
                    return Result.success("recovered");
                }
            };
            LoadBalance load = (u, i) -> u.get(attempts.get() % u.size());
            FailoverCluster cluster = new FailoverCluster(directory, load, client, 3);

            Result result = cluster.invoke(new Invocation());
            assertEquals("recovered", result.getValue());
            assertEquals(3, attempts.get());
        }

        @Test
        void shouldNotRetryBusinessFailure() {
            AtomicInteger attempts = new AtomicInteger();
            Result businessFailure = Result.failure(new Result.RpcResultException(
                    "business rejected", "java.lang.IllegalStateException", ResultCodes.UNKNOWN_ERROR.getCode()));
            ClientInvoker client = new ClientInvoker(null) {
                @Override
                public Result invoke(Invocation invocation, URL targetUrl) {
                    attempts.incrementAndGet();
                    return businessFailure;
                }
            };
            FailoverCluster cluster = new FailoverCluster(directory, (u, i) -> u.get(0), client, 3);

            assertSame(businessFailure, cluster.invoke(new Invocation()));
            assertEquals(1, attempts.get());
        }

        @Test
        void shouldRetryTransportFailureResult() {
            AtomicInteger attempts = new AtomicInteger();
            ClientInvoker client = new ClientInvoker(null) {
                @Override
                public Result invoke(Invocation invocation, URL targetUrl) {
                    if (attempts.incrementAndGet() == 1) {
                        return Result.failure(new Result.RpcResultException(
                                "connection reset",
                                "io.rpcell.api.exception.RpcTransportException",
                                ResultCodes.TRANSPORT_ERROR.getCode()));
                    }
                    return Result.success("recovered");
                }
            };
            FailoverCluster cluster = new FailoverCluster(directory, (u, i) -> u.get(0), client, 1);

            assertEquals("recovered", cluster.invoke(new Invocation()).getValue());
            assertEquals(2, attempts.get());
        }

        @Test
        void shouldExhaustRetries() {
            AtomicInteger attempts = new AtomicInteger();
            ClientInvoker client = new ClientInvoker(null) {
                @Override
                public Result invoke(Invocation invocation) {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always");
                }

                @Override
                public Result invoke(Invocation invocation, URL targetUrl) {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always");
                }
            };
            LoadBalance load = (u, i) -> u.get(0);
            FailoverCluster cluster = new FailoverCluster(directory, load, client, 2);

            Result result = cluster.invoke(new Invocation());
            assertTrue(result.hasException());
            assertEquals(3, attempts.get());
        }

        @Test
        void shouldNotExceedRetryCount() {
            AtomicInteger attempts = new AtomicInteger();
            ClientInvoker client = new ClientInvoker(null) {
                @Override
                public Result invoke(Invocation invocation) {
                    attempts.incrementAndGet();
                    throw new RuntimeException("fail");
                }

                @Override
                public Result invoke(Invocation invocation, URL targetUrl) {
                    attempts.incrementAndGet();
                    throw new RuntimeException("fail");
                }
            };
            LoadBalance load = (u, i) -> u.get(0);
            FailoverCluster cluster = new FailoverCluster(directory, load, client, 1);

            Result result = cluster.invoke(new Invocation());
            assertTrue(result.hasException());
            assertEquals(2, attempts.get());
        }

        @Test
        void shouldHandleMaximumRetriesWithoutOverflow() {
            AtomicInteger attempts = new AtomicInteger();
            ClientInvoker client = new ClientInvoker(null) {
                @Override
                public Result invoke(Invocation invocation, URL targetUrl) {
                    attempts.incrementAndGet();
                    return Result.success("ok");
                }
            };
            FailoverCluster cluster = new FailoverCluster(directory, (u, i) -> u.get(0), client, Integer.MAX_VALUE);

            Result result = cluster.invoke(new Invocation());

            assertEquals("ok", result.getValue());
            assertEquals(1, attempts.get());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void shouldReturnFailureResultForNullClient() {
            LoadBalance load = (u, i) -> u.get(0);
            FailoverCluster cluster = new FailoverCluster(directory, load, null, 3);
            Invocation inv = new Invocation();
            inv.setInterfaceName("TestService");
            Result result = cluster.invoke(inv);
            assertNotNull(result);
            assertTrue(result.hasException());
        }
    }
}
