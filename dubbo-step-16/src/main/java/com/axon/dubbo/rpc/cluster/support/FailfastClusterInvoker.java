package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.LoadBalance;

import java.util.List;

/**
 * 快速失败集群 Invoker
 *
 * 只调用一次，失败立即抛出异常，不重试。
 *
 * 适用场景：非幂等写操作（如新增、删除），重试可能导致重复执行。
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.support.FailfastClusterInvoker
 */
public class FailfastClusterInvoker<T> extends AbstractClusterInvoker<T> {

    public FailfastClusterInvoker(Directory<T> directory) { super(directory); }
    public FailfastClusterInvoker(Directory<T> directory, LoadBalance lb) { super(directory, lb); }

    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        List<Invoker<T>> invokers = list(invocation);
        if (invokers.isEmpty()) throw new IllegalStateException("Failfast: 没有可用的 Provider");

        Invoker<T> invoker = select(invocation);
        System.out.println("[FailfastClusterInvoker] 调用 → " + invoker.getUrl().getAddress());

        try {
            return invoker.invoke(invocation);
        } catch (Throwable e) {
            throw new RuntimeException("Failfast: 调用失败 → " + invoker.getUrl().getAddress(), e);
        }
    }
}
