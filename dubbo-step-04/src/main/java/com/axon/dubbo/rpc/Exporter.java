package com.axon.dubbo.rpc;

/**
 * Exporter —— 服务导出器
 *
 * 当一个 Invoker 被 "export" 后，就成为一个可被远程访问的服务。
 * Exporter 是 export 操作的返回值，用于管理导出后的服务生命周期。
 *
 * 工作流程：
 * 1. Protocol.export(invoker) → Exporter<T>
 * 2. 服务端启动，通过 Exporter 获取 Invoker 处理请求
 * 3. 服务端关闭时，调用 Exporter.unexport() 取消暴露
 *
 * 对应官方源码：org.apache.dubbo.rpc.Exporter
 *
 * @param <T> 服务接口类型
 * @author axon-dubbo
 * @since 1.0.0
 */
public interface Exporter<T> {

    /**
     * 获取被导出的 Invoker
     */
    Invoker<T> getInvoker();

    /**
     * 取消服务导出
     * 通常在应用关闭时调用，负责清理资源
     */
    void unexport();
}
