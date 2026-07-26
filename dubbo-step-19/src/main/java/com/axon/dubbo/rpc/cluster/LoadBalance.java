package com.axon.dubbo.rpc.cluster;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Invoker;

import java.util.List;

/**
 * 负载均衡接口
 *
 * 从多个 Provider Invoker 中选择一个进行调用。
 * 这是 Dubbo 集群层的核心组件之一。
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.LoadBalance（@SPI("random")）
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface LoadBalance {

    /**
     * 从 Invoker 列表中选择一个
     *
     * @param invokers   可用的 Invoker 列表
     * @param url        调用相关 URL（可用于获取配置参数如 method name）
     * @param invocation 调用上下文（可用于一致性哈希等需要参数信息的策略）
     * @param <T>        服务接口类型
     * @return 选中的 Invoker
     */
    <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation);
}
