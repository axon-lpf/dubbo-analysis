package com.axon.dubbo.rpc.cluster.loadbalance;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.cluster.AbstractLoadBalance;

import java.util.List;
import java.util.Random;

/**
 * 加权随机负载均衡
 *
 * 算法：
 * 1. 计算所有 Invoker 的权重总和
 * 2. 生成 [0, totalWeight) 范围内的随机数
 * 3. 遍历 Invoker 列表，累减随机数，找到命中的 Invoker
 *
 * 示例：Provider A(权重100) B(权重200) C(权重300)
 *   总权重 = 600
 *   随机数范围 [0, 600)
 *   A ∈ [0, 100), B ∈ [100, 300), C ∈ [300, 600)
 *
 * 如果所有 Provider 权重相同 → 直接 random.nextInt(n)（更快）
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.loadbalance.RandomLoadBalance
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class RandomLoadBalance extends AbstractLoadBalance {

    private final Random random = new Random();

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        int n = invokers.size();

        // 1. 计算总权重 & 检查是否所有权重相同
        int[] weights = new int[n];
        int totalWeight = 0;
        boolean sameWeight = true;
        int firstWeight = getWeight(invokers.get(0));

        for (int i = 0; i < n; i++) {
            int w = getWeight(invokers.get(i));
            weights[i] = w;
            totalWeight += w;
            if (w != firstWeight) {
                sameWeight = false;
            }
        }

        // 2. 所有权重相同 → 纯随机
        if (sameWeight) {
            return invokers.get(random.nextInt(n));
        }

        // 3. 权重不同 → 加权随机
        int offset = random.nextInt(totalWeight);
        for (int i = 0; i < n; i++) {
            offset -= weights[i];
            if (offset < 0) {
                return invokers.get(i);
            }
        }

        // 兜底：返回最后一个
        return invokers.get(n - 1);
    }
}
