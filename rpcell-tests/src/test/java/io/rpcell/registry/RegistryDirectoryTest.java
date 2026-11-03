package io.rpcell.registry;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.constant.Attachments;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.URL;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegistryDirectoryTest {

    private RegistryDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new RegistryDirectory();
    }

    @Test
    void shouldBeEmptyInitially() {
        assertTrue(directory.list().isEmpty());
    }

    @Test
    void shouldUpdateAfterNotify() {
        List<URL> urls = new ArrayList<>();
        urls.add(new URL("rpcell", "127.0.0.1", 8080, "Test", "default", "1.0"));
        directory.notify(urls);
        assertEquals(1, directory.list().size());
    }

    @Test
    void shouldFilterByInvocationGroupAndVersion() {
        URL expected = new URL("rpcell", "127.0.0.1", 8080, "Test", "g1", "v1");
        directory.notify(List.of(
                expected,
                new URL("rpcell", "127.0.0.2", 8080, "Test", "g2", "v1"),
                new URL("rpcell", "127.0.0.3", 8080, "Test", "g1", "v2")));
        Invocation invocation = new Invocation();
        invocation.getAttachments().put(Attachments.GROUP, "g1");
        invocation.getAttachments().put(Attachments.VERSION, "v1");

        assertEquals(List.of(expected), directory.list(invocation));
    }

    @Test
    void shouldHandleNullNotification() {
        directory.notify(null);
        assertTrue(directory.list().isEmpty());
    }

    @Test
    void shouldListReturnImmutableSnapshot() {
        List<URL> urls = new ArrayList<>();
        urls.add(new URL("rpcell", "127.0.0.1", 8080, "Test", "default", "1.0"));
        directory.notify(urls);
        List<URL> snapshot = directory.list();
        urls.clear();
        assertEquals(1, snapshot.size());
    }

    @Test
    void shouldIsEmptyReturnTrueInitially() {
        assertTrue(directory.isEmpty());
    }

    @Test
    void shouldIsEmptyReturnFalseAfterNotify() {
        List<URL> urls = new ArrayList<>();
        urls.add(new URL("rpcell", "127.0.0.1", 8080, "Test", "default", "1.0"));
        directory.notify(urls);
        assertFalse(directory.isEmpty());
    }

    @Test
    void shouldNotifyReplaceExistingUrls() {
        List<URL> first = new ArrayList<>();
        first.add(new URL("rpcell", "10.0.0.1", 8080, "Svc", "g", "v"));
        directory.notify(first);
        assertEquals(1, directory.list().size());

        List<URL> second = new ArrayList<>();
        second.add(new URL("rpcell", "10.0.0.2", 8081, "Svc", "g", "v"));
        second.add(new URL("rpcell", "10.0.0.3", 8082, "Svc", "g", "v"));
        directory.notify(second);
        assertEquals(2, directory.list().size());
    }

    @Nested
    class ListUnmodifiable {

        @Test
        void shouldThrowWhenModified() {
            List<URL> urls = new ArrayList<>();
            urls.add(new URL("rpcell", "127.0.0.1", 8080, "Test", "default", "1.0"));
            directory.notify(urls);
            List<URL> snapshot = directory.list();
            assertThrows(UnsupportedOperationException.class, () -> snapshot.add(new URL()));
        }

        @Test
        void shouldThrowWhenRemoved() {
            List<URL> urls = new ArrayList<>();
            urls.add(new URL("rpcell", "127.0.0.1", 8080, "Test", "default", "1.0"));
            directory.notify(urls);
            List<URL> snapshot = directory.list();
            assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(0));
        }
    }
}
