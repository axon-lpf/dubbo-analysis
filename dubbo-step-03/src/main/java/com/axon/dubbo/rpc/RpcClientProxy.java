package com.axon.dubbo.rpc;

import java.lang.reflect.Proxy;

/**
 * @author：liupengfei
 * @date：2025/5/29
 * @description：
 */
public class RpcClientProxy {
    private final String host;
    private final int port;

    public RpcClientProxy(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class[]{interfaceClass},
                new RpcInvocationHandler(host, port)
                                         );
    }
}