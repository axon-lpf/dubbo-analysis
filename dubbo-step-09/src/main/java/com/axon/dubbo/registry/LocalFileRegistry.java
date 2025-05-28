package com.axon.dubbo.registry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class LocalFileRegistry implements Registry {

    private static final String REGISTRY_FILE = System.getProperty("user.dir") + File.separator + "services.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final File file = new File(REGISTRY_FILE);

    private final Map<String, Set<String>> registryCache = new HashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public LocalFileRegistry() {
        loadFromFile();
    }

    @Override
    public void register(String serviceName, String serviceAddress) {
        lock.writeLock().lock();
        try {
            registryCache.computeIfAbsent(serviceName, k -> new HashSet<>()).add(serviceAddress);
            persistToFile();
            System.out.printf("[LocalFileRegistry] 注册服务: %s -> %s%n", serviceName, serviceAddress);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void unregister(String serviceName, String serviceAddress) {
        lock.writeLock().lock();
        try {
            Set<String> addresses = registryCache.get(serviceName);
            if (addresses != null) {
                addresses.remove(serviceAddress);
                if (addresses.isEmpty()) {
                    registryCache.remove(serviceName);
                }
                persistToFile();
                System.out.printf("[LocalFileRegistry] 注销服务: %s -> %s%n", serviceName, serviceAddress);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<String> lookup(String serviceName) {
        lock.readLock().lock();
        try {
            Set<String> addresses = registryCache.get(serviceName);
            if (addresses == null)
                return Collections.emptyList();
            return new ArrayList<>(addresses);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void loadFromFile() {
        if (!file.exists())
            return;

        lock.writeLock().lock();
        try {
            Map<String, List<String>> jsonMap = objectMapper.readValue(file, Map.class);
            jsonMap.forEach((key, value) -> registryCache.put(key, new HashSet<>(value)));
        } catch (IOException e) {
            throw new RegistryException("注册中心加载失败", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void persistToFile() {
        try {
            Map<String, List<String>> jsonMap = new HashMap<>();
            registryCache.forEach((key, value) -> jsonMap.put(key, new ArrayList<>(value)));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, jsonMap);
        } catch (IOException e) {
            throw new RegistryException("注册中心写入失败", e);
        }
    }
}