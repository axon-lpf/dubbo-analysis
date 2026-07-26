package com.axon.dubbo.rpc.cluster;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Invoker;

import java.util.List;

/**
 * 服务目录接口
 *
 * Directory 是 Consumer 端的"本地服务目录"，
 * 缓存了从注册中心获取的 Provider Invoker 列表。
 *
 * 核心价值：
 * 1. 本地缓存：避免每次调用都查询注册中心
 * 2. 动态更新：注册中心推送变更时自动刷新列表
 * 3. 路由入口：后续集群策略（LoadBalance、Router）从 Directory 获取候选 Invoker
 *
 * Directory 是 Dubbo 集群层的入口，
 * 连接了 Registry（服务发现）和 Cluster（集群调用）。
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.Directory
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface Directory<T> {

    /**
     * 获取服务接口类型
     */
    Class<T> getInterface();

    /**
     * 获取当前可用的 Invoker 列表
     *
     * 返回值可能因 Provider 上下线而动态变化。
     * 后续步骤中，这个方法会经过 Router 过滤（路由规则）。
     *
     * @param invocation 调用上下文（可用于按参数路由）
     * @return 当前可用的 Invoker 列表
     */
    List<Invoker<T>> list(Invocation invocation);

    /**
     * 获取 Consumer 端的 URL
     */
    URL getConsumerUrl();
}
