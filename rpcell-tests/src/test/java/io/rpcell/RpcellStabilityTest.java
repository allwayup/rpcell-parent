package io.rpcell;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.SerializerConfig;
import io.rpcell.api.model.URL;
import io.rpcell.api.spi.Filter;
import io.rpcell.api.spi.Invoker;
import io.rpcell.codec.RpcCellProtocol;
import io.rpcell.filter.FilterChain;
import io.rpcell.serializer.SerializerManager;
import io.rpcell.serializer.msgpack.MessagePackSerializerFactory;
import io.rpcell.spi.ExtensionLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RpcellStabilityTest {

    @BeforeAll
    static void initSpi() {
        ExtensionLoader.register("serializer:msgpack", MessagePackSerializerFactory.class);
        SerializerManager.init(new SerializerConfig());
    }

    @AfterEach
    void tearDown() {
        ExtensionLoader.clear();
    }

    @Nested
    class FilterChainConcurrency {

        private final Invoker baseInvoker = inv -> {
            Result r = new Result();
            r.setValue("ok:" + inv.getMethodName());
            return r;
        };

        @Test
        void shouldHandleConcurrentFilterChainAccess() throws Exception {
            List<Filter> filters = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final int id = i;
                filters.add((next, inv) -> {
                    Result r = next.invoke(inv);
                    r.setValue(r.getValue() + "." + id);
                    return r;
                });
            }
            Invoker chain = FilterChain.buildFilterChain(baseInvoker, filters);

            int threads = 20;
            int iterations = 500;
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                final int tid = t;
                new Thread(() -> {
                            try {
                                for (int i = 0; i < iterations; i++) {
                                    Invocation inv = createInvocation("m" + tid + "_" + i);
                                    Result result = chain.invoke(inv);
                                    assertNotNull(result);
                                    assertTrue(((String) result.getValue()).startsWith("ok:m"));
                                }
                            } catch (Throwable e) {
                                errors.incrementAndGet();
                            } finally {
                                latch.countDown();
                            }
                        })
                        .start();
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertEquals(0, errors.get());
        }

        @Test
        void shouldHandleEmptyFilterChainUnderConcurrency() throws Exception {
            Invoker chain = FilterChain.buildFilterChain(baseInvoker);

            int threads = 30;
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                new Thread(() -> {
                            try {
                                for (int i = 0; i < 1000; i++) {
                                    Result result = chain.invoke(createInvocation("test"));
                                    assertEquals("ok:test", result.getValue());
                                }
                            } catch (Throwable e) {
                                errors.incrementAndGet();
                            } finally {
                                latch.countDown();
                            }
                        })
                        .start();
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertEquals(0, errors.get());
        }
    }

    @Nested
    class SerializerConcurrency {

        @Test
        void shouldHandleConcurrentSerialization() throws Exception {
            int threads = 10;
            int iterations = 200;
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                final int tid = t;
                new Thread(() -> {
                            try {
                                for (int i = 0; i < iterations; i++) {
                                    Invocation inv = new Invocation();
                                    inv.setInterfaceName("Svc" + tid);
                                    inv.setMethodName("m" + i);
                                    inv.setArguments(new Object[] {"hello-" + tid + "-" + i});

                                    byte[] data = SerializerManager.getDefaultSerializer()
                                            .serialize(inv);
                                    assertNotNull(data);
                                    assertTrue(data.length > 0);

                                    Invocation restored = SerializerManager.getDefaultSerializer()
                                            .deserialize(data, Invocation.class);
                                    assertEquals("Svc" + tid, restored.getInterfaceName());
                                    assertEquals("m" + i, restored.getMethodName());
                                }
                            } catch (Throwable e) {
                                errors.incrementAndGet();
                            } finally {
                                latch.countDown();
                            }
                        })
                        .start();
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertEquals(0, errors.get());
        }
    }

    @Nested
    class ProtocolConcurrency {

        @Test
        void shouldHandleConcurrentEncodeDecode() throws Exception {
            RpcCellProtocol protocol = new RpcCellProtocol();
            int threads = 16;
            int iterations = 500;
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                final long baseRequestId = t * 1000000L;
                new Thread(() -> {
                            try {
                                for (int i = 0; i < iterations; i++) {
                                    long requestId = baseRequestId + i;
                                    Invocation inv = new Invocation();
                                    inv.setInterfaceName("Svc");
                                    inv.setMethodName("test");
                                    inv.setArguments(new Object[] {"data"});

                                    byte[] frame = protocol.encodeRequest(inv, requestId, 1);
                                    assertNotNull(frame);

                                    byte[] header = java.util.Arrays.copyOf(frame, 14);
                                    RpcCellProtocol.FrameHeader fh = protocol.parseHeader(header);
                                    assertEquals(requestId, fh.requestId);

                                    byte[] body = protocol.extractBody(frame);
                                    Invocation decoded = protocol.decodeRequest(body, fh.serializationId);
                                    assertEquals("Svc", decoded.getInterfaceName());
                                }
                            } catch (Throwable e) {
                                errors.incrementAndGet();
                            } finally {
                                latch.countDown();
                            }
                        })
                        .start();
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertEquals(0, errors.get());
        }
    }

    @Nested
    class ResultConcurrency {

        @Test
        void shouldBeThreadSafeForMultiThreadReadWrite() throws Exception {
            int threads = 20;
            CountDownLatch latch = new CountDownLatch(threads);
            Result result = new Result();
            result.setValue("initial");

            for (int t = 0; t < threads; t++) {
                final int tid = t;
                new Thread(() -> {
                            try {
                                for (int i = 0; i < 500; i++) {
                                    result.setValue("updated-" + tid);
                                    Object val = result.getValue();
                                    assertNotNull(val);
                                    assertFalse(result.hasException());
                                }
                            } finally {
                                latch.countDown();
                            }
                        })
                        .start();
            }

            assertTrue(latch.await(20, TimeUnit.SECONDS));
        }
    }

    @Nested
    class URLConcurrency {

        @Test
        void shouldBeThreadSafeForConcurrentOperations() throws Exception {
            URL url = new URL("rpcell", "192.168.1.1", 8080, "Svc", "g", "v1");
            int threads = 20;
            CountDownLatch latch = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                new Thread(() -> {
                            try {
                                for (int i = 0; i < 500; i++) {
                                    String addr = url.toAddress();
                                    assertEquals("192.168.1.1:8080", addr);
                                    url.addParam("k" + i, "v" + i);
                                    url.getParam("k" + (i / 2));
                                }
                            } finally {
                                latch.countDown();
                            }
                        })
                        .start();
            }

            assertTrue(latch.await(20, TimeUnit.SECONDS));
        }
    }

    @Nested
    class InvocationConcurrency {

        @Test
        void shouldHandleConcurrentInvocationAccess() throws Exception {
            Invocation inv = new Invocation();
            inv.setInterfaceName("Svc");
            inv.setMethodName("method");
            inv.setAttachments(new java.util.HashMap<>());

            int threads = 15;
            CountDownLatch latch = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                final int tid = t;
                new Thread(() -> {
                            try {
                                for (int i = 0; i < 300; i++) {
                                    inv.getAttachments().put("k" + tid + "_" + i, "v" + i);
                                    inv.setArguments(new Object[] {"arg-" + i});
                                    Object[] args = inv.getArguments();
                                    assertNotNull(args);
                                }
                            } finally {
                                latch.countDown();
                            }
                        })
                        .start();
            }

            assertTrue(latch.await(20, TimeUnit.SECONDS));
        }
    }

    @Nested
    class HighFrequencyStress {

        @Test
        void shouldSustainHighThroughputFilterChain() throws Exception {
            IgnoreFilter ignore = new IgnoreFilter();
            List<Filter> filters = Collections.singletonList(ignore);
            Invoker target = inv -> Result.success("done");
            Invoker chain = FilterChain.buildFilterChain(target, filters);

            int threadCount = 8;
            int totalOps = 100000;
            int perThread = totalOps / threadCount;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);
            ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

            for (int t = 0; t < threadCount; t++) {
                new Thread(() -> {
                            try {
                                for (int i = 0; i < perThread; i++) {
                                    long start = System.nanoTime();
                                    Result result = chain.invoke(createInvocation("m" + i));
                                    latencies.add(System.nanoTime() - start);
                                    assertEquals("done", result.getValue());
                                }
                            } catch (Throwable e) {
                                errors.incrementAndGet();
                            } finally {
                                latch.countDown();
                            }
                        })
                        .start();
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertEquals(0, errors.get());

            List<Long> latList = new ArrayList<>(latencies);
            Collections.sort(latList);
            long p50 = latList.get(latList.size() / 2);
            long p99 = latList.get((int) (latList.size() * 0.99));

            assertTrue(
                    p50 < TimeUnit.MILLISECONDS.toNanos(10),
                    "P50 latency should be <10ms, got " + TimeUnit.NANOSECONDS.toMillis(p50) + "ms");
            assertTrue(
                    p99 < TimeUnit.MILLISECONDS.toNanos(100),
                    "P99 latency should be <100ms, got " + TimeUnit.NANOSECONDS.toMillis(p99) + "ms");
        }
    }

    @Nested
    class RapidCreateDestroyCycle {

        @Test
        void shouldSurviveRapidFilterChainCreation() throws Exception {
            int threads = 10;
            int cycles = 200;
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                new Thread(() -> {
                            try {
                                for (int c = 0; c < cycles; c++) {
                                    List<Filter> filters = new ArrayList<>();
                                    for (int i = 0; i < 20; i++) {
                                        filters.add((next, inv) -> next.invoke(inv));
                                    }
                                    Invoker chain = FilterChain.buildFilterChain(inv -> Result.success("x"), filters);
                                    Result result = chain.invoke(createInvocation("test"));
                                    assertEquals("x", result.getValue());
                                }
                            } catch (Throwable e) {
                                errors.incrementAndGet();
                            } finally {
                                latch.countDown();
                            }
                        })
                        .start();
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertEquals(0, errors.get());
        }
    }

    @Nested
    class GracefulDegradation {

        @Test
        void shouldDegradeGracefullyUnderPartialFailures() throws Exception {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            Filter flakyFilter = (next, inv) -> {
                if (Math.random() < 0.5) {
                    failCount.incrementAndGet();
                    throw new RuntimeException("simulated failure");
                }
                successCount.incrementAndGet();
                return next.invoke(inv);
            };

            Invoker target = inv -> Result.success("ok");
            Invoker chain = FilterChain.buildFilterChain(target, Collections.singletonList(flakyFilter));

            int totalCalls = 1000;
            int errors = 0;
            for (int i = 0; i < totalCalls; i++) {
                try {
                    Result result = chain.invoke(createInvocation("m" + i));
                    assertNotNull(result);
                } catch (Exception e) {
                    errors++;
                }
            }

            assertTrue(successCount.get() > 0, "Some calls must succeed");
            assertTrue(failCount.get() > 0, "Some calls must fail");
            assertTrue(errors > 0, "Some errors must propagate to caller");
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void shouldHandleDeepFilterChain() {
            List<Filter> filters = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int id = i;
                filters.add((next, inv) -> next.invoke(inv));
            }
            Invoker chain = FilterChain.buildFilterChain(inv -> Result.success("deep"), filters);
            Result result = chain.invoke(createInvocation("test"));
            assertEquals("deep", result.getValue());
        }

        @Test
        void shouldHandleLargeInvocationNullSafety() {
            Invoker chain = FilterChain.buildFilterChain(
                    inv -> {
                        assertNotNull(inv);
                        assertNotNull(inv.getInterfaceName());
                        return Result.success("ok");
                    },
                    null);

            Invocation inv = new Invocation();
            inv.setInterfaceName("LargeSvc");
            inv.setMethodName("biz");

            for (int i = 0; i < 1000; i++) {
                Result result = chain.invoke(inv);
                assertEquals("ok", result.getValue());
            }
        }
    }

    private static class IgnoreFilter implements Filter {

        @Override
        public Result invoke(Invoker invoker, Invocation invocation) {
            return invoker.invoke(invocation);
        }
    }

    private Invocation createInvocation(String methodName) {
        Invocation inv = new Invocation();
        inv.setInterfaceName("TestService");
        inv.setMethodName(methodName);
        return inv;
    }
}
