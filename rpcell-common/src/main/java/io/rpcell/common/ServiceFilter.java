package io.rpcell.common;

import io.rpcell.api.annotation.RpcMethod;
import io.rpcell.api.annotation.RpcService;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * Method visibility filter implementing the rule:
 * 1. Method-level @RpcMethod annotation short-circuits
 * 2. Exclude list is checked first (exclude has higher priority)
 * 3. When include is non-empty, only matched methods pass
 * 4. Default: all methods are exposed
 */
public final class ServiceFilter {

    private ServiceFilter() {}

    /**
     * Determine whether a method is exposed for RPC calls.
     *
     * @param method the method to check
     * @param annotation the @RpcService annotation on the interface
     * @return true if the method should be exposed
     */
    public static boolean isExposed(Method method, RpcService annotation) {
        RpcMethod rpcMethod = method.getAnnotation(RpcMethod.class);
        if (rpcMethod != null) {
            return rpcMethod.exposed();
        }
        if (matchAny(annotation.exclude(), method.getName())) {
            return false;
        }
        String[] includes = annotation.include();
        if (includes.length > 0 && !matchAny(includes, method.getName())) {
            return false;
        }
        return true;
    }

    /**
     * Check if the method name matches any pattern in the list.
     * Supports '*' wildcard matching.
     *
     * @param patterns the pattern list
     * @param methodName the method name to check
     * @return true if any pattern matches
     */
    public static boolean matchAny(String[] patterns, String methodName) {
        for (String pattern : patterns) {
            if (wildcardMatch(pattern, methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple wildcard matching supporting '*' only.
     *
     * @param pattern the pattern, may contain '*'
     * @param input the input string
     * @return true if matches
     */
    public static boolean wildcardMatch(String pattern, String input) {
        if (pattern == null || input == null) {
            return false;
        }
        if ("*".equals(pattern)) {
            return true;
        }
        if (!pattern.contains("*")) {
            return pattern.equals(input);
        }
        String[] literalParts = pattern.split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < literalParts.length; i++) {
            regex.append(Pattern.quote(literalParts[i]));
            if (i < literalParts.length - 1) {
                regex.append(".*");
            }
        }
        return input.matches(regex.toString());
    }
}
