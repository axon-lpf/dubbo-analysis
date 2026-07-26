package com.axon.dubbo.rpc.filter;

import com.axon.dubbo.common.extension.Activate;
import com.axon.dubbo.rpc.*;

/**
 * 异常处理过滤器
 *
 * 捕获 Invoker 抛出的异常，统一包装为 RpcResult。
 *
 * @Activate(group = "provider") → 在 Provider 端自动激活
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
@Activate(group = "provider", order = 200)
public class ExceptionFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        try {
            // 调用下一个 Filter
            Result result = invoker.invoke(invocation);

            // 如果已有异常，统一包装
            if (result.hasException()) {
                Throwable t = result.getException();
                System.out.println("[ExceptionFilter] 捕获到异常: " + t.getMessage());
                return new RpcResult(new RuntimeException(
                        "Provider 端异常: " + t.getMessage(), t));
            }

            return result;

        } catch (Throwable e) {
            System.out.println("[ExceptionFilter] 捕获到未处理异常: " + e.getMessage());
            return new RpcResult(new RuntimeException(
                    "Provider 端未处理异常: " + e.getMessage(), e));
        }
    }
}
