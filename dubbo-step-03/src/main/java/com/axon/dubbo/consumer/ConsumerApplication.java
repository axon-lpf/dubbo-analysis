package com.axon.dubbo.consumer;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.core.Invoker;
import com.axon.dubbo.core.JdkProxyFactory;
import com.axon.dubbo.core.ProxyFactory;
import com.axon.dubbo.protocol.Protocol;
import com.axon.dubbo.protocol.RpcProtocol;

public class ConsumerApplication {
    public static void main(String[] args) {
        Protocol protocol = new RpcProtocol();
        Invoker<HelloService> invoker = protocol.refer(HelloService.class, "localhost", 1234);

        ProxyFactory proxyFactory = new JdkProxyFactory();
        HelloService helloService = proxyFactory.getProxy(invoker);

        String result = helloService.sayHello("大飞哥");
        System.out.println("调用结果：" + result);
    }
}
