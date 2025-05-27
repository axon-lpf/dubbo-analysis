package com.axon.dubbo.provider;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.core.transport.NettyServer;
import com.axon.dubbo.provider.impl.HelloServiceImpl;

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
        NettyServer server = new NettyServer(port);

        // 注册服务接口与实现类
        server.registerService(HelloService.class.getName(), new HelloServiceImpl());

        System.out.println("服务提供者启动，监听端口: " + port);
        server.start();
    }



}
