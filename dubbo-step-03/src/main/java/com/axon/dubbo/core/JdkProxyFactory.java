package com.axon.dubbo.core;

import java.lang.reflect.Proxy;

/**
 * @author：liupengfei
 * @date：2025/5/28
 * @description：
 */
public class JdkProxyFactory implements ProxyFactory{
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker) {
        ClassLoader loader = invoker.getInterface().getClassLoader();
        Class<?>[] interfaces = new Class<?>[]{invoker.getInterface()};

        return (T) Proxy.newProxyInstance(loader, interfaces, (proxy, method, args) -> {
            Invocation invocation = new RpcInvocation(
                    method.getName(), method.getParameterTypes(), args
            );
            Result result = invoker.invoke(invocation);
            return result.getValue();
        });
    }
}
