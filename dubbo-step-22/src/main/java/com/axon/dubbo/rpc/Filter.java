package com.axon.dubbo.rpc;

import com.axon.dubbo.common.extension.SPI;

/**
 * 过滤器接口
 *
 * Filter 在 Invoker 调用链路中插入自定义处理逻辑。
 * 采用责任链模式：每个 Filter 包装下一个 Invoker，
 * 在调用前后可以执行预处理和后处理。
 *
 * 调用模型：
 *
 * Request → Filter1 → Filter2 → ... → FilterN → Invoker.invoke()
 *               │                          │
 *               └── 前置处理                └── 后置处理
 *
 * 对应官方源码：org.apache.dubbo.rpc.Filter（@SPI）
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
@SPI
public interface Filter {

    /**
     * 过滤调用
     *
     * @param invoker    下一个 Invoker（责任链的下一个节点）
     * @param invocation 调用信息
     * @return 调用结果
     */
    Result invoke(Invoker<?> invoker, Invocation invocation);
}
