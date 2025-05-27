package com.axon.dubbo.consumer;

import com.axon.dubbo.api.HelloService;
import com.axon.dubbo.core.ProxyFactory;
import com.axon.dubbo.core.codec.RpcRequest;
import com.axon.dubbo.core.codec.RpcResponse;
import com.axon.dubbo.core.transport.NettyClient;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */

public class ConsumerClient {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 8888;

        NettyClient client = new NettyClient(host, port);

        // 构造RPC请求对象
        RpcRequest request = new RpcRequest();
        request.setInterfaceName(HelloService.class.getName());
        request.setMethodName("sayHello");
        request.setParamTypes(new Class<?>[]{String.class});
        request.setParameters(new Object[]{"Netty RPC, 大飞哥"});

        // 发送请求并接收响应
        RpcResponse response = client.sendRequest(request);

        if (response.hasException()) {
            System.err.println("调用异常: " + response.getException());
        } else {
            System.out.println("调用结果: " + response.getResult());
        }
    }
}
