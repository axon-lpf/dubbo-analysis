package com.axon.dubbo.provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ServiceRepository {


    private static final Map<String, Object> serviceMap = new ConcurrentHashMap<>();

    public static void register(String interfaceName, Object refImpl) {
        serviceMap.put(interfaceName, refImpl);
    }

    public static Object getService(String interfaceName) {
        return serviceMap.get(interfaceName);
    }
}
