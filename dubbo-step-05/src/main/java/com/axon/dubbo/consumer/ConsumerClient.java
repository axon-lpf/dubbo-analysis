package com.axon.dubbo.consumer;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.core.ProxyFactory;
import com.axon.dubbo.core.codec.RpcRequest;
import com.axon.dubbo.core.codec.RpcResponse;
import com.axon.dubbo.core.transport.NettyClient;
import com.axon.dubbo.registry.InMemoryRegistry;
import com.axon.dubbo.registry.LocalFileRegistry;
import com.axon.dubbo.registry.Registry;

import java.util.List;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */

public class ConsumerClient {

    public static void main(String[] args) throws Exception {
        Registry registry = new LocalFileRegistry();

        // 从注册中心查询服务地址
        List<String> addresses = registry.lookup(HelloService.class.getName());
        if (addresses.isEmpty()) {
            System.err.println("服务未注册");
            return;
        }

        // 简单起见，取第一个地址
        String[] hostPort = addresses.get(0).split(":");
        String host = hostPort[0];
        int port = Integer.parseInt(hostPort[1]);

        NettyClient client = new NettyClient(host, port);

        RpcRequest request = new RpcRequest();
        request.setInterfaceName(HelloService.class.getName());
        request.setMethodName("sayHello");
        request.setParamTypes(new Class<?>[]{String.class});
        request.setParameters(new Object[]{"内存注册中心测试"});

        RpcResponse response = client.sendRequest(request);

        if (response.hasException()) {
            System.err.println("调用异常: " + response.getException());
        } else {
            System.out.println("调用结果: " + response.getResult());
        }
    }
}
