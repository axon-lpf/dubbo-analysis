package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.LoadBalance;

import java.util.List;
import java.util.concurrent.*;

/**
 * 失败自动恢复集群 Invoker
 *
 * 调用失败后，将请求放入后台重试队列，立即返回空结果给调用方。
 *
 * 适用场景：消息通知等允许最终一致性的场景。
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.support.FailbackClusterInvoker
 */
public class FailbackClusterInvoker<T> extends AbstractClusterInvoker<T> {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ConcurrentMap<Invocation, Invoker<T>> failed = new ConcurrentHashMap<>();

    public FailbackClusterInvoker(Directory<T> directory) { super(directory); }
    public FailbackClusterInvoker(Directory<T> directory, LoadBalance lb) { super(directory, lb); }

    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        List<Invoker<T>> invokers = list(invocation);
        if (invokers.isEmpty()) return new RpcResult((Object) null);

        Invoker<T> invoker = select(invocation);
        System.out.println("[FailbackClusterInvoker] 调用 → " + invoker.getUrl().getAddress());

        try {
            return invoker.invoke(invocation);
        } catch (Throwable e) {
            System.err.println("[FailbackClusterInvoker] 调用失败，加入后台重试: " + e.getMessage());
            failed.put(invocation, invoker);
            // 5 秒后后台重试
            scheduler.schedule(() -> retry(invocation, invoker), 5, TimeUnit.SECONDS);
            return new RpcResult((Object) null); // 立即返回空
        }
    }

    private void retry(Invocation invocation, Invoker<T> invoker) {
        try {
            System.out.println("[FailbackClusterInvoker] 后台重试 → " + invoker.getUrl().getAddress());
            Result result = invoker.invoke(invocation);
            if (!result.hasException()) {
                System.out.println("[FailbackClusterInvoker] 后台重试成功！");
                failed.remove(invocation);
            }
        } catch (Throwable e) {
            System.err.println("[FailbackClusterInvoker] 后台重试仍失败: " + e.getMessage());
        }
    }
}
