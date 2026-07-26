package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.LoadBalance;

import java.util.List;

/**
 * 广播调用集群 Invoker
 *
 * 逐个调用所有 Provider，任一失败即视为失败。
 *
 * 适用场景：通知所有 Provider 执行操作（如缓存刷新、配置更新）。
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.support.BroadcastClusterInvoker
 */
public class BroadcastClusterInvoker<T> extends AbstractClusterInvoker<T> {

    public BroadcastClusterInvoker(Directory<T> directory) { super(directory); }
    public BroadcastClusterInvoker(Directory<T> directory, LoadBalance lb) { super(directory, lb); }

    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        List<Invoker<T>> invokers = list(invocation);
        if (invokers.isEmpty()) throw new IllegalStateException("Broadcast: 没有可用的 Provider");

        System.out.println("[BroadcastClusterInvoker] 广播调用 " + invokers.size() + " 个 Provider");
        Result lastResult = null;

        for (int i = 0; i < invokers.size(); i++) {
            Invoker<T> invoker = invokers.get(i);
            System.out.println("[BroadcastClusterInvoker]  [" + (i + 1) + "/"
                    + invokers.size() + "] → " + invoker.getUrl().getAddress());
            try {
                Result result = invoker.invoke(invocation);
                if (result.hasException()) {
                    throw new RuntimeException("广播失败: " + invoker.getUrl().getAddress(),
                            result.getException());
                }
                lastResult = result;
            } catch (Throwable e) {
                System.err.println("[BroadcastClusterInvoker] 广播调用失败: " + e.getMessage());
                throw e;
            }
        }

        System.out.println("[BroadcastClusterInvoker] 广播完成，全部成功！");
        return lastResult != null ? lastResult : new RpcResult((Object) null);
    }
}
