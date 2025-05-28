
package com.axon.dubbo.provider;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.provider.impl.HelloServiceImpl;
import com.axon.dubbo.transport.RpcFramework;

public class ProviderServer {
    public static void main(String[] args) throws Exception {
        HelloService helloService = new HelloServiceImpl();
        RpcFramework.export(helloService, 8080); // 发布服务
    }

}
