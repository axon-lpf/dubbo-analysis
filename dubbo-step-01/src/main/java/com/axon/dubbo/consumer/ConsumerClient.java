
package com.axon.dubbo.consumer;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.transport.RpcFramework;

public class ConsumerClient {
    public static void main(String[] args) throws Exception {
        HelloService service = RpcFramework.refer(HelloService.class, "127.0.0.1", 8080);
        String result = service.sayHello("大飞哥");
        System.out.println("调用结果: " + result);
    }
}
