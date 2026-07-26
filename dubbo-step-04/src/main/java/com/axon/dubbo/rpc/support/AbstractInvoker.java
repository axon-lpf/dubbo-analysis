package com.axon.dubbo.rpc.support;

import com.axon.dubbo.common.URL;
import com.axon.dubbo.rpc.Invocation;
import com.axon.dubbo.rpc.Invoker;
import com.axon.dubbo.rpc.Result;
import com.axon.dubbo.rpc.RpcResult;

/**
 * Invoker 抽象基类
 *
 * 提供 Invoker 接口的默认实现骨架：
 * - 管理 available 状态
 * - 管理 URL 和接口类信息
 * - 模板方法：invoke() 调用 doInvoke()，doInvoke() 由子类实现
 *
 * 模板方法模式的价值：
 * 子类只需要关心"如何执行调用"（Provider 用反射，Consumer 用网络），
 * 不需要重复实现状态管理、参数校验等通用逻辑。
 *
 * 对应官方源码：org.apache.dubbo.rpc.support.AbstractInvoker
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public abstract class AbstractInvoker<T> implements Invoker<T> {

    /**
     * 服务接口类型
     */
    private final Class<T> type;

    /**
     * 服务 URL（包含协议、地址、参数等完整元数据）
     */
    private final URL url;

    /**
     * 当前 Invoker 是否可用
     */
    private volatile boolean available = true;

    public AbstractInvoker(Class<T> type, URL url) {
        if (type == null) {
            throw new IllegalArgumentException("服务接口类型不能为 null");
        }
        if (url == null) {
            throw new IllegalArgumentException("服务 URL 不能为 null");
        }
        this.type = type;
        this.url = url;
    }

    @Override
    public Class<T> getInterface() {
        return type;
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void destroy() {
        this.available = false;
    }

    /**
     * 公共调用入口（模板方法）
     *
     * 这里可以添加：
     * - 调用前的拦截（前置过滤器）
     * - 调用参数的校验
     * - 异常的统一包装
     * - 调用后的拦截（后置过滤器）
     *
     * 后续步骤会在这里集成 Filter 链。
     */
    @Override
    public Result invoke(Invocation invocation) {
        if (!available) {
            return new RpcResult(
                    new IllegalStateException("Invoker 不可用: " + url.getServiceKey()));
        }

        try {
            // 调用子类实现的 doInvoke
            return doInvoke(invocation);
        } catch (Throwable e) {
            // 将子类抛出的所有异常包装为 RpcResult
            return new RpcResult(e);
        }
    }

    /**
     * 子类实现具体的调用逻辑（模板方法钩子）
     *
     * Provider 端：反射调用本地实现类
     * Consumer 端：网络发送请求到远程 Provider
     *
     * @param invocation 调用信息
     * @return 调用结果
     * @throws Throwable 调用异常
     */
    protected abstract Result doInvoke(Invocation invocation) throws Throwable;
}
