package com.axon.dubbo.rpc.filter;

import com.axon.dubbo.common.extension.Activate;
import com.axon.dubbo.rpc.*;

/**
 * 耗时统计过滤器（Consumer 端）
 *
 * 在 Consumer 端记录每次 RPC 调用的耗时。
 *
 * @Activate(group = "consumer") → 在 Consumer 端自动激活
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
@Activate(group = "consumer", order = 100)
public class TimeCostFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        long start = System.currentTimeMillis();

        Result result = invoker.invoke(invocation);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[TimeCost] Consumer 端调用耗时: " + elapsed + "ms");

        return result;
    }
}
