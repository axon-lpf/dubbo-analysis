package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.LoadBalance;
import java.util.List;

/**
 * 安全失败集群 Invoker
 *
 * 调用失败时忽略异常，返回空结果。
 *
 * 适用场景：日志记录、监控上报等不重要的旁路调用。
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.support.FailsafeClusterInvoker
 */
public class FailsafeClusterInvoker<T> extends AbstractClusterInvoker<T> {

    public FailsafeClusterInvoker(Directory<T> directory) { super(directory); }
    public FailsafeClusterInvoker(Directory<T> directory, LoadBalance lb) { super(directory, lb); }

    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        List<Invoker<T>> invokers = list(invocation);
        if (invokers.isEmpty()) {
            System.out.println("[FailsafeClusterInvoker] 无 Provider，返回空结果");
            return new RpcResult((Object) null);
        }

        Invoker<T> invoker = select(invocation);
        System.out.println("[FailsafeClusterInvoker] 调用 → " + invoker.getUrl().getAddress());

        try {
            return invoker.invoke(invocation);
        } catch (Throwable e) {
            System.err.println("[FailsafeClusterInvoker] 调用失败，忽略异常: " + e.getMessage());
            return new RpcResult((Object) null); // 吞掉异常，返回空
        }
    }
}
