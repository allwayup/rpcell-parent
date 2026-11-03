package io.rpcell.common;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.annotation.RpcService;
import io.rpcell.api.exception.RpcParamException;
import io.rpcell.api.model.ServiceDescriptor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServiceDescriptorBuilderTest {

    @RpcService(value = "CustomName", group = "test", version = "2.0")
    interface NamedService {
        void hello();

        String echo(String msg);
    }

    @RpcService
    interface DefaultService {
        void run();
    }

    interface NotAnnotatedService {
        void method();
    }

    @RpcService(
            include = {"visible"},
            exclude = {"hidden", "internal"})
    interface FilteredService {
        void visible();

        void hidden();

        void internal();

        void other();
    }

    @Nested
    class Build {

        @Test
        void shouldUseCustomServiceName() {
            ServiceDescriptor desc = ServiceDescriptorBuilder.build(NamedService.class, null);
            assertEquals("CustomName", desc.getServiceName());
        }

        @Test
        void shouldUseGroupAndVersion() {
            ServiceDescriptor desc = ServiceDescriptorBuilder.build(NamedService.class, null);
            assertEquals("test", desc.getGroup());
            assertEquals("2.0", desc.getVersion());
        }

        @Test
        void shouldFallbackToClassName() {
            ServiceDescriptor desc = ServiceDescriptorBuilder.build(DefaultService.class, null);
            assertEquals(DefaultService.class.getName(), desc.getServiceName());
        }

        @Test
        void shouldThrowWhenAnnotationMissing() {
            assertThrows(
                    RpcParamException.class, () -> ServiceDescriptorBuilder.build(NotAnnotatedService.class, null));
        }

        @Test
        void shouldSetInterfaceClass() {
            ServiceDescriptor desc = ServiceDescriptorBuilder.build(NamedService.class, null);
            assertEquals(NamedService.class, desc.getInterfaceClass());
        }

        @Test
        void shouldSetInstanceWhenProvided() {
            NamedService impl = new NamedService() {
                @Override
                public void hello() {}

                @Override
                public String echo(String msg) {
                    return msg;
                }
            };
            ServiceDescriptor desc = ServiceDescriptorBuilder.build(NamedService.class, impl);
            assertSame(impl, desc.getInstance());
        }

        @Test
        void shouldSetInstanceNullWhenNotProvided() {
            ServiceDescriptor desc = ServiceDescriptorBuilder.build(NamedService.class, null);
            assertNull(desc.getInstance());
        }

        @Test
        void shouldCollectAllPublicMethods() {
            ServiceDescriptor desc = ServiceDescriptorBuilder.build(NamedService.class, null);
            assertTrue(desc.getMethods().contains("hello"));
            assertTrue(desc.getMethods().contains("echo"));
        }

        @Test
        void shouldFilterByExclude() {
            ServiceDescriptor desc = ServiceDescriptorBuilder.build(FilteredService.class, null);
            assertTrue(desc.containsMethod("visible"));
            assertFalse(desc.containsMethod("hidden"));
            assertFalse(desc.containsMethod("internal"));
            assertFalse(desc.containsMethod("other"));
        }
    }

    @Nested
    class ErrorMessage {

        @Test
        void shouldIncludeClassNameInError() {
            RpcParamException ex = assertThrows(
                    RpcParamException.class, () -> ServiceDescriptorBuilder.build(NotAnnotatedService.class, null));
            assertTrue(ex.getMessage().contains(NotAnnotatedService.class.getName()));
        }
    }
}
