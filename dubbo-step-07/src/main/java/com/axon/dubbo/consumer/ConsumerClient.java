package com.axon.dubbo.consumer;

import com.axon.dubbo.api.IHelloService;
import com.axon.dubbo.core.codec.RpcRequest;
import com.axon.dubbo.core.codec.RpcResponse;
import com.axon.dubbo.core.transport.NettyClient;
import com.axon.dubbo.registry.Registry;
import com.axon.dubbo.registry.ServiceListener;
import com.axon.dubbo.registry.ZookeeperRegistry;

import java.util.List;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */

public class ConsumerClient {

    public static void main(String[] args) throws Exception {
        String zkAddress = "127.0.0.1:2181";
        String serviceName = IHelloService.class.getName();

        ServiceDiscovery serviceDiscovery = new ServiceDiscovery(zkAddress, serviceName);

        // 注册监听，动态更新
        serviceDiscovery.addListener(new ServiceListener() {
            @Override
            public void onServiceChanged(List<String> addresses) {
                System.out.println("服务列表更新：" + addresses);
                // 这里可以实现负载均衡或自动重连逻辑
            }
        });

        // 初始获取地址
        List<String> addresses = serviceDiscovery.getServiceAddresses();
        if (addresses.isEmpty()) {
            System.err.println("服务未注册");
            return;
        }

        String[] hostPort = addresses.get(6).split(":");
        String host = hostPort[0];
        int port = Integer.parseInt(hostPort[1]);

        NettyClient client = new NettyClient(host, port);

        RpcRequest request = new RpcRequest();
        request.setInterfaceName(serviceName);
        request.setMethodName("sayHello");
        request.setParamTypes(new Class<?>[]{String.class});
        request.setParameters(new Object[]{"服务订阅测试"});

        RpcResponse response = client.sendRequest(request);

        if (response.hasException()) {
            System.err.println("调用异常: " + response.getException());
        } else {
            System.out.println("调用结果: " + response.getResult());
        }

        // 模拟长时间运行，观察服务变化
        Thread.sleep(600000);
        serviceDiscovery.close();
    }
}
