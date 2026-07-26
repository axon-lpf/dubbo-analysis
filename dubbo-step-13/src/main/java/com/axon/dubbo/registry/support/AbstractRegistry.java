package com.axon.dubbo.registry.support;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.NotifyListener;
import com.axon.dubbo.registry.RegistryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 注册中心抽象基类
 *
 * 提供通用的注册中心实现骨架：
 * - 已注册 URL 的管理
 * - 订阅者管理
 * - 服务查找逻辑
 *
 * 子类只需实现具体的存储方式：
 * - LocalRegistry：内存 Map 存储
 * - ZookeeperRegistry：ZK 节点存储（Step 08）
 *
 * 对应官方源码：org.apache.dubbo.registry.support.AbstractRegistry
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public abstract class AbstractRegistry implements RegistryService {

    /**
     * 注册中心自己的 URL（用于标识注册中心地址）
     */
    private final URL registryUrl;

    /**
     * 已注册的服务 URL 集合（Provider 注册的 URL）
     * Key: 服务标识（接口名:版本号）
     * Value: URL 列表（一个服务可能有多个 Provider）
     */
    private final ConcurrentMap<String, List<URL>> registered = new ConcurrentHashMap<>();

    /**
     * 订阅者集合
     * Key: 订阅的服务标识
     * Value: 监听器列表
     */
    private final ConcurrentMap<String, Set<NotifyListener>> subscribed = new ConcurrentHashMap<>();

    public AbstractRegistry(URL registryUrl) {
        this.registryUrl = registryUrl;
    }

    public URL getRegistryUrl() {
        return registryUrl;
    }

    // ==================== register / unregister ====================

    @Override
    public void register(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("注册 URL 不能为 null");
        }

        String serviceKey = url.getServiceKey();
        List<URL> urlList = registered.computeIfAbsent(
                serviceKey, k -> new ArrayList<>());

        // 避免重复注册
        if (!urlList.contains(url)) {
            urlList.add(url);
            System.out.println("[AbstractRegistry] 注册服务: " + serviceKey
                    + " → " + url.getAddress()
                    + "（当前共 " + urlList.size() + " 个提供者）");

            // 通知订阅者
            notifySubscribers(serviceKey);
        }
    }

    @Override
    public void unregister(URL url) {
        if (url == null) return;

        String serviceKey = url.getServiceKey();
        List<URL> urlList = registered.get(serviceKey);
        if (urlList != null && urlList.remove(url)) {
            System.out.println("[AbstractRegistry] 取消注册: " + serviceKey
                    + " → " + url.getAddress());

            // 通知订阅者
            notifySubscribers(serviceKey);
        }
    }

    // ==================== lookup ====================

    @Override
    public List<URL> lookup(URL condition) {
        String serviceKey = condition.getServiceKey();
        List<URL> urlList = registered.get(serviceKey);
        if (urlList == null || urlList.isEmpty()) {
            System.out.println("[AbstractRegistry] 未找到服务: " + serviceKey);
            return new ArrayList<>();
        }
        System.out.println("[AbstractRegistry] 查找服务: " + serviceKey
                + " → 找到 " + urlList.size() + " 个提供者");
        return new ArrayList<>(urlList); // 返回副本，避免外部修改
    }

    // ==================== subscribe / unsubscribe ====================

    @Override
    public void subscribe(URL url, NotifyListener listener) {
        if (url == null || listener == null) return;

        String serviceKey = url.getServiceKey();
        Set<NotifyListener> listeners = subscribed.computeIfAbsent(
                serviceKey, k -> ConcurrentHashMap.newKeySet());
        listeners.add(listener);
        System.out.println("[AbstractRegistry] 添加订阅: " + serviceKey);

        // 立即推送当前已有的 Provider 列表
        List<URL> currentUrls = lookup(url);
        listener.notify(currentUrls);
    }

    @Override
    public void unsubscribe(URL url, NotifyListener listener) {
        if (url == null || listener == null) return;

        String serviceKey = url.getServiceKey();
        Set<NotifyListener> listeners = subscribed.get(serviceKey);
        if (listeners != null) {
            listeners.remove(listener);
            System.out.println("[AbstractRegistry] 取消订阅: " + serviceKey);
        }
    }

    /**
     * 通知订阅者
     */
    private void notifySubscribers(String serviceKey) {
        Set<NotifyListener> listeners = subscribed.get(serviceKey);
        if (listeners != null && !listeners.isEmpty()) {
            List<URL> currentUrls = new ArrayList<>(
                    registered.getOrDefault(serviceKey, new ArrayList<>()));
            for (NotifyListener listener : listeners) {
                try {
                    listener.notify(currentUrls);
                } catch (Exception e) {
                    System.err.println("[AbstractRegistry] 通知订阅者异常: " + e.getMessage());
                }
            }
        }
    }

    // ==================== 供子类使用的监听器管理方法 ====================

    /**
     * 添加监听器（供 ZookeeperRegistry 等子类使用）
     */
    protected void addListener(String serviceKey, NotifyListener listener) {
        Set<NotifyListener> listeners = subscribed.computeIfAbsent(
                serviceKey, k -> ConcurrentHashMap.newKeySet());
        listeners.add(listener);
    }

    /**
     * 移除监听器
     */
    protected void removeListener(String serviceKey, NotifyListener listener) {
        Set<NotifyListener> listeners = subscribed.get(serviceKey);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    /**
     * 检查指定服务是否有监听器
     */
    protected boolean hasListeners(String serviceKey) {
        Set<NotifyListener> listeners = subscribed.get(serviceKey);
        return listeners != null && !listeners.isEmpty();
    }

    /**
     * 用指定的 URL 列表通知订阅者（供 ZookeeperRegistry 等子类使用）
     */
    protected void notifyListeners(String serviceKey, List<URL> urls) {
        Set<NotifyListener> listeners = subscribed.get(serviceKey);
        if (listeners != null && !listeners.isEmpty()) {
            for (NotifyListener listener : listeners) {
                try {
                    listener.notify(urls);
                } catch (Exception e) {
                    System.err.println("[AbstractRegistry] 通知异常: " + e.getMessage());
                }
            }
        }
    }
}
