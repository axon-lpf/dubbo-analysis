package com.axon.dubbo.protocol;

import com.axon.dubbo.provider.ServiceRepository;
import com.axon.dubbo.registry.Registry;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class RpcProtocol {

    private final Registry registry;

    public RpcProtocol(Registry registry) {
        this.registry = registry;
    }

    /**
     * 暴露服务逻辑
     * @param interfaceName 接口名
     * @param impl 实现类
     * @param host 本地host
     * @param port 本地端口
     */
    public Exporter export(String interfaceName, Object impl, String host, int port) {
        // 1. 注册到服务仓库
        ServiceRepository.register(interfaceName, impl);

        // 2. 注册到注册中心
        String address = host + ":" + port;
        registry.register(interfaceName, address);

        // 3. 返回Exporter，用于后续可能注销服务
        return () -> {
            registry.unregister(interfaceName, address);
            System.out.println("服务下线: " + interfaceName);
        };
    }
}
