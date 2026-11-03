package io.rpcell.proxy;

import io.rpcell.api.annotation.RpcMethod;
import io.rpcell.api.constant.Attachments;
import io.rpcell.api.exception.RpcRemoteException;
import io.rpcell.api.model.Invocation;
import io.rpcell.api.model.Result;
import io.rpcell.api.spi.Invoker;
import io.rpcell.api.spi.Serializer;
import io.rpcell.serializer.SerializerManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDK dynamic proxy factory for RPC consumer side.
 * Assembles an Invocation and delegates to the downstream Invoker.
 */
public final class RpcProxyFactory {

    private static final Logger LOG = LoggerFactory.getLogger(RpcProxyFactory.class);

    private RpcProxyFactory() {}

    /**
     * Create a JDK dynamic proxy for the given interface.
     *
     * @param interfaceClass the interface to proxy
     * @param invoker the downstream invoker (typically a cluster invoker)
     * @param <T> the interface type
     * @return a proxy instance
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> interfaceClass, Invoker invoker) {
        return createProxy(interfaceClass, invoker, null);
    }

    /**
     * Create a JDK dynamic proxy with per-call metadata carried in {@link Invocation#getAttachments()}.
     *
     * @param interfaceClass the interface to proxy
     * @param invoker the downstream invoker
     * @param metadata per-call configuration from annotations (may be null)
     * @param <T> the interface type
     * @return a proxy instance
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> interfaceClass, Invoker invoker, CallMetadata metadata) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(interfaceClass.getName() + " is not an interface");
        }
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class[] {interfaceClass},
                new RpcInvocationHandler(interfaceClass, invoker, metadata));
    }

    /**
     * Per-call metadata carried into {@link Invocation#getAttachments()} on every invocation.
     * Populated from {@code @RpcReference} annotation values at proxy creation time.
     */
    public static class CallMetadata {

        private final int timeout;
        private final int retries;
        private final String group;
        private final String version;
        private final String loadbalance;
        private final boolean check;
        private final String[] filter;

        private CallMetadata(Builder builder) {
            this.timeout = builder.timeout;
            this.retries = builder.retries;
            this.group = builder.group;
            this.version = builder.version;
            this.loadbalance = builder.loadbalance;
            this.check = builder.check;
            this.filter = builder.filter;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private int timeout = -1;
            private int retries = -1;
            private String group;
            private String version;
            private String loadbalance;
            private boolean check;
            private String[] filter;

            public Builder timeout(int timeout) {
                this.timeout = timeout;
                return this;
            }

            public Builder retries(int retries) {
                this.retries = retries;
                return this;
            }

            public Builder group(String group) {
                this.group = group;
                return this;
            }

            public Builder version(String version) {
                this.version = version;
                return this;
            }

            public Builder loadbalance(String loadbalance) {
                this.loadbalance = loadbalance;
                return this;
            }

            public Builder check(boolean check) {
                this.check = check;
                return this;
            }

            public Builder filter(String[] filter) {
                this.filter = filter;
                return this;
            }

            public CallMetadata build() {
                return new CallMetadata(this);
            }
        }

        public String[] getFilter() {
            return filter != null ? filter.clone() : new String[0];
        }

        public boolean isCheck() {
            return check;
        }

        Map<String, String> toAttachments() {
            Map<String, String> map = new HashMap<>();
            if (timeout >= 0) {
                map.put(Attachments.TIMEOUT, String.valueOf(timeout));
            }
            if (retries >= 0) {
                map.put(Attachments.RETRIES, String.valueOf(retries));
            }
            if (group != null && !group.isEmpty()) {
                map.put(Attachments.GROUP, group);
            }
            if (version != null && !version.isEmpty()) {
                map.put(Attachments.VERSION, version);
            }
            if (loadbalance != null && !loadbalance.isEmpty()) {
                map.put(Attachments.LOADBALANCE, loadbalance);
            }
            map.put(Attachments.CHECK, String.valueOf(check));
            return map;
        }
    }

    /**
     * Invocation handler that builds an Invocation and delegates to the Invoker.
     */
    private static class RpcInvocationHandler implements InvocationHandler {

        private final Class<?> interfaceClass;
        private final Invoker invoker;
        private final CallMetadata metadata;

        RpcInvocationHandler(Class<?> interfaceClass, Invoker invoker, CallMetadata metadata) {
            this.interfaceClass = interfaceClass;
            this.invoker = invoker;
            this.metadata = metadata;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                if ("equals".equals(method.getName())) {
                    return args != null && args.length == 1 && proxy == args[0];
                }
                return method.invoke(this, args);
            }

            Map<String, String> attachments = new HashMap<>();
            if (metadata != null) {
                attachments.putAll(metadata.toAttachments());
            }
            applyMethodAnnotation(method, attachments);

            Invocation invocation = new Invocation();
            invocation.setInterfaceName(interfaceClass.getName());
            invocation.setMethodName(method.getName());
            invocation.setParameterTypes(method.getParameterTypes());
            invocation.setArguments(args);
            invocation.setAttachments(attachments);

            LOG.debug("RPC proxy invoke: {}.{}", interfaceClass.getName(), method.getName());

            Result result = invoker.invoke(invocation);
            if (result.hasException()) {
                Result.RpcResultException error = result.getException();
                throw new RpcRemoteException(error.getErrorCode(), error.getMessage(), error.getExceptionClassName());
            }
            Object value = result.getValue();
            if (value instanceof Map
                    && !method.getReturnType().isAssignableFrom(Map.class)
                    && !method.getReturnType().equals(void.class)) {
                Serializer serializer = SerializerManager.getDefaultOrNull();
                if (serializer != null) {
                    try {
                        byte[] bytes = serializer.serialize(value);
                        value = serializer.deserialize(bytes, method.getReturnType());
                    } catch (Exception e) {
                        LOG.warn(
                                "RPC result type conversion from Map to {} failed: {}",
                                method.getReturnType().getName(),
                                e.getMessage());
                    }
                }
            }
            return value;
        }
    }

    private static void applyMethodAnnotation(Method method, Map<String, String> attachments) {
        RpcMethod rpcMethod = method.getAnnotation(RpcMethod.class);
        if (rpcMethod == null) {
            return;
        }
        if (rpcMethod.timeout() >= 0) {
            attachments.put(Attachments.TIMEOUT, String.valueOf(rpcMethod.timeout()));
        }
        if (rpcMethod.retries() >= 0) {
            attachments.put(Attachments.RETRIES, String.valueOf(rpcMethod.retries()));
        }
    }
}
