package com.axon.dubbo.registry;

import java.util.HashMap;
import java.util.Map;

/**
 * @author：liupengfei
 * @date：2025/5/26
 * @description：
 */
public class LocalRegistry {

    // 服务名 -> 实现类（用于服务端）
    private static final Map<String, Class<?>> serviceMap = new HashMap<>();

    // 注册服务（服务端用）
    public static void register(String serviceName, Class<?> implClass) {
        serviceMap.put(serviceName, implClass);
    }

    // 获取服务（服务端用）
    public static Class<?> get(String serviceName) {
        return serviceMap.get(serviceName);
    }
}
