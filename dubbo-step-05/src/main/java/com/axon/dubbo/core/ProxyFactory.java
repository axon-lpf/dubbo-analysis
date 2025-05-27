package com.axon.dubbo.core;

import java.lang.reflect.Proxy;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ProxyFactory {

    /**
     * 创建接口的代理对象，实际调用转发给 RpcInvocationHandler
     */
    @SuppressWarnings("unchecked")
    public static <T> T getProxy(Class<T> serviceInterface, String host, int port) {
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                new RpcInvocationHandler(host, port, serviceInterface)
                                         );
    }
}
