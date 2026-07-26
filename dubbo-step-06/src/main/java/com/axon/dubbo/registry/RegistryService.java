package com.axon.dubbo.registry;

import com.axon.dubbo.common.URL;

import java.util.List;

/**
 * 注册中心服务接口
 *
 * 定义服务注册与发现的核心契约。
 * 这是 Dubbo 服务治理的基础——通过注册中心解耦 Provider 和 Consumer。
 *
 * 核心操作：
 * - register:   Provider 启动时将服务地址注册到注册中心
 * - unregister: Provider 关闭时从注册中心移除服务地址
 * - lookup:     Consumer 启动时从注册中心获取 Provider 地址列表
 * - subscribe:  Consumer 订阅服务变更（后续步骤实现）
 *
 * 对应官方源码：org.apache.dubbo.registry.RegistryService
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface RegistryService {

    /**
     * 注册服务提供者 URL
     *
     * Provider 启动时调用，将服务地址（协议+主机+端口+接口名）注册到注册中心。
     *
     * @param url 服务提供者 URL（如 dubbo://192.168.1.100:20880/com.axon.demo.IUserService?version=1.0.0）
     */
    void register(URL url);

    /**
     * 取消注册
     *
     * Provider 关闭时调用，从注册中心移除服务地址。
     */
    void unregister(URL url);

    /**
     * 查找服务提供者 URL 列表
     *
     * Consumer 启动时调用，获取指定服务的所有 Provider 地址。
     *
     * @param condition 查找条件（通常携带接口名和版本号）
     * @return Provider URL 列表，如果未找到返回空列表
     */
    List<URL> lookup(URL condition);

    /**
     * 订阅服务变更通知
     *
     * Consumer 订阅后，当 Provider 列表发生变化时，注册中心会通知 Consumer。
     * 本步骤暂不实现，在 Step 07 中引入 NotifyListener 机制。
     *
     * @param url      订阅的服务 URL
     * @param listener 变更监听器
     */
    void subscribe(URL url, NotifyListener listener);

    /**
     * 取消订阅
     */
    void unsubscribe(URL url, NotifyListener listener);
}
