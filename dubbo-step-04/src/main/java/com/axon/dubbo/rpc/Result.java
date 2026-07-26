package com.axon.dubbo.rpc;

/**
 * RPC 调用结果接口
 *
 * 封装一次 RPC 调用的返回值或异常。
 * Invoker.invoke() 的返回值类型。
 *
 * 对应官方源码：org.apache.dubbo.rpc.Result
 *
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface Result {

    /**
     * 获取调用结果
     */
    Object getValue();

    /**
     * 获取调用异常（成功时返回 null）
     */
    Throwable getException();

    /**
     * 调用是否抛出了异常
     */
    boolean hasException();

    /**
     * 重建结果：有异常时抛出异常，无异常时返回正常值
     * 这是实际使用中最常用的方法
     */
    Object recreate() throws Throwable;
}
