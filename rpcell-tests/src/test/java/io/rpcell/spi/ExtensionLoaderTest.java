package io.rpcell.spi;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.exception.RpcParamException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExtensionLoaderTest {

    @BeforeEach
    public void setUp() {
        ExtensionLoader.register("test-ext", TestExtension.class);
    }

    public static class TestExtension {
        private boolean initialized;

        public TestExtension() {
            this.initialized = true;
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

    @Test
    public void testRegisterAndLoad() {
        TestExtension ext = ExtensionLoader.load("test-ext");
        assertNotNull(ext);
        assertTrue(ext.isInitialized());
    }

    @Test
    public void testCacheReturnsSameInstance() {
        TestExtension ext1 = ExtensionLoader.load("test-ext");
        TestExtension ext2 = ExtensionLoader.load("test-ext");
        assertSame(ext1, ext2);
    }

    @Test
    public void testLoadUnknownExtension() {
        assertThrows(RpcParamException.class, () -> ExtensionLoader.load("unknown-ext"));
    }

    @Test
    public void testRegisterEmptyName() {
        assertThrows(RpcParamException.class, () -> ExtensionLoader.register("", TestExtension.class));
    }

    @Test
    public void testRegisterNullName() {
        assertThrows(RpcParamException.class, () -> ExtensionLoader.register(null, TestExtension.class));
    }

    @Test
    public void testRegisterNullImpl() {
        assertThrows(RpcParamException.class, () -> ExtensionLoader.register("some-name", null));
    }

    @Test
    public void testMultipleRegistrations() {
        ExtensionLoader.register("a", ExtensionA.class);
        ExtensionLoader.register("b", ExtensionB.class);
        assertInstanceOf(ExtensionA.class, ExtensionLoader.load("a"));
        assertInstanceOf(ExtensionB.class, ExtensionLoader.load("b"));
    }

    @Test
    public void testOverwriteRegistration() {
        ExtensionLoader.clear();
        ExtensionLoader.register("test-ext", ExtensionA.class);
        assertInstanceOf(ExtensionA.class, ExtensionLoader.load("test-ext"));

        ExtensionLoader.register("test-ext", ExtensionB.class);
        assertInstanceOf(ExtensionB.class, ExtensionLoader.load("test-ext"));
    }

    @Test
    public void testRegistrationDuringConstructionDoesNotCacheStaleImplementation() {
        ExtensionLoader.clear();
        ExtensionLoader.register("reentrant-ext", SelfReplacingExtension.class);

        assertInstanceOf(ExtensionB.class, ExtensionLoader.load("reentrant-ext"));
        assertInstanceOf(ExtensionB.class, ExtensionLoader.load("reentrant-ext"));
    }

    @Test
    public void testConstructorCanRegisterFromAnotherThread() {
        ExtensionLoader.clear();
        ExtensionLoader.register("thread-ext", ThreadRegisteringExtension.class);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertInstanceOf(ExtensionB.class, ExtensionLoader.load("thread-ext"));
        });
    }

    @Test
    public void testCircularLoadingFailsFast() {
        ExtensionLoader.clear();
        ExtensionLoader.register("circular-ext", CircularExtension.class);

        assertThrows(RpcParamException.class, () -> ExtensionLoader.load("circular-ext"));
    }

    public static class SelfReplacingExtension {
        public SelfReplacingExtension() {
            ExtensionLoader.register("reentrant-ext", ExtensionB.class);
        }
    }

    public static class ThreadRegisteringExtension {
        public ThreadRegisteringExtension() {
            Thread registrar = new Thread(() -> ExtensionLoader.register("thread-ext", ExtensionB.class));
            registrar.start();
            try {
                registrar.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class CircularExtension {
        public CircularExtension() {
            ExtensionLoader.load("circular-ext");
        }
    }

    public static class ExtensionA {}

    public static class ExtensionB {}
}
