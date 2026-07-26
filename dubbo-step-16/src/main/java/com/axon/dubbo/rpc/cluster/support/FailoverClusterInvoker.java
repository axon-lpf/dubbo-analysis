package com.axon.dubbo.rpc.cluster.support;

import com.axon.dubbo.rpc.*;
import com.axon.dubbo.rpc.cluster.Directory;
import com.axon.dubbo.rpc.cluster.LoadBalance;

import java.util.ArrayList;
import java.util.List;

/**
 * 失败自动切换集群 Invoker
 *
 * 容错策略：
 * 1. 通过 LoadBalance 选择一个 Provider
 * 2. 调用 invoke()
 * 3. 如果失败 → 重试，切换到下一个 Provider
 * 4. 最多重试 retries 次（默认 2 次，不含第一次调用）
 *
 * 适用场景：读操作（幂等），写操作慎用（可能重复执行）
 *
 * 调用示例（retries=2，共 3 个 Provider）：
 *   第 1 次 → P1 → 失败
 *   第 2 次 → P2 → 失败
 *   第 3 次 → P3 → 成功 ✓
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.support.FailoverClusterInvoker
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public class FailoverClusterInvoker<T> extends AbstractClusterInvoker<T> {

    public FailoverClusterInvoker(Directory<T> directory) {
        super(directory);
    }

    public FailoverClusterInvoker(Directory<T> directory, LoadBalance loadBalance) {
        super(directory, loadBalance);
    }

    @Override
    protected Result doInvoke(Invocation invocation) throws Throwable {
        List<Invoker<T>> invokers = list(invocation);
        if (invokers == null || invokers.isEmpty()) {
            throw new IllegalStateException("Failover: 没有可用的 Provider");
        }

        // 重试次数（从 URL 参数获取，默认 2）
        int retries = getUrl().getParameter("retries", 2);

        // 记录已调用的 Invoker（避免重复重试同一个）
        List<Invoker<T>> invoked = new ArrayList<>();

        // 最后一次异常（所有重试都失败时抛出）
        Throwable lastException = null;

        System.out.println("[FailoverClusterInvoker] 开始调用，候选 Provider 数: "
                + invokers.size() + "，最大重试: " + retries);

        // 执行调用 + 重试
        for (int i = 0; i <= retries; i++) {
            // 1. 选择一个 Invoker（排除已失败的）
            Invoker<T> invoker = selectCandidate(invocation, invokers, invoked);
            invoked.add(invoker);

            System.out.println("[FailoverClusterInvoker] 第" + (i + 1)
                    + "次尝试 → " + invoker.getUrl().getAddress());

            try {
                // 2. 执行调用
                Result result = invoker.invoke(invocation);

                // 3. 成功 → 返回
                if (!result.hasException()) {
                    System.out.println("[FailoverClusterInvoker] 调用成功！");
                    return result;
                }

                lastException = result.getException();
                System.err.println("[FailoverClusterInvoker] 调用失败: "
                        + lastException.getMessage());

            } catch (Throwable e) {
                lastException = e;
                System.err.println("[FailoverClusterInvoker] 调用异常: " + e.getMessage());
            }

            // 4. 失败 → 等待重试间隔
            if (i < retries) {
                Thread.sleep(100); // 重试间隔
            }
        }

        // 5. 全部失败 → 抛出最终异常
        throw new RuntimeException(
                "Failover: 全部 " + (retries + 1) + " 次尝试失败，"
                        + "服务: " + getUrl().getServiceKey(),
                lastException);
    }

    /**
     * 选择一个未调用过的 Invoker
     */
    private Invoker<T> selectCandidate(Invocation invocation,
                              List<Invoker<T>> invokers,
                              List<Invoker<T>> invoked) {
        // 过滤出未调用过的
        List<Invoker<T>> candidates = new ArrayList<>(invokers);
        candidates.removeAll(invoked);

        if (candidates.isEmpty()) {
            // 所有都试过了 → 重新选择（可能是最后一个可用的）
            candidates = invokers;
        }

        return loadBalance.select(candidates, getUrl(), invocation);
    }
}
