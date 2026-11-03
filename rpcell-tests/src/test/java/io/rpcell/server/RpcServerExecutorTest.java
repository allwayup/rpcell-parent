package io.rpcell.server;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.annotation.RpcService;
import io.rpcell.api.constant.Attachments;
import io.rpcell.api.exception.RpcParamException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.model.ServiceDescriptor;
import io.rpcell.api.model.URL;
import io.rpcell.common.ServiceDescriptorBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RpcServerExecutorTest {

    @RpcService("HelloService")
    interface HelloService {
        String sayHello(String name);

        String ping();
    }

    interface GreetingService {
        String greet();
    }

    @RpcService("OverloadedService")
    interface OverloadedService {
        String call(String value);

        String call();

        default String fail() {
            throw new IllegalStateException("business failure");
        }
    }

    private static class VersionedHelloService implements HelloService {
        private final String version;

        VersionedHelloService(String version) {
            this.version = version;
        }

        @Override
        public String sayHello(String name) {
            return version + ":" + name;
        }

        @Override
        public String ping() {
            return version;
        }
    }

    private RpcServerExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new RpcServerExecutor("127.0.0.1", 20880);
    }

    @Nested
    class ServiceRegistration {

        @Test
        void shouldRegisterService() {
            ServiceDescriptor desc = ServiceDescriptorBuilder.build(HelloService.class, new HelloService() {
                @Override
                public String sayHello(String name) {
                    return "Hello " + name;
                }

                @Override
                public String ping() {
                    return "pong";
                }
            });
            executor.registerService(desc, false);
            assertTrue(true);
        }

        @Test
        void shouldRejectDuplicateServiceKey() {
            HelloService impl = new HelloService() {
                @Override
                public String sayHello(String name) {
                    return "Hello " + name;
                }

                @Override
                public String ping() {
                    return "pong";
                }
            };
            ServiceDescriptor desc1 = ServiceDescriptorBuilder.build(HelloService.class, impl);
            ServiceDescriptor desc2 = ServiceDescriptorBuilder.build(HelloService.class, impl);
            executor.registerService(desc1, false);
            assertThrows(RpcParamException.class, () -> executor.registerService(desc2, true));
        }
    }

    @Nested
    class Execution {

        @BeforeEach
        void registerService() {
            HelloService impl = new HelloService() {
                @Override
                public String sayHello(String name) {
                    return "Hello " + name;
                }

                @Override
                public String ping() {
                    return "pong";
                }
            };
            ServiceDescriptor desc = ServiceDescriptorBuilder.build(HelloService.class, impl);
            executor.registerService(desc, false);
        }

        @Test
        void shouldExecuteSuccessfully() {
            Invocation inv = new Invocation();
            inv.setInterfaceName(HelloService.class.getName());
            inv.setMethodName("sayHello");
            inv.setParameterTypes(new Class<?>[] {String.class});
            inv.setArguments(new Object[] {"World"});

            Result result = executor.execute(inv);
            assertTrue(result.hasValue());
            assertEquals("Hello World", result.getValue());
        }

        @Test
        void shouldExecuteNoArgMethod() {
            Invocation inv = new Invocation();
            inv.setInterfaceName(HelloService.class.getName());
            inv.setMethodName("ping");

            Result result = executor.execute(inv);
            assertEquals("pong", result.getValue());
        }

        @Test
        void shouldExecuteNoArgMethodWithOverloads() {
            OverloadedService impl = new OverloadedService() {
                @Override
                public String call(String value) {
                    return "arg: " + value;
                }

                @Override
                public String call() {
                    return "no-arg";
                }
            };
            executor.registerService(ServiceDescriptorBuilder.build(OverloadedService.class, impl), false);
            Invocation inv = new Invocation();
            inv.setInterfaceName(OverloadedService.class.getName());
            inv.setMethodName("call");
            inv.setParameterTypes(new Class<?>[0]);

            Result result = executor.execute(inv);
            assertTrue(result.hasValue());
            assertEquals("no-arg", result.getValue());
        }

        @Test
        void shouldReturnActualBusinessException() {
            OverloadedService impl = new OverloadedService() {
                @Override
                public String call(String value) {
                    return value;
                }

                @Override
                public String call() {
                    return "no-arg";
                }
            };
            executor.registerService(ServiceDescriptorBuilder.build(OverloadedService.class, impl), false);
            Invocation invocation = new Invocation();
            invocation.setInterfaceName(OverloadedService.class.getName());
            invocation.setMethodName("fail");
            invocation.setParameterTypes(new Class<?>[0]);

            Result result = executor.execute(invocation);

            assertTrue(result.hasException());
            assertEquals("business failure", result.getException().getMessage());
            assertEquals(
                    IllegalStateException.class.getName(), result.getException().getExceptionClassName());
        }

        @Test
        void shouldSelectExactServiceGroupAndVersion() {
            ServiceDescriptor v1 = ServiceDescriptorBuilder.build(HelloService.class, new VersionedHelloService("v1"));
            v1.setGroup("g1");
            v1.setVersion("1.0");
            ServiceDescriptor v2 = ServiceDescriptorBuilder.build(HelloService.class, new VersionedHelloService("v2"));
            v2.setGroup("g1");
            v2.setVersion("2.0");
            executor.registerService(v1, true);
            executor.registerService(v2, true);
            Invocation invocation = new Invocation();
            invocation.setInterfaceName(HelloService.class.getName());
            invocation.setMethodName("sayHello");
            invocation.setParameterTypes(new Class<?>[] {String.class});
            invocation.setArguments(new Object[] {"test"});
            invocation.getAttachments().put(Attachments.GROUP, "g1");
            invocation.getAttachments().put(Attachments.VERSION, "2.0");

            Result result = executor.execute(invocation);
            assertEquals("v2:test", result.getValue());

            invocation.getAttachments().put(Attachments.VERSION, "3.0");
            assertTrue(executor.execute(invocation).hasException());
        }

        @Test
        void shouldReturnErrorForServiceNotFound() {
            Invocation inv = new Invocation();
            inv.setInterfaceName("UnknownService");
            inv.setMethodName("doSomething");

            Result result = executor.execute(inv);
            assertTrue(result.hasException());
            assertTrue(result.getException().getMessage().contains("UnknownService"));
        }

        @Test
        void shouldReturnErrorForMethodNotExposed() {
            Invocation inv = new Invocation();
            inv.setInterfaceName(HelloService.class.getName());
            inv.setMethodName("secretMethod");

            Result result = executor.execute(inv);
            assertTrue(result.hasException());
            assertTrue(result.getException().getMessage().contains("Method not exposed"));
        }

        @Test
        void shouldReturnErrorForMethodNotFound() {
            Invocation inv = new Invocation();
            inv.setInterfaceName(HelloService.class.getName());
            inv.setMethodName("sayHello");
            inv.setParameterTypes(new Class<?>[] {Integer.class});

            Result result = executor.execute(inv);
            assertTrue(result.hasException());
            assertTrue(result.getException().getMessage().contains("Method not found"));
        }
    }

    @Nested
    class ExportUrl {

        @Test
        void shouldExportCorrectUrl() {
            URL url = executor.exportUrl("TestService", "default", "1.0");
            assertEquals("rpcell", url.getProtocol());
            assertEquals("127.0.0.1", url.getHost());
            assertEquals(20880, url.getPort());
            assertEquals("TestService", url.getServiceName());
            assertEquals("default", url.getGroup());
            assertEquals("1.0", url.getVersion());
        }
    }
}
