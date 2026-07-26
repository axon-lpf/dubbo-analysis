package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.LoadBalance;
import com.axon.dubbo.rpc.cluster.loadbalance.RandomLoadBalance;
import com.axon.dubbo.rpc.support.AbstractInvoker;

import java.util.List;

/**
 * 集群 Invoker 抽象基类
 *
 * 将 Directory 中的多个 Provider Invoker 封装为单一调用入口。
 * 内置负载均衡策略，子类实现具体的容错逻辑。
 *
 * 调用链路：
 * ClusterInvoker.invoke(invocation)
 *   → list(invocation)       — 从 Directory 获取 Invoker 列表
 *   → select(invokers, ...)  — 通过 LoadBalance 选择一个
 *   → invoker.invoke(inv)    — 执行实际调用
 *   → result                 — 返回结果
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.support.AbstractClusterInvoker
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public abstract class AbstractClusterInvoker<T> extends AbstractInvoker<T> {

    /**
     * 服务目录（提供所有可用的 Provider Invoker）
     */
    protected final Directory<T> directory;

    /**
     * 负载均衡策略（默认加权随机）
     */
    protected final LoadBalance loadBalance;

    public AbstractClusterInvoker(Directory<T> directory) {
        super(directory.getInterface(), directory.getConsumerUrl());
        this.directory = directory;
        this.loadBalance = new RandomLoadBalance();
    }

    public AbstractClusterInvoker(Directory<T> directory, LoadBalance loadBalance) {
        super(directory.getInterface(), directory.getConsumerUrl());
        this.directory = directory;
        this.loadBalance = loadBalance;
    }

    /**
     * 从 Directory 获取当前可用的 Invoker 列表
     */
    protected List<Invoker<T>> list(Invocation invocation) {
        return directory.list(invocation);
    }

    /**
     * 通过负载均衡选择一个 Invoker
     */
    protected Invoker<T> select(Invocation invocation) {
        List<Invoker<T>> invokers = list(invocation);
        if (invokers == null || invokers.isEmpty()) {
            throw new IllegalStateException("没有可用的 Provider: " + getUrl().getServiceKey());
        }
        return loadBalance.select(invokers, getUrl(), invocation);
    }
}
