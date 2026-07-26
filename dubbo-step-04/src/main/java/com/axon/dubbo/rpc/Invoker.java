package com.axon.dubbo.rpc;

import com.axon.dubbo.common.URL;

/**
 * Invoker —— Dubbo 最核心的调用器抽象
 *
 * Invoker 是 Dubbo 中的"一等公民"，Provider 端和 Consumer 端都使用它：
 *
 * Provider 端：
 *   Invoker<T> 封装了服务实现类，invoke() 通过反射执行本地方法。
 *   T = 服务接口类型（如 IUserService）
 *
 * Consumer 端：
 *   Invoker<T> 封装了远程通信能力，invoke() 通过网络发送请求给 Provider。
 *   T = 服务接口类型（如 IUserService，客户端只知道接口）
 *
 * 这个统一的设计让 Dubbo 可以在 Invoker 上叠加各种能力：
 * Invoker → Filter → Invoker → Filter → ... → 最终的 Invoker
 *
 * 官方源码中，Invoker 是 Dubbo RPC 层的核心抽象，
 * "Protocol → Exporter / Invoker" 是理解 Dubbo 架构的关键。
 *
 * 对应官方源码：org.apache.dubbo.rpc.Invoker
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface Invoker<T> {

    /**
     * 获取服务接口的 Class 对象
     */
    Class<T> getInterface();

    /**
     * 执行一次 RPC 调用
     *
     * Provider 端：通过反射调用本地实现类的方法
     * Consumer 端：将调用信息发送到远程 Provider
     *
     * @param invocation 调用信息
     * @return 调用结果
     */
    Result invoke(Invocation invocation);

    /**
     * 获取此 Invoker 对应的 URL
     *
     * URL 包含了完整的服务元数据（协议、地址、参数等）
     */
    URL getUrl();

    /**
     * 判断此 Invoker 是否可用
     */
    boolean isAvailable();

    /**
     * 销毁此 Invoker
     */
    void destroy();
}
