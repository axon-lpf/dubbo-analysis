package com.axon.dubbo.registry.zookeeper;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.NotifyListener;
import com.axon.dubbo.registry.support.AbstractRegistry;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.zookeeper.CreateMode;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZooKeeper 注册中心实现
 *
 * 基于 ZooKeeper 实现服务注册与发现：
 *
 * register():
 *   在 ZK 上创建临时节点（EPHEMERAL），
 *   Provider 断连后 ZK 自动删除节点，无需手动清理。
 *
 * unregister():
 *   删除 ZK 上的临时节点。
 *
 * lookup():
 *   读取 ZK 上指定服务的所有子节点，解码后返回 URL 列表。
 *
 * subscribe():
 *   使用 Curator 的 PathChildrenCache 监听节点变化，
 *   Provider 上下线时自动回调 NotifyListener。
 *
 * ZK 节点结构：
 * ```
 * /dubbo
 *   /com.axon.dubbo.demo.IUserService:1.0.0
 *     /providers
 *       /dubbo%3A%2F%2F192.168.1.100%3A20880%2F...   ← 临时节点
 *       /dubbo%3A%2F%2F192.168.1.101%3A20880%2F...   ← 临时节点
 *     /consumers
 *       /consumer%3A%2F%2F...
 * ```
 *
 * 为什么用临时节点？
 * - Provider 正常关闭时，会主动删除节点
 * - Provider 异常崩溃时，ZK Session 超时后自动删除节点
 * - 不需要 Provider 手动清理，天然支持故障检测
 *
 * 对应官方源码：org.apache.dubbo.registry.zookeeper.ZookeeperRegistry
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class ZookeeperRegistry extends AbstractRegistry {

    /**
     * ZK 根路径
     */
    private static final String ROOT_PATH = "/dubbo";

    /**
     * Curator 客户端
     */
    private final CuratorFramework client;

    /**
     * 服务 → PathChildrenCache 的映射（每个服务一个 Watcher）
     */
    private final Map<String, PathChildrenCache> watcherMap = new ConcurrentHashMap<>();

    public ZookeeperRegistry(URL registryUrl, CuratorFramework client) {
        super(registryUrl);
        this.client = client;
        System.out.println("[ZookeeperRegistry] ZK 注册中心已启动，连接: "
                + client.getZookeeperClient().getCurrentConnectionString());
    }

    // ==================== register（ZK 临时节点） ====================

    @Override
    public void register(URL url) {
        if (url == null) return;

        try {
            // 构建 ZK 节点路径
            String path = toProviderPath(url);

            // 创建临时节点（父节点自动创建为持久节点）
            // creatingParentsIfNeeded: 自动创建路径中不存在的父节点
            // CreateMode.EPHEMERAL: 临时节点，Session 断开后自动删除
            client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(path);

            System.out.println("[ZookeeperRegistry] 注册服务: " + url.getServiceKey()
                    + " → ZK 节点: " + path);

            // 通知订阅者
            notifyListeners(url.getServiceKey());

        } catch (Exception e) {
            System.err.println("[ZookeeperRegistry] 注册失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== unregister ====================

    @Override
    public void unregister(URL url) {
        if (url == null) return;

        try {
            String path = toProviderPath(url);

            // 删除临时节点
            client.delete().quietly().forPath(path);
            System.out.println("[ZookeeperRegistry] 取消注册: " + url.getServiceKey()
                    + " → 删除节点: " + path);

            notifyListeners(url.getServiceKey());

        } catch (Exception e) {
            System.err.println("[ZookeeperRegistry] 取消注册失败: " + e.getMessage());
        }
    }

    // ==================== lookup（从 ZK 读取） ====================

    @Override
    public List<URL> lookup(URL condition) {
        String serviceKey = condition.getServiceKey();
        String providerPath = toProvidersDir(serviceKey);

        List<URL> urls = new ArrayList<>();
        try {
            // 检查节点是否存在
            if (client.checkExists().forPath(providerPath) == null) {
                System.out.println("[ZookeeperRegistry] 未找到服务: " + serviceKey);
                return urls;
            }

            // 获取所有子节点名（即 URL 编码后的 Provider URL 字符串）
            List<String> children = client.getChildren().forPath(providerPath);

            for (String encodedUrl : children) {
                try {
                    String urlStr = URLDecoder.decode(encodedUrl, "UTF-8");
                    // 从 URL 字符串重建 URL 对象
                    URL url = rebuildUrl(urlStr);
                    urls.add(url);
                } catch (Exception e) {
                    System.err.println("[ZookeeperRegistry] 解码 URL 失败: " + encodedUrl);
                }
            }

            System.out.println("[ZookeeperRegistry] 查找服务: " + serviceKey
                    + " → 找到 " + urls.size() + " 个提供者");

        } catch (Exception e) {
            System.err.println("[ZookeeperRegistry] 查找失败: " + e.getMessage());
        }

        return urls;
    }

    // ==================== subscribe（ZK Watch 机制） ====================

    @Override
    public void subscribe(URL url, NotifyListener listener) {
        String serviceKey = url.getServiceKey();

        // 1. 注册监听器（复用 AbstractRegistry）
        addListener(serviceKey, listener);

        // 2. 创建 PathChildrenCache（如果不存在）
        watcherMap.computeIfAbsent(serviceKey, key -> {
            String providerPath = toProvidersDir(key);
            PathChildrenCache cache = new PathChildrenCache(client, providerPath, true);
            cache.getListenable().addListener((client, event) -> {
                // Provider 节点变化时，重新读取全部 Provider 并通知
                if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED
                        || event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED
                        || event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED) {

                    System.out.println("[ZookeeperRegistry] ZK 节点变化: "
                            + event.getType() + " → 刷新 " + serviceKey);

                    // 重新 lookup 并通知所有监听器
                    List<URL> currentUrls = lookup(url);
                    notifyListeners(serviceKey, currentUrls);
                }
            });

            try {
                cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
                System.out.println("[ZookeeperRegistry] 启动 Watcher: " + providerPath);
            } catch (Exception e) {
                System.err.println("[ZookeeperRegistry] Watcher 启动失败: " + e.getMessage());
            }

            return cache;
        });

        // 3. 立即推送当前 Provider 列表
        List<URL> currentUrls = lookup(url);
        listener.notify(currentUrls);
    }

    @Override
    public void unsubscribe(URL url, NotifyListener listener) {
        String serviceKey = url.getServiceKey();
        removeListener(serviceKey, listener);

        // 如果没有更多监听器，关闭 Watcher
        if (!hasListeners(serviceKey)) {
            PathChildrenCache cache = watcherMap.remove(serviceKey);
            if (cache != null) {
                try {
                    cache.close();
                    System.out.println("[ZookeeperRegistry] 关闭 Watcher: " + serviceKey);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ==================== ZK 路径工具方法 ====================

    /**
     * 将 Provider URL 转换为 ZK 节点路径
     */
    private String toProviderPath(URL url) {
        return toProvidersDir(url.getServiceKey())
                + "/" + encodeUrl(url.toString());
    }

    /**
     * 获取服务 Provider 目录路径
     */
    private String toProvidersDir(String serviceKey) {
        return ROOT_PATH + "/" + serviceKey + "/providers";
    }

    /**
     * URL 编码（ZK 节点名不能包含特殊字符）
     */
    private String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return url;
        }
    }

    /**
     * 从 URL 字符串重建 URL 对象（简化版）
     */
    private URL rebuildUrl(String urlStr) {
        // 解析 dubbo://host:port/path?key=value
        try {
            String protocol = urlStr.substring(0, urlStr.indexOf("://"));
            String rest = urlStr.substring(urlStr.indexOf("://") + 3);
            String hostPort = rest.substring(0, rest.indexOf('/'));
            String host = hostPort.contains(":") ? hostPort.substring(0, hostPort.indexOf(':')) : hostPort;
            int port = hostPort.contains(":") ? Integer.parseInt(hostPort.substring(hostPort.indexOf(':') + 1)) : 20880;
            String pathAndParams = rest.substring(rest.indexOf('/') + 1);
            String path = pathAndParams;
            if (pathAndParams.contains("?")) {
                path = pathAndParams.substring(0, pathAndParams.indexOf('?'));
            }
            return new URL(protocol, host, port, path);
        } catch (Exception e) {
            return URL.builder().path(urlStr).build();
        }
    }

    // ==================== 通知（需要访问 AbstractRegistry 的监听器） ====================

    /**
     * 通知指定服务的所有订阅者
     */
    private void notifyListeners(String serviceKey) {
        // 从 ZK 重新读取，然后通知
        URL condition = URL.builder().path(serviceKey).build();
        List<URL> urls = lookup(condition);
        notifyListeners(serviceKey, urls);
    }

    // ==================== AbstractRegistry 需要暴露的方法 ====================

    // AbstractRegistry 已经提供了这些操作的实现：
    // - addListener / removeListener / hasListeners / notifyListeners
    // 但我们需要检查它们是否可访问...

    /**
     * 关闭注册中心
     */
    public void close() {
        for (PathChildrenCache cache : watcherMap.values()) {
            try { cache.close(); } catch (Exception e) {}
        }
        watcherMap.clear();
        client.close();
        System.out.println("[ZookeeperRegistry] 已关闭");
    }
}
