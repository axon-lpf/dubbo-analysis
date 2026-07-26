package com.axon.dubbo.rpc.cluster.loadbalance;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.cluster.AbstractLoadBalance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 最少活跃调用数负载均衡
 *
 * 算法：
 * 1. 遍历所有 Invoker，找出活跃调用数最少的那些
 * 2. 如果有多个相同最少活跃数 → 按权重随机选一个
 * 3. 如果活跃数都一样 → 纯随机
 *
 * 活跃数 = 正在进行的调用数 = 开始调用(active+1) - 调用完成(active-1)
 *
 * 优点：自动避开慢的 Provider（慢的 Provider 活跃数自然更高）
 *
 * 注：本实现用简化的活跃数模拟（URL 参数 "active"），
 *     生产环境 Dubbo 通过 RpcStatus 实时统计。
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.loadbalance.LeastActiveLoadBalance
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    private final Random random = new Random();

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        int n = invokers.size();

        // 1. 找出最少活跃数
        int[] actives = new int[n];
        int leastActive = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int active = invokers.get(i).getUrl().getParameter("active", 0);
            actives[i] = active;
            if (active < leastActive) {
                leastActive = active;
            }
        }

        // 2. 筛选活跃数最少的 Invoker
        List<Integer> leastIndexes = new ArrayList<>();
        int totalWeight = 0;
        boolean sameWeight = true;
        int firstWeight = -1;

        for (int i = 0; i < n; i++) {
            if (actives[i] == leastActive) {
                leastIndexes.add(i);
                int w = getWeight(invokers.get(i));
                totalWeight += w;
                if (firstWeight == -1) {
                    firstWeight = w;
                } else if (w != firstWeight) {
                    sameWeight = false;
                }
            }
        }

        // 3. 只有 1 个最少活跃 → 直接返回
        if (leastIndexes.size() == 1) {
            return invokers.get(leastIndexes.get(0));
        }

        // 4. 多个最少活跃 → 加权随机
        if (!sameWeight && totalWeight > 0) {
            int offset = random.nextInt(totalWeight);
            for (int idx : leastIndexes) {
                offset -= getWeight(invokers.get(idx));
                if (offset < 0) {
                    return invokers.get(idx);
                }
            }
        }

        // 5. 所有权重相同 → 纯随机
        return invokers.get(leastIndexes.get(random.nextInt(leastIndexes.size())));
    }
}
