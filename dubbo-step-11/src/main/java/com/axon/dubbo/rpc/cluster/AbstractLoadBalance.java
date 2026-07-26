package com.axon.dubbo.rpc.cluster;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Invoker;

import java.util.List;

/**
 * 负载均衡抽象基类
 *
 * 提供公共逻辑：
 * 1. 只有 1 个 Invoker 时直接返回（无需负载均衡）
 * 2. 提取权重信息
 * 3. 模板方法：doSelect() 由子类实现具体策略
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.loadbalance.AbstractLoadBalance
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public abstract class AbstractLoadBalance implements LoadBalance {

    /** 默认权重 */
    protected static final int DEFAULT_WEIGHT = 100;

    @Override
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        if (invokers == null || invokers.isEmpty()) {
            return null;
        }
        if (invokers.size() == 1) {
            return invokers.get(0);
        }
        return doSelect(invokers, url, invocation);
    }

    /**
     * 子类实现具体的负载均衡算法
     */
    protected abstract <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation);

    /**
     * 从 URL 参数中提取 Invoker 的权重
     *
     * 权重从 URL 的 "weight" 参数中读取，默认 100。
     * 权重越高，被分配到的概率越大。
     */
    protected int getWeight(Invoker<?> invoker) {
        int weight = invoker.getUrl().getParameter("weight", DEFAULT_WEIGHT);
        if (weight > 0) {
            return weight;
        }
        return DEFAULT_WEIGHT;
    }
}
