package com.axon.dubbo.rpc.cluster.loadbalance;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.cluster.AbstractLoadBalance;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 加权轮询负载均衡
 *
 * 算法（Nginx 平滑加权轮询）：
 * 1. 每个 Invoker 维护 currentWeight（当前权重）和 staticWeight（固定权重）
 * 2. 每次选择：所有 currentWeight += staticWeight，选出最大的
 * 3. 选中的 Invoker：currentWeight -= totalWeight
 *
 * 示例：A(5) B(1) C(1)，共 7 次调用：
 *   轮次  current(A,B,C)  选中  减后(A,B,C)
 *   1     (5,1,1)        A     (-2,1,1)
 *   2     (3,2,2)        A     (-4,2,2)
 *   3     (1,3,3)        B     (1,-4,3)
 *   4     (6,-3,4)       A     (-1,-3,4)
 *   5     (4,-2,5)       C     (4,-2,-2)
 *   6     (9,-1,-1)      A     (2,-1,-1)
 *   7     (7,0,0)        A     (0,0,0)
 *   结果：A=5次 B=1次 C=1次，完美符合权重！
 *
 * 对应官方源码：org.apache.dubbo.rpc.cluster.loadbalance.RoundRobinLoadBalance
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public class RoundRobinLoadBalance extends AbstractLoadBalance {

    /**
     * 每个服务方法维护自己的轮询状态
     * Key: invoker.identity → WeightedRoundRobin
     */
    private final ConcurrentMap<String, WeightedRoundRobin> weightMap = new ConcurrentHashMap<>();

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();

        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        WeightedRoundRobin selected = null;
        Invoker<T> selectedInvoker = null;

        for (Invoker<T> invoker : invokers) {
            String identity = invoker.getUrl().getAddress();
            int weight = getWeight(invoker);
            totalWeight += weight;

            WeightedRoundRobin wrr = weightMap.computeIfAbsent(key + "." + identity,
                    k -> new WeightedRoundRobin());
            wrr.setWeight(weight);
            wrr.increaseCurrent(); // current += weight

            if (wrr.getCurrent() > maxCurrent) {
                maxCurrent = wrr.getCurrent();
                selected = wrr;
                selectedInvoker = invoker;
            }
        }

        if (selected != null) {
            selected.decreaseCurrent(totalWeight); // current -= totalWeight
        }

        return selectedInvoker != null ? selectedInvoker : invokers.get(0);
    }

    /**
     * 加权轮询状态
     */
    static class WeightedRoundRobin {
        private int weight;         // 固定权重
        private long current = 0;   // 当前权重（动态变化）

        void setWeight(int weight) { this.weight = weight; }
        long getCurrent() { return current; }
        void increaseCurrent() { current += weight; }
        void decreaseCurrent(int total) { current -= total; }
    }
}
