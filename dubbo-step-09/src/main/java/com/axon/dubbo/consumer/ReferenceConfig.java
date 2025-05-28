package com.axon.dubbo.consumer;

import com.axon.dubbo.protocol.RpcInvoker;
import com.axon.dubbo.registry.Registry;

import java.lang.reflect.Proxy;
import java.util.List;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ReferenceConfig<T> {

    private final Registry registry;

    public ReferenceConfig(Registry registry) {
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    public T getProxy(Class<T> interfaceClass) {
        String interfaceName = interfaceClass.getName();

        // 从注册中心获取服务地址
        List<String> addresses = registry.lookup(interfaceName);
        if (addresses.isEmpty()) {
            throw new RuntimeException("找不到服务提供者: " + interfaceName);
        }

        // 暂时只取第一个地址（后续支持负载均衡）
        RpcInvoker invoker = new RpcInvoker(addresses.get(1));

        return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[]{ interfaceClass},
                                          (proxy, method, args) -> invoker.invoke(
                                                  interfaceName,
                                                  method.getName(),
                                                  method.getParameterTypes(),
                                                  args
                                                                                 ));
    }
}