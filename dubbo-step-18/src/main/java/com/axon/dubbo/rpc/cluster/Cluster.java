package com.axon.dubbo.rpc.cluster;

import com.axon.dubbo.rpc.Invoker;

/**
 * 集群接口
 *
 * 将 Directory（一组 Provider Invoker）合并为一个逻辑 Invoker。
 * 这个逻辑 Invoker 内部集成了负载均衡和容错策略。
 *
 * Cluster 是 Dubbo 集群层的入口：
 * Directory（服务列表）→ Cluster（join）→ Invoker（统一调用入口）
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.Cluster（@SPI("failover")）
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface Cluster {

    /**
     * 将服务目录中的多个 Invoker 合并为一个集群 Invoker
     *
     * @param directory 服务目录（包含所有 Provider Invoker）
     * @param <T>       服务接口类型
     * @return 集群 Invoker（内置负载均衡 + 容错）
     */
    <T> Invoker<T> join(Directory<T> directory);
}
