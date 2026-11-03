package io.rpcell.filter;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.spi.Filter;
import io.rpcell.api.spi.Invoker;
import io.rpcell.spi.ExtensionLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilterChainTest {

    private static final Invoker ECHO_INVOKER = invocation -> {
        Result result = new Result();
        result.setValue("echo:" + invocation.getMethodName());
        return result;
    };

    @AfterEach
    void tearDown() {
        ExtensionLoader.clear();
    }

    @Nested
    class BuildFilterChain {

        @Test
        void shouldReturnTargetWhenFiltersEmpty() {
            Invoker chain = FilterChain.buildFilterChain(ECHO_INVOKER);
            Result result = chain.invoke(createInvocation("hello"));
            assertEquals("echo:hello", result.getValue());
        }

        @Test
        void shouldReturnTargetWhenFiltersNull() {
            Invoker chain = FilterChain.buildFilterChain(ECHO_INVOKER, null);
            Result result = chain.invoke(createInvocation("world"));
            assertEquals("echo:world", result.getValue());
        }

        @Test
        void shouldReturnTargetWhenFiltersEmptyList() {
            Invoker chain = FilterChain.buildFilterChain(ECHO_INVOKER, Collections.emptyList());
            Result result = chain.invoke(createInvocation("test"));
            assertEquals("echo:test", result.getValue());
        }
    }

    @Nested
    class SingleFilter {

        @Test
        void shouldWrapWithSingleFilter() {
            Filter uppercase = (next, inv) -> {
                Result r = next.invoke(inv);
                r.setValue(((String) r.getValue()).toUpperCase());
                return r;
            };
            Invoker chain = FilterChain.buildFilterChain(ECHO_INVOKER, Collections.singletonList(uppercase));
            Result result = chain.invoke(createInvocation("hello"));
            assertEquals("ECHO:HELLO", result.getValue());
        }

        @Test
        void shouldAllowFilterToBlockCall() {
            Filter blocker = (next, inv) -> {
                Result result = new Result();
                result.setException(new Result.RpcResultException("blocked", "TestException", 403));
                return result;
            };
            Invoker chain = FilterChain.buildFilterChain(ECHO_INVOKER, Collections.singletonList(blocker));
            Result result = chain.invoke(createInvocation("test"));
            assertTrue(result.hasException());
            assertEquals("blocked", result.getException().getMessage());
        }

        @Test
        void shouldAllowFilterToModifyInvocation() {
            Filter prefixer = (next, inv) -> {
                inv.setMethodName("prefix:" + inv.getMethodName());
                return next.invoke(inv);
            };
            Invoker chain = FilterChain.buildFilterChain(ECHO_INVOKER, Collections.singletonList(prefixer));
            Result result = chain.invoke(createInvocation("world"));
            assertEquals("echo:prefix:world", result.getValue());
        }
    }

    @Nested
    class MultipleFilters {

        @Test
        void shouldExecuteFiltersInOrder() {
            List<String> executionOrder = new ArrayList<>();
            Filter first = (next, inv) -> {
                executionOrder.add("first-before");
                Result r = next.invoke(inv);
                executionOrder.add("first-after");
                return r;
            };
            Filter second = (next, inv) -> {
                executionOrder.add("second-before");
                Result r = next.invoke(inv);
                executionOrder.add("second-after");
                return r;
            };
            Invoker chain = FilterChain.buildFilterChain(ECHO_INVOKER, Arrays.asList(first, second));
            chain.invoke(createInvocation("test"));
            assertEquals(Arrays.asList("first-before", "second-before", "second-after", "first-after"), executionOrder);
        }

        @Test
        void shouldStackFilterTransformations() {
            Filter appendA = (next, inv) -> {
                Result r = next.invoke(inv);
                r.setValue(r.getValue() + ".A");
                return r;
            };
            Filter appendB = (next, inv) -> {
                Result r = next.invoke(inv);
                r.setValue(r.getValue() + ".B");
                return r;
            };
            Invoker chain = FilterChain.buildFilterChain(ECHO_INVOKER, Arrays.asList(appendA, appendB));
            Result result = chain.invoke(createInvocation("msg"));
            assertEquals("echo:msg.B.A", result.getValue());
        }
    }

    @Nested
    class IntegrationWithExtensionLoader {

        @Test
        void shouldLoadFiltersViaExtensionLoader() {
            ExtensionLoader.register("filter:test", TestFilter.class);
            TestFilter filter = ExtensionLoader.load("filter:test");
            assertNotNull(filter);
            Invoker chain = FilterChain.buildFilterChain(ECHO_INVOKER, Collections.singletonList(filter));
            Result result = chain.invoke(createInvocation("hello"));
            assertEquals("[ECHO:HELLO]", result.getValue());
        }
    }

    public static class TestFilter implements Filter {

        @Override
        public Result invoke(Invoker invoker, Invocation invocation) {
            Result r = invoker.invoke(invocation);
            String value = (String) r.getValue();
            r.setValue("[" + value.toUpperCase() + "]");
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
