package com.axon.dubbo.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class InMemoryRegistry implements Registry{
    // 服务名 -> 服务地址集合
    private final Map<String, Set<String>> serviceMap = new ConcurrentHashMap<>();

    @Override
    public void register(String serviceName, String serviceAddress) throws RegistryException {
        serviceMap.computeIfAbsent(serviceName, key -> new CopyOnWriteArraySet<>()).add(serviceAddress);
        System.out.printf("[Registry] 注册服务: %s -> %s%n", serviceName, serviceAddress);
    }

    @Override
    public void unregister(String serviceName, String serviceAddress) throws RegistryException {
        Set<String> addresses = serviceMap.get(serviceName);
        if (addresses != null) {
            addresses.remove(serviceAddress);
            if (addresses.isEmpty()) {
                serviceMap.remove(serviceName);
            }
            System.out.printf("[Registry] 注销服务: %s -> %s%n", serviceName, serviceAddress);
        }
    }

    @Override
    public List<String> lookup(String serviceName) throws RegistryException {
        Set<String> addresses = serviceMap.get(serviceName);
        if (addresses == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(addresses);
    }
}
