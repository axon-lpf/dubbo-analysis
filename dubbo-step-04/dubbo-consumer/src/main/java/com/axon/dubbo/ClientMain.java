package com.axon.dubbo;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.consumer.RpcInvocationHandler;

import java.lang.reflect.Proxy;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
public class ClientMain {
    public static void main(String[] args) {
        HelloService helloService = (HelloService) Proxy.newProxyInstance(HelloService.class.getClassLoader(),
                                                                          new Class<?>[]{ HelloService.class },
                                                                          new RpcInvocationHandler("localhost", 8080));

        String res = helloService.sayHello("netty");
        System.out.println("客户端收到结果：" + res);
    }
}