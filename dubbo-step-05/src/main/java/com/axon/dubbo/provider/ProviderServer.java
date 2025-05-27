package com.axon.dubbo.provider;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.core.transport.NettyServer;
import com.axon.dubbo.provider.impl.HelloServiceImpl;
import com.axon.dubbo.registry.InMemoryRegistry;
import com.axon.dubbo.registry.LocalFileRegistry;
import com.axon.dubbo.registry.Registry;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

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
        server.registerService(HelloService.class.getName(), new HelloServiceImpl());

        // 新增：创建注册中心，注册服务地址
        Registry registry = new LocalFileRegistry();
        registry.register(HelloService.class.getName(), serviceAddress);

        System.out.println("服务提供者启动，监听端口: " + port);
        server.start();

    }



}
