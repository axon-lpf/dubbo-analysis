package com.axon.dubbo.rpc.filter;

import com.axon.dubbo.common.extension.Activate;
import com.axon.dubbo.rpc.*;

/**
 * 访问日志过滤器
 *
 * 记录每次 RPC 调用的基本信息。
 *
 * @Activate(group = "provider") → 在 Provider 端自动激活
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
@Activate(group = "provider", order = 100)
public class AccessLogFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        long start = System.currentTimeMillis();

        System.out.println("[AccessLog] 收到调用: " + invocation.getServiceName()
                + "." + invocation.getMethodName()
                + " | 参数: " + java.util.Arrays.toString(invocation.getArguments()));

        // 调用下一个 Filter（或最终的 Invoker）
        Result result = invoker.invoke(invocation);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[AccessLog] 调用完成: 耗时 " + elapsed + "ms"
                + " | 结果: " + (result.hasException() ? "异常" : "成功"));

        return result;
    }
}
