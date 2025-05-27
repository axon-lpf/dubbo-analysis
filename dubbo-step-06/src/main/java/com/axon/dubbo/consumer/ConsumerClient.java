package com.axon.dubbo.consumer;

import com.axon.dubbo.api.IHelloService;
import com.axon.dubbo.core.codec.RpcRequest;
import com.axon.dubbo.core.codec.RpcResponse;
import com.axon.dubbo.core.transport.NettyClient;
import com.axon.dubbo.registry.Registry;
import com.axon.dubbo.registry.ZookeeperRegistry;

import java.util.List;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */

public class ConsumerClient {

    public static void main(String[] args) throws Exception {
        Registry registry = new ZookeeperRegistry("127.0.0.1:2181");

        // 从注册中心查询服务地址
        List<String> addresses = registry.lookup(IHelloService.class.getName());
        if (addresses.isEmpty()) {
            System.err.println("服务未注册");
            return;
        }

        String[] hostPort = addresses.get(0).split(":");
        String host = hostPort[0];
        int port = Integer.parseInt(hostPort[1]);

        NettyClient client = new NettyClient(host, port);

        RpcRequest request = new RpcRequest();
        request.setInterfaceName(IHelloService.class.getName());
        request.setMethodName("sayHello");
        request.setParamTypes(new Class<?>[]{String.class});
        request.setParameters(new Object[]{"Zookeeper注册中心测试"});

        RpcResponse response = client.sendRequest(request);

        if (response.hasException()) {
            System.err.println("调用异常: " + response.getException());
        } else {
            System.out.println("调用结果: " + response.getResult());
        }
    }
}
