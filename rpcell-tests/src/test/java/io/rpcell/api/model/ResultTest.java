package io.rpcell.api.model;

import static org.junit.jupiter.api.Assertions.*;

import io.rpcell.api.model.Result.RpcResultException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResultTest {

    @Nested
    class SuccessCases {

        @Test
        void shouldHaveValueEqualsInput() {
            Result r = Result.success("data");
            assertEquals("data", r.getValue());
        }

        @Test
        void shouldSupportNullValue() {
            Result r = Result.success(null);
            assertNull(r.getValue());
        }

        @Test
        void shouldHasValueBeTrue() {
            assertTrue(Result.success("ok").hasValue());
        }

        @Test
        void shouldHasExceptionBeFalse() {
            assertFalse(Result.success("ok").hasException());
        }

        @Test
        void shouldExceptionBeNull() {
            assertNull(Result.success("ok").getException());
        }
    }

    @Nested
    class FailureCases {

        @Test
        void shouldHaveExceptionSet() {
            RpcResultException ex = new RpcResultException("msg", "java.lang.Exception", 500);
            Result r = Result.failure(ex);
            assertEquals(ex, r.getException());
        }

        @Test
        void shouldValueBeNull() {
            RpcResultException ex = new RpcResultException("msg", "java.lang.Exception", 500);
            Result r = Result.failure(ex);
            assertNull(r.getValue());
        }

        @Test
        void shouldHasValueBeFalse() {
            RpcResultException ex = new RpcResultException("msg", "java.lang.Exception", 500);
            assertFalse(Result.failure(ex).hasValue());
        }

        @Test
        void shouldHasExceptionBeTrue() {
            RpcResultException ex = new RpcResultException("msg", "java.lang.Exception", 500);
            assertTrue(Result.failure(ex).hasException());
        }
    }

    @Nested
    class DefaultConstructor {

        @Test
        void shouldHaveNullValueAndException() {
            Result r = new Result();
            assertNull(r.getValue());
            assertNull(r.getException());
        }

        @Test
        void shouldAllowManualValueSet() {
            Result r = new Result();
            r.setValue(42);
            assertEquals(42, r.getValue());
        }

        @Test
        void shouldAllowManualExceptionSet() {
            Result r = new Result();
            RpcResultException ex = new RpcResultException("oops", "Throwable", 999);
            r.setException(ex);
            assertEquals(ex, r.getException());
        }
    }

    @Nested
    class RpcResultExceptionCases {

        @Test
        void shouldStoreMessageClassNameAndCode() {
            RpcResultException ex = new RpcResultException("timeout", "java.util.concurrent.TimeoutException", 1001);
            assertEquals("timeout", ex.getMessage());
            assertEquals("java.util.concurrent.TimeoutException", ex.getExceptionClassName());
            assertEquals(1001, ex.getErrorCode());
        }

        @Test
        void defaultConstructorShouldHaveNulls() {
            RpcResultException ex = new RpcResultException();
            assertNull(ex.getMessage());
            assertNull(ex.getExceptionClassName());
            assertEquals(0, ex.getErrorCode());
        }

        @Test
        void shouldSupportSetters() {
            RpcResultException ex = new RpcResultException();
            ex.setMessage("updated");
            ex.setExceptionClassName("java.lang.IllegalStateException");
            ex.setErrorCode(42);
            assertEquals("updated", ex.getMessage());
            assertEquals("java.lang.IllegalStateException", ex.getExceptionClassName());
            assertEquals(42, ex.getErrorCode());
        }
    }
}
