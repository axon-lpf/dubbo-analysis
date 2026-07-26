package com.axon.dubbo.rpc.cluster.directory;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.registry.NotifyListener;
import com.axon.dubbo.registry.RegistryService;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.cluster.Directory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于注册中心的服务目录
 *
 * 这是 Directory 的核心实现，实现了动态服务发现：
 *
 * 1. 构造时订阅注册中心（subscribe）
 * 2. 注册中心立即推送当前 Provider 列表（第一次 notify）
 * 3. Provider 上下线时注册中心推送变更（后续 notify）
 * 4. 每次 notify 都将 URL 列表转换为 Invoker 列表并缓存
 *
 * 实现 NotifyListener 接口：
 * RegistryDirectory 自己就是订阅回调的接收者，
 * 注册中心推送 URL 列表，RegistryDirectory 负责转换为 Invoker。
 *
 * 对应官方源码：org.apache.dubbo.registry.integration.RegistryDirectory
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class RegistryDirectory<T> implements Directory<T>, NotifyListener {

    /**
     * 服务接口类型
     */
    private final Class<T> serviceType;

    /**
     * Consumer 端 URL（用于订阅条件）
     */
    private final URL consumerUrl;

    /**
     * 注册中心引用
     */
    private final RegistryService registry;

    /**
     * 协议引用（用于将 URL 转换为 Invoker）
     */
    private final InvokerFactory<T> invokerFactory;

    /**
     * 缓存的 Invoker 列表（线程安全，volatile + 不可变集合）
     */
    private volatile List<Invoker<T>> invokers = Collections.emptyList();

    /**
     * 构造服务目录并立即订阅注册中心
     */
    public RegistryDirectory(Class<T> serviceType, URL consumerUrl,
                             RegistryService registry, InvokerFactory<T> invokerFactory) {
        this.serviceType = serviceType;
        this.consumerUrl = consumerUrl;
        this.registry = registry;
        this.invokerFactory = invokerFactory;

        // 向注册中心订阅此服务 —— 注册中心会立即推送当前列表！
        registry.subscribe(consumerUrl, this);
        System.out.println("[RegistryDirectory] 已订阅服务: " + consumerUrl.getServiceKey());
    }

    @Override
    public Class<T> getInterface() {
        return serviceType;
    }

    @Override
    public URL getConsumerUrl() {
        return consumerUrl;
    }

    /**
     * 获取当前可用的 Invoker 列表
     *
     * 返回的是缓存的最新列表，调用非常快（不涉及网络）。
     * 列表内容由 notify() 方法持续更新。
     */
    @Override
    public List<Invoker<T>> list(Invocation invocation) {
        return invokers; // volatile 保证了可见性
    }

    /**
     * 注册中心推送的变更通知
     *
     * 这是 Directory 最核心的方法：
     * 接收注册中心推送的 URL 列表 → 转换为 Invoker 列表 → 更新缓存
     *
     * @param providerUrls 全量的 Provider URL 列表
     */
    @Override
    public void notify(List<URL> providerUrls) {
        System.out.println("[RegistryDirectory] 收到通知，Provider 数量: "
                + providerUrls.size());

        // 1. 将 URL 转换为 Invoker
        List<Invoker<T>> newInvokers = new ArrayList<>();
        for (URL url : providerUrls) {
            try {
                Invoker<T> invoker = invokerFactory.createInvoker(url);
                newInvokers.add(invoker);
                System.out.println("[RegistryDirectory]   → " + url.getAddress());
            } catch (Exception e) {
                System.err.println("[RegistryDirectory] 创建 Invoker 失败: "
                        + url + ", " + e.getMessage());
            }
        }

        // 2. 原子替换（volatile write）
        this.invokers = Collections.unmodifiableList(newInvokers);
        System.out.println("[RegistryDirectory] Invoker 列表已更新，共 "
                + newInvokers.size() + " 个");
    }

    /**
     * 销毁 Directory，取消订阅
     */
    public void destroy() {
        registry.unsubscribe(consumerUrl, this);
        System.out.println("[RegistryDirectory] 已取消订阅: " + consumerUrl.getServiceKey());
    }

    /**
     * Invoker 创建工厂接口
     *
     * 将 URL 转换为 Invoker 的具体逻辑由 DubboProtocol 提供，
     * Directory 不关心 Invoker 的内部实现（远程/本地/集群）。
     *
     * @param <T> 服务接口类型
     */
    public interface InvokerFactory<T> {
        Invoker<T> createInvoker(URL providerUrl);
    }
}
