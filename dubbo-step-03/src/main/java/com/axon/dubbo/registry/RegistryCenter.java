package com.axon.dubbo.registry;

import java.util.HashMap;
import java.util.Map;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 *
 * 模拟注册中心（服务地址注册与发现）
 */
public class RegistryCenter {

    // 服务名 -> 地址（IP:PORT）
    private static final Map<String, String> registryMap = new HashMap<>();

    // 注册服务地址（服务端调用）
    public static void register(String serviceName, String serviceAddress) {
        registryMap.put(serviceName, serviceAddress);
    }

    // 获取服务地址（客户端调用）
    public static String getServiceAddress(String serviceName) {
        return registryMap.get(serviceName);
    }

}
