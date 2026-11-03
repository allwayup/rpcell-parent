package io.rpcell.proxy;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.exception.RpcRemoteException;
import io.rpcell.api.model.Result;
import io.rpcell.api.spi.Invoker;
import org.junit.jupiter.api.Test;

public class RpcProxyFactoryTest {

    interface TestService {
        String echo(String msg);
    }

    @Test
    public void testCreateProxy() {
        Invoker invoker = invocation -> Result.success(invocation.getArguments()[0]);
        TestService proxy = RpcProxyFactory.createProxy(TestService.class, invoker);
        assertNotNull(proxy);
    }

    @Test
    public void testProxyInvocation() {
        Invoker invoker = invocation -> Result.success(invocation.getArguments()[0]);
        TestService proxy = RpcProxyFactory.createProxy(TestService.class, invoker);
        String result = proxy.echo("hello");
        assertEquals("hello", result);
    }

    @Test
    public void testRemoteFailurePreservesErrorMetadata() {
        Invoker invoker = invocation -> Result.failure(
                new Result.RpcResultException("business rejected", "java.lang.IllegalStateException", 4321));
        TestService proxy = RpcProxyFactory.createProxy(TestService.class, invoker);

        RpcRemoteException error = assertThrows(RpcRemoteException.class, () -> proxy.echo("hello"));
        assertEquals("business rejected", error.getMessage());
        assertEquals(4321, error.getErrorCode());
        assertEquals("java.lang.IllegalStateException", error.getRemoteExceptionClassName());
    }

    @Test
    public void testNonInterfaceRejected() {
        Invoker invoker = invocation -> Result.success(null);
        assertThrows(IllegalArgumentException.class, () -> RpcProxyFactory.createProxy(String.class, invoker));
    }

    @Test
    public void testToStringDelegated() {
        Invoker invoker = invocation -> Result.success(null);
        TestService proxy = RpcProxyFactory.createProxy(TestService.class, invoker);
        String str = proxy.toString();
        assertNotNull(str);
    }

    @Test
    public void testEqualsDelegated() {
        Invoker invoker = invocation -> Result.success(null);
        TestService proxy = RpcProxyFactory.createProxy(TestService.class, invoker);
        assertTrue(proxy.equals(proxy));
        assertFalse(proxy.equals("not-a-proxy"));
    }

    @Test
    public void testHashCodeDelegated() {
        Invoker invoker = invocation -> Result.success(null);
        TestService proxy = RpcProxyFactory.createProxy(TestService.class, invoker);
        assertTrue(proxy.hashCode() != 0);
    }
}
