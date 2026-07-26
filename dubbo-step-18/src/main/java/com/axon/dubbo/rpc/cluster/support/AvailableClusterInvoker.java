package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.LoadBalance;

import java.util.List;

/**
 * 可用性检查集群 Invoker
 *
 * 遍历 Provider 列表，返回第一个可用的。
 *
 * 适用场景：不需要负载均衡，只找第一个能用的 Provider。
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.support.AvailableClusterInvoker
 */
public class AvailableClusterInvoker<T> extends AbstractClusterInvoker<T> {

    public AvailableClusterInvoker(Directory<T> directory) { super(directory); }
    public AvailableClusterInvoker(Directory<T> directory, LoadBalance lb) { super(directory, lb); }

    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        List<Invoker<T>> invokers = list(invocation);

        for (Invoker<T> invoker : invokers) {
            if (invoker.isAvailable()) {
                System.out.println("[AvailableClusterInvoker] 找到可用 Provider → "
                        + invoker.getUrl().getAddress());
                return invoker.invoke(invocation);
            }
        }
        throw new IllegalStateException("Available: 没有可用的 Provider");
    }
}
