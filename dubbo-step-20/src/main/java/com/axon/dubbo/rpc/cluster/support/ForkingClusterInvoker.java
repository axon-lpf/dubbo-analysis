package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.LoadBalance;

import java.util.List;
import java.util.concurrent.*;

/**
 * 并行调用集群 Invoker
 *
 * 同时向多个 Provider 发起调用，取第一个成功的结果。
 *
 * 算法：
 * 1. 从 Directory 获取所有 Provider
 * 2. 并发向所有 Provider（或前 forks 个）发送请求
 * 3. 第一个成功返回 → 使用该结果
 * 4. 全部失败 → 抛出异常
 *
 * 适用场景：对实时性要求很高的读操作（用冗余调用换取低延迟）
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.support.ForkingClusterInvoker
 */
public class ForkingClusterInvoker<T> extends AbstractClusterInvoker<T> {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ForkingClusterInvoker(Directory<T> directory) { super(directory); }
    public ForkingClusterInvoker(Directory<T> directory, LoadBalance lb) { super(directory, lb); }

    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        List<Invoker<T>> invokers = list(invocation);
        if (invokers.isEmpty()) throw new IllegalStateException("Forking: 没有可用的 Provider");

        int forks = Math.min(invokers.size(), 3); // 最多并行调用 3 个
        System.out.println("[ForkingClusterInvoker] 并行调用 " + forks + " 个 Provider");

        // 并发调用
        CompletionService<Result> cs = new ExecutorCompletionService<>(executor);
        int submitted = 0;
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < forks && i < invokers.size(); i++) {
            Invoker<T> invoker = invokers.get(i);
            int seq = i + 1;
            cs.submit(() -> {
                System.out.println("[ForkingClusterInvoker] 并发#" + seq
                        + " → " + invoker.getUrl().getAddress());
                try {
                    return invoker.invoke(invocation);
                } catch (Throwable t) {
                    errors.add(t);
                    throw t;
                }
            });
            submitted++;
        }

        // 等待第一个成功结果
        for (int i = 0; i < submitted; i++) {
            try {
                Future<Result> future = cs.poll(5000, TimeUnit.MILLISECONDS);
                if (future != null) {
                    Result result = future.get();
                    if (!result.hasException()) {
                        System.out.println("[ForkingClusterInvoker] 获得成功结果！");
                        return result;
                    }
                    errors.add(result.getException());
                }
            } catch (InterruptedException ignored) {}
        }

        throw new RuntimeException("Forking: 全部 " + submitted + " 个并发调用失败", errors.get(0));
    }
}
