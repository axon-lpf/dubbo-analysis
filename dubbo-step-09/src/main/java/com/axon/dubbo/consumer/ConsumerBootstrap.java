package com.axon.dubbo.consumer;

import com.axon.dubbo.api.IHelloService;
import com.axon.dubbo.core.codec.RpcRequest;
import com.axon.dubbo.core.codec.RpcResponse;
import com.axon.dubbo.core.transport.NettyClient;
import com.axon.dubbo.registry.Registry;
import com.axon.dubbo.registry.ServiceListener;
import com.axon.dubbo.registry.ZookeeperRegistry;

import java.util.List;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */

public class ConsumerBootstrap {

    public static void main(String[] args) throws Exception {
        Registry registry = new ZookeeperRegistry("127.0.0.1:2181");

        ReferenceConfig<IHelloService> reference = new ReferenceConfig<>(registry);
        IHelloService helloService = reference.getProxy(IHelloService.class);

        String result = helloService.sayHello("dubbo");
        System.out.println("调用结果：" + result);
    }
}
