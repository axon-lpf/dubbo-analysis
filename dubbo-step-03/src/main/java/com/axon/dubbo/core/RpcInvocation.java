
package com.axon.dubbo.core;

public class RpcInvocation implements Invocation {
    private String methodName;
    private Class<?>[] parameterTypes;
    private Object[] arguments;

    public RpcInvocation(String methodName, Class<?>[] parameterTypes, Object[] arguments) {
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.arguments = arguments;
    }

    public String getMethodName() { return methodName; }
    public Class<?>[] getParameterTypes() { return parameterTypes; }
    public Object[] getArguments() { return arguments; }
}
