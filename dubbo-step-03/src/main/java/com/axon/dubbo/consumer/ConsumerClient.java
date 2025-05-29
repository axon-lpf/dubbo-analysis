package com.axon.dubbo.consumer;


import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.rpc.RpcClientProxy;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ConsumerClient {
    public static void main(String[] args) throws Exception {
        RpcClientProxy proxy = new RpcClientProxy("localhost", 12345);
        HelloService helloService = proxy.getProxy(HelloService.class);

        // 关键：此处就像调用本地接口一样，无需关心网络细节！
        String result = helloService.sayHello("大飞哥");
        System.out.println("RPC调用结果：" + result);
    }
}
