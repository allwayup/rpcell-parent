package io.rpcell.api.annotation;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnnotationsTest {

    @Nested
    class RpcServiceAttributes {

        @Test
        void shouldHaveRequiredAttributes() {
            assertNotNull(SingleService.class.getAnnotation(RpcService.class));
        }

        @Test
        void shouldHaveValueAttribute() {
            RpcService ann = SingleService.class.getAnnotation(RpcService.class);
            assertEquals("", ann.value());
        }

        @Test
        void shouldHaveGroupAttribute() {
            RpcService ann = SingleService.class.getAnnotation(RpcService.class);
            assertEquals("", ann.group());
        }

        @Test
        void shouldHaveVersionAttribute() {
            RpcService ann = SingleService.class.getAnnotation(RpcService.class);
            assertEquals("", ann.version());
        }

        @Test
        void shouldHaveTimeoutAttribute() {
            RpcService ann = SingleService.class.getAnnotation(RpcService.class);
            assertEquals(-1, ann.timeout());
        }

        @Test
        void shouldHaveRetriesAttribute() {
            RpcService ann = SingleService.class.getAnnotation(RpcService.class);
            assertEquals(-1, ann.retries());
        }

        @Test
        void shouldHaveIncludeExcludeAttributes() {
            RpcService ann = SingleService.class.getAnnotation(RpcService.class);
            assertEquals(0, ann.include().length);
            assertEquals(0, ann.exclude().length);
        }

        @Test
        void shouldHaveMultiInstanceAttribute() {
            RpcService ann = SingleService.class.getAnnotation(RpcService.class);
            assertFalse(ann.multiInstance());
        }

        @Test
        void shouldHaveExportAttribute() {
            RpcService ann = SingleService.class.getAnnotation(RpcService.class);
            assertTrue(ann.export());
        }

        @Test
        void shouldHaveRegisterAttribute() {
            RpcService ann = SingleService.class.getAnnotation(RpcService.class);
            assertTrue(ann.register());
        }

        @Test
        void shouldHaveFilterAttribute() {
            RpcService ann = SingleService.class.getAnnotation(RpcService.class);
            assertEquals(0, ann.filter().length);
        }
    }

    @Nested
    class RpcReferenceAttributes {

        @Test
        void shouldHaveRequiredAttributes() throws Exception {
            Field field = AnnotatedFields.class.getDeclaredField("defaultRef");
            RpcReference ann = field.getAnnotation(RpcReference.class);
            assertNotNull(ann);
        }

        @Test
        void shouldHaveGroupAttribute() throws Exception {
            Field field = AnnotatedFields.class.getDeclaredField("defaultRef");
            RpcReference ann = field.getAnnotation(RpcReference.class);
            assertEquals("", ann.group());
        }

        @Test
        void shouldHaveVersionAttribute() throws Exception {
            Field field = AnnotatedFields.class.getDeclaredField("defaultRef");
            RpcReference ann = field.getAnnotation(RpcReference.class);
            assertEquals("", ann.version());
        }

        @Test
        void shouldHaveTimeoutAttribute() throws Exception {
            Field field = AnnotatedFields.class.getDeclaredField("defaultRef");
            RpcReference ann = field.getAnnotation(RpcReference.class);
            assertEquals(-1, ann.timeout());
        }

        @Test
        void shouldHaveRetriesAttribute() throws Exception {
            Field field = AnnotatedFields.class.getDeclaredField("defaultRef");
            RpcReference ann = field.getAnnotation(RpcReference.class);
            assertEquals(-1, ann.retries());
        }

        @Test
        void shouldHaveLoadbalanceAttribute() throws Exception {
            Field field = AnnotatedFields.class.getDeclaredField("defaultRef");
            RpcReference ann = field.getAnnotation(RpcReference.class);
            assertEquals("", ann.loadbalance());
        }

        @Test
        void shouldHaveCheckAttribute() throws Exception {
            Field field = AnnotatedFields.class.getDeclaredField("defaultRef");
            RpcReference ann = field.getAnnotation(RpcReference.class);
            assertFalse(ann.check());
        }

        @Test
        void shouldHaveFilterAttribute() throws Exception {
            Field field = AnnotatedFields.class.getDeclaredField("defaultRef");
            RpcReference ann = field.getAnnotation(RpcReference.class);
            assertEquals(0, ann.filter().length);
        }
    }

    @Nested
    class RpcMethodAttributes {

        @Test
        void shouldHaveRequiredAttributes() throws Exception {
            Method method = AnnotatedMethods.class.getDeclaredMethod("defaultMethod");
            RpcMethod ann = method.getAnnotation(RpcMethod.class);
            assertNotNull(ann);
        }

        @Test
        void shouldHaveExposedAttribute() throws Exception {
            Method method = AnnotatedMethods.class.getDeclaredMethod("defaultMethod");
            RpcMethod ann = method.getAnnotation(RpcMethod.class);
            assertTrue(ann.exposed());
        }

        @Test
        void shouldHaveTimeoutAttribute() throws Exception {
            Method method = AnnotatedMethods.class.getDeclaredMethod("defaultMethod");
            RpcMethod ann = method.getAnnotation(RpcMethod.class);
            assertEquals(-1, ann.timeout());
        }

        @Test
        void shouldHaveRetriesAttribute() throws Exception {
            Method method = AnnotatedMethods.class.getDeclaredMethod("defaultMethod");
            RpcMethod ann = method.getAnnotation(RpcMethod.class);
            assertEquals(-1, ann.retries());
        }
    }

    @Nested
    class AnnotationCompatibility {

        @Test
        void shouldAllowMinimalRpcService() {
            assertNotNull(SingleService.class.getAnnotation(RpcService.class));
        }

        @Test
        void shouldAllowFullRpcService() {
            RpcService ann = FullService.class.getAnnotation(RpcService.class);
            assertEquals(5000, ann.timeout());
            assertEquals(3, ann.retries());
            assertEquals("filter:a,filter:b", ann.filter()[0]);
        }

        @Test
        void shouldAllowFullRpcReference() throws Exception {
            Field field = AnnotatedFields.class.getDeclaredField("fullRef");
            RpcReference ann = field.getAnnotation(RpcReference.class);
            assertEquals(10000, ann.timeout());
            assertEquals(5, ann.retries());
            assertEquals("leastactive", ann.loadbalance());
            assertEquals("filter:seata,filter:auth", ann.filter()[0]);
        }

        @Test
        void shouldAllowFullRpcMethod() throws Exception {
            Method method = AnnotatedMethods.class.getDeclaredMethod("doWork");
            RpcMethod ann = method.getAnnotation(RpcMethod.class);
            assertEquals(2000, ann.timeout());
            assertEquals(1, ann.retries());
        }
    }

    @Nested
    class RetentionPolicyTests {

        @Test
        void shouldAllBeRuntimeRetained() {
            assertAnnotationRetention(RpcService.class);
            assertAnnotationRetention(RpcReference.class);
            assertAnnotationRetention(RpcMethod.class);
        }

        private void assertAnnotationRetention(Class<? extends Annotation> annotationClass) {
            Retention retention = annotationClass.getAnnotation(Retention.class);
            assertNotNull(retention, annotationClass.getName() + " must have @Retention");
            assertEquals(RetentionPolicy.RUNTIME, retention.value());
        }
    }

    @RpcService
    interface SingleService {}

    @RpcService(
            timeout = 5000,
            retries = 3,
            register = false,
            export = false,
            multiInstance = false,
            filter = {"filter:a,filter:b"})
    interface FullService {}

    static class AnnotatedFields {

        @RpcReference
        Object defaultRef;

        @RpcReference(
                timeout = 10000,
                retries = 5,
                loadbalance = "leastactive",
                check = true,
                filter = {"filter:seata,filter:auth"})
        Object fullRef;
    }

    static class AnnotatedMethods {

        @RpcMethod
        void defaultMethod() {}

        @RpcMethod(timeout = 2000, retries = 1)
        void doWork() {}
    }
}
