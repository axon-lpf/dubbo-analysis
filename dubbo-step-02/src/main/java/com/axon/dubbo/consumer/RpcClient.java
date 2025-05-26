package com.axon.dubbo.consumer;

import com.axon.dubbo.api.HelloService;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
// consumer/RpcClient.java
public class RpcClient {
    public static void main(String[] args) {
        HelloService service = RpcProxy.getProxy(HelloService.class);
        String result = service.sayHello("大飞哥");
        System.out.println("调用结果：" + result);
    }
}
