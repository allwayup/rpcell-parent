package io.rpcell.filter;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.spi.Filter;
import io.rpcell.api.spi.Invoker;
import io.rpcell.spi.ExtensionLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilterLoaderTest {

    @AfterEach
    void tearDown() {
        ExtensionLoader.clear();
    }

    @Nested
    class LoadWithNamesList {

        @Test
        void shouldReturnEmptyListWhenNamesNull() {
            List<Filter> filters = FilterLoader.load((List<String>) null);
            assertTrue(filters.isEmpty());
        }

        @Test
        void shouldReturnEmptyListWhenNamesEmpty() {
            List<Filter> filters = FilterLoader.load(Collections.emptyList());
            assertTrue(filters.isEmpty());
        }

        @Test
        void shouldLoadSingleFilter() {
            ExtensionLoader.register("filter:alpha", AlphaFilter.class);
            List<Filter> filters = FilterLoader.load(Collections.singletonList("filter:alpha"));
            assertEquals(1, filters.size());
            assertTrue(filters.get(0) instanceof AlphaFilter);
        }

        @Test
        void shouldLoadMultipleFiltersInOrder() {
            ExtensionLoader.register("filter:alpha", AlphaFilter.class);
            ExtensionLoader.register("filter:beta", BetaFilter.class);
            List<Filter> filters = FilterLoader.load(Arrays.asList("filter:alpha", "filter:beta"));
            assertEquals(2, filters.size());
            assertTrue(filters.get(0) instanceof AlphaFilter);
            assertTrue(filters.get(1) instanceof BetaFilter);
        }

        @Test
        void shouldFailFastWhenFilterCannotBeLoaded() {
            ExtensionLoader.register("filter:alpha", AlphaFilter.class);
            assertThrows(
                    io.rpcell.api.exception.RpcParamException.class,
                    () -> FilterLoader.load(Arrays.asList("filter:alpha", "filter:nonexistent")));
        }

        @Test
        void shouldFailWhenAllFiltersAreMissing() {
            assertThrows(
                    io.rpcell.api.exception.RpcParamException.class,
                    () -> FilterLoader.load(Arrays.asList("filter:bad1", "filter:bad2")));
        }

        @Test
        void shouldLoadFromChainAndExecute() {
            ExtensionLoader.register("filter:alpha", AlphaFilter.class);
            ExtensionLoader.register("filter:beta", BetaFilter.class);
            List<Filter> filters = FilterLoader.load("filter:alpha", "filter:beta");
            Invoker target = inv -> {
                Result r = new Result();
                r.setValue("target:" + inv.getMethodName());
                return r;
            };
            Invoker chain = FilterChain.buildFilterChain(target, filters);
            Result result = chain.invoke(createInvocation("hello"));
            assertEquals("target:hello.B.A", result.getValue());
        }
    }

    @Nested
    class LoadWithVarargs {

        @Test
        void shouldReturnEmptyListWhenVarargsNull() {
            List<Filter> filters = FilterLoader.load((String[]) null);
            assertTrue(filters.isEmpty());
        }

        @Test
        void shouldReturnEmptyListWhenVarargsEmpty() {
            List<Filter> filters = FilterLoader.load();
            assertTrue(filters.isEmpty());
        }

        @Test
        void shouldLoadSingleFilterFromVarargs() {
            ExtensionLoader.register("filter:alpha", AlphaFilter.class);
            List<Filter> filters = FilterLoader.load("filter:alpha");
            assertEquals(1, filters.size());
        }
    }

    public static class AlphaFilter implements Filter {

        @Override
        public Result invoke(Invoker invoker, Invocation invocation) {
            Result r = invoker.invoke(invocation);
            r.setValue(r.getValue() + ".A");
            return r;
        }
    }

    public static class BetaFilter implements Filter {

        @Override
        public Result invoke(Invoker invoker, Invocation invocation) {
            Result r = invoker.invoke(invocation);
            r.setValue(r.getValue() + ".B");
            return r;
        }
    }

    private Invocation createInvocation(String methodName) {
        Invocation inv = new Invocation();
        inv.setInterfaceName("TestService");
        inv.setMethodName(methodName);
        return inv;
    }
}
