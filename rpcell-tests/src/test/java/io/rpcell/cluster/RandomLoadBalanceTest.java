package io.rpcell.cluster;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RandomLoadBalanceTest {

    private List<URL> urls;
    private Invocation inv;
    private RandomLoadBalance loadBalance;

    @BeforeEach
    void setUp() {
        loadBalance = new RandomLoadBalance();
        urls = new ArrayList<>();
        urls.add(new URL("rpcell", "10.0.0.1", 20880, "Test", "default", "1.0"));
        urls.add(new URL("rpcell", "10.0.0.2", 20880, "Test", "default", "1.0"));
        urls.add(new URL("rpcell", "10.0.0.3", 20880, "Test", "default", "1.0"));
        urls.add(new URL("rpcell", "10.0.0.4", 20880, "Test", "default", "1.0"));
        urls.add(new URL("rpcell", "10.0.0.5", 20880, "Test", "default", "1.0"));
        inv = new Invocation();
        inv.setInterfaceName("TestService");
    }

    @Test
    void shouldReturnValidUrl() {
        URL selected = loadBalance.select(urls, inv);
        assertNotNull(selected);
        assertTrue(urls.contains(selected));
    }

    @Test
    void shouldReturnNullOnEmptyList() {
        assertNull(loadBalance.select(new ArrayList<>(), inv));
    }

    @Test
    void shouldReturnNullOnNull() {
        assertNull(loadBalance.select(null, inv));
    }

    @Test
    void shouldReturnSameUrlForSingleElement() {
        List<URL> single = new ArrayList<>();
        URL u = new URL("rpcell", "127.0.0.1", 9999, "Test", "default", "1.0");
        single.add(u);
        assertEquals(u, loadBalance.select(single, inv));
    }

    @Nested
    class DistributionTest {

        @Test
        void shouldDistributeEvenlyAcrossNodes() {
            int iterations = 10000;
            Map<String, Integer> counter = new HashMap<>();
            for (URL url : urls) {
                counter.put(url.toAddress(), 0);
            }

            for (int i = 0; i < iterations; i++) {
                URL selected = loadBalance.select(urls, inv);
                counter.merge(selected.toAddress(), 1, Integer::sum);
            }

            double expected = iterations / (double) urls.size();
            for (int count : counter.values()) {
                double deviation = Math.abs(count - expected) / expected;
                assertTrue(
                        deviation < 0.20,
                        "Expected ~" + expected + " but got " + count + " (deviation="
                                + String.format("%.2f", deviation) + ")");
            }
        }

        @Test
        void shouldNotProduceOnlyOneNode() {
            Map<String, Integer> counter = new HashMap<>();
            for (URL url : urls) {
                counter.put(url.toAddress(), 0);
            }

            for (int i = 0; i < 1000; i++) {
                URL selected = loadBalance.select(urls, inv);
                counter.merge(selected.toAddress(), 1, Integer::sum);
            }

            int nonZeroCount = 0;
            for (int count : counter.values()) {
                if (count > 0) nonZeroCount++;
            }
            assertTrue(
                    nonZeroCount >= urls.size() - 1,
                    "Expected at least " + (urls.size() - 1) + " nodes used, got " + nonZeroCount);
        }
    }
}
