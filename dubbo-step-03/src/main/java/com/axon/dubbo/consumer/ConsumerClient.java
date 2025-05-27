package com.axon.dubbo.consumer;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.core.ProxyFactory;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */

public class ConsumerClient {

    public static void main(String[] args) {
        // 通过代理获取接口实现，实际调用转发给远程服务
        HelloService helloService = ProxyFactory.getProxy(HelloService.class, "localhost", 9000);
        String result = helloService.sayHello("Dynamic Proxy 大飞哥");
        System.out.println("客户端收到返回值：" + result);
    }
}
