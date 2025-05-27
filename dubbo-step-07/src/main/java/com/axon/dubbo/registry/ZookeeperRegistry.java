package com.axon.dubbo.registry;

import java.util.ArrayList;
import java.util.List;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ZookeeperRegistry implements Registry{

    private static final String ROOT = "/dubbo";
    private final ZkClientWrapper zkClient;

    public ZookeeperRegistry(String zkAddress) {
        this.zkClient = new ZkClientWrapper(zkAddress);
        try {
            zkClient.createPersistentNode(ROOT);
        } catch (Exception e) {
            throw new RegistryException("创建根节点失败", e);
        }
    }

    @Override
    public void register(String serviceName, String serviceAddress) throws RegistryException {
        try {
            String servicePath = ROOT + "/" + serviceName;
            zkClient.createPersistentNode(servicePath);

            // 创建临时顺序节点，表示服务实例
            String addressPath = servicePath + "/address-";
            zkClient.createEphemeralSequentialNode(addressPath, serviceAddress);
            System.out.printf("[ZookeeperRegistry] 注册服务 %s -> %s%n", serviceName, serviceAddress);
        } catch (Exception e) {
            throw new RegistryException("注册服务失败", e);
        }
    }

    @Override
    public void unregister(String serviceName, String serviceAddress) throws RegistryException {
        // ZK临时节点会自动删除，注销可以留空或实现手动删除
        System.out.printf("[ZookeeperRegistry] 注销服务 %s -> %s%n", serviceName, serviceAddress);
    }

    @Override
    public List<String> lookup(String serviceName) throws RegistryException {
        try {
            String servicePath = ROOT + "/" + serviceName;
            List<String> children = zkClient.getChildren(servicePath);
            List<String> addresses = new ArrayList<>();
            if (children != null) {
                for (String child : children) {
                    // child格式如 address-00000001，获取节点数据即服务地址
                    byte[] data = zkClient.getData(servicePath + "/" + child);
                    if (data != null) {
                        addresses.add(new String(data));
                    }
                }
            }
            return addresses;
        } catch (Exception e) {
            throw new RegistryException("查询服务地址失败", e);
        }
    }
}
