package com.axon.dubbo.consumer;

import com.axon.dubbo.common.RpcRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
public class RpcInvocationHandler  implements InvocationHandler {
    private final String host;
    private final int port;

    public RpcInvocationHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        RpcRequest request = new RpcRequest();
        request.setClassName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);

        RpcNettyClient client = new RpcNettyClient();
        return client.send(request, host, port);
    }
}
