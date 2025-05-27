package com.axon.dubbo.consumer;

import com.axon.dubbo.registry.ServiceListener;
import com.axon.dubbo.registry.ZkClientWrapper;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ServiceDiscovery {

    private final ZkClientWrapper zkClient;
    private final String servicePath;
    private final List<String> serviceAddresses = new CopyOnWriteArrayList<>();
    private final List<ServiceListener> listeners = new CopyOnWriteArrayList<>();

    public ServiceDiscovery(String zkAddress, String serviceName) throws Exception {
        this.zkClient = new ZkClientWrapper(zkAddress);
        this.servicePath = "/dubbo/" + serviceName;

        // 订阅节点变化
        zkClient.watchChildren(servicePath, new PathChildrenCacheListener() {
            @Override
            public void childEvent(org.apache.curator.framework.CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                refreshServiceAddresses();
                notifyListeners();
            }
        });

        // 初始化节点列表
        refreshServiceAddresses();
    }

    private void refreshServiceAddresses() throws Exception {
        List<String> children = zkClient.getChildren(servicePath);
        serviceAddresses.clear();
        if (children != null) {
            for (String child : children) {
                byte[] data = zkClient.getData(servicePath + "/" + child);
                if (data != null) {
                    serviceAddresses.add(new String(data));
                }
            }
        }
        System.out.println("[ServiceDiscovery] 服务地址更新: " + serviceAddresses);
    }

    public void addListener(ServiceListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (ServiceListener listener : listeners) {
            listener.onServiceChanged(serviceAddresses);
        }
    }

    public List<String> getServiceAddresses() {
        return serviceAddresses;
    }

    public void close() {
        zkClient.close();
    }
}
