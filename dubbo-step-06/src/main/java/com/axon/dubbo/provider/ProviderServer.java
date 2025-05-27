package com.axon.dubbo.provider;

import com.axon.dubbo.api.IHelloService;
import com.axon.dubbo.core.transport.NettyServer;
import com.axon.dubbo.provider.impl.HelloServiceImpl;
import com.axon.dubbo.registry.Registry;
import com.axon.dubbo.registry.ZookeeperRegistry;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ProviderServer {

    public static void main(String[] args) throws Exception {
        int port = 8888;
        String serviceAddress = "127.0.0.1:" + port;

        NettyServer server = new NettyServer(port);
        server.registerService(IHelloService.class.getName(), new HelloServiceImpl());

        // 使用Zookeeper注册中心
        Registry registry = new ZookeeperRegistry("127.0.0.1:2181");
        registry.register(IHelloService.class.getName(), serviceAddress);

        System.out.println("服务提供者启动，监听端口: " + port);
        server.start();

    }



}
