package com.axon.dubbo.registry;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class NacosRegistry  implements Registry{

    private final NamingService namingService;

    public NacosRegistry(String serverAddr) {
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            namingService = NacosFactory.createNamingService(properties);
        } catch (NacosException e) {
            throw new RegistryException("初始化Nacos失败", e);
        }
    }

    @Override
    public void register(String serviceName, String serviceAddress) throws RegistryException {
        try {
            String[] parts = serviceAddress.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);
            namingService.registerInstance(serviceName, ip, port);
            System.out.printf("[NacosRegistry] 注册服务 %s -> %s%n", serviceName, serviceAddress);
        } catch (Exception e) {
            throw new RegistryException("注册服务失败", e);
        }
    }

    @Override
    public void unregister(String serviceName, String serviceAddress) throws RegistryException {
        try {
            String[] parts = serviceAddress.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);
            namingService.deregisterInstance(serviceName, ip, port);
            System.out.printf("[NacosRegistry] 注销服务 %s -> %s%n", serviceName, serviceAddress);
        } catch (Exception e) {
            throw new RegistryException("注销服务失败", e);
        }
    }

    @Override
    public List<String> lookup(String serviceName) throws RegistryException {
        try {
            List<Instance> instances = namingService.getAllInstances(serviceName);
            List<String> result = new ArrayList<>();
            for (Instance instance : instances) {
                result.add(instance.getIp() + ":" + instance.getPort());
            }
            return result;
        } catch (NacosException e) {
            throw new RegistryException("查询服务失败", e);
        }
    }
}
