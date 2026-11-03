package io.rpcell.common;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.annotation.RpcMethod;
import io.rpcell.api.annotation.RpcService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

@RpcService(
        include = {},
        exclude = {})
interface TestServiceAllExposed {
    void methodA();

    void methodB();

    String methodC();
}

@RpcService(
        include = {"methodA"},
        exclude = {})
interface TestServiceInclude {
    void methodA();

    void methodB();

    String methodC();
}

@RpcService(
        include = {},
        exclude = {"methodB"})
interface TestServiceExclude {
    void methodA();

    void methodB();

    String methodC();
}

@RpcService(
        include = {"methodB"},
        exclude = {"methodB"})
interface TestServiceExcludePriority {
    void methodA();

    void methodB();

    String methodC();
}

@RpcService(
        include = {},
        exclude = {})
interface TestServiceWithRpcMethod {
    @RpcMethod(exposed = false)
    void methodA();

    void methodB();

    @RpcMethod(exposed = true)
    String methodC();
}

@RpcService(
        include = {"*"},
        exclude = {})
interface TestServiceWildcard {
    void methodA();

    void methodB();
}

public class ServiceFilterTest {

    @Test
    public void testAllExposedByDefault() throws Exception {
        RpcService annotation = TestServiceAllExposed.class.getAnnotation(RpcService.class);
        assertTrue(ServiceFilter.isExposed(method(TestServiceAllExposed.class, "methodA"), annotation));
        assertTrue(ServiceFilter.isExposed(method(TestServiceAllExposed.class, "methodB"), annotation));
    }

    @Test
    public void testIncludeOnly() throws Exception {
        RpcService annotation = TestServiceInclude.class.getAnnotation(RpcService.class);
        assertTrue(ServiceFilter.isExposed(method(TestServiceInclude.class, "methodA"), annotation));
        assertFalse(ServiceFilter.isExposed(method(TestServiceInclude.class, "methodB"), annotation));
    }

    @Test
    public void testExcludeOnly() throws Exception {
        RpcService annotation = TestServiceExclude.class.getAnnotation(RpcService.class);
        assertTrue(ServiceFilter.isExposed(method(TestServiceExclude.class, "methodA"), annotation));
        assertFalse(ServiceFilter.isExposed(method(TestServiceExclude.class, "methodB"), annotation));
    }

    @Test
    public void testExcludePriority() throws Exception {
        RpcService annotation = TestServiceExcludePriority.class.getAnnotation(RpcService.class);
        assertFalse(ServiceFilter.isExposed(method(TestServiceExcludePriority.class, "methodB"), annotation));
    }

    @Test
    public void testRpcMethodOverride() throws Exception {
        RpcService annotation = TestServiceWithRpcMethod.class.getAnnotation(RpcService.class);
        assertFalse(ServiceFilter.isExposed(method(TestServiceWithRpcMethod.class, "methodA"), annotation));
        assertTrue(ServiceFilter.isExposed(method(TestServiceWithRpcMethod.class, "methodB"), annotation));
        assertTrue(ServiceFilter.isExposed(method(TestServiceWithRpcMethod.class, "methodC"), annotation));
    }

    @Test
    public void testWildcardMatch() {
        assertTrue(ServiceFilter.wildcardMatch("*", "anything"));
        assertTrue(ServiceFilter.wildcardMatch("method*", "methodA"));
        assertFalse(ServiceFilter.wildcardMatch("method*", "funcA"));
        assertTrue(ServiceFilter.wildcardMatch("exact", "exact"));
        assertTrue(ServiceFilter.wildcardMatch("method(*", "method(A"));
        assertFalse(ServiceFilter.wildcardMatch("foo|bar*", "fooA"));
        assertTrue(ServiceFilter.wildcardMatch("foo|bar*", "foo|barA"));
        assertFalse(ServiceFilter.wildcardMatch(null, "anything"));
        assertFalse(ServiceFilter.wildcardMatch("*", null));
    }

    @Test
    public void testMatchAny() {
        assertTrue(ServiceFilter.matchAny(new String[] {"a*", "b*"}, "alpha"));
        assertFalse(ServiceFilter.matchAny(new String[] {"a*", "b*"}, "gamma"));
        assertFalse(ServiceFilter.matchAny(new String[] {}, "anything"));
    }

    @Test
    public void testWildcardInclude() throws Exception {
        RpcService annotation = TestServiceWildcard.class.getAnnotation(RpcService.class);
        assertTrue(ServiceFilter.isExposed(method(TestServiceWildcard.class, "methodA"), annotation));
        assertTrue(ServiceFilter.isExposed(method(TestServiceWildcard.class, "methodB"), annotation));
    }

    private Method method(Class<?> clazz, String name) throws NoSuchMethodException {
        return clazz.getDeclaredMethod(name);
    }
}
