package com.axon.dubbo.registry;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.List;

/**
 * @author：liupengfei
 * @date：2025/5/27
 * @description：
 */
public class ZkClientWrapper {

    private CuratorFramework client;

    public ZkClientWrapper(String zkAddress) {
        client = CuratorFrameworkFactory.builder()
                                        .connectString(zkAddress)
                                        .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                                        .build();
        client.start();
    }

    public void createPersistentNode(String path) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().forPath(path);
        }
    }

    public String createEphemeralSequentialNode(String path, String data) throws Exception {
        return client.create().creatingParentsIfNeeded().withProtection()
                     .withMode(org.apache.zookeeper.CreateMode.EPHEMERAL_SEQUENTIAL)
                     .forPath(path, data.getBytes());
    }

    public void deleteNode(String path) throws Exception {
        if (client.checkExists().forPath(path) != null) {
            client.delete().forPath(path);
        }
    }

    public List<String> getChildren(String path) throws Exception {
        if (client.checkExists().forPath(path) != null) {
            return client.getChildren().forPath(path);
        }
        return null;
    }

    public void addChildListener(String path, Runnable listener) throws Exception {
        // 这里可以利用Curator的PathChildrenCache做监听，示例简化为调用listener.run()
    }


    /**
     * 监听指定路径子节点变化，触发回调
     */
    public void watchChildren(String path, PathChildrenCacheListener listener) throws Exception {
        PathChildrenCache cache = new PathChildrenCache(client, path, true);
        cache.getListenable().addListener(listener);
        cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
    }


    public byte[] getData(String path) throws Exception {
        if (client.checkExists().forPath(path) != null) {
            return client.getData().forPath(path);
        }
        return null;
    }

    public void close() {
        client.close();
    }



}
