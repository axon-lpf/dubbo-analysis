package com.axon.dubbo.provider;

import com.axon.dubbo.api.IHelloService;
import com.axon.dubbo.core.transport.NettyServer;
import com.axon.dubbo.protocol.RpcProtocol;
import com.axon.dubbo.provider.impl.HelloServiceImpl;
import com.axon.dubbo.registry.Registry;
import com.axon.dubbo.registry.ZookeeperRegistry;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ProviderBootstrap {

    public static void main(String[] args) throws Exception {
        String zkAddress = "127.0.0.1:2181";
        int port = 20880;
        String host = "127.0.0.1";

        // 1. 启动Netty Server
        NettyServer.start(port);

        // 2. 创建注册中心
        Registry registry = new ZookeeperRegistry(zkAddress);

        // 3. 创建服务协议 & 暴露服务
        RpcProtocol protocol = new RpcProtocol(registry);
        protocol.export(IHelloService.class.getName(), new HelloServiceImpl(), host, port);

        System.out.println("服务暴露成功: " + IHelloService.class.getName());

    }



}
